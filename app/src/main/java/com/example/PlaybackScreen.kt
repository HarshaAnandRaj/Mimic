package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily

data class ParsedLandmark(val id: Int, val x: Float, val y: Float, val visibility: Float)

@Composable
fun PlaybackScreen(modifier: Modifier = Modifier, file: File, onNavigateBack: () -> Unit) {
    val bgDark = Color(0xFF1A1C1E)
    val accentBlue = Color(0xFFD0E4FF)
    
    var frames by remember { mutableStateOf<List<List<ParsedLandmark>>?>(null) }
    var currentFrameIndex by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var isFaceData by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    LaunchedEffect(file) {
        if (file.exists() && file.extension == "json") {
            try {
                val parsedData = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val result = mutableListOf<List<ParsedLandmark>>()
                    var detectedFaceData = false
                    
                    val reader = android.util.JsonReader(java.io.FileReader(file))
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        val frameLandmarks = mutableListOf<ParsedLandmark>()
                        while (reader.hasNext()) {
                            val name = reader.nextName()
                            when (name) {
                                "type" -> {
                                    if (reader.nextString() == "metadata") {
                                        // Metadata block
                                    }
                                }
                                "tracking_mode" -> {
                                    if (reader.nextString() == "FACE") {
                                        detectedFaceData = true
                                    }
                                }
                                "landmarks" -> {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        var id = 0
                                        var x = 0f
                                        var y = 0f
                                        var visibility = 0f
                                        
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "id" -> id = reader.nextInt()
                                                "x" -> x = reader.nextDouble().toFloat()
                                                "y" -> y = reader.nextDouble().toFloat()
                                                "visibility" -> visibility = reader.nextDouble().toFloat()
                                                else -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                        frameLandmarks.add(ParsedLandmark(id, x, y, visibility))
                                    }
                                    reader.endArray()
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        if (frameLandmarks.isNotEmpty()) {
                            result.add(frameLandmarks)
                        }
                    }
                    reader.endArray()
                    reader.close()
                    
                    Pair(result, detectedFaceData)
                }
                frames = parsedData.first
                isFaceData = parsedData.second
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(isPlaying, currentFrameIndex, frames) {
        if (isPlaying && frames != null) {
            val totalFrames = frames!!.size
            if (currentFrameIndex < totalFrames - 1) {
                delay(33L) // Roughly 30fps
                currentFrameIndex++
            } else {
                isPlaying = false
            }
        }
    }

    // Standard MediaPipe Pose connections
    val connections = listOf(
        Pair(11, 12), Pair(11, 13), Pair(13, 15), Pair(12, 14), Pair(14, 16),
        Pair(11, 23), Pair(12, 24), Pair(23, 24), Pair(23, 25), Pair(24, 26),
        Pair(25, 27), Pair(26, 28), Pair(27, 29), Pair(28, 30), Pair(29, 31),
        Pair(30, 32), Pair(27, 31), Pair(28, 32)
    )

    Column(modifier = modifier.fillMaxSize().background(bgDark).clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null
    ) { showControls = !showControls }) {
        // App Bar
        androidx.compose.animation.AnimatedVisibility(
            visible = showControls,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.fadeOut()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = "Viewing: ${file.name}",
                    color = Color.White,
                    fontSize = 18.sp,
                    maxLines = 1,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
            if (frames == null) {
                if (file.extension == "bvh") {
                    Text(text = "BVH playback not supported yet.", color = Color.Gray)
                } else {
                    Text(text = "Loading frames...", color = Color.Gray)
                }
            } else if (frames!!.isNotEmpty()) {
                val frameList = frames!!
                if (currentFrameIndex < frameList.size) {
                    val frameLandmarks = frameList[currentFrameIndex]
                    
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val mappedPoints = mutableMapOf<Int, Offset>()
                        val visibilities = mutableMapOf<Int, Float>()
                        
                        // Pass 1: map points
                        for (lm in frameLandmarks) {
                            val id = lm.id
                            val x = lm.x
                            val y = lm.y
                            val visibility = lm.visibility
                            
                            visibilities[id] = visibility
                            
                            if (visibility > 0.0f) {
                                // Dynamic scale guessing. Typical capture is 720 width, 1280 height. Just fit vertically
                                val scale = size.height / 1280f
                                val cx = size.width/2f + (x - 360f) * scale
                                val cy = (y * scale)
                                mappedPoints[id] = Offset(cx, cy)
                            }
                        }
                        
                        // Draw Bones
                        if (!isFaceData) {
                            connections.forEach { (a, b) ->
                                val ptA = mappedPoints[a]
                                val ptB = mappedPoints[b]
                                val visA = visibilities[a] ?: 0f
                                val visB = visibilities[b] ?: 0f
                                
                                if (ptA != null && ptB != null && visA > 0.0f && visB > 0.0f) {
                                    val minVis = minOf(visA, visB)
                                    val color = when {
                                        minVis > 0.7f -> Color.Green.copy(alpha = 0.5f)
                                        minVis > 0.4f -> Color.Yellow.copy(alpha = 0.5f)
                                        minVis > 0.3f -> Color.Red.copy(alpha = 0.5f)
                                        else -> Color.Gray.copy(alpha = 0.4f)
                                    }
                                    val width = if (minVis > 0.3f) 8f else 4f
                                    drawLine(color, start = ptA, end = ptB, strokeWidth = width)
                                }
                            }
                        }

                        // Draw Joints
                        mappedPoints.forEach { (id, offset) ->
                            val visibility = visibilities[id] ?: 0f
                            val color = when {
                                visibility > 0.75f -> Color.Green
                                visibility > 0.4f -> Color.Yellow
                                visibility > 0.3f -> Color.Red
                                else -> Color.Gray
                            }
                            val radius = if (isFaceData) 2f else if (visibility > 0.3f) 6f else 4f
                            drawCircle(color, radius = radius, center = offset)
                        }
                    }
                }
            }
        }

        // Controls
        if (frames != null && frames!!.isNotEmpty()) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showControls,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha=0.6f)).padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { 
                        if (currentFrameIndex >= frames!!.size - 1) currentFrameIndex = 0
                        isPlaying = !isPlaying 
                    }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = accentBlue
                        )
                    }
                    Text(
                        text = String.format("%04d / %04d", currentFrameIndex, frames!!.size),
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Slider(
                        value = currentFrameIndex.toFloat(),
                        onValueChange = { currentFrameIndex = it.toInt() },
                        valueRange = 0f..(frames!!.size - 1).toFloat(),
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = accentBlue,
                            activeTrackColor = accentBlue.copy(alpha = 0.7f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        }
    }
}
