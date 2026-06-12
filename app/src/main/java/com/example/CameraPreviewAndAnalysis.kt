package com.example

import android.annotation.SuppressLint
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.isActive
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions

enum class TrackingMode { BODY, FACE }

@Composable
fun CameraPreviewAndAnalysis(
    trackingMode: TrackingMode = TrackingMode.BODY,
    onPoseDetected: (SmoothedPose, Int, Int) -> Unit = {_,_,_->},
    onFaceDetected: (FaceTrackingFrame, Int, Int) -> Unit = {_,_,_->},
    modifier: Modifier = Modifier,
    isFlashActive: Boolean = false,
    isGhostMode: Boolean = false,
    isFrontCamera: Boolean = false,
    isOverheating: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val backgroundExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    
    val options = remember {
        AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
    }
    val poseDetector = remember { PoseDetection.getClient(options) }
    val prefs = remember { context.getSharedPreferences("mocap_prefs", android.content.Context.MODE_PRIVATE) }
    val poseProcessor = remember { 
        AdaptivePoseProcessor().apply {
            val savedRl = prefs.getString("rl_memory", null)
            if (savedRl != null) loadData(savedRl)
        }
    }

    // MediaPipe face tracker state
    var currentFaceWidth by remember { mutableStateOf(0) }
    var currentFaceHeight by remember { mutableStateOf(0) }
    
    val currentOnPoseDetected by rememberUpdatedState(onPoseDetected)
    val currentOnFaceDetected by rememberUpdatedState(onFaceDetected)
    val currentIsOverheating by rememberUpdatedState(isOverheating)

    val faceTracker = remember(context) { 
        try {
            MediaPipeFaceTracker(
                context = context,
                onFrame = { frame ->
                    currentOnFaceDetected(frame, currentFaceWidth, currentFaceHeight)
                },
                onError = { it.printStackTrace() }
            ) 
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } catch (e: Error) { // Traps fatal native crashes like UnsatisfiedLinkError
            e.printStackTrace()
            null
        }
    }
    
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    
    DisposableEffect(Unit) {
        onDispose {
            prefs.edit().putString("rl_memory", poseProcessor.getSaveData()).apply()
            try { cameraProviderFuture.get().unbindAll() } catch (e: Exception) {}
            Thread {
                Thread.sleep(500)
                try { poseDetector.close() } catch (e: Exception) {}
                try { faceTracker?.close() } catch (e: Exception) {}
                try { backgroundExecutor.shutdown() } catch (e: Exception) {}
            }.start()
        }
    }
    
    LaunchedEffect(isFlashActive, cameraControl) {
        try {
            cameraControl?.enableTorch(isFlashActive)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    val resolutionSelector = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    }

    Box(modifier = modifier) {
        if (!isGhostMode) {
            val previewView = remember {
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            }

            DisposableEffect(trackingMode, isFrontCamera) {
                val listener = Runnable {
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        
                        val preview = Preview.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .build()
                            .also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            
                        var frameCounter = 0
                        val imageAnalyzer = ImageAnalysis.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(backgroundExecutor) { imageProxy ->
                                    frameCounter++
                                    if (currentIsOverheating && frameCounter % 2 == 0) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }
                                    if (trackingMode == TrackingMode.BODY) {
                                        processImageProxy(poseDetector, imageProxy) { pose, width, height, timestamp ->
                                            val smoothedPose = poseProcessor.process(pose, timestamp)
                                            currentOnPoseDetected(smoothedPose, width, height)
                                        }
                                    } else {
                                        processMpFaceImageProxy(faceTracker, imageProxy) { width, height ->
                                            currentFaceWidth = width
                                            currentFaceHeight = height
                                        }
                                    }
                                }
                            }
                            
                        var cameraSelector = if (trackingMode == TrackingMode.FACE) CameraSelector.DEFAULT_FRONT_CAMERA else if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            if (!cameraProvider.hasCamera(cameraSelector)) {
                                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                            }
                        } catch (e: Exception) {}

                        try {
                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalyzer
                            )
                            cameraControl = camera.cameraControl
                        } catch (e: Exception) {
                            e.printStackTrace()
                            android.widget.Toast.makeText(context, "Camera locked by another app!", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {}
                }
                cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))
                onDispose {
                    try { cameraProviderFuture.get().unbindAll() } catch (e: Exception) {}
                }
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black))
            
            DisposableEffect(trackingMode, isFrontCamera) {
                val listener = Runnable {
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        var frameCounter = 0
                        val imageAnalyzer = ImageAnalysis.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(backgroundExecutor) { imageProxy ->
                                    frameCounter++
                                    if (currentIsOverheating && frameCounter % 2 == 0) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }
                                    if (trackingMode == TrackingMode.BODY) {
                                        processImageProxy(poseDetector, imageProxy) { pose, width, height, timestamp ->
                                            val smoothedPose = poseProcessor.process(pose, timestamp)
                                            currentOnPoseDetected(smoothedPose, width, height)
                                        }
                                    } else {
                                        processMpFaceImageProxy(faceTracker, imageProxy) { width, height ->
                                            currentFaceWidth = width
                                            currentFaceHeight = height
                                        }
                                    }
                                }
                            }
                        
                        var cameraSelector = if (trackingMode == TrackingMode.FACE) CameraSelector.DEFAULT_FRONT_CAMERA else if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            if (!cameraProvider.hasCamera(cameraSelector)) {
                                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                            }
                        } catch (e: Exception) {}
                        
                        val dummyPreview = Preview.Builder().build()

                        try {
                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                dummyPreview,
                                imageAnalyzer
                            )
                            cameraControl = camera.cameraControl
                        } catch (e: Exception) {
                            e.printStackTrace()
                            android.widget.Toast.makeText(context, "Camera locked by another app!", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {}
                }
                cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))
                onDispose {
                    try { cameraProviderFuture.get().unbindAll() } catch (e: Exception) {}
                }
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun processImageProxy(
    poseDetector: com.google.mlkit.vision.pose.PoseDetector,
    imageProxy: ImageProxy,
    onSuccess: (Pose, Int, Int, Long) -> Unit
) {
    try {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val timestampMs = imageProxy.imageInfo.timestamp / 1000000
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            poseDetector.process(image)
                .addOnSuccessListener { pose ->
                    val isImageFlipped = imageProxy.imageInfo.rotationDegrees == 90 || imageProxy.imageInfo.rotationDegrees == 270
                    val width = if (isImageFlipped) imageProxy.height else imageProxy.width
                    val height = if (isImageFlipped) imageProxy.width else imageProxy.height
                    onSuccess(pose, width, height, timestampMs)
                }
                .addOnFailureListener {
                    // Ignore
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        try { imageProxy.close() } catch (ex: Exception) {}
    }
}

@android.annotation.SuppressLint("UnsafeOptInUsageError")
private fun processMpFaceImageProxy(
    faceTracker: MediaPipeFaceTracker?,
    imageProxy: ImageProxy,
    onSuccess: (Int, Int) -> Unit
) {
    try {
        val bitmap = imageProxy.toBitmap()
        // Rotate bitmap based on image proxy rotation
        val isImageFlipped = imageProxy.imageInfo.rotationDegrees == 90 || imageProxy.imageInfo.rotationDegrees == 270
        val width = if (isImageFlipped) imageProxy.height else imageProxy.width
        val height = if (isImageFlipped) imageProxy.width else imageProxy.height
        
        val matrix = android.graphics.Matrix()
        matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        
        // Front camera might need horizontal flip, but we handle that in overlays usually.
        val rotatedBitmap = android.graphics.Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false
        )
        
        bitmap.recycle()
        
        onSuccess(rotatedBitmap.width, rotatedBitmap.height)
        faceTracker?.detect(rotatedBitmap, imageProxy.imageInfo.timestamp / 1000000)
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        imageProxy.close()
    }
}
