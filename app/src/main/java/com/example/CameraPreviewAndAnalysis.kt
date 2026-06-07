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
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions

@Composable
fun CameraPreviewAndAnalysis(
    onPoseDetected: (Pose, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    isFlashActive: Boolean = false,
    isGhostMode: Boolean = false,
    isFrontCamera: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    // We will keep a reference to camera to change flash later
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    
    LaunchedEffect(isFlashActive) {
        camera?.cameraControl?.enableTorch(isFlashActive)
    }

    val options = remember {
        AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
    }
    val poseDetector = remember { PoseDetection.getClient(options) }

    LaunchedEffect(isGhostMode, isFrontCamera, previewView) {
        if (previewView == null) return@LaunchedEffect
        
        val executor = ContextCompat.getMainExecutor(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val imageAnalyzer = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy(Size(720, 1280), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(executor) { imageProxy ->
                        processImageProxy(poseDetector, imageProxy) { pose, width, height ->
                            onPoseDetected(pose, width, height)
                        }
                    }
                }

            val cameraSelector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                
                if (isGhostMode) {
                    // Ghost mode: Only bind ImageAnalysis, zero preview rendering
                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, imageAnalyzer
                    )
                } else {
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView!!.surfaceProvider)
                    }
                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, imageAnalyzer
                    )
                }
                camera?.cameraControl?.enableTorch(isFlashActive)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, executor)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isGhostMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
        
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            update = { pv ->
                previewView = pv
            },
            modifier = Modifier.fillMaxSize().let { 
                if (isGhostMode) it.alpha(0f) else it 
            }
        )
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun processImageProxy(
    poseDetector: com.google.mlkit.vision.pose.PoseDetector,
    imageProxy: ImageProxy,
    onSuccess: (Pose, Int, Int) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        poseDetector.process(image)
            .addOnSuccessListener { pose ->
                // Image dimensions can be swapped based on rotation
                val isImageFlipped = imageProxy.imageInfo.rotationDegrees == 90 || imageProxy.imageInfo.rotationDegrees == 270
                val width = if (isImageFlipped) imageProxy.height else imageProxy.width
                val height = if (isImageFlipped) imageProxy.width else imageProxy.height
                onSuccess(pose, width, height)
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
}
