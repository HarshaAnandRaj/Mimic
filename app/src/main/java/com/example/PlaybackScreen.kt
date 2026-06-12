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
data class ParsedFrame(val timestamp: Double, val landmarks: List<ParsedLandmark>)

data class PlaybackMetadata(
    var isFaceData: Boolean = false,
    var imageWidth: Int = 0,
    var imageHeight: Int = 0,
    var isFrontCamera: Boolean = true,
    var minX: Float = Float.MAX_VALUE,
    var maxX: Float = Float.MIN_VALUE,
    var minY: Float = Float.MAX_VALUE,
    var maxY: Float = Float.MIN_VALUE
)

@Composable
fun PlaybackScreen(modifier: Modifier = Modifier, file: File, onNavigateBack: () -> Unit) {
    val bgDark = Color(0xFF1A1C1E)
    val accentBlue = Color(0xFFD0E4FF)
    
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var frames by remember { mutableStateOf<List<ParsedFrame>?>(null) }
    var metadata by remember { mutableStateOf(PlaybackMetadata()) }
    var currentFrameIndex by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableStateOf(1f) }

    LaunchedEffect(file) {
        if (file.exists() && file.extension == "json") {
            try {
                val parsedData = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val result = mutableListOf<ParsedFrame>()
                    val parsedMetadata = PlaybackMetadata()
                    
                    val reader = android.util.JsonReader(java.io.FileReader(file))
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        val frameLandmarks = mutableListOf<ParsedLandmark>()
                        var timestamp = 0.0
                        while (reader.hasNext()) {
                            val name = reader.nextName()
                            when (name) {
                                "timestamp" -> timestamp = reader.nextDouble()
                                "type" -> {
                                    if (reader.nextString() == "metadata") {
                                        // inside metadata
                                    }
                                }
                                "tracking_mode" -> {
                                    if (reader.nextString() == "FACE") {
                                        parsedMetadata.isFaceData = true
                                    }
                                }
                                "image_width" -> parsedMetadata.imageWidth = reader.nextInt()
                                "image_height" -> parsedMetadata.imageHeight = reader.nextInt()
                                "is_front_camera" -> parsedMetadata.isFrontCamera = reader.nextBoolean()
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
                                        
                                        if (visibility > 0) {
                                            if (x < parsedMetadata.minX) parsedMetadata.minX = x
                                            if (x > parsedMetadata.maxX) parsedMetadata.maxX = x
                                            if (y < parsedMetadata.minY) parsedMetadata.minY = y
                                            if (y > parsedMetadata.maxY) parsedMetadata.maxY = y
                                        }
                                    }
                                    reader.endArray()
                                }
                                "blendshapes" -> reader.skipValue()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        if (frameLandmarks.isNotEmpty()) {
                            result.add(ParsedFrame(timestamp, frameLandmarks))
                        }
                    }
                    reader.endArray()
                    reader.close()
                    
                    Pair(result, parsedMetadata)
                }
                frames = parsedData.first
                metadata = parsedData.second
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Failed to load file. It may be corrupted."
            }
        }
    }

    LaunchedEffect(isPlaying, frames) {
        if (isPlaying && frames != null) {
            while (currentFrameIndex < frames!!.size - 1 && isPlaying) {
                val currentFrame = frames!![currentFrameIndex]
                val nextFrame = frames!![currentFrameIndex + 1]
                var delayMs = ((nextFrame.timestamp - currentFrame.timestamp) * 1000).toLong()
                
                // Clamp delay to avoid freezing on dropped frames or rushing
                delayMs = delayMs.coerceIn(16L, 250L) 
                
                delay((delayMs / playbackSpeed).toLong())
                currentFrameIndex++
            }
            if (currentFrameIndex >= frames!!.size - 1) {
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
                    text = "Viewing: ${file.nameWithoutExtension}",
                    color = Color.White,
                    fontSize = 18.sp,
                    maxLines = 1,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
            if (errorMessage != null) {
                Text(text = errorMessage!!, color = Color.Red, fontWeight = FontWeight.Bold)
            } else if (frames == null) {
                if (file.extension == "bvh") {
                    Text(text = "BVH playback not supported yet.", color = Color.Gray)
                } else {
                    Text(text = "Loading frames...", color = Color.Gray)
                }
            } else if (frames!!.isNotEmpty()) {
                val frameList = frames!!
                if (currentFrameIndex < frameList.size) {
                    val frameLandmarks = frameList[currentFrameIndex].landmarks
                    val isFace = metadata.isFaceData
                    
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val mappedPoints = mutableMapOf<Int, Offset>()
                        val visibilities = mutableMapOf<Int, Float>()
                        
                        // Pass 1: map points
                        for (lm in frameLandmarks) {
                            val id = lm.id
                            var x = lm.x
                            var y = lm.y
                            val visibility = lm.visibility
                            
                            visibilities[id] = visibility
                            
                            if (visibility > 0.0f) {
                                // If imageWidth/Height is provided, use it. Otherwise, use min/max bounds.
                                val iw = if (metadata.imageWidth > 0) metadata.imageWidth.toFloat() else (metadata.maxX - metadata.minX)
                                val ih = if (metadata.imageHeight > 0) metadata.imageHeight.toFloat() else (metadata.maxY - metadata.minY)
                                val wX = if (metadata.imageWidth > 0) x else (x - metadata.minX)
                                val wY = if (metadata.imageHeight > 0) y else (y - metadata.minY)
                                
                                val safeW = if (iw > 0) iw else 1f
                                val safeH = if (ih > 0) ih else 1f
                                
                                // Calculate scale to fit canvas width or height depending on aspect ratio
                                val scale = minOf(size.width / safeW, size.height / safeH)
                                
                                // Center the mapped rect in the canvas
                                val xOffset = (size.width - (safeW * scale)) / 2f
                                val yOffset = (size.height - (safeH * scale)) / 2f
                                
                                // Front camera needs mirroring logically, but the points are usually already mirrored in MLKit space if mapped correctly
                                // Actually, we'll just plot them as-is.
                                val cx = xOffset + wX * scale
                                val cy = yOffset + wY * scale
                                
                                mappedPoints[id] = Offset(cx, cy)
                            }
                        }
                        
                        // Draw Bones
                        if (!isFace) {
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
                            val radius = if (isFace) 2f else if (visibility > 0.3f) 6f else 4f
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
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "${playbackSpeed}x",
                        color = accentBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable {
                            playbackSpeed = when (playbackSpeed) {
                                1f -> 2f
                                2f -> 0.5f
                                else -> 1f
                            }
                        }.padding(8.dp)
                    )
                }
            }
        }
    }
}
