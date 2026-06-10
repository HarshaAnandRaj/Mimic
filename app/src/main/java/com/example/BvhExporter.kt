package com.example

import java.io.File
import kotlin.math.*

class BvhExporter {
    
    private val SCALE_FACTOR = 100.0f
    
    data class FrameData(
        val joints: List<FloatArray>,
        val leftPinky: FloatArray,
        val rightPinky: FloatArray
    )
    
    fun export(jsonFile: File, outputFile: File) {
        val framesPos = mutableListOf<FrameData>()
        var metadataTPose: FrameData? = null
        
        // Define Joints and Hierarchy
        val jointNames = listOf(
            "Hips", 
            "LeftHip", "LeftKnee", "LeftAnkle", 
            "RightHip", "RightKnee", "RightAnkle", 
            "Spine", "Neck", 
            "LeftShoulder", "LeftElbow", "LeftWrist", 
            "RightShoulder", "RightElbow", "RightWrist",
            "LeftHand", "RightHand"
        )
        val jointIds = listOf(
            -1, // Hips
            23, 25, 27, // L Leg
            24, 26, 28, // R Leg
            -2, -3, // Spine, Neck
            11, 13, 15, // L Arm
            12, 14, 16,  // R Arm
            19, 20      // L Hand, R Hand (Index knuckles)
        )
        val parentIndices = listOf(
            -1, // Hips
            0, 1, 2, 
            0, 4, 5,
            0, 7,
            8, 9, 10,
            8, 12, 13,
            11, // Left Hand's parent is Left Wrist (11)
            14  // Right Hand's parent is Right Wrist (14)
        )
        
        try {
            val reader = android.util.JsonReader(java.io.FileReader(jsonFile))
            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginObject()
                
                var isMetadata = false
                val posMap = mutableMapOf<Int, FloatArray>()
                
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    when (name) {
                        "type" -> {
                            if (reader.nextString() == "metadata") isMetadata = true
                        }
                        "tpose" -> {
                            if (isMetadata) {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    var id = 0; var x = 0f; var y = 0f; var z = 0f
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            "id" -> id = reader.nextInt()
                                            "x" -> x = (reader.nextDouble() * SCALE_FACTOR).toFloat()
                                            "y" -> y = (-reader.nextDouble() * SCALE_FACTOR).toFloat()
                                            "z" -> z = (reader.nextDouble() * SCALE_FACTOR).toFloat()
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                    posMap[id] = floatArrayOf(x, y, z)
                                }
                                reader.endArray()
                            } else {
                                reader.skipValue()
                            }
                        }
                        "landmarks" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                var id = 0; var x = 0f; var y = 0f; var z = 0f
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "id" -> id = reader.nextInt()
                                        "x" -> x = (reader.nextDouble() * SCALE_FACTOR).toFloat()
                                        "y" -> y = (-reader.nextDouble() * SCALE_FACTOR).toFloat()
                                        "z" -> z = (reader.nextDouble() * SCALE_FACTOR).toFloat()
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                posMap[id] = floatArrayOf(x, y, z)
                            }
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                
                if (posMap.isNotEmpty()) {
                    val hips = avgPos(posMap[23], posMap[24])
                    val neck = avgPos(posMap[11], posMap[12])
                    val spine = avgPos(hips, neck)
                    posMap[-1] = hips
                    posMap[-2] = spine
                    posMap[-3] = neck
                    
                    val processedList = jointIds.map { id -> posMap[id] ?: floatArrayOf(0f,0f,0f) }
                    
                    val frameData = FrameData(
                        joints = processedList,
                        leftPinky = posMap[17] ?: floatArrayOf(0f,0f,0f),
                        rightPinky = posMap[18] ?: floatArrayOf(0f,0f,0f)
                    )
                    
                    if (isMetadata) {
                        metadataTPose = frameData
                    } else {
                        framesPos.add(frameData)
                    }
                }
            }
            reader.endArray()
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.writeText("ERROR: Failed to parse file.")
            return
        }
        
        if (framesPos.isEmpty()) {
            outputFile.writeText("ERROR: No valid frames found.")
            return
        }
        
        // Try getting T-Pose from metadata
        var tpose: FrameData? = metadataTPose
        
        if (tpose == null || tpose.joints.size != jointIds.size) {
            tpose = framesPos.first()
        }
        
        val offsets = Array(tpose.joints.size) { floatArrayOf(0f,0f,0f) }
        for (i in 1 until tpose.joints.size) {
            val pIdx = parentIndices[i]
            offsets[i] = floatArrayOf(
                tpose.joints[i][0] - tpose.joints[pIdx][0],
                tpose.joints[i][1] - tpose.joints[pIdx][1],
                tpose.joints[i][2] - tpose.joints[pIdx][2]
            )
        }
        offsets[0] = floatArrayOf(0f, 0f, 0f) // Hips offset initially
        
        outputFile.bufferedWriter().use { writer ->
            writer.write("HIERARCHY\n")
            
            // Build Hierarchy recursively
            fun buildNode(idx: Int, indent: String) {
                if (idx == 0) {
                    writer.write("${indent}ROOT ${jointNames[idx]}\n")
                } else {
                    writer.write("${indent}JOINT ${jointNames[idx]}\n")
                }
                writer.write("${indent}{\n")
                writer.write(java.util.Locale.US.let { java.lang.String.format(it, "${indent}  OFFSET %.4f %.4f %.4f\n", offsets[idx][0], offsets[idx][1], offsets[idx][2]) })
                if (idx == 0) {
                    writer.write("${indent}  CHANNELS 6 Xposition Yposition Zposition Zrotation Xrotation Yrotation\n")
                } else {
                    writer.write("${indent}  CHANNELS 3 Zrotation Xrotation Yrotation\n")
                }
                
                val children = parentIndices.indices.filter { parentIndices[it] == idx }
                if (children.isEmpty()) {
                    writer.write("${indent}  End Site\n")
                    writer.write("${indent}  {\n")
                    val fakeOff = floatArrayOf(0f, -10f, 0f)
                    writer.write(java.util.Locale.US.let { java.lang.String.format(it, "${indent}    OFFSET %.4f %.4f %.4f\n", fakeOff[0], fakeOff[1], fakeOff[2]) })
                    writer.write("${indent}  }\n")
                } else {
                    for (c in children) {
                        buildNode(c, "$indent  ")
                    }
                }
                writer.write("${indent}}\n")
            }
            buildNode(0, "")
            
            writer.write("MOTION\n")
            writer.write("Frames: ${framesPos.size}\n")
            writer.write("Frame Time: 0.0333333\n")
            
            for (f in framesPos.indices) {
                val currPos = framesPos[f]
                val globalRots = Array(tpose.joints.size) { floatArrayOf(1f,0f,0f,0f) }
                
                for (i in tpose.joints.indices) {
                    val children = parentIndices.indices.filter { parentIndices[it] == i }
                    if (children.isEmpty()) continue
                    
                    var q = floatArrayOf(1f,0f,0f,0f)
                    if (i == 0) {
                        val upRest = normalize(sub(tpose.joints[7], tpose.joints[0]))
                        val rightRest = normalize(sub(tpose.joints[4], tpose.joints[1]))
                        val fwdRest = normalize(cross(upRest, rightRest))
                        
                        val upCurr = normalize(sub(currPos.joints[7], currPos.joints[0]))
                        val rightCurr = normalize(sub(currPos.joints[4], currPos.joints[1]))
                        val fwdCurr = normalize(cross(upCurr, rightCurr))
                        
                        q = quatFromBaseVectors(rightRest, upRest, fwdRest, rightCurr, upCurr, fwdCurr)
                    } else if (i == 8) {
                        val upRest = normalize(sub(tpose.joints[8], tpose.joints[7]))
                        val rightRest = normalize(sub(tpose.joints[12], tpose.joints[9])) 
                        val fwdRest = normalize(cross(upRest, rightRest))
                        
                        val upCurr = normalize(sub(currPos.joints[8], currPos.joints[7]))
                        val rightCurr = normalize(sub(currPos.joints[12], currPos.joints[9]))
                        val fwdCurr = normalize(cross(upCurr, rightCurr))
                        
                        q = quatFromBaseVectors(rightRest, upRest, fwdRest, rightCurr, upCurr, fwdCurr)
                    } else if (i == 11) { // Left Wrist
                        val midRest = avgPos(tpose.joints[15], tpose.leftPinky)
                        val fwdRest = normalize(sub(midRest, tpose.joints[11]))
                        val rightRest = normalize(sub(tpose.joints[15], tpose.leftPinky))
                        val upRest = normalize(cross(fwdRest, rightRest))
                        
                        val midCurr = avgPos(currPos.joints[15], currPos.leftPinky)
                        val fwdCurr = normalize(sub(midCurr, currPos.joints[11]))
                        val rightCurr = normalize(sub(currPos.joints[15], currPos.leftPinky))
                        val upCurr = normalize(cross(fwdCurr, rightCurr))
                        
                        q = quatFromBaseVectors(rightRest, upRest, fwdRest, rightCurr, upCurr, fwdCurr)
                    } else if (i == 14) { // Right Wrist
                        val midRest = avgPos(tpose.joints[16], tpose.rightPinky)
                        val fwdRest = normalize(sub(midRest, tpose.joints[14]))
                        // Right hand: Pinky to Index points Left. We want Right. So Index to Pinky points Right.
                        // Wait! Index(20) is left, Pinky(18) is right (for Palm down). So Pinky(18) - Index(20) points Right.
                        val rightRest = normalize(sub(tpose.rightPinky, tpose.joints[16]))
                        val upRest = normalize(cross(fwdRest, rightRest))
                        
                        val midCurr = avgPos(currPos.joints[16], currPos.rightPinky)
                        val fwdCurr = normalize(sub(midCurr, currPos.joints[14]))
                        val rightCurr = normalize(sub(currPos.rightPinky, currPos.joints[16]))
                        val upCurr = normalize(cross(fwdCurr, rightCurr))
                        
                        q = quatFromBaseVectors(rightRest, upRest, fwdRest, rightCurr, upCurr, fwdCurr)
                    } else {
                        val c = children.first()
                        val vRest = normalize(sub(tpose.joints[c], tpose.joints[i]))
                        val vCurr = normalize(sub(currPos.joints[c], currPos.joints[i]))
                        q = fromToRotation(vRest, vCurr)
                    }
                    globalRots[i] = q
                }
                
                val localRots = Array(tpose.joints.size) { floatArrayOf(1f,0f,0f,0f) }
                localRots[0] = globalRots[0]
                for (i in 1 until tpose.joints.size) {
                    val pIdx = parentIndices[i]
                    localRots[i] = quatMul(quatInv(globalRots[pIdx]), globalRots[i])
                }
                
                val hipPos = currPos.joints[0]
                writer.write(java.util.Locale.US.let { java.lang.String.format(it, "%.4f %.4f %.4f ", hipPos[0], hipPos[1], hipPos[2]) })
                
                for (i in tpose.joints.indices) {
                    val euler = quatToEulerZXY(localRots[i])
                    val ez = Math.toDegrees(euler[0].toDouble())
                    val ex = Math.toDegrees(euler[1].toDouble())
                    val ey = Math.toDegrees(euler[2].toDouble())
                    writer.write(java.util.Locale.US.let { java.lang.String.format(it, "%.4f %.4f %.4f ", ez, ex, ey) })
                }
                writer.write("\n")
            }
        }
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
