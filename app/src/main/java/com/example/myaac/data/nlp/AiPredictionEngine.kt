package com.example.myaac.data.nlp

import com.example.myaac.data.remote.GeminiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.LruCache

/**
 * AI-powered prediction engine using Gemini
 * Provides contextual, intelligent predictions
 */
class AiPredictionEngine(
    private val geminiService: GeminiService,
    private val languageCode: String = "en"
) : PredictionEngine {
    
    // Cache for AI predictions (key = context hash, value = predictions)
    private val predictionCache = LruCache<String, Pair<List<String>, Long>>(100)
    private val cacheExpiryMs = 5 * 60 * 1000L // 5 minutes
    
    override suspend fun predict(context: List<String>, count: Int): List<String> = withContext(Dispatchers.IO) {
        if (context.isEmpty()) {
            return@withContext emptyList()
        }
        
        // Check cache
        val cacheKey = context.takeLast(3).joinToString(" ").lowercase()
        val cached = predictionCache.get(cacheKey)
        if (cached != null && System.currentTimeMillis() - cached.second < cacheExpiryMs) {
            return@withContext cached.first.take(count)
        }
        
        try {
            // Call Gemini for predictions
            val predictions = geminiService.predictNextWords(
                context = context.takeLast(5), // Use last 5 words for context
                count = count,
                languageCode = languageCode
            )
            
            // Cache the result
            predictionCache.put(cacheKey, predictions to System.currentTimeMillis())
            
            predictions
        } catch (e: Exception) {
            android.util.Log.e("AiPredictionEngine", "Error getting AI predictions", e)
            emptyList()
        }
    }
    
    override suspend fun recordUsage(word: String) {
        // AI engine doesn't need to record usage locally
        // (Gemini learns from broader patterns)
    }
    
    override suspend fun recordSentence(words: List<String>) {
        // AI engine doesn't need to record sentences locally
    }
    
    /**
     * Clear the prediction cache
     */
    fun clearCache() {
        predictionCache.evictAll()
    }
}
