package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*

@Composable
fun FaceOverlay(
    faceFrame: FaceTrackingFrame?,
    imageWidth: Int,
    imageHeight: Int,
    isFrontCamera: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (faceFrame == null || imageWidth == 0 || imageHeight == 0) return
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val scaleX = size.width / imageWidth.toFloat()
        val scaleY = size.height / imageHeight.toFloat()
        val scale = maxOf(scaleX, scaleY)
        val offsetX = (size.width - imageWidth * scale) / 2f
        val offsetY = (size.height - imageHeight * scale) / 2f

        for (point in faceFrame.landmarks) {
            val px = point.x * imageWidth
            val py = point.y * imageHeight
            val rawX = px * scale + offsetX
            val cx = if (isFrontCamera) size.width - rawX else rawX
            val cy = py * scale + offsetY
            
            drawCircle(PrimaryWhite.copy(alpha=0.4f), radius = 4f, center = Offset(cx, cy))
            drawCircle(Color.White.copy(alpha=0.8f), radius = 1.5f, center = Offset(cx, cy))
        }
    }
}
