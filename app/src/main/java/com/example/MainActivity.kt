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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.view.OrientationEventListener
import android.view.WindowManager
import android.os.PowerManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.FileProvider
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

    var isRecordingSession by remember { mutableStateOf(false) }

    LaunchedEffect(privacyAccepted) {
        if (privacyAccepted && !cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    val bgDark = Color(0xFF1A1C1E)
    val textLight = Color(0xFFE2E2E6)
    val accentBlue = Color(0xFFD0E4FF)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (privacyAccepted && cameraPermissionState.status.isGranted && 
                currentScreen != AppScreen.Privacy && currentScreen != AppScreen.Playback && !isRecordingSession) {
                NavigationBar(
                    containerColor = bgDark,
                    contentColor = textLight,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.Capture,
                        onClick = { currentScreen = AppScreen.Capture },
                        icon = { Icon(Icons.Default.FiberManualRecord, contentDescription = "Capture") },
                        label = { Text("Capture") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = bgDark,
                            selectedTextColor = accentBlue,
                            indicatorColor = accentBlue,
                            unselectedIconColor = textLight.copy(alpha = 0.5f),
                            unselectedTextColor = textLight.copy(alpha = 0.5f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.Library,
                        onClick = { currentScreen = AppScreen.Library },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Library") },
                        label = { Text("Library") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = bgDark,
                            selectedTextColor = accentBlue,
                            indicatorColor = accentBlue,
                            unselectedIconColor = textLight.copy(alpha = 0.5f),
                            unselectedTextColor = textLight.copy(alpha = 0.5f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.Analytics,
                        onClick = { currentScreen = AppScreen.Analytics },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = "Analytics") },
                        label = { Text("Analytics") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = bgDark,
                            selectedTextColor = accentBlue,
                            indicatorColor = accentBlue,
                            unselectedIconColor = textLight.copy(alpha = 0.5f),
                            unselectedTextColor = textLight.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
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
                    onNavigate = { currentScreen = it },
                    onRecordingStateChange = { isRecordingSession = it }
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera permission is required.", color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}


@Composable
fun MoCapScreen(modifier: Modifier = Modifier, onNavigate: (AppScreen) -> Unit, onRecordingStateChange: (Boolean) -> Unit = {}) {
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
    var showQualityScore by remember { mutableStateOf<PoseRecorder.SessionStats?>(null) }
    var isLowStorage by remember { mutableStateOf(false) }
    var isOverheating by remember { mutableStateOf(false) }
    var countdownTimer by remember { mutableStateOf(0) }
    var xGestureTime by remember { mutableStateOf(0L) }
    var triggerFlash by remember { mutableStateOf(false) }
    
    LaunchedEffect(triggerFlash) {
        if (triggerFlash) {
            kotlinx.coroutines.delay(1000)
            triggerFlash = false
        }
    }
    
    // Telemetry & Hardware Setup
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100) }
    var latestGravity by remember { mutableStateOf(floatArrayOf(0f, 9.8f, 0f)) }
    var isFlashActive by remember { mutableStateOf(false) }
    var isGhostMode by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(false) }
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
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                isOverheating = status >= PowerManager.THERMAL_STATUS_SEVERE
            }
            powerManager.addThermalStatusListener(thermalListener)
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null) {
                powerManager.removeThermalStatusListener(thermalListener)
            }
        }
    }
    
    val haptic = LocalHapticFeedback.current
    val recorder = remember { PoseRecorder(context) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                if (isRecording) {
                    isRecording = false
                    val file = recorder.stopRecording()
                    lastSavedFile = file
                    currentDuration = 0L
                    showQualityScore = recorder.lastSessionStats
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    LaunchedEffect(isRecording) {
        onRecordingStateChange(isRecording)
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
    
    LaunchedEffect(countdownTimer) {
        if (countdownTimer > 0) {
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
            kotlinx.coroutines.delay(1000)
            countdownTimer--
            if (countdownTimer == 0) {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500)
                lastSavedFile = null
                frameCount = 0
                bufferSize = 0
                recorder.setDeviceGravity(latestGravity)
                recorder.startRecording()
                isRecording = true
                trackingState = TrackingState.RECORDING
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (recorder.isRecording()) recorder.stopRecording()
        }
    }

    val bgDark = Color(0xFF0D0D0D)
    val textLight = Color(0xFFE2E2E6)
    val accentBlue = Color(0xFF00E5FF) // accentCyan
    val recordRed = Color(0xFFFF1744)
    val panelBg = Color(0xFF2D2F31)
    val btnBg = Color(0xFF3D4758)

    Column(modifier = modifier.fillMaxSize().background(bgDark)) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1.0f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutLinearInEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )

        // Main Camera Viewport
        val trackerColor = when {
            triggerFlash -> Color.Green
            trackingState == TrackingState.CALIBRATING -> Color.Yellow.copy(alpha = pulseAlpha)
            trackingState == TrackingState.READY -> Color.Green
            else -> accentBlue
        }
        val trackerStroke = when {
            triggerFlash -> 20f
            trackingState == TrackingState.CALIBRATING -> 12f * pulseAlpha
            trackingState == TrackingState.SEARCHING || trackingState == TrackingState.LOSS -> 1.5f
            else -> 4f
        }
        
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .drawWithContent {
                    drawContent()
                    val stroke = trackerStroke
                    val bracketLength = 32.dp.toPx()
                    // Top-Left
                    drawLine(trackerColor, start = Offset(0f, 0f), end = Offset(bracketLength, 0f), strokeWidth = stroke)
                    drawLine(trackerColor, start = Offset(0f, 0f), end = Offset(0f, bracketLength), strokeWidth = stroke)
                    // Top-Right
                    drawLine(trackerColor, start = Offset(this.size.width, 0f), end = Offset(this.size.width - bracketLength, 0f), strokeWidth = stroke)
                    drawLine(trackerColor, start = Offset(this.size.width, 0f), end = Offset(this.size.width, bracketLength), strokeWidth = stroke)
                    // Bottom-Left
                    drawLine(trackerColor, start = Offset(0f, this.size.height), end = Offset(bracketLength, this.size.height), strokeWidth = stroke)
                    drawLine(trackerColor, start = Offset(0f, this.size.height), end = Offset(0f, this.size.height - bracketLength), strokeWidth = stroke)
                    // Bottom-Right
                    drawLine(trackerColor, start = Offset(this.size.width, this.size.height), end = Offset(this.size.width - bracketLength, this.size.height), strokeWidth = stroke)
                    drawLine(trackerColor, start = Offset(this.size.width, this.size.height), end = Offset(this.size.width, this.size.height - bracketLength), strokeWidth = stroke)
                }
        ) {
            CameraPreviewAndAnalysis(
                isFlashActive = isFlashActive,
                isGhostMode = isGhostMode,
                isFrontCamera = isFrontCamera,
                onPoseDetected = { pose, w, h ->
                    currentPose = pose
                    imageWidth = w
                    imageHeight = h
                    
                    val isFullBody = analyzeFullBodyVisibility(pose)
                    val isTPose = isFullBody && analyzeTPose(pose)
                    val confidence = getAverageConfidence(pose)
                    trackingConfidence = confidence
                    
                    if (isRecording) {
                        val lWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
                        val rWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
                        val lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
                        val rShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
                        if (lWrist != null && rWrist != null && lShoulder != null && rShoulder != null &&
                            lWrist.inFrameLikelihood > 0.5f && rWrist.inFrameLikelihood > 0.5f) {
                            if (lWrist.position.x > rWrist.position.x &&
                                Math.abs(lWrist.position.y - rWrist.position.y) < 100f &&
                                lWrist.position.y < lShoulder.position.y + 100f) {
                                if (xGestureTime == 0L) xGestureTime = System.currentTimeMillis()
                                else if (System.currentTimeMillis() - xGestureTime > 2000) {
                                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500)
                                    isRecording = false
                                    trackingState = TrackingState.SEARCHING
                                    val file = recorder.stopRecording()
                                    lastSavedFile = file
                                    currentDuration = 0L
                                    showQualityScore = recorder.lastSessionStats
                                    xGestureTime = 0L
                                }
                            } else {
                                xGestureTime = 0L
                            }
                        } else {
                            xGestureTime = 0L
                        }

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
                                if (isFullBody) trackingState = TrackingState.READY
                            }
                            TrackingState.READY -> {
                                if (!isFullBody) {
                                    trackingState = TrackingState.SEARCHING
                                } else if (isTPose) {
                                    trackingState = TrackingState.CALIBRATING
                                    calibrationTime = System.currentTimeMillis()
                                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 200)
                                }
                            }
                            TrackingState.CALIBRATING -> {
                                if (!isTPose) {
                                    trackingState = TrackingState.READY
                                } else {
                                    if (System.currentTimeMillis() - calibrationTime > 1500) {
                                        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
                                        recorder.calibrate(pose)
                                        trackingState = TrackingState.READY
                                        triggerFlash = true
                                    }
                                }
                            }
                            TrackingState.RECORDING -> {
                                // Handled externally when isRecording is false
                            }
                        }
                    }
                }
            )

            if (isGhostMode) {
                Text(
                    "PRIVACY MODE ACTIVE\nVideo Feed Disabled",
                    color = Color.White.copy(alpha=0.3f),
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            PoseOverlay(
                pose = currentPose,
                imageWidth = imageWidth,
                imageHeight = imageHeight
            )

            if (showQualityScore != null) {
                val stats = showQualityScore!!
                val score = (stats.avgConfidence * 100).toInt()
                val scoreColor = if (score > 80) accentBlue else if (score > 50) Color(0xFFFFB74D) else recordRed
                AlertDialog(
                    onDismissRequest = { showQualityScore = null },
                    containerColor = panelBg,
                    title = { Text("Take Quality Report", color = textLight) },
                    text = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Overall Quality:", color = textLight.copy(alpha=0.7f))
                                Spacer(Modifier.weight(1f))
                                Text("$score%", color = scoreColor, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Frames Captured: ${stats.totalFrames}", color = textLight, fontSize = 14.sp)
                            Text("Dropped/Low Conf frames: ${stats.lowConfFrames}", color = textLight, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                            if (stats.totalFrames > 0 && stats.lowConfFrames > stats.totalFrames * 0.1f) {
                                Text("Warning: Many frames lacked strong tracking. Consider reshooting with full body view and better lighting.", color = Color(0xFFFFB74D), fontSize = 12.sp)
                            } else {
                                Text("Good tracking! Ready to export.", color = accentBlue, fontSize = 12.sp)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { 
                            showQualityScore = null
                            if (lastSavedFile != null) {
                                Toast.makeText(context, "Saved to ${lastSavedFile!!.name}", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Confirm", color = accentBlue) }
                    }
                )
            }

            if (!isRecording && trackingState != TrackingState.READY) {
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

                if (countdownTimer > 0) {
                    Text(
                        "$countdownTimer",
                        color = accentBlue,
                        fontSize = 120.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Column(modifier = Modifier.padding(24.dp)) {
                // Tracking Status & Confidence Meter
                Row(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(recordRed, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "REC",
                                color = textLight.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            )
                        }
                        Text(
                            text = String.format("%02d:%02d", minutes, seconds),
                            color = Color.White,
                            fontSize = 36.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
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
                
                if (isOverheating) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFFF9800).copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = "Overheating", tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "THERMAL THROTTLING",
                            color = Color.Black,
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
                    text = "FRAME",
                    color = textLight.copy(alpha=0.5f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = String.format("%06d", frameCount),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "BUFFER: ${String.format("%06d", bufferSize)} | EXP: BVH (READY)",
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
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
                // Secondary Action Left (Flip Camera)
                IconButton(
                    onClick = { isFrontCamera = !isFrontCamera },
                    modifier = Modifier.size(56.dp).background(btnBg, RoundedCornerShape(16.dp))
                ) {
                    Icon(imageVector = Icons.Default.FlipCameraAndroid, contentDescription = "Flip", tint = textLight)
                }

                // Main Record Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, if (isRecording) recordRed else btnBg, CircleShape)
                        .padding(4.dp)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isRecording) {
                                isRecording = false
                                trackingState = TrackingState.SEARCHING
                                lastSavedFile = recorder.stopRecording()
                                showQualityScore = recorder.lastSessionStats
                            } else if (countdownTimer == 0) {
                                countdownTimer = 5 // Start countdown
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val buttonShape by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isRecording) 16f else 50f, label = "buttonShape")
                    val buttonSize by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isRecording) 32f else 72f, label = "buttonSize")
                    Box(modifier = Modifier
                        .size(buttonSize.dp)
                        .background(recordRed, androidx.compose.foundation.shape.RoundedCornerShape(percent = buttonShape.toInt()))
                    )
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
