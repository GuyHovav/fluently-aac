package com.example.myaac.data.nlp

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
        // ALWAYS return local predictions immediately (fast path)
        val localPredictions = localEngine.predict(context, count, topic)
        
        // If AI is disabled or unavailable, return local predictions immediately
        if (!aiEnabled || aiEngine == null || context.isEmpty()) {
            return@coroutineScope localPredictions
        }
        
        // OPTIONAL: Launch AI predictions in background (fire-and-forget)
        // This doesn't block - AI results could be used for cache warming
        launch {
            try {
                withTimeout(3000L) { // 3 second timeout, but doesn't block main flow
                    val aiPredictions = aiEngine.predict(context, count, topic)
                    // AI predictions could be logged or used to warm cache
                    android.util.Log.d("HybridPrediction", "Background AI returned: $aiPredictions")
                }
            } catch (e: Exception) {
                android.util.Log.w("HybridPrediction", "Background AI predictions failed (non-blocking)", e)
            }
        }
        
        // Return local predictions immediately - never wait for AI
        localPredictions
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
