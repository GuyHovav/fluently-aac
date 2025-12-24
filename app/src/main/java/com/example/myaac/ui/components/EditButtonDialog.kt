package com.example.myaac.ui.components

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.myaac.model.AacButton
import com.example.myaac.model.ButtonAction
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun EditButtonDialog(
    button: AacButton,
    onDismiss: () -> Unit,
    onSave: (AacButton) -> Unit,
    onIdentifyItem: suspend (Bitmap) -> Pair<String, FloatArray?>
) {
    var label by remember { mutableStateOf(button.label) }
    var speechText by remember { mutableStateOf(button.speechText ?: "") }
    var selectedColor by remember { mutableStateOf(button.backgroundColor) }
    var imageUri by remember { mutableStateOf<Uri?>(button.iconPath?.let { Uri.parse(it) }) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var showSymbolSearch by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Helper to crop
    fun cropBitmap(source: Bitmap, box: FloatArray): Bitmap {
        val ymin = (box[0] * source.height).toInt().coerceIn(0, source.height)
        val xmin = (box[1] * source.width).toInt().coerceIn(0, source.width)
        val ymax = (box[2] * source.height).toInt().coerceIn(0, source.height)
        val xmax = (box[3] * source.width).toInt().coerceIn(0, source.width)
        
        val width = (xmax - xmin).coerceAtLeast(1)
        val height = (ymax - ymin).coerceAtLeast(1)
        
        return Bitmap.createBitmap(source, xmin, ymin, width, height)
    }

    // Camera Launcher
    // Note: In real app, need to manage FileProvider for full qualtiy, using thumbnail for prototype speed if data is null
    // But for "Get Content", we get a URI.
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            // Auto-analyze? Maybe let user click button.
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            // Save bitmap to temp file to get URI for display/storage
            val file = File(context.cacheDir, "temp_cam_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            imageUri = Uri.fromFile(file)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        }
    }
    
    // Helper to get bitmap from URI
    @Suppress("DEPRECATION")
    fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT < 28) {
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            } else {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Edit Button",
                    style = MaterialTheme.typography.titleLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Image Section
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.LightGray)
                        .clickable { galleryLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "Button Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Image, contentDescription = "Add Image", tint = Color.Gray)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                     Button(onClick = { galleryLauncher.launch("image/*") }) {
                         Icon(Icons.Default.Image, contentDescription = null)
                         Spacer(modifier = Modifier.width(4.dp))
                         Text("Gallery")
                     }
                     Button(onClick = { 
                         if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            cameraLauncher.launch(null)
                         } else {
                            permissionLauncher.launch(android.Manifest.permission.CAMERA)
                         }
                     }) {
                         Icon(Icons.Default.CameraAlt, contentDescription = null)
                         Spacer(modifier = Modifier.width(4.dp))
                         Text("Camera")
                     }

                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showSymbolSearch = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Search Symbol Library")
                }

                // AI Magic Button
                if (imageUri != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val uri = imageUri
                            if (uri == null) {
                                errorMessage = "Error: No image selected"
                                return@OutlinedButton
                            }
                            
                            try {
                                val bitmap = uriToBitmap(uri)
                                if (bitmap == null) {
                                    errorMessage = "Error: Failed to load image"
                                    return@OutlinedButton
                                }
                                
                                isAnalyzing = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        val (name, coords) = onIdentifyItem(bitmap)
                                        
                                        // Check if result is an error
                                        if (name.startsWith("Error:") || name.startsWith("Err:")) {
                                            errorMessage = name
                                            isAnalyzing = false
                                            return@launch
                                        }
                                        
                                        label = name
                                        
                                        if (coords != null) {
                                            var processedBitmap = cropBitmap(bitmap, coords)
                                            
                                            // Apply Background Removal (Shopping Site Look)
                                            // DISABLED TEMPORARILY due to dependency issue
                                            // try {
                                            //      processedBitmap = com.example.myaac.util.ImageHelper.removeBackground(processedBitmap)
                                            // } catch (e: Exception) {
                                            //     e.printStackTrace()
                                            // }

                                            // Save processed image
                                            val file = File(context.cacheDir, "processed_${System.currentTimeMillis()}.png")
                                            FileOutputStream(file).use { out ->
                                                processedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                            }
                                            imageUri = Uri.fromFile(file)
                                        }
                                        
                                        isAnalyzing = false
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        errorMessage = "Error: ${e.message ?: "Unknown error"}"
                                        isAnalyzing = false
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                errorMessage = "Error: ${e.message ?: "Failed to process image"}"
                            }
                        },
                        enabled = !isAnalyzing && imageUri != null
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyzing...")
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Auto-name with AI")
                        }
                    }
                    
                    // Display error message if any
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Color Picker
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                   listOf(0xFFFFFFFF, 0xFFE3F2FD, 0xFFFFEBEE, 0xFFE8F5E9, 0xFFFFF3E0, 0xFFF3E5F5).forEach { colorVal ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(colorVal))
                                .border(
                                    width = if (selectedColor == colorVal) 2.dp else 1.dp,
                                    color = if (selectedColor == colorVal) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorVal }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        // Persist image to internal storage if new one selected
                        // For prototype we just use the URI string, but in real app copy to app storage
                        onSave(
                            button.copy(
                                label = label,
                                speechText = speechText.ifBlank { null },
                                backgroundColor = selectedColor,
                                iconPath = imageUri?.toString(),
                                action = if (button.action is ButtonAction.Speak) {
                                     ButtonAction.Speak(speechText.ifBlank { label })
                                } else {
                                    button.action
                                }
                            )
                        )
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
    
    if (showSymbolSearch) {
        SymbolSearchDialog(
            onDismiss = { showSymbolSearch = false },
            onSymbolSelected = { url, symbolLabel ->
                imageUri = Uri.parse(url)
                label = symbolLabel.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                showSymbolSearch = false
            }
        )
    }
}
