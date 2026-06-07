package com.example

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

fun analyzeFullBodyVisibility(pose: Pose?): Boolean {
    if (pose == null || pose.allPoseLandmarks.isEmpty()) return false

    // We consider it full body if we can see shoulders, hips, and ankles with decent confidence.
    val requiredLandmarks = listOf(
        PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE
    )

    for (type in requiredLandmarks) {
        val landmark = pose.getPoseLandmark(type)
        if (landmark == null || landmark.inFrameLikelihood < 0.6f) return false
    }
    return true
}

fun analyzeTPose(pose: Pose?): Boolean {
    if (pose == null) return false

    val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) ?: return false
    val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) ?: return false
    val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST) ?: return false
    val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST) ?: return false

    // Check if wrists are roughly at shoulder height
    val shoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2
    
    val leftWristDiffY = abs(leftWrist.position.y - leftShoulder.position.y)
    val rightWristDiffY = abs(rightWrist.position.y - rightShoulder.position.y)

    // Rough check: wrist shouldn't be much higher or lower than shoulder
    // Assuming image height is around 480 or 640, a threshold of 40-50 pixels is reasonable
    val heightTolerance = 50f 

    if (leftWristDiffY > heightTolerance || rightWristDiffY > heightTolerance) {
        return false
    }
    
    // Check if wrists are out wide (x distance)
    val shoulderDist = abs(leftShoulder.position.x - rightShoulder.position.x)
    val wristDist = abs(leftWrist.position.x - rightWrist.position.x)
    
    // In T pose, wrist distance should be significantly larger than shoulder width
    if (wristDist < shoulderDist * 2.0f) {
        return false
    }

    return true
}

fun getAverageConfidence(pose: Pose?): Float {
    if (pose == null || pose.allPoseLandmarks.isEmpty()) return 0f
    var sum = 0f
    for (l in pose.allPoseLandmarks) {
        sum += l.inFrameLikelihood
    }
    return sum / pose.allPoseLandmarks.size
}
