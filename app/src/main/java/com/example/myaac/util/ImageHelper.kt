package com.example.myaac.util

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ImageHelper {

    suspend fun removeBackground(bitmap: Bitmap): Bitmap {
        // Placeholder for when dependency is fixed
        return bitmap
        /*
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        val segmenter = SubjectSegmentation.getClient(options)
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        return suspendCancellableCoroutine { continuation ->
            segmenter.process(inputImage)
                .addOnSuccessListener { result ->
                    // result.foregroundBitmap contains the segmented object(s) with transparent bg
                    val foreground = result.foregroundBitmap
                    if (foreground != null) {
                        continuation.resume(foreground)
                    } else {
                        // If no distinct subject found, return original
                        continuation.resume(bitmap)
                    }
                }
                .addOnFailureListener { e ->
                    // On failure, return original instead of crashing
                    e.printStackTrace()
                    continuation.resume(bitmap)
                }
        }
        */
    }
}
