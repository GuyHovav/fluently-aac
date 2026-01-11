package com.example.myaac.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onGloballyPositioned

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ImageEditorDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onSave: (Bitmap) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // Use onGloballyPositioned to get size to avoid side effects in drawing
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var cropRect by remember { mutableStateOf(Rect.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false) 
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            canvasSize = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
                            val canvasWidth = coordinates.size.width.toFloat()
                            val canvasHeight = coordinates.size.height.toFloat()
                            val squareSize = kotlin.math.min(canvasWidth, canvasHeight) * 0.8f
                            cropRect = Rect(
                                offset = Offset(
                                    (canvasWidth - squareSize) / 2f,
                                    (canvasHeight - squareSize) / 2f
                                ),
                                size = Size(squareSize, squareSize)
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 5f)
                                offset += pan
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Image Drawing Logic
                        val imgWidth = bitmap.width.toFloat()
                        val imgHeight = bitmap.height.toFloat()
                        
                        // Recalculate these for drawing to ensure sync with state
                        val squareSize = kotlin.math.min(size.width, size.height) * 0.8f
                        // Logic in onGloballyPositioned should match this.
                        
                        val baseScale = max(squareSize / imgWidth, squareSize / imgHeight)
                        val effectiveScale = baseScale * scale
                        
                        // Center of canvas
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        
                        val drawLeft = centerX - (imgWidth * baseScale) / 2f
                        val drawTop = centerY - (imgHeight * baseScale) / 2f
                        
                        // Apply transformations
                        withTransform({
                            translate(offset.x, offset.y)
                            scale(scale, scale, pivot = Offset(centerX, centerY)) 
                        }) {
                             drawImage(
                                image = bitmap.asImageBitmap(),
                                dstOffset = IntOffset(drawLeft.roundToInt(), drawTop.roundToInt()),
                                dstSize = IntSize((imgWidth * baseScale).roundToInt(), (imgHeight * baseScale).roundToInt())
                             )
                        }
                        
                        // Draw Overlay
                        val rect = cropRect // Use state
                        if (rect != Rect.Zero) {
                            // Top
                            drawRect(Color.Black.copy(alpha = 0.7f), Offset(0f, 0f), Size(size.width, rect.top))
                            // Bottom
                            drawRect(Color.Black.copy(alpha = 0.7f), Offset(0f, rect.bottom), Size(size.width, size.height - rect.bottom))
                            // Left
                            drawRect(Color.Black.copy(alpha = 0.7f), Offset(0f, rect.top), Size(rect.left, rect.height))
                            // Right
                            drawRect(Color.Black.copy(alpha = 0.7f), Offset(rect.right, rect.top), Size(size.width - rect.right, rect.height))
                            
                             // Border
                            drawRect(Color.White, rect.topLeft, rect.size, style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                }
                
                // Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(32.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            .size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                         modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                         IconButton(
                            onClick = { scale = (scale - 0.2f).coerceAtLeast(0.5f) },
                        ) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White)
                        }
                        Text(
                            text = "${(scale * 100).toInt()}%",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                        IconButton(
                            onClick = { scale = (scale + 0.2f).coerceAtMost(5f) },
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White)
                        }
                    }

                    IconButton(
                        onClick = {
                            if (canvasSize != Size.Zero && cropRect != Rect.Zero) {
                                // Perform Crop
                                val cropped = performCrop(
                                    source = bitmap,
                                    cropRect = cropRect,
                                    scale = scale,
                                    offset = offset,
                                    canvasSize = canvasSize
                                )
                                onSave(cropped)
                            }
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .size(56.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save", tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun performCrop(
    source: Bitmap,
    cropRect: Rect,
    scale: Float,
    offset: Offset,
    canvasSize: Size
): Bitmap {
    android.util.Log.d("ImageEditor", "performCrop: cropRect=$cropRect, scale=$scale, offset=$offset, canvasSize=$canvasSize")
    
    val imgWidth = source.width.toFloat()
    val imgHeight = source.height.toFloat()
    val squareSize = cropRect.width
    
    val baseScale = max(squareSize / imgWidth, squareSize / imgHeight)
    
    val centerX = canvasSize.width / 2f
    val centerY = canvasSize.height / 2f
    
    fun screenToBitmap(x: Float, y: Float): Offset {
        val tx = x - offset.x
        val ty = y - offset.y
        
        val sx = (tx - centerX) / scale + centerX
        val sy = (ty - centerY) / scale + centerY
        
        val imageScreenLeft = centerX - (imgWidth * baseScale) / 2f
        val imageScreenTop = centerY - (imgHeight * baseScale) / 2f
        
        val bx = (sx - imageScreenLeft) / baseScale
        val by = (sy - imageScreenTop) / baseScale
        
        return Offset(bx, by)
    }
    
    val topLeft = screenToBitmap(cropRect.left, cropRect.top)
    val bottomRight = screenToBitmap(cropRect.right, cropRect.bottom)
    
    android.util.Log.d("ImageEditor", "performCrop: srcRect=$topLeft - $bottomRight")

    val outputSize = 512
    val result = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(result)
    // Draw white background to debug if transparency is issue, or keep black/transparent?
    // Let's use TRANSPARENT for PNG, but maybe the user wants a solid button?
    // Buttons usually have background color.
    canvas.drawColor(android.graphics.Color.TRANSPARENT)
    
    val matrix = android.graphics.Matrix()
    
    val srcRect = android.graphics.RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
    val dstRect = android.graphics.RectF(0f, 0f, outputSize.toFloat(), outputSize.toFloat())
    
    matrix.setRectToRect(srcRect, dstRect, android.graphics.Matrix.ScaleToFit.FILL)
    
    val paint = android.graphics.Paint().apply { 
        isFilterBitmap = true 
        isAntiAlias = true
    }
    
    try {
        canvas.drawBitmap(source, matrix, paint)
    } catch (e: Exception) {
        android.util.Log.e("ImageEditor", "Error drawing bitmap", e)
    }
    
    return result
}
