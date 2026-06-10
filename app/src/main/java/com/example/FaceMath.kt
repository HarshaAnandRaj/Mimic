package com.example

import com.google.mlkit.vision.facemesh.FaceMeshPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.asin

object FaceMath {
    fun distance(p1: FaceMeshPoint, p2: FaceMeshPoint): Float {
        val dx = p1.position.x - p2.position.x
        val dy = p1.position.y - p2.position.y
        val dz = p1.position.z - p2.position.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun solveBlendshapes(points: Array<FaceMeshPoint?>, faceWidth: Float, faceHeight: Float): Map<String, Float> {
        val bs = mutableMapOf<String, Float>()
        if (points.isEmpty()) return bs

        val p33 = points[33]; val p263 = points[263]
        val interocularDistance = if (p33 != null && p263 != null) {
            distance(p33, p263)
        } else {
            faceWidth * 0.4f
        }
        val scaleRef = if (interocularDistance > 0f) interocularDistance else 1f

        // MediaPipe roughly matches these indices:
        // Top lip: 13, Bottom lip: 14
        val p13 = points[13]; val p14 = points[14]
        if (p13 != null && p14 != null) {
            val mouthOpen = distance(p13, p14) / scaleRef
            bs["JawOpen"] = (mouthOpen * 2.5f).coerceIn(0f, 1f)
        }

        // Left eye closed: vertical distance between 159 (top) and 145 (bottom)
        val p159 = points[159]; val p145 = points[145]
        if (p159 != null && p145 != null) {
            val leftEyeOpen = distance(p159, p145) / scaleRef
            bs["EyeBlinkLeft"] = (1f - (leftEyeOpen * 6f)).coerceIn(0f, 1f)
        }
        
        // Right eye closed: vertical distance between 386 (top) and 374 (bottom)
        val p386 = points[386]; val p374 = points[374]
        if (p386 != null && p374 != null) {
            val rightEyeOpen = distance(p386, p374) / scaleRef
            bs["EyeBlinkRight"] = (1f - (rightEyeOpen * 6f)).coerceIn(0f, 1f)
        }
        
        // Mouth Smile Left: distance from left corner (61) to left eye corner (133)
        // Usually, when smiling, the mouth corner goes UP and OUT
        val p61 = points[61]; val p0 = points[0]; val p291 = points[291]
        val restingMouthHalfWidth = scaleRef * 0.5f
        if (p61 != null && p0 != null) {
            val mouthLeftToCenter = distance(p61, p0)
            bs["MouthSmileLeft"] = ((mouthLeftToCenter - restingMouthHalfWidth) / (scaleRef * 0.25f)).coerceIn(0f, 1f)
        }
        if (p291 != null && p0 != null) {
            val mouthRightToCenter = distance(p291, p0)
            bs["MouthSmileRight"] = ((mouthRightToCenter - restingMouthHalfWidth) / (scaleRef * 0.25f)).coerceIn(0f, 1f)
        }
        
        // Brow up center : average of brows vs nose root
        val p52 = points[52]; val p282 = points[282]; val p168 = points[168]
        if (p52 != null && p282 != null && p168 != null) {
            val browCenter = (p52.position.y + p282.position.y) * 0.5f
            val noseRoot = p168.position.y
            val browDist = Math.abs(browCenter - noseRoot) / scaleRef
            bs["BrowsUpCenter"] = ((browDist - 0.15f) * 5f).coerceIn(0f, 1f)
        }

        return bs
    }

    // Calculates 6-DoF Head Pose
    fun solveHeadPose(points: Array<FaceMeshPoint?>): FloatArray {
        val defaultPose = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 1f) // X, Y, Z, Qx, Qy, Qz, Qw
        if (points.isEmpty()) return defaultPose

        val nose = points[1]
        val leftEar = points[234]
        val rightEar = points[454]
        val chin = points[152]
        val forehead = points[10]
        
        if (nose == null || leftEar == null || rightEar == null || chin == null || forehead == null) {
            return defaultPose
        }

        // Position is just the nose
        val x = nose.position.x
        val y = nose.position.y
        val z = nose.position.z

        // Normal vector for Pitch and Yaw
        // Vector from chin to forehead (Up)
        val vx = forehead.position.x - chin.position.x
        val vy = forehead.position.y - chin.position.y
        val vz = forehead.position.z - chin.position.z
        
        // Vector from Left to Right Ear (Right)
        val hx = rightEar.position.x - leftEar.position.x
        val hy = rightEar.position.y - leftEar.position.y
        val hz = rightEar.position.z - leftEar.position.z

        val pitch = atan2(vz.toDouble(), vy.toDouble()).toFloat()
        val yaw = atan2(hz.toDouble(), hx.toDouble()).toFloat()
        val roll = atan2(vy.toDouble(), hx.toDouble()).toFloat() // Approximate

        // Convert Euler to Quaternion
        val cy = cos(yaw * 0.5)
        val sy = sin(yaw * 0.5)
        val cp = cos(pitch * 0.5)
        val sp = sin(pitch * 0.5)
        val cr = cos(roll * 0.5)
        val sr = sin(roll * 0.5)

        val qw = (cr * cp * cy + sr * sp * sy).toFloat()
        val qx = (sr * cp * cy - cr * sp * sy).toFloat()
        val qy = (cr * sp * cy + sr * cp * sy).toFloat()
        val qz = (cr * cp * sy - sr * sp * cy).toFloat()

        return floatArrayOf(x, y, z, qx, qy, qz, qw)
    }

    fun solveEyeGaze(points: Array<FaceMeshPoint?>): Map<String, Float> {
        val bs = mutableMapOf<String, Float>()
        // Right eye (MediaPipe indices)
        val reLeft = points[362]; val reRight = points[263]
        val rePupil = points[473] ?: points[468] // roughly pupil center in 468 mesh
        
        if (reLeft != null && reRight != null && rePupil != null) {
            val reWidth = distance(reLeft, reRight)
            val reCenterToLeft = distance(rePupil, reLeft)
            val reCenterToRight = distance(rePupil, reRight)
            
            bs["EyeLookInRight"] = if (reCenterToLeft < reCenterToRight) 1f else 0f
            bs["EyeLookOutRight"] = if (reCenterToRight < reCenterToLeft) 1f else 0f
        }
        return bs
    }
}
