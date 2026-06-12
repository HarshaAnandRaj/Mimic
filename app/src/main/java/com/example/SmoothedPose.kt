package com.example

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

data class SmoothedPoseLandmark(
    val landmarkType: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val inFrameLikelihood: Float
)

data class SmoothedPose(val allPoseLandmarks: List<SmoothedPoseLandmark>) {
    fun getPoseLandmark(type: Int): SmoothedPoseLandmark? {
        return allPoseLandmarks.find { it.landmarkType == type }
    }
}

class AdaptivePoseProcessor {
    private val bank = SmoothJointBank()

    fun getSaveData() = bank.getAgentData()
    fun loadData(str: String) { bank.loadAgentData(str) }

    fun process(pose: Pose, timestampMs: Long): SmoothedPose {
        val rawLandmarks = pose.allPoseLandmarks
        val smoothedLandmarks = mutableListOf<SmoothedPoseLandmark>()

        for (landmark in rawLandmarks) {
            val id = landmark.landmarkType
            val conf = landmark.inFrameLikelihood
            
            var x = landmark.position.x
            var y = landmark.position.y
            var z = 0f
            
            try {
                val p3d = landmark.javaClass.getMethod("getPosition3D").invoke(landmark)
                x = p3d.javaClass.getMethod("getX").invoke(p3d) as Float
                y = p3d.javaClass.getMethod("getY").invoke(p3d) as Float
                z = p3d.javaClass.getMethod("getZ").invoke(p3d) as Float
            } catch (e: Exception) {}

            val smoothed = bank.smooth(id, x, y, z, conf, timestampMs)
            
            smoothedLandmarks.add(
                SmoothedPoseLandmark(
                    landmarkType = id,
                    x = smoothed[0],
                    y = smoothed[1],
                    z = smoothed[2],
                    inFrameLikelihood = conf
                )
            )
        }

        return SmoothedPose(smoothedLandmarks)
    }
}
