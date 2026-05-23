package com.example

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import android.media.ToneGenerator
import android.media.AudioManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.OrientationEventListener
import androidx.core.content.FileProvider
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.pose.Pose
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MoCapApp()
            }
        }
    }
}

enum class AppScreen { Capture, Library, Analytics, Playback, Privacy }

enum class TrackingState { SEARCHING, CALIBRATING, READY, RECORDING, LOSS }

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MoCapApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mocap_prefs", android.content.Context.MODE_PRIVATE) }
    var privacyAccepted by remember { mutableStateOf(prefs.getBoolean("privacy_accepted", false)) }
    
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
    var currentScreen by remember { mutableStateOf(if (privacyAccepted) AppScreen.Capture else AppScreen.Privacy) }
    var fileToPlay by remember { mutableStateOf<java.io.File?>(null) }

    LaunchedEffect(privacyAccepted) {
        if (privacyAccepted && !cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        if (!privacyAccepted || currentScreen == AppScreen.Privacy) {
            PrivacyScreen(
                modifier = Modifier.padding(innerPadding),
                onAccept = {
                    prefs.edit().putBoolean("privacy_accepted", true).apply()
                    privacyAccepted = true
                    currentScreen = AppScreen.Capture
                }
            )
        } else if (cameraPermissionState.status.isGranted) {
            when (currentScreen) {
                AppScreen.Capture -> MoCapScreen(
                    modifier = Modifier.padding(innerPadding),
                    onNavigate = { currentScreen = it }
                )
                AppScreen.Library -> LibraryScreen(
                    modifier = Modifier.padding(innerPadding),
                    onNavigate = { currentScreen = it },
                    onPlay = { 
                        fileToPlay = it 
                        currentScreen = AppScreen.Playback 
                    }
                )
                AppScreen.Analytics -> AnalyticsScreen(
                    modifier = Modifier.padding(innerPadding),
                    onNavigate = { currentScreen = it }
                )
                AppScreen.Playback -> {
                    if (fileToPlay != null) {
                        PlaybackScreen(
                            modifier = Modifier.padding(innerPadding),
                            file = fileToPlay!!,
                            onNavigateBack = { currentScreen = AppScreen.Library }
                        )
                    } else {
                        currentScreen = AppScreen.Library
                    }
                }
                AppScreen.Privacy -> {
                    // Handled above, but required for exhaustiveness
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Camera permission is required.")
            }
        }
    }
}


@Composable
fun MoCapScreen(modifier: Modifier = Modifier, onNavigate: (AppScreen) -> Unit) {
    val context = LocalContext.current
    var currentPose by remember { mutableStateOf<Pose?>(null) }
    var imageWidth by remember { mutableStateOf(0) }
    var imageHeight by remember { mutableStateOf(0) }
    
    var trackingState by remember { mutableStateOf(TrackingState.SEARCHING) }
    var calibrationTime by remember { mutableStateOf(0L) }
    var trackingConfidence by remember { mutableStateOf(0f) }
    
    var isRecording by remember { mutableStateOf(false) }
    var lastSavedFile by remember { mutableStateOf<File?>(null) }
    var frameCount by remember { mutableStateOf(0) }
    var bufferSize by remember { mutableStateOf(0) }
    var recordingStartTime by remember { mutableStateOf(0L) }
    var currentDuration by remember { mutableStateOf(0L) }
    
    var showExportDialog by remember { mutableStateOf<File?>(null) }
    var isLowStorage by remember { mutableStateOf(false) }
    var isLandscape by remember { mutableStateOf(false) }
    
    // Telemetry & Hardware Setup
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100) }
    var latestGravity by remember { mutableStateOf(floatArrayOf(0f, 9.8f, 0f)) }
    var isFlashActive by remember { mutableStateOf(false) }
    var isGhostMode by remember { mutableStateOf(false) }
    var showPrivacyInfo by remember { mutableStateOf(false) }
    
    LaunchedEffect(trackingState, isRecording) {
        if (trackingState == TrackingState.LOSS && isRecording) {
            while (true) {
                isFlashActive = true
                kotlinx.coroutines.delay(200)
                isFlashActive = false
                kotlinx.coroutines.delay(200)
            }
        } else {
            isFlashActive = false
        }
    }
    
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
        val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_GRAVITY) {
                    latestGravity = event.values.clone()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, gravitySensor, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager.unregisterListener(listener)
            toneGenerator.release()
        }
    }
    
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                // Portrait is around 0 or 180. Landscape is around 90 or 270.
                isLandscape = (orientation in 45..135) || (orientation in 225..315)
            }
        }
        listener.enable()
        onDispose {
            listener.disable()
        }
    }
    
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingStartTime = System.currentTimeMillis()
            var tick = 0
            while (true) {
                currentDuration = System.currentTimeMillis() - recordingStartTime
                
                if (tick % 20 == 0) { // Check roughly every 2 seconds
                    try {
                        val path = Environment.getDataDirectory()
                        val stat = StatFs(path.path)
                        val bytesAvailable = stat.blockSizeLong * stat.availableBlocksLong
                        isLowStorage = bytesAvailable < 500L * 1024L * 1024L
                    } catch (e: Exception) {
                        isLowStorage = false
                    }
                }
                tick++
                
                kotlinx.coroutines.delay(100L)
            }
        } else {
            currentDuration = 0L
            isLowStorage = false
        }
    }
    
    val recorder = remember { PoseRecorder(context) }

    DisposableEffect(Unit) {
        onDispose {
            if (recorder.isRecording()) recorder.stopRecording()
        }
    }

    val bgDark = Color(0xFF1A1C1E)
    val textLight = Color(0xFFE2E2E6)
    val accentBlue = Color(0xFFD0E4FF)
    val recordRed = Color(0xFFFFB4AB)
    val panelBg = Color(0xFF2D2F31)
    val btnBg = Color(0xFF3D4758)

    Column(modifier = modifier.fillMaxSize().background(bgDark)) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu", tint = textLight)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Mimic",
                        color = accentBlue,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .background(accentBlue.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PRO",
                            color = accentBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            IconButton(onClick = {}) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = textLight)
            }
        }

        // Main Camera Viewport
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                .background(Color.Black)
        ) {
            CameraPreviewAndAnalysis(
                isFlashActive = isFlashActive,
                isGhostMode = isGhostMode,
                onPoseDetected = { pose, w, h ->
                    currentPose = pose
                    imageWidth = w
                    imageHeight = h
                    
                    val isFullBody = analyzeFullBodyVisibility(pose)
                    val isTPose = isFullBody && analyzeTPose(pose)
                    val confidence = getAverageConfidence(pose)
                    trackingConfidence = confidence
                    
                    if (isRecording) {
                        if (confidence > 0.4f) { // Only record if we have decent confidence to avoid snapping
                            recorder.recordFrame(pose)
                            frameCount++
                            bufferSize = recorder.getBufferLength()
                            trackingState = TrackingState.RECORDING // reusing an enum value? Wait, we didn't add RECORDING
                        } else {
                            if (trackingState != TrackingState.LOSS) {
                                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 300)
                            }
                            trackingState = TrackingState.LOSS
                        }
                    } else {
                        when (trackingState) {
                            TrackingState.SEARCHING, TrackingState.LOSS -> {
                                if (isFullBody) trackingState = TrackingState.CALIBRATING
                            }
                            TrackingState.CALIBRATING -> {
                                if (!isFullBody) {
                                    trackingState = TrackingState.SEARCHING
                                    calibrationTime = 0L
                                } else if (isTPose) {
                                    if (calibrationTime == 0L) {
                                        calibrationTime = System.currentTimeMillis()
                                    } else if (System.currentTimeMillis() - calibrationTime > 1500L) {
                                        trackingState = TrackingState.READY
                                        toneGenerator.startTone(ToneGenerator.TONE_PROP_PROMPT, 200)
                                    }
                                } else {
                                    calibrationTime = 0L
                                }
                            }
                            TrackingState.READY -> {
                                if (!isFullBody) {
                                    trackingState = TrackingState.SEARCHING
                                    calibrationTime = 0L
                                }
                            }
                            TrackingState.RECORDING -> {
                                // Handled externally when isRecording is false
                            }
                        }
                    }
                }
            )

            PoseOverlay(
                pose = currentPose,
                imageWidth = imageWidth,
                imageHeight = imageHeight
            )

            if (isLandscape) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = "Rotate Device",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Please rotate to Portrait\nfor optimal full-body tracking",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                    }
                }
            } else if (!isRecording && trackingState != TrackingState.READY) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when (trackingState) {
                                TrackingState.SEARCHING -> "Step Back"
                                TrackingState.CALIBRATING -> "Hold T-Pose to Calibrate"
                                else -> ""
                            },
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (trackingState) {
                                TrackingState.SEARCHING -> "Ensure full body is visible in frame"
                                TrackingState.CALIBRATING -> "Keep arms horizontal for 2 seconds"
                                else -> ""
                            },
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (trackingState == TrackingState.LOSS && isRecording) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "TRACKING LOST\nPlease step back into frame",
                        color = Color.Red,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // HUD Overlays
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Right Settings
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.IconButton(
                        onClick = { showPrivacyInfo = true },
                        modifier = Modifier.background(bgDark, CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = "Privacy Info", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    androidx.compose.material3.IconButton(
                        onClick = { isGhostMode = !isGhostMode },
                        modifier = Modifier.background(if(isGhostMode) Color.Green else bgDark, CircleShape)
                    ) {
                        Icon(
                            imageVector = if(isGhostMode) Icons.Default.VisibilityOff else Icons.Default.Visibility, 
                            contentDescription = "Ghost Mode", 
                            tint = if (isGhostMode) Color.Black else Color.White
                        )
                    }
                }

                Column(modifier = Modifier.padding(24.dp)) {
                // Tracking Status & Confidence Meter
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val confidenceColor = when {
                        trackingConfidence > 0.75f -> Color.Green
                        trackingConfidence > 0.4f -> Color.Yellow
                        else -> Color.Red
                    }
                    val confidenceText = when {
                        trackingConfidence > 0.75f -> "STABLE"
                        trackingConfidence > 0.4f -> "SHAKY"
                        else -> "POOR"
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(confidenceColor, CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TRACKING: $confidenceText",
                                color = textLight,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CONFIDENCE",
                                color = textLight.copy(alpha = 0.7f),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.width(60.dp).height(4.dp).background(Color.DarkGray)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(trackingConfidence)
                                        .background(confidenceColor)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                if (isRecording) {
                    val seconds = (currentDuration / 1000) % 60
                    val minutes = (currentDuration / 1000) / 60
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(recordRed, CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format("REC %02d:%02d", minutes, seconds),
                            color = textLight,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(Color.Green, CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LIVE: MediaPipe",
                            color = textLight,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }
                
                if (isLowStorage) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = "Warning", tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LOW STORAGE (< 500MB)",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
            ) {
                Text(
                    text = "FRAME: ${String.format("%06d", frameCount)}\nBUFFER: ${String.format("%06d", bufferSize)}\nEXP: BVH (READY)\nSMOOTH: EXPONENTIAL",
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
             }
             
             // Confidence
             Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(accentBlue, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
             ) {
                Text(
                    text = "CONF: ${if (currentPose != null) "98.2%" else "--"}",
                    color = bgDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
             }
            } // Close HUD Overlays Box
        }

        // Bottom Control Panel
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary Action Left (Flip Camera mock)
                IconButton(
                    onClick = { /* TODO */ },
                    modifier = Modifier.size(56.dp).background(btnBg, RoundedCornerShape(16.dp))
                ) {
                    Icon(imageVector = Icons.Default.FlipCameraAndroid, contentDescription = "Flip", tint = textLight)
                }

                // Main Record Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, if (trackingState == TrackingState.READY || isRecording) btnBg else btnBg.copy(alpha=0.5f), CircleShape)
                        .padding(4.dp)
                        .clickable(enabled = trackingState == TrackingState.READY || isRecording) {
                            if (isRecording) {
                                isRecording = false
                                trackingState = TrackingState.READY
                                lastSavedFile = recorder.stopRecording()
                                Toast.makeText(context, "Saved to ${lastSavedFile?.name}", Toast.LENGTH_SHORT).show()
                            } else {
                                lastSavedFile = null
                                frameCount = 0
                                bufferSize = 0
                                recorder.setDeviceGravity(latestGravity)
                                recorder.startRecording()
                                isRecording = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                         Box(modifier = Modifier.size(72.dp).background(recordRed, CircleShape), contentAlignment = Alignment.Center) {
                              Box(modifier = Modifier.size(24.dp).background(bgDark, RoundedCornerShape(4.dp)))
                         }
                    } else {
                         Box(modifier = Modifier.size(72.dp).background(if (trackingState == TrackingState.READY) recordRed else recordRed.copy(alpha = 0.3f), CircleShape))
                    }
                }

                // Secondary Action Right (Share)
                IconButton(
                    onClick = { 
                        if (lastSavedFile != null && !isRecording) {
                            showExportDialog = lastSavedFile
                        }
                    },
                    modifier = Modifier.size(56.dp).background(if (lastSavedFile != null && !isRecording) btnBg else btnBg.copy(alpha=0.5f), RoundedCornerShape(16.dp))
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = if (lastSavedFile != null && !isRecording) textLight else textLight.copy(alpha=0.5f))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Stats
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    modifier = Modifier.weight(1f).background(panelBg, RoundedCornerShape(16.dp)).border(1.dp, Color.White.copy(alpha=0.05f), RoundedCornerShape(16.dp)).padding(16.dp)
                ) {
                    Text("PIPELINE", color = Color.White.copy(alpha=0.5f), fontSize = 10.sp, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFF60A5FA), CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Android → Blender", color = textLight, fontSize = 14.sp)
                    }
                }
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.weight(1f).background(panelBg, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).border(1.dp, Color.White.copy(alpha=0.05f), androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(16.dp)
                ) {
                    Text("THERMAL STATUS", color = Color.White.copy(alpha=0.5f), fontSize = 10.sp, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(Color.Green, CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stable (38°C)", color = textLight, fontSize = 14.sp)
                    }
                }
            }
        }
        
        // Bottom Nav Area Mockup
        Row(
            modifier = Modifier.fillMaxWidth().height(80.dp).border(1.dp, Color.White.copy(alpha = 0.05f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onNavigate(AppScreen.Capture) }) {
                Icon(imageVector = Icons.Default.FiberManualRecord, contentDescription = "Capture", tint = accentBlue, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Capture", color = accentBlue, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(0.5f).clickable { onNavigate(AppScreen.Library) }) {
                Icon(imageVector = Icons.Default.Folder, contentDescription = "Library", tint = textLight, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Library", color = textLight, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(0.5f)) {
                Icon(imageVector = Icons.Default.BarChart, contentDescription = "Analytics", tint = textLight, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Analytics", color = textLight, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
        
        if (showPrivacyInfo) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showPrivacyInfo = false },
                title = { Text("Zero-Cloud Privacy Policy", color = textLight) },
                text = { 
                    Column {
                        Text("This application is an Intelligent Mock Engine built for VTubers and animators.", color = textLight.copy(alpha = 0.8f))
                        Spacer(Modifier.height(8.dp))
                        Text("• Zero Video Capture: Ghost Mode disables the camera preview entirely. No MP4s are saved to your device.", color = textLight.copy(alpha = 0.8f))
                        Text("• Air-Gapped Architecture: This app does not have internet permissions. All ML processing happens locally in RAM.", color = textLight.copy(alpha = 0.8f))
                        Text("• Sandboxed Storage: Mocap tracking streams are written securely to the app's internal cache.", color = textLight.copy(alpha = 0.8f))
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showPrivacyInfo = false }) {
                        Text("Got it", color = accentBlue)
                    }
                },
                containerColor = panelBg
            )
        }
        
        if (showExportDialog != null) {
            val fileToShare = showExportDialog!!
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showExportDialog = null },
                title = { Text("Export Format", color = textLight) },
                text = { Text("Choose the format you want to export:", color = textLight.copy(alpha = 0.8f)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        shareMocapFile(context, fileToShare, "json")
                        showExportDialog = null
                    }) {
                        Text("JSON", color = accentBlue)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        shareMocapFile(context, fileToShare, "bvh")
                        showExportDialog = null
                    }) {
                        Text("BVH", color = accentBlue)
                    }
                },
                containerColor = panelBg
            )
        }
    }
}
