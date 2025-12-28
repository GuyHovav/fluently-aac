package com.example.myaac.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraScreen(
    onImageCaptured: (Bitmap) -> Unit,
    onError: (ImageCaptureException) -> Unit,
    onClose: () -> Unit,
    currentPhotoCount: Int = 0,
    maxPhotos: Int = Int.MAX_VALUE
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var preview by remember { mutableStateOf<Preview?>(null) }
    
    // Default to back camera
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    
    val isLimitReached = currentPhotoCount >= maxPhotos

    // Get CameraProvider
    LaunchedEffect(Unit) {
        try {
            cameraProvider = suspendCoroutine { cont ->
                cameraProviderFuture.addListener({
                    try {
                        cont.resume(cameraProviderFuture.get())
                    } catch (e: Exception) {
                        cont.resumeWith(Result.failure(e))
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        } catch (e: Exception) {
            Log.e("CameraScreen", "Failed to get camera provider", e)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                
                val listener = object :android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                        val currentZoomRatio = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                        val delta = detector.scaleFactor
                        camera?.cameraControl?.setZoomRatio(currentZoomRatio * delta)
                        return true
                    }
                }
                val scaleGestureDetector = android.view.ScaleGestureDetector(ctx, listener)
                
                previewView.setOnTouchListener { _, event ->
                    scaleGestureDetector.onTouchEvent(event)
                    return@setOnTouchListener true
                }
                
                previewView
            },
            update = { previewView ->
                val provider = cameraProvider ?: return@AndroidView
                
                // Re-bind use cases
                try {
                    // Create use cases
                    val previewUseCase = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imageCaptureUseCase = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    
                    // Update state variables so we can use imageCapture in the button click
                    imageCapture = imageCaptureUseCase
                    preview = previewUseCase

                    provider.unbindAll()
                    val boundCamera = provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        previewUseCase,
                        imageCaptureUseCase
                    )
                    camera = boundCamera
                } catch (exc: Exception) {
                    Log.e("CameraScreen", "Use case binding failed", exc)
                }
            }
        )

        // Overlays
        
        // Close Button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                Icons.Default.Close, 
                contentDescription = "Close Camera",
                tint = Color.White
            )
        }

        // Limit Warning
        if (isLimitReached) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Photo Limit Reached",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Switch Camera
            IconButton(
                onClick = {
                    cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera",
                    tint = Color.White
                )
            }

            // Capture Button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .border(4.dp, if (isLimitReached) Color.Gray else Color.White, CircleShape)
                    .padding(4.dp)
            ) {
                Button(
                    onClick = {
                        val imageCapture = imageCapture ?: return@Button
                        imageCapture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bitmap = image.toBitmap()
                                    // Should we rotate? CameraX usually handles this with ImageCapture?
                                    // Actually ImageProxy.toBitmap() handles rotation if using proper extensions or manual.
                                    // Let's ensure we get a rotated bitmap.
                                    val rotationDegrees = image.imageInfo.rotationDegrees
                                    val rotatedBitmap = if (rotationDegrees != 0) {
                                        rotateBitmap(bitmap, rotationDegrees.toFloat())
                                    } else {
                                        bitmap
                                    }
                                    
                                    onImageCaptured(rotatedBitmap)
                                    image.close()
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e("CameraScreen", "Photo capture failed: ${exception.message}", exception)
                                    onError(exception)
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isLimitReached) Color.Gray else Color.White, CircleShape),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLimitReached) Color.Gray else Color.White,
                        disabledContainerColor = Color.Gray
                    ),
                    shape = CircleShape,
                    enabled = !isLimitReached
                ) {}
            }
            
            // Spacer for symmetry
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

// Extension to convert ImageProxy to Bitmap
// Note: In a real production app we might save to file directly to avoid OOM with large bitmaps, 
// but for this app's existing logic (passing Bitmaps around), this is consistent.
fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degrees)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
