package com.example.myaac.data.nlp

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

/**
 * Hybrid prediction engine combining local and AI predictions
 * Provides fast local predictions with AI enhancement
 */
class HybridPredictionEngine(
    private val localEngine: LocalPredictionEngine,
    private val aiEngine: AiPredictionEngine?,
    private val aiEnabled: Boolean = true
) : PredictionEngine {
    
    override suspend fun predict(context: List<String>, count: Int, topic: String?): List<String> = coroutineScope {
        val predictions = mutableSetOf<String>()
        
        // Always get local predictions (fast, offline)
        val localPredictions = localEngine.predict(context, count, topic)
        predictions.addAll(localPredictions)
        
        // Get AI predictions if enabled and available
        if (aiEnabled && aiEngine != null && context.isNotEmpty()) {
            try {
                // Run AI predictions with timeout (don't block too long)
                val aiPredictionsDeferred = async {
                    withTimeout(2000L) { // 2 second timeout
                        aiEngine.predict(context, count, topic)
                    }
                }
                
                val aiPredictions = try {
                    aiPredictionsDeferred.await()
                } catch (e: Exception) {
                    android.util.Log.w("HybridPredictionEngine", "AI predictions timed out or failed", e)
                    emptyList()
                }
                
                // Merge AI predictions (prioritize AI for first few slots)
                val merged = mutableListOf<String>()
                
                // Add AI predictions first (up to half the count)
                val aiCount = minOf(aiPredictions.size, count / 2)
                merged.addAll(aiPredictions.take(aiCount))
                
                // Fill remaining with local predictions
                val remaining = localPredictions.filter { it !in merged }
                merged.addAll(remaining.take(count - merged.size))
                
                return@coroutineScope merged.take(count)
            } catch (e: Exception) {
                android.util.Log.e("HybridPredictionEngine", "Error in AI predictions", e)
                // Fall back to local predictions only
            }
        }
        
        // Return local predictions (AI disabled or failed)
        predictions.take(count).toList()
    }
    
    override suspend fun recordUsage(word: String) {
        localEngine.recordUsage(word)
        aiEngine?.recordUsage(word)
    }
    
    override suspend fun recordSentence(words: List<String>) {
        localEngine.recordSentence(words)
        aiEngine?.recordSentence(words)
    }
    
    companion object {
        /**
         * Get starter words for beginning a sentence
         */
        fun getStarterWords(languageCode: String = "en"): List<String> {
            return when (languageCode) {
                "en" -> listOf(
                    "I", "You", "We", "They", "He", "She",
                    "Want", "Need", "Like", "Feel", "Have", "Go",
                    "What", "Where", "When", "Why", "How", "Who"
                )
                "iw" -> listOf(
                    "אני", "אתה", "את", "אנחנו", "הם", "הן",
                    "רוצה", "צריך", "אוהב", "מרגיש", "יש", "הולך",
                    "מה", "איפה", "מתי", "למה", "איך", "מי"
                )
                else -> getStarterWords("en")
            }
        }
        
        /**
         * Get verbs that commonly follow a pronoun
         */
        fun getVerbsForPronoun(pronoun: String, languageCode: String = "en"): List<String> {
            return when (languageCode) {
                "en" -> when (pronoun.lowercase()) {
                    "i" -> listOf("want", "need", "am", "like", "feel", "have", "think", "see", "go", "can", "will", "would")
                    "you" -> listOf("are", "want", "need", "can", "should", "have", "do", "will", "would", "like")
                    "we" -> listOf("are", "want", "need", "can", "should", "have", "will", "would", "like", "go")
                    "they" -> listOf("are", "want", "need", "can", "have", "will", "would", "like", "go", "do")
                    "he", "she" -> listOf("is", "wants", "needs", "can", "has", "will", "would", "likes", "goes", "does")
                    else -> emptyList()
                }
                "iw" -> when (pronoun) {
                    "אני" -> listOf("רוצה", "צריך", "אוהב", "מרגיש", "יש לי", "הולך", "יכול", "רואה")
                    "אתה", "את" -> listOf("רוצה", "צריך", "אוהב", "מרגיש", "יש לך", "הולך", "יכול")
                    "אנחנו" -> listOf("רוצים", "צריכים", "אוהבים", "מרגישים", "יש לנו", "הולכים", "יכולים")
                    "הם", "הן" -> listOf("רוצים", "צריכים", "אוהבים", "מרגישים", "יש להם", "הולכים", "יכולים")
                    else -> emptyList()
                }
                else -> getVerbsForPronoun(pronoun, "en")
            }
        }
        
        /**
         * Get common completions for a phrase
         * @param boardNames Optional list of board names to suggest for location-related contexts
         */
        fun getCompletionsForPhrase(
            phrase: String, 
            languageCode: String = "en",
            boardNames: List<String> = emptyList()
        ): List<String> {
            val lowerPhrase = phrase.lowercase().trim()
            return when (languageCode) {
                "en" -> when {
                    // Location/destination contexts - suggest board names
                    lowerPhrase.matches(Regex(".*(want to go|let'?s go to|going to|go to)$")) -> 
                        boardNames.ifEmpty { listOf("the park", "school", "home", "the store") }
                    
                    lowerPhrase.endsWith("i want") || lowerPhrase.endsWith("want") -> 
                        listOf("to", "water", "food", "help", "more", "go", "eat", "drink", "sleep", "play")
                    lowerPhrase.endsWith("i need") || lowerPhrase.endsWith("need") -> 
                        listOf("help", "water", "food", "to", "bathroom", "more", "rest", "medicine")
                    lowerPhrase.endsWith("i like") || lowerPhrase.endsWith("like") -> 
                        listOf("this", "that", "it", "you", "to", "music", "food")
                    lowerPhrase.endsWith("i feel") || lowerPhrase.endsWith("feel") -> 
                        listOf("good", "bad", "tired", "happy", "sad", "sick", "hungry", "thirsty")
                    lowerPhrase.startsWith("what") -> 
                        listOf("is", "do", "are", "time", "happened", "about", "can", "should")
                    lowerPhrase.startsWith("where") -> 
                        listOf("is", "are", "do", "can", "should", "am")
                    lowerPhrase.startsWith("when") -> 
                        listOf("is", "do", "are", "can", "will", "should")
                    lowerPhrase.startsWith("why") -> 
                        listOf("is", "do", "are", "can", "did", "should")
                    lowerPhrase.startsWith("how") -> 
                        listOf("are", "do", "is", "can", "did", "should")
                    else -> emptyList()
                }
                "iw" -> when {
                    // Location/destination contexts - suggest board names
                    lowerPhrase.matches(Regex(".*(הולך ל|נלך ל|רוצה ללכת ל|הולכים ל)$")) -> 
                        boardNames.ifEmpty { listOf("הפארק", "בית ספר", "הבית", "החנות") }
                    
                    lowerPhrase.endsWith("רוצה") -> 
                        listOf("ל", "מים", "אוכל", "עזרה", "עוד", "ללכת", "לאכול", "לשתות")
                    lowerPhrase.endsWith("צריך") -> 
                        listOf("עזרה", "מים", "אוכל", "ל", "שירותים", "עוד")
                    lowerPhrase.startsWith("מה") -> 
                        listOf("זה", "קורה", "השעה", "עושים", "יש")
                    lowerPhrase.startsWith("איפה") -> 
                        listOf("זה", "אתה", "הם", "נמצא")
                    else -> emptyList()
                }
                else -> getCompletionsForPhrase(phrase, "en")
            }
        }
    }
}
