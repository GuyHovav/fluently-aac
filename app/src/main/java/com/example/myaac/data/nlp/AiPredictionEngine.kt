package com.example.myaac.data.nlp

import com.example.myaac.data.remote.GeminiService
import com.example.myaac.data.cache.PhraseCacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI-powered prediction engine using Gemini
 * Provides contextual, intelligent predictions with persistent caching
 */
class AiPredictionEngine(
    private val geminiService: GeminiService,
    private val cacheService: PhraseCacheService,
    private val languageCode: String = "en"
) : PredictionEngine {
    
    override suspend fun predict(context: List<String>, count: Int, topic: String?): List<String> = withContext(Dispatchers.IO) {
        if (context.isEmpty()) {
            return@withContext emptyList()
        }
        
        // Try to get from persistent cache first
        val cached = cacheService.getCachedPredictions(context, languageCode)
        if (cached != null && cached.isNotEmpty()) {
            android.util.Log.d("AiPredictionEngine", "✓ Cache HIT for: ${context.takeLast(3).joinToString(" ")}")
            return@withContext cached.take(count)
        }
        
        android.util.Log.d("AiPredictionEngine", "✗ Cache MISS, calling Gemini API...")
        
        try {
            // Call Gemini for predictions
            val predictions = geminiService.predictNextWords(
                context = context.takeLast(5), // Use last 5 words for context
                count = count,
                languageCode = languageCode,
                topic = topic
            )
            
            // Cache the result if not empty
            if (predictions.isNotEmpty()) {
                cacheService.cachePredictions(context, predictions, languageCode)
                android.util.Log.d("AiPredictionEngine", "✓ Cached ${predictions.size} predictions")
            }
            
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
    suspend fun clearCache() {
        cacheService.clearByType(com.example.myaac.data.local.CacheType.PREDICTION)
    }
}
