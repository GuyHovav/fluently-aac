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

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int = 2048): Bitmap {
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

    suspend fun identifyMultipleItems(image: Bitmap): List<Pair<String, FloatArray>> {
        return withContext(Dispatchers.IO) {
            val prompt = """
                You are analyzing a photo to create an AAC (Augmentative and Alternative Communication) board.
                
                Identify 8-12 of the MOST IMPORTANT and CLEARLY VISIBLE objects that a person would want to communicate about.
                Focus on:
                - Objects that are large and clearly visible
                - Interactive items (things you can touch, use, or eat)
                - Common household items, furniture, appliances, food, toys, people
                - Objects that take up significant space in the image
                
                AVOID:
                - Small or partially visible objects
                - Background details
                - Decorative items
                - Ambiguous objects
                
                For EACH object, provide:
                1. A simple, single-word label (e.g., "Refrigerator" not "White Refrigerator")
                2. Accurate bounding box coordinates
                
                CRITICAL FORMAT - One object per line:
                ObjectName | ymin, xmin, ymax, xmax
                
                Where coordinates are percentages (0-100):
                - ymin: distance from TOP edge to object's TOP
                - xmin: distance from LEFT edge to object's LEFT
                - ymax: distance from TOP edge to object's BOTTOM
                - xmax: distance from LEFT edge to object's RIGHT
                
                Example:
                Refrigerator | 10, 5, 85, 45
                Table | 60, 30, 95, 90
                
                Respond ONLY with the object list. No explanations.
            """.trimIndent()
            
            // Use higher resolution for better recognition
            val scaledBitmap = scaleBitmap(image, maxDimension = 2048)
            
            try {
                val response = visionModel.generateContent(
                    content {
                        image(scaledBitmap)
                        text(prompt)
                    }
                )
                
                val text = response.text?.trim() ?: return@withContext emptyList()
                android.util.Log.d("GeminiDebug", "Multi-Object Response:\n$text")

                val results = text.lines()
                    .filter { it.isNotBlank() && it.contains("|") }
                    .mapNotNull { line ->
                        try {
                            val parts = line.split("|")
                            if (parts.size != 2) return@mapNotNull null
                            
                            val name = parts[0].trim()
                            if (name.isEmpty()) return@mapNotNull null
                            
                            val coordsString = parts[1].trim()
                            val cleanCoords = coordsString.replace("[", "").replace("]", "").replace(" ", "")
                            val coords = cleanCoords.split(",")
                                .mapNotNull { it.trim().toFloatOrNull() }
                                .map { it / 100f }
                                .toFloatArray()
                            
                            if (coords.size == 4 && 
                                coords[0] >= 0f && coords[0] <= 1f &&
                                coords[1] >= 0f && coords[1] <= 1f &&
                                coords[2] >= 0f && coords[2] <= 1f &&
                                coords[3] >= 0f && coords[3] <= 1f &&
                                coords[2] > coords[0] && coords[3] > coords[1]) {
                                name to coords
                            } else {
                                android.util.Log.w("GeminiDebug", "Invalid coords for $name: ${coords.joinToString()}")
                                null
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("GeminiDebug", "Error parsing line: $line", e)
                            null
                        }
                    }
                
                android.util.Log.d("GeminiDebug", "Successfully parsed ${results.size} objects")
                results
            } catch (e: Exception) {
                android.util.Log.e("GeminiDebug", "Error in identifyMultipleItems", e)
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun predictNextWords(
        context: List<String>,
        count: Int = 5,
        languageCode: String = "en",
        topic: String? = null
    ): List<String> {
        return withContext(Dispatchers.IO) {
            val languageName = if (languageCode == "iw" || languageCode == "he") "Hebrew" else "English"
            val contextText = context.takeLast(5).joinToString(" ")
            val topicClause = if (!topic.isNullOrBlank()) {
                "The current conversation topic/board is: '$topic'. Prioritize words related to this topic."
            } else ""
            
            val prompt = """
                You are helping with word prediction for an AAC (Augmentative and Alternative Communication) app.
                
                The user has typed: "$contextText"
                $topicClause
                
                Predict the next $count words they might want to say in $languageName.
                Focus on:
                - Common conversational words
                - Contextually relevant words (especially related to '$topic' if provided)
                - Simple, everyday vocabulary
                
                Return ONLY the words, separated by commas.
                Example: want, need, like, have, go
            """.trimIndent()
            
            try {
                val response = textModel.generateContent(prompt)
                val text = response.text?.trim() ?: return@withContext emptyList()
                text.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .take(count)
            } catch (e: Exception) {
                android.util.Log.e("GeminiService", "Error in predictNextWords", e)
                e.printStackTrace()
                emptyList()
            }
        }
    }

    // predictNextButtons removed

    // Agent Actions
    sealed class AgentAction {
        data class CreateBoard(val topic: String, val negativeConstraints: String? = null) : AgentAction()
        data class AnswerQuestion(val question: String) : AgentAction()
        data class Unknown(val reason: String) : AgentAction()
    }

    suspend fun parseAgentCommand(query: String): AgentAction {
        return withContext(Dispatchers.IO) {
            val prompt = """
                You are an AI assistant for an AAC app. Analyze the user's request and classify it into one of these actions:
                
                1. CREATE_BOARD: User wants to create a new symbol board.
                   - Extract 'topic' (e.g., "nightclub", "food", "park")
                   - Extract 'negative_constraints' if any (e.g., "no alcohol", "without meat"). Return "null" if none.
                
                2. ANSWER_QUESTION: User asks a question about the app or general help.
                   - Extract the 'question'.

                3. UNKNOWN: If the request is unclear or unrelated.

                Response Format:
                TYPE | param1 | param2
                
                Examples:
                "Create a board about space" -> CREATE_BOARD | Space | null
                "Make a board for a bar but no beer symbols" -> CREATE_BOARD | Bar | No beer symbols
                "How do I use this app?" -> ANSWER_QUESTION | How do I use this app? | null
                "Hello" -> ANSWER_QUESTION | Hello | null
                
                Input: "$query"
                
                Return ONLY the formatted string.
            """.trimIndent()
            
            try {
                val response = textModel.generateContent(prompt)
                val text = response.text?.trim() ?: return@withContext AgentAction.Unknown("Empty response")
                
                val parts = text.split("|").map { it.trim() }
                when (parts[0].uppercase()) {
                    "CREATE_BOARD" -> {
                        val topic = parts.getOrNull(1) ?: "General"
                        val constraints = parts.getOrNull(2).takeIf { it != "null" && it != "None" }
                        AgentAction.CreateBoard(topic, constraints)
                    }
                    "ANSWER_QUESTION" -> {
                        val question = parts.getOrNull(1) ?: query
                        AgentAction.AnswerQuestion(question)
                    }
                    else -> AgentAction.Unknown(text)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                AgentAction.Unknown("Error: ${e.message}")
            }
        }
    }

    suspend fun answerQuestion(question: String): String {
        return withContext(Dispatchers.IO) {
            // Simplified Knowledge Base
            val knowledgeBase = """
                You are 'Fluently', a helpful AI assistant built into an AAC (Augmentative and Alternative Communication) app.
                
                App Features:
                - Create Boards: Users can create custom boards by typing a topic (e.g., "Create board for school").
                - Magic Boards: The app uses AI to generate relevant vocabulary.
                - Photo Boards: Users can take a photo to generate a board based on the scene (using the camera icon).
                - Text-to-Speech: Tapping icons speaks the word.
                - Validation: You can validate the query against app capabilities.
                
                Style:
                - Be concise, helpful, and friendly.
                - Keep answers short (under 3 sentences) as users may have reading difficulties.
            """.trimIndent()
            
            val prompt = """
                $knowledgeBase
                
                User Question: "$question"
                
                Answer:
            """.trimIndent()
            
            try {
                val response = textModel.generateContent(prompt)
                response.text?.trim() ?: "I couldn't generate an answer."
            } catch (e: Exception) {
                 "Sorry, I'm having trouble connecting right now."
            }
        }
    }

    suspend fun generateBoard(topic: String, languageCode: String = "en", count: Int = 16, negativeConstraints: String? = null): List<String> {
        return withContext(Dispatchers.IO) {
            val languageName = if (languageCode == "iw" || languageCode == "he") "Hebrew" else "English"
            val constraintsClause = if (!negativeConstraints.isNullOrBlank()) {
                "CRITICAL INSTRUCTION: Do NOT include any words related to: $negativeConstraints."
            } else ""
            
            val prompt = """
                Create a list of $count vocabulary words related to the topic '$topic' for an AAC communication board.
                Includes nouns, verbs, and adjectives.
                $constraintsClause
                Output the words in $languageName.
                Return ONLY the words, separated by commas.
                Example: Dog, Cat, Pet, Walk, Furry, Bark
            """.trimIndent()
            
            try {
                val response = textModel.generateContent(prompt)
                val text = response.text?.trim() ?: return@withContext emptyList()
                text.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(count)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun generateMoreItems(topic: String, existingItems: List<String>, count: Int, languageCode: String = "en"): List<String> {
        return withContext(Dispatchers.IO) {
            val languageName = if (languageCode == "iw" || languageCode == "he") "Hebrew" else "English"
            val exclusions = existingItems.joinToString(", ")
            val prompt = """
                Create a list of $count NEW vocabulary words related to the topic '$topic' for an AAC communication board.
                Includes nouns, verbs, and adjectives.
                The words must be different from: $exclusions.
                Output the words in $languageName.
                Return ONLY the words, separated by commas.
            """.trimIndent()
            
            try {
                val response = textModel.generateContent(prompt)
                val text = response.text?.trim() ?: return@withContext emptyList()
                text.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(count)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun simplifyLocationName(rawName: String, languageCode: String = "en"): String {
        return withContext(Dispatchers.IO) {
            val languageName = if (languageCode == "iw" || languageCode == "he") "Hebrew" else "English"
            val prompt = """
                Convert this specific location name to a generic category suitable for an AAC board recommendation.
                Output the category in $languageName.
                Examples (if English): 
                - 'The Grand Urban Mall' -> 'Mall'
                - 'St. Mary's School' -> 'School'
                
                Location: "$rawName"
                
                Return ONLY the single category word.
            """.trimIndent()

            try {
                val response = textModel.generateContent(prompt)
                response.text?.trim() ?: rawName
            } catch (e: Exception) {
                e.printStackTrace()
                rawName // Fallback to original name
            }
        }
    }

    suspend fun suggestBoardName(images: List<Bitmap>, languageCode: String = "en"): String {
        return withContext(Dispatchers.IO) {
            val languageName = if (languageCode == "iw" || languageCode == "he") "Hebrew" else "English"
            val prompt = """
                Analyze these images and provide a single, short, descriptive title for a communication board that handles these items.
                The title should be 1-3 words maximum.
                Output the title in $languageName.
                Examples: "Breakfast", "Toys", "Kitchen Items", "My Room".
                
                Return ONLY the title. No punctuation.
            """.trimIndent()
            
            // Limit to first 3 images to save bandwidth/token usage if many are selected, 
            // but for "Quick Board" typically user selects a few.
            // Let's take up to 4 images.
            val imagesToProcess = images.take(4).map { scaleBitmap(it) }

            try {
                val response = visionModel.generateContent(
                    content {
                        imagesToProcess.forEach { image(it) }
                        text(prompt)
                    }
                )
                response.text?.trim() ?: ""
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
    }

    suspend fun correctGrammar(sentence: String, languageCode: String = "en", cacheService: com.example.myaac.data.cache.PhraseCacheService? = null): String {
        return withContext(Dispatchers.IO) {
            // Try cache first if available
            if (cacheService != null) {
                val cached = cacheService.getCachedGrammar(sentence, languageCode)
                if (cached != null) {
                    android.util.Log.d("GeminiService", "✓ Grammar cache HIT for: ${sentence.take(50)}")
                    return@withContext cached
                }
                android.util.Log.d("GeminiService", "✗ Grammar cache MISS, calling Gemini API...")
            }
            
            val languageName = if (languageCode == "iw" || languageCode == "he") "Hebrew" else "English"
            val prompt = """
                Fix the grammar of this sentence in $languageName.
                Make it sound natural for spoken conversation.
                Return ONLY the corrected sentence. No quotes, no explanations.

                Input: "$sentence"
            """.trimIndent()

            try {
                val response = textModel.generateContent(prompt)
                val corrected = response.text?.trim() ?: sentence
                
                // Cache the result if available and different from input
                if (cacheService != null && corrected != sentence) {
                    cacheService.cacheGrammar(sentence, corrected, languageCode)
                    android.util.Log.d("GeminiService", "✓ Cached grammar correction")
                }
                
                corrected
            } catch (e: Exception) {
                e.printStackTrace()
                sentence
            }
        }
    }

    suspend fun testLatency(prompt: String = "ping"): Long {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                textModel.generateContent(prompt)
                System.currentTimeMillis() - start
            } catch (e: Exception) {
                e.printStackTrace()
                -1L
            }
        }
    }
}
