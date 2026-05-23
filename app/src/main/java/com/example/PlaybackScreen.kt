package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
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
                frames = JSONArray(content)
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

    Column(modifier = modifier.fillMaxSize().background(bgDark)) {
        // App Bar
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                        for (i in 0 until landmarks.length()) {
                            val lm = landmarks.getJSONObject(i)
                            val x = lm.getDouble("x").toFloat()
                            val y = lm.getDouble("y").toFloat()
                            val visibility = lm.getDouble("visibility").toFloat()
                            
                            val color = when {
                                visibility > 0.75f -> Color.Green
                                visibility > 0.4f -> Color.Yellow
                                else -> Color.Red
                            }
                            
                            if (visibility > 0.2f) {
                                // Since ML Kit returns image coordinates, we need to map them to the canvas roughly
                                // Assuming typical camera resolution ~640x480, we just do a proportional map
                                // Here we just draw them where they are and try to fit them. This is very rough.
                                val cx = (x / 480f) * size.width
                                val cy = (y / 640f) * size.height
                                drawCircle(color, radius = 8f, center = Offset(cx, cy))
                            }
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
