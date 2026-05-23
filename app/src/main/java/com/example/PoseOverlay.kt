package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

@Composable
fun PoseOverlay(
    pose: Pose?,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (pose == null || imageWidth == 0 || imageHeight == 0) return@Canvas

        val scaleX = size.width / imageWidth
        val scaleY = size.height / imageHeight
        val scale = maxOf(scaleX, scaleY)
        val offsetX = (size.width - imageWidth * scale) / 2f
        val offsetY = (size.height - imageHeight * scale) / 2f

        fun translateX(x: Float): Float = x * scale + offsetX
        fun translateY(y: Float): Float = y * scale + offsetY

        fun getConfidenceColor(confidence: Float): Color {
            return when {
                confidence > 0.75f -> Color.Green
                confidence > 0.4f -> Color.Yellow
                else -> Color.Red
            }
        }

        fun drawLineOrEmpty(start: PoseLandmark?, end: PoseLandmark?) {
            if (start != null && end != null) {
                // If either is very low confidence, don't draw or draw faint red
                if (start.inFrameLikelihood < 0.2f || end.inFrameLikelihood < 0.2f) return

                val avgConfidence = (start.inFrameLikelihood + end.inFrameLikelihood) / 2f
                val color = getConfidenceColor(avgConfidence).copy(alpha = 0.8f)

                drawLine(
                    color = color,
                    start = Offset(translateX(start.position.x), translateY(start.position.y)),
                    end = Offset(translateX(end.position.x), translateY(end.position.y)),
                    strokeWidth = 5f
                )
            }
        }

        fun drawPoint(landmark: PoseLandmark?) {
            if (landmark != null && landmark.inFrameLikelihood > 0.2f) {
                drawCircle(
                    color = getConfidenceColor(landmark.inFrameLikelihood),
                    radius = 6f,
                    center = Offset(translateX(landmark.position.x), translateY(landmark.position.y))
                )
            }
        }

        val landmarks = pose.allPoseLandmarks
        if (landmarks.isEmpty()) return@Canvas

        // Draw connections
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        drawLineOrEmpty(leftShoulder, rightShoulder)
        drawLineOrEmpty(leftHip, rightHip)
        drawLineOrEmpty(leftShoulder, leftHip)
        drawLineOrEmpty(rightShoulder, rightHip)

        drawLineOrEmpty(leftShoulder, leftElbow)
        drawLineOrEmpty(leftElbow, leftWrist)
        drawLineOrEmpty(rightShoulder, rightElbow)
        drawLineOrEmpty(rightElbow, rightWrist)

        drawLineOrEmpty(leftHip, leftKnee)
        drawLineOrEmpty(leftKnee, leftAnkle)
        drawLineOrEmpty(rightHip, rightKnee)
        drawLineOrEmpty(rightKnee, rightAnkle)

        // Draw points
        for (landmark in landmarks) {
            drawPoint(landmark)
        }
    }
}
