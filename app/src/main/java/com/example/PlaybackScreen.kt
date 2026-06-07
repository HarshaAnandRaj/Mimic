package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import org.json.JSONArray
import java.io.File
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily

@Composable
fun PlaybackScreen(modifier: Modifier = Modifier, file: File, onNavigateBack: () -> Unit) {
    val bgDark = Color(0xFF1A1C1E)
    val accentBlue = Color(0xFFD0E4FF)
    
    var frames by remember { mutableStateOf<JSONArray?>(null) }
    var currentFrameIndex by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(file) {
        if (file.exists() && file.extension == "json") {
            try {
                val content = file.readText()
                val rawFrames = JSONArray(content)
                val validFrames = JSONArray()
                for (i in 0 until rawFrames.length()) {
                    val obj = rawFrames.getJSONObject(i)
                    if (obj.has("landmarks")) {
                        validFrames.put(obj)
                    }
                }
                frames = validFrames
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(isPlaying, currentFrameIndex, frames) {
        if (isPlaying && frames != null) {
            val totalFrames = frames!!.length()
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

    Column(modifier = modifier.fillMaxSize().background(bgDark)) {
        // App Bar
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

        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
            if (frames == null) {
                if (file.extension == "bvh") {
                    Text(text = "BVH playback not supported yet.", color = Color.Gray)
                } else {
                    Text(text = "Loading frames...", color = Color.Gray)
                }
            } else if (frames!!.length() > 0) {
                val frameArr = frames!!
                if (currentFrameIndex < frameArr.length()) {
                    val frameObj = frameArr.getJSONObject(currentFrameIndex)
                    val landmarks = frameObj.getJSONArray("landmarks")
                    
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val mappedPoints = mutableMapOf<Int, Offset>()
                        val visibilities = mutableMapOf<Int, Float>()
                        
                        // Pass 1: map points
                        for (i in 0 until landmarks.length()) {
                            val lm = landmarks.getJSONObject(i)
                            val id = lm.getInt("id")
                            val x = lm.getDouble("x").toFloat()
                            val y = lm.getDouble("y").toFloat()
                            val visibility = lm.getDouble("visibility").toFloat()
                            
                            visibilities[id] = visibility
                            
                            if (visibility > 0.2f) {
                                // Dynamic scale guessing. Typical capture is 720 width, 1280 height. Just fit vertically
                                val scale = size.height / 1000f
                                val cx = size.width/2f + (x - 360f) * scale
                                val cy = (y * scale)
                                mappedPoints[id] = Offset(cx, cy)
                            }
                        }
                        
                        // Draw Bones
                        connections.forEach { (a, b) ->
                            val ptA = mappedPoints[a]
                            val ptB = mappedPoints[b]
                            val visA = visibilities[a] ?: 0f
                            val visB = visibilities[b] ?: 0f
                            
                            if (ptA != null && ptB != null && visA > 0.3f && visB > 0.3f) {
                                val minVis = minOf(visA, visB)
                                val color = when {
                                    minVis > 0.7f -> Color.Green.copy(alpha = 0.5f)
                                    minVis > 0.4f -> Color.Yellow.copy(alpha = 0.5f)
                                    else -> Color.Red.copy(alpha = 0.5f)
                                }
                                drawLine(color, start = ptA, end = ptB, strokeWidth = 8f)
                            }
                        }

                        // Draw Joints
                        mappedPoints.forEach { (id, offset) ->
                            val visibility = visibilities[id] ?: 0f
                            val color = when {
                                visibility > 0.75f -> Color.Green
                                visibility > 0.4f -> Color.Yellow
                                else -> Color.Red
                            }
                            drawCircle(color, radius = 6f, center = offset)
                        }
                    }
                }
            }
        }

        // Controls
        if (frames != null && frames!!.length() > 0) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { 
                    if (currentFrameIndex >= frames!!.length() - 1) currentFrameIndex = 0
                    isPlaying = !isPlaying 
                }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = accentBlue
                    )
                }
                Text(
                    text = String.format("%04d / %04d", currentFrameIndex, frames!!.length()),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Slider(
                    value = currentFrameIndex.toFloat(),
                    onValueChange = { currentFrameIndex = it.toInt() },
                    valueRange = 0f..(frames!!.length() - 1).toFloat(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
