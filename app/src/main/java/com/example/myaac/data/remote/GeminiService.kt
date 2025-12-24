package com.example.myaac.data.remote

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting

class GeminiService(private val apiKey: String) {

    // Separate models for Vision and Text tasks to ensure stability
    private val visionModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey,
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.ONLY_HIGH)
        )
    )
    
    private val textModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey,
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.ONLY_HIGH)
        )
    )

    suspend fun identifyItem(image: Bitmap): Pair<String, FloatArray?> {
        return withContext(Dispatchers.IO) {
            val prompt = """
                Identify the main object in this image and provide its bounding box location.
                
                CRITICAL: You MUST respond in this EXACT format:
                ObjectName | ymin, xmin, ymax, xmax
                
                Where:
                - ObjectName: Single simple word (e.g., Apple, Dog, Chair, Person)
                - ymin, xmin, ymax, xmax: Bounding box coordinates as percentages (0-100)
                  * ymin: top edge (% from top)
                  * xmin: left edge (% from left)
                  * ymax: bottom edge (% from top)
                  * xmax: right edge (% from left)
                
                Examples:
                - "Apple | 20, 30, 80, 70" (apple in center-right)
                - "Dog | 10, 5, 90, 95" (dog filling most of image)
                - "Chair | 40, 25, 85, 75" (chair in lower portion)
                
                Do NOT identify specific individuals.
                Do NOT add any extra text or explanation.
                ALWAYS include the pipe | and coordinates.
            """.trimIndent()
            
            val scaledBitmap = scaleBitmap(image)
            
            try {
                val response = visionModel.generateContent(
                    content {
                        image(scaledBitmap)
                        text(prompt)
                    }
                )
                
                // Detailed Debugging
                val finishReason = response.candidates.firstOrNull()?.finishReason
                val safety = response.promptFeedback?.blockReason
                
                android.util.Log.e("GeminiDebug", "FinishReason: $finishReason, BlockReason: $safety")
                
                if (response.text == null) {
                    if (safety != null) return@withContext "Err: Blocked ($safety)" to null
                    if (finishReason != null) return@withContext "Err: End ($finishReason)" to null
                    return@withContext "Err: Empty/Null Response" to null
                }

                val text = response.text!!.trim()
                android.util.Log.d("GeminiDebug", "Raw Response: '$text'")

                // Parse "Name | 10, 20, 90, 80" or "Name|10,20,90,80"
                if (text.contains("|")) {
                    val parts = text.split("|")
                    val name = parts[0].trim()
                    val coordsString = parts.getOrNull(1)?.trim() ?: ""
                    
                    android.util.Log.d("GeminiDebug", "Parsed name: '$name', coords string: '$coordsString'")
                    
                    // Remove brackets and parse coordinates
                    val cleanCoords = coordsString.replace("[", "").replace("]", "").replace(" ", "")
                    val coords = cleanCoords.split(",").mapNotNull { 
                        it.trim().toFloatOrNull()?.div(100f) 
                    }.toFloatArray()
                    
                    android.util.Log.d("GeminiDebug", "Parsed ${coords.size} coordinates: ${coords.joinToString()}")
                    
                    if (coords.size == 4) {
                        android.util.Log.d("GeminiDebug", "✓ Successfully parsed bounding box for '$name'")
                        return@withContext name to coords
                    } else {
                        android.util.Log.w("GeminiDebug", "✗ Invalid coordinate count (${coords.size}), expected 4")
                    }
                    return@withContext name to null
                }
                
                // Fallback: If it just returned a name without coordinates
                android.util.Log.w("GeminiDebug", "✗ No pipe delimiter found, no bounding box available")
                return@withContext text to null
                
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("GeminiDebug", "Exception: ${e.message}")
                
                val msg = e.localizedMessage ?: "Unknown Error"
                if (msg.contains("400")) return@withContext "Err: 400 Bad Request" to null
                if (msg.contains("401")) return@withContext "Err: 401 Unauthorized" to null
                
                return@withContext "Err: ${e.javaClass.simpleName}: $msg" to null
            }
        }
    }

    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val maxDimension = 1024
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var newWidth = originalWidth
        var newHeight = originalHeight

        if (originalWidth > maxDimension || originalHeight > maxDimension) {
            if (originalWidth > originalHeight) {
                newWidth = maxDimension
                newHeight = (maxDimension.toFloat() / originalWidth * originalHeight).toInt()
            } else {
                newHeight = maxDimension
                newWidth = (maxDimension.toFloat() / originalHeight * originalWidth).toInt()
            }
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }
        return bitmap
    }

    suspend fun predictNextButtons(currentSentence: String, availableButtons: List<String>): List<String> {
        return withContext(Dispatchers.IO) {
            // Optimization: Limit available buttons to ~100 distinct labels to avoid token limits if database grows huge
            val distinctCandidates = availableButtons.distinct().take(200).joinToString(", ")
            
            val prompt = """
                Context: User is building a sentence on an AAC device.
                Current Sentence: "$currentSentence"
                Available Buttons: [$distinctCandidates]
                
                Task: Select the 3 most likely next buttons from the Available Buttons list that complete the sentence grammatically or contextually.
                Return ONLY a comma-separated list of the 3 button labels. Do not invent new buttons. If unsure, return nothing.
            """.trimIndent()
            
            try {
                val response = textModel.generateContent(prompt)
                val text = response.text?.trim() ?: return@withContext emptyList()
                
                // Parse "Juice, Please, Now" -> ["Juice", "Please", "Now"]
                text.split(",").map { it.trim() }.filter { it.isNotBlank() }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    suspend fun generateBoard(topic: String): List<String> {
        return withContext(Dispatchers.IO) {
            val prompt = """
                Create a list of 16 vocabulary words related to the topic '$topic' for an AAC communication board.
                Includes nouns, verbs, and adjectives.
                Return ONLY the words, separated by commas.
                Example: Dog, Cat, Pet, Walk, Furry, Bark
            """.trimIndent()
            
            try {
                val response = textModel.generateContent(prompt)
                val text = response.text?.trim() ?: return@withContext emptyList()
                text.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(16)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
}
