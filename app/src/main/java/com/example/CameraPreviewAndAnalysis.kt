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
import kotlinx.coroutines.isActive
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import com.google.mlkit.vision.facemesh.FaceMesh 

enum class TrackingMode { BODY, FACE }

@Composable
fun CameraPreviewAndAnalysis(
    trackingMode: TrackingMode = TrackingMode.BODY,
    onPoseDetected: (Pose, Int, Int) -> Unit = {_,_,_->},
    onFaceDetected: (FaceMesh, Int, Int) -> Unit = {_,_,_->},
    modifier: Modifier = Modifier,
    isFlashActive: Boolean = false,
    isGhostMode: Boolean = false,
    isFrontCamera: Boolean = false,
    isOverheating: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current as androidx.activity.ComponentActivity

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val backgroundExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    
    // We will keep a reference to camera to change flash later
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var frameCounter by remember { mutableStateOf(0) }
    
    LaunchedEffect(isFlashActive) {
        camera?.cameraControl?.enableTorch(isFlashActive)
    }

    val options = remember {
        AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
    }
    val poseDetector = remember { PoseDetection.getClient(options) }

    val faceOptions = remember {
        FaceMeshDetectorOptions.Builder()
            .setUseCase(FaceMeshDetectorOptions.FACE_MESH)
            .build()
    }
    val faceDetector = remember { FaceMeshDetection.getClient(faceOptions) }

    var lifecycleState by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            lifecycleState = event.targetState
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                cameraProvider?.unbindAll()
            } catch (e: Exception) {}
        }
    }

    val currentOverheating by rememberUpdatedState(isOverheating)
    val view = androidx.compose.ui.platform.LocalView.current
    var hasWindowFocus by remember { mutableStateOf(view.hasWindowFocus()) }
    DisposableEffect(view) {
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focus ->
            hasWindowFocus = focus
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
        }
    }

    LaunchedEffect(isGhostMode, isFrontCamera, previewView, trackingMode, lifecycleState, hasWindowFocus) {
        if (previewView == null) return@LaunchedEffect
        if (!lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@LaunchedEffect
        if (!hasWindowFocus) return@LaunchedEffect
        
        val scope = this // Capture the coroutine scope for cancellation check

        // Delay carefully to ensure AppOps is fully ready and our permission state is flushed to system
        kotlinx.coroutines.delay(1500)

        // Pre-check for generic emulator to avoid AppOps error spam in logs
        val isGenericEmulator = android.os.Build.FINGERPRINT.contains("generic", ignoreCase = true) || 
                                android.os.Build.MODEL.contains("sdk", ignoreCase = true) ||
                                android.os.Build.MODEL.contains("Emulator", ignoreCase = true) ||
                                android.os.Build.DEVICE.contains("vsoc", ignoreCase = true) ||
                                android.os.Build.DEVICE.contains("cf_", ignoreCase = true) ||
                                android.os.Build.HARDWARE.contains("cutf", ignoreCase = true)
        
        if (isGenericEmulator) {
            return@LaunchedEffect
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            if (!scope.isActive) return@addListener // PREVENT LISTENER LEAK
            if (!lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@addListener
            if (!hasWindowFocus) return@addListener
            try {
                cameraProvider = cameraProviderFuture.get()
                
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(Size(480, 640), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(backgroundExecutor) { imageProxy ->
                            frameCounter++
                            if (currentOverheating && frameCounter % 2 == 0) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            
                            if (trackingMode == TrackingMode.BODY) {
                                processImageProxy(poseDetector, imageProxy) { pose, width, height ->
                                    onPoseDetected(pose, width, height)
                                }
                            } else {
                                processFaceImageProxy(faceDetector, imageProxy) { faceMesh, width, height ->
                                    onFaceDetected(faceMesh, width, height)
                                }
                            }
                        }
                    }

                var cameraSelector = if (trackingMode == TrackingMode.FACE) CameraSelector.DEFAULT_FRONT_CAMERA else if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    // Fallback to back camera if front camera is not available, or vice versa
                    if (cameraProvider?.hasCamera(cameraSelector) != true) {
                        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        } else {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        }
                    }
                    if (cameraProvider?.hasCamera(cameraSelector) != true) {
                        val externalSelector = androidx.camera.core.CameraSelector.Builder()
                            .requireLensFacing(androidx.camera.core.CameraSelector.LENS_FACING_EXTERNAL)
                            .build()
                        if (cameraProvider?.hasCamera(externalSelector) == true) {
                            cameraSelector = externalSelector
                        } else {
                            // Just try binding ANY available camera if we reach this point
                            val infos = cameraProvider?.availableCameraInfos
                            if (infos?.isNotEmpty() == true) {
                                cameraSelector = infos.first().cameraSelector
                            } else {
                                return@addListener
                            }
                        }
                    }
                    cameraProvider?.unbindAll()
                    
                    if (isGhostMode) {
                        // Ghost mode: Only bind ImageAnalysis, zero preview rendering
                        camera = cameraProvider?.bindToLifecycle(
                            activity, cameraSelector, imageAnalyzer
                        )
                    } else {
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView!!.surfaceProvider)
                        }
                        camera = cameraProvider?.bindToLifecycle(
                            activity, cameraSelector, preview, imageAnalyzer
                        )
                    }
                    camera?.cameraControl?.enableTorch(isFlashActive)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
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
                if (isGhostMode) it.alpha(0.01f) else it 
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

@SuppressLint("UnsafeOptInUsageError")
private fun processFaceImageProxy(
    faceDetector: com.google.mlkit.vision.facemesh.FaceMeshDetector,
    imageProxy: ImageProxy,
    onSuccess: (FaceMesh, Int, Int) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                val isImageFlipped = imageProxy.imageInfo.rotationDegrees == 90 || imageProxy.imageInfo.rotationDegrees == 270
                val width = if (isImageFlipped) imageProxy.height else imageProxy.width
                val height = if (isImageFlipped) imageProxy.width else imageProxy.height
                if (faces.isNotEmpty()) {
                    onSuccess(faces[0], width, height)
                }
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
