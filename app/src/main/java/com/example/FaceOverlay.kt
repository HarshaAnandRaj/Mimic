package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.google.mlkit.vision.facemesh.FaceMesh

@Composable
fun FaceOverlay(faceMesh: FaceMesh?, imageWidth: Int, imageHeight: Int) {
    if (faceMesh == null || imageWidth == 0 || imageHeight == 0) return
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scaleX = size.width / imageWidth.toFloat()
        val scaleY = size.height / imageHeight.toFloat()
        val scale = maxOf(scaleX, scaleY)
        val offsetX = (size.width - imageWidth * scale) / 2f
        val offsetY = (size.height - imageHeight * scale) / 2f

        for (point in faceMesh.allPoints) {
            val cx = point.position.x * scale + offsetX
            val cy = point.position.y * scale + offsetY
            drawCircle(Color.Green, radius = 2f, center = Offset(cx, cy))
        }
    }
}
