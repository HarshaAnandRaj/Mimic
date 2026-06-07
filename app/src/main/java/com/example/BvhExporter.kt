package com.example

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.*

class BvhExporter {
    
    private val SCALE_FACTOR = 100.0f
    
    fun export(jsonFile: File, outputFile: File) {
        val jsonStr = jsonFile.readText()
        val frames = JSONArray(jsonStr)

        val validFrames = mutableListOf<JSONObject>()
        for (i in 0 until frames.length()) {
            val obj = frames.getJSONObject(i)
            if (obj.has("landmarks")) {
                validFrames.add(obj)
            }
        }
        
        if (validFrames.isEmpty()) {
            outputFile.writeText("ERROR: No valid frames found.")
            return
        }
        
        // Define Joints and Hierarchy
        val jointNames = listOf(
            "Hips", 
            "LeftHip", "LeftKnee", "LeftAnkle", 
            "RightHip", "RightKnee", "RightAnkle", 
            "Spine", "Neck", 
            "LeftShoulder", "LeftElbow", "LeftWrist", 
            "RightShoulder", "RightElbow", "RightWrist"
        )
        val jointIds = listOf(
            -1, // Hips
            23, 25, 27, // L Leg
            24, 26, 28, // R Leg
            -2, -3, // Spine, Neck
            11, 13, 15, // L Arm
            12, 14, 16  // R Arm
        )
        val parentIndices = listOf(
            -1, // Hips
            0, 1, 2, 
            0, 4, 5,
            0, 7,
            8, 9, 10,
            8, 12, 13
        )
        
        // Extract PosMap for all frames
        val framesPos = validFrames.map { frame ->
            val landmarks = frame.getJSONArray("landmarks")
            val posMap = mutableMapOf<Int, FloatArray>()
            for (i in 0 until landmarks.length()) {
                val lm = landmarks.getJSONObject(i)
                val id = lm.getInt("id")
                val x = (lm.getDouble("x") * SCALE_FACTOR).toFloat()
                val y = (-lm.getDouble("y") * SCALE_FACTOR).toFloat()
                val z = (lm.getDouble("z") * SCALE_FACTOR).toFloat()
                posMap[id] = floatArrayOf(x, y, z)
            }
            val hips = avgPos(posMap[23], posMap[24])
            val neck = avgPos(posMap[11], posMap[12])
            val spine = avgPos(hips, neck)
            posMap[-1] = hips
            posMap[-2] = spine
            posMap[-3] = neck
            
            jointIds.map { id -> posMap[id] ?: floatArrayOf(0f,0f,0f) }
        }
        
        // Frame 0 is T-Pose / Reference
        val tpose = framesPos.first()
        
        val offsets = Array(tpose.size) { floatArrayOf(0f,0f,0f) }
        for (i in 1 until tpose.size) {
            val pIdx = parentIndices[i]
            offsets[i] = floatArrayOf(
                tpose[i][0] - tpose[pIdx][0],
                tpose[i][1] - tpose[pIdx][1],
                tpose[i][2] - tpose[pIdx][2]
            )
        }
        offsets[0] = floatArrayOf(0f, 0f, 0f) // Hips offset initially
        
        val sb = java.lang.StringBuilder()
        sb.append("HIERARCHY\n")
        
        // Build Hierarchy recursively
        fun buildNode(idx: Int, indent: String) {
            if (idx == 0) {
                sb.append("${indent}ROOT ${jointNames[idx]}\n")
            } else {
                sb.append("${indent}JOINT ${jointNames[idx]}\n")
            }
            sb.append("${indent}{\n")
            sb.append(java.util.Locale.US.let { java.lang.String.format(it, "${indent}  OFFSET %.4f %.4f %.4f\n", offsets[idx][0], offsets[idx][1], offsets[idx][2]) })
            if (idx == 0) {
                sb.append("${indent}  CHANNELS 6 Xposition Yposition Zposition Zrotation Xrotation Yrotation\n")
            } else {
                sb.append("${indent}  CHANNELS 3 Zrotation Xrotation Yrotation\n")
            }
            
            val children = parentIndices.indices.filter { parentIndices[it] == idx }
            if (children.isEmpty()) {
                sb.append("${indent}  End Site\n")
                sb.append("${indent}  {\n")
                val fakeOff = floatArrayOf(0f, -10f, 0f)
                sb.append(java.util.Locale.US.let { java.lang.String.format(it, "${indent}    OFFSET %.4f %.4f %.4f\n", fakeOff[0], fakeOff[1], fakeOff[2]) })
                sb.append("${indent}  }\n")
            } else {
                for (c in children) {
                    buildNode(c, "$indent  ")
                }
            }
            sb.append("${indent}}\n")
        }
        buildNode(0, "")
        
        sb.append("MOTION\n")
        sb.append("Frames: ${validFrames.size}\n")
        sb.append("Frame Time: 0.0333333\n")
        
        for (f in framesPos.indices) {
            val currPos = framesPos[f]
            val globalRots = Array(tpose.size) { floatArrayOf(1f,0f,0f,0f) }
            
            for (i in tpose.indices) {
                val children = parentIndices.indices.filter { parentIndices[it] == i }
                if (children.isEmpty()) continue
                
                var q = floatArrayOf(1f,0f,0f,0f)
                if (i == 0) {
                    val upRest = normalize(sub(tpose[7], tpose[0]))
                    val rightRest = normalize(sub(tpose[4], tpose[1]))
                    val fwdRest = normalize(cross(upRest, rightRest))
                    
                    val upCurr = normalize(sub(currPos[7], currPos[0]))
                    val rightCurr = normalize(sub(currPos[4], currPos[1]))
                    val fwdCurr = normalize(cross(upCurr, rightCurr))
                    
                    q = quatFromBaseVectors(rightRest, upRest, fwdRest, rightCurr, upCurr, fwdCurr)
                } else if (i == 8) {
                    val upRest = normalize(sub(tpose[8], tpose[7]))
                    val rightRest = normalize(sub(tpose[12], tpose[9])) 
                    val fwdRest = normalize(cross(upRest, rightRest))
                    
                    val upCurr = normalize(sub(currPos[8], currPos[7]))
                    val rightCurr = normalize(sub(currPos[12], currPos[9]))
                    val fwdCurr = normalize(cross(upCurr, rightCurr))
                    
                    q = quatFromBaseVectors(rightRest, upRest, fwdRest, rightCurr, upCurr, fwdCurr)
                } else {
                    val c = children.first()
                    val vRest = normalize(sub(tpose[c], tpose[i]))
                    val vCurr = normalize(sub(currPos[c], currPos[i]))
                    q = fromToRotation(vRest, vCurr)
                }
                globalRots[i] = q
            }
            
            val localRots = Array(tpose.size) { floatArrayOf(1f,0f,0f,0f) }
            localRots[0] = globalRots[0]
            for (i in 1 until tpose.size) {
                val pIdx = parentIndices[i]
                localRots[i] = quatMul(quatInv(globalRots[pIdx]), globalRots[i])
            }
            
            val hipPos = currPos[0]
            sb.append(java.util.Locale.US.let { java.lang.String.format(it, "%.4f %.4f %.4f ", hipPos[0], hipPos[1], hipPos[2]) })
            
            for (i in tpose.indices) {
                val euler = quatToEulerZXY(localRots[i])
                val ez = Math.toDegrees(euler[0].toDouble())
                val ex = Math.toDegrees(euler[1].toDouble())
                val ey = Math.toDegrees(euler[2].toDouble())
                sb.append(java.util.Locale.US.let { java.lang.String.format(it, "%.4f %.4f %.4f ", ez, ex, ey) })
            }
            sb.append("\n")
        }
        outputFile.writeText(sb.toString())
    }
    
    private fun avgPos(p1: FloatArray?, p2: FloatArray?): FloatArray {
        if (p1 == null || p2 == null) return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf((p1[0]+p2[0])/2f, (p1[1]+p2[1])/2f, (p1[2]+p2[2])/2f)
    }
    
    private fun sub(a: FloatArray, b: FloatArray): FloatArray {
        return floatArrayOf(a[0]-b[0], a[1]-b[1], a[2]-b[2])
    }
    
    private fun cross(a: FloatArray, b: FloatArray): FloatArray {
        return floatArrayOf(
            a[1]*b[2] - a[2]*b[1],
            a[2]*b[0] - a[0]*b[2],
            a[0]*b[1] - a[1]*b[0]
        )
    }
    
    private fun dot(a: FloatArray, b: FloatArray): Float {
        return a[0]*b[0] + a[1]*b[1] + a[2]*b[2]
    }
    
    private fun length(a: FloatArray): Float {
        return sqrt(dot(a, a))
    }
    
    private fun normalize(a: FloatArray): FloatArray {
        val l = length(a)
        if (l < 0.0001f) return floatArrayOf(1f, 0f, 0f)
        return floatArrayOf(a[0]/l, a[1]/l, a[2]/l)
    }
    
    private fun fromToRotation(v1: FloatArray, v2: FloatArray): FloatArray {
        val u1 = normalize(v1)
        val u2 = normalize(v2)
        val d = dot(u1, u2)
        if (d > 0.9999f) return floatArrayOf(1f, 0f, 0f, 0f)
        if (d < -0.9999f) {
            var ortho = cross(floatArrayOf(1f,0f,0f), u1)
            if (length(ortho) < 0.01f) ortho = cross(floatArrayOf(0f,1f,0f), u1)
            ortho = normalize(ortho)
            return floatArrayOf(0f, ortho[0], ortho[1], ortho[2])
        }
        val w = cross(u1, u2)
        val q = floatArrayOf(1f + d, w[0], w[1], w[2])
        val ql = sqrt(q[0]*q[0] + q[1]*q[1] + q[2]*q[2] + q[3]*q[3])
        return floatArrayOf(q[0]/ql, q[1]/ql, q[2]/ql, q[3]/ql)
    }
    
    private fun quatMul(q1: FloatArray, q2: FloatArray): FloatArray {
        return floatArrayOf(
            q1[0]*q2[0] - q1[1]*q2[1] - q1[2]*q2[2] - q1[3]*q2[3],
            q1[0]*q2[1] + q1[1]*q2[0] + q1[2]*q2[3] - q1[3]*q2[2],
            q1[0]*q2[2] - q1[1]*q2[3] + q1[2]*q2[0] + q1[3]*q2[1],
            q1[0]*q2[3] + q1[1]*q2[2] - q1[2]*q2[1] + q1[3]*q2[0]
        )
    }
    
    private fun quatInv(q: FloatArray): FloatArray {
        return floatArrayOf(q[0], -q[1], -q[2], -q[3])
    }
    
    private fun quatFromBaseVectors(
        rx1: FloatArray, ry1: FloatArray, rz1: FloatArray,
        rx2: FloatArray, ry2: FloatArray, rz2: FloatArray
    ): FloatArray {
        val m00 = rx2[0]*rx1[0] + ry2[0]*ry1[0] + rz2[0]*rz1[0]
        val m01 = rx2[0]*rx1[1] + ry2[0]*ry1[1] + rz2[0]*rz1[1]
        val m02 = rx2[0]*rx1[2] + ry2[0]*ry1[2] + rz2[0]*rz1[2]
        val m10 = rx2[1]*rx1[0] + ry2[1]*ry1[0] + rz2[1]*rz1[0]
        val m11 = rx2[1]*rx1[1] + ry2[1]*ry1[1] + rz2[1]*rz1[1]
        val m12 = rx2[1]*rx1[2] + ry2[1]*ry1[2] + rz2[1]*rz1[2]
        val m20 = rx2[2]*rx1[0] + ry2[2]*ry1[0] + rz2[2]*rz1[0]
        val m21 = rx2[2]*rx1[1] + ry2[2]*ry1[1] + rz2[2]*rz1[1]
        val m22 = rx2[2]*rx1[2] + ry2[2]*ry1[2] + rz2[2]*rz1[2]
        
        val tr = m00 + m11 + m22
        var qw: Float; var qx: Float; var qy: Float; var qz: Float
        if (tr > 0) {
            val S = sqrt(tr + 1.0f) * 2f
            qw = 0.25f * S
            qx = (m21 - m12) / S
            qy = (m02 - m20) / S
            qz = (m10 - m01) / S
        } else if ((m00 > m11) && (m00 > m22)) {
            val S = sqrt(1.0f + m00 - m11 - m22) * 2f
            qw = (m21 - m12) / S
            qx = 0.25f * S
            qy = (m01 + m10) / S
            qz = (m02 + m20) / S
        } else if (m11 > m22) {
            val S = sqrt(1.0f + m11 - m00 - m22) * 2f
            qw = (m02 - m20) / S
            qx = (m01 + m10) / S
            qy = 0.25f * S
            qz = (m12 + m21) / S
        } else {
            val S = sqrt(1.0f + m22 - m00 - m11) * 2f
            qw = (m10 - m01) / S
            qx = (m02 + m20) / S
            qy = (m12 + m21) / S
            qz = 0.25f * S
        }
        val ql = sqrt(qw*qw + qx*qx + qy*qy + qz*qz)
        return floatArrayOf(qw/ql, qx/ql, qy/ql, qz/ql)
    }
    
    private fun quatToEulerZXY(q: FloatArray): FloatArray {
        val w = q[0]; val x = q[1]; val y = q[2]; val z = q[3]
        val rx = asin(max(-1f, min(1f, 2f*(w*x - y*z))))
        val ry = atan2(2f*(w*y + x*z), w*w - x*x - y*y + z*z)
        val rz = atan2(2f*(w*z + x*y), w*w - x*x + y*y - z*z)
        return floatArrayOf(rz, rx, ry)
    }
}
