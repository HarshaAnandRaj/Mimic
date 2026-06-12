package com.example

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.example.TrackingMode
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

data class FaceTrackingFrame(
    val timestampMs: Long,
    val blendshapes: Map<String, Float>,
    val landmarks: List<FaceLandmark>,
    val transformMatrix: FloatArray?
)

data class FaceLandmark(
    val id: Int,
    val x: Float,
    val y: Float,
    val z: Float
)

class MediaPipeFaceTracker(
    context: Context,
    private val onFrame: (FaceTrackingFrame) -> Unit,
    private val onError: (Throwable) -> Unit = {}
) : AutoCloseable {

    private val landmarker = FaceLandmarker.createFromOptions(
        context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("face_landmarker.task")
                    .build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.6f)
            .setMinFacePresenceConfidence(0.6f)
            .setMinTrackingConfidence(0.6f)
            .setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(true)
            .setResultListener { result, _ ->
                onFrame(result.toFaceTrackingFrame(SystemClock.uptimeMillis()))
            }
            .setErrorListener { error -> onError(error) }
            .build()
    )

    fun detect(bitmap: Bitmap, timestampMs: Long = SystemClock.uptimeMillis()) {
        val mpImage = BitmapImageBuilder(bitmap).build()
        landmarker.detectAsync(mpImage, timestampMs)
    }

    override fun close() {
        landmarker.close()
    }
}

private fun FaceLandmarkerResult.toFaceTrackingFrame(timestampMs: Long): FaceTrackingFrame {
    val blendshapesResult = faceBlendshapes().orElse(null)
    val catList = blendshapesResult?.firstOrNull() ?: emptyList()
    
    val blendshapesMap = catList.associate { category ->
        category.categoryName().toMimicBlendshapeName() to category.score().coerceIn(0f, 1f)
    }

    val landmarksResult = faceLandmarks()
    val markList = landmarksResult.firstOrNull() ?: emptyList()
    val landmarks = markList.mapIndexed { index, p ->
        FaceLandmark(index, p.x(), p.y(), p.z())
    }

    val matrixList = facialTransformationMatrixes().orElse(null)
    val matrix = if (!matrixList.isNullOrEmpty()) {
        matrixList[0] as? FloatArray
    } else null

    return FaceTrackingFrame(timestampMs, blendshapesMap, landmarks, matrix)
}

private fun String.toMimicBlendshapeName(): String =
    replaceFirstChar { it.uppercaseChar() }

class FaceBlendshapeProcessor {
    private val neutral = mutableMapOf<String, Float>()
    private val bank = SmoothBlendshapeBank()

    fun setNeutral(samples: List<Map<String, Float>>) {
        neutral.clear()
        samples.flatMap { it.keys }.distinct().forEach { key ->
            neutral[key] = samples.map { it[key] ?: 0f }.average().toFloat()
        }
    }

    fun process(raw: Map<String, Float>, timestampMs: Long): Map<String, Float> {
        val calibratedMap = raw.mapValues { (name, value) ->
            val base = neutral[name] ?: 0f
            val denominator = (1f - base - 0.03f).coerceAtLeast(0.01f)
            ((value - base - 0.03f) / denominator).coerceIn(0f, 1f)
        }
        return bank.process(calibratedMap, timestampMs)
    }
}

class SmoothBlendshapeBank {
    private val filters = mutableMapOf<String, OneEuroFloat>()
    private var lastTimeMs: Long = 0L

    fun process(raw: Map<String, Float>, timestampMs: Long): Map<String, Float> {
        val dt = if (lastTimeMs == 0L) 1f / 30f else (timestampMs - lastTimeMs) / 1000f
        lastTimeMs = timestampMs

        return raw.mapValues { (name, value) ->
            val filter = filters.getOrPut(name) {
                when {
                    name.startsWith("EyeBlink") -> OneEuroFloat(minCutoff = 4.0f, beta = 0.08f)
                    name.startsWith("EyeLook") -> OneEuroFloat(minCutoff = 1.2f, beta = 0.03f)
                    name.startsWith("Brow") -> OneEuroFloat(minCutoff = 2.0f, beta = 0.05f)
                    name.startsWith("Mouth") || name.startsWith("Jaw") -> OneEuroFloat(minCutoff = 2.8f, beta = 0.06f)
                    else -> OneEuroFloat(minCutoff = 1.5f, beta = 0.03f)
                }
            }

            filter.filter(value.coerceIn(0f, 1f), dt)
        }
    }
}
