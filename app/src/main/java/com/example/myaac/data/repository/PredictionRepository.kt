package com.example.myaac.data.repository

import com.example.myaac.data.local.WordFrequencyDao
import com.example.myaac.data.nlp.HybridPredictionEngine
import com.example.myaac.data.nlp.LocalPredictionEngine
import com.example.myaac.data.nlp.AiPredictionEngine
import com.example.myaac.data.remote.GeminiService
import com.example.myaac.data.cache.PhraseCacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing word predictions
 * Coordinates between local and AI prediction engines with persistent caching
 */
class PredictionRepository(
    private val wordFrequencyDao: WordFrequencyDao,
    private val geminiService: GeminiService?,
    private val settingsRepository: SettingsRepository,
    private val cacheService: PhraseCacheService
) {
    private val localEngine = LocalPredictionEngine(
        wordFrequencyDao = wordFrequencyDao,
        languageCode = settingsRepository.settings.value.languageCode
    )
    private var aiEngine: AiPredictionEngine? = null
    private var hybridEngine: HybridPredictionEngine? = null
    
    init {
        updateEngines()
    }
    
    /**
     * Update prediction engines based on current settings
     */
    private fun updateEngines() {
        val settings = settingsRepository.settings.value
        
        // Create AI engine if Gemini is available
        aiEngine = if (geminiService != null && settings.aiPredictionEnabled) {
            AiPredictionEngine(
                geminiService = geminiService,
                cacheService = cacheService,
                languageCode = settings.languageCode
            )
        } else {
            null
        }
        
        // Create hybrid engine
        hybridEngine = HybridPredictionEngine(
            localEngine = localEngine,
            aiEngine = aiEngine,
            aiEnabled = settings.aiPredictionEnabled
        )
    }
    
    /**
     * Get predictions for the current context
     * @param context List of words in the current sentence
     * @param topic Optional topic/board name to influence predictions
     */
    suspend fun getPredictions(context: List<String>, topic: String? = null): List<String> = withContext(Dispatchers.IO) {
        if (!settingsRepository.settings.value.predictionEnabled) {
            return@withContext emptyList()
        }
        
        // Use hybrid engine if available (which handles delegation to local/AI)
        val engine = hybridEngine ?: localEngine
        
        try {
            val count = settingsRepository.settings.value.predictionCount
            engine.predict(context, count, topic)
        } catch (e: Exception) {
            android.util.Log.e("PredictionRepo", "Error generating predictions", e)
            // Fallback to local default predictions if error occurs
            try {
                localEngine.predict(context, 5, topic)
            } catch (e2: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * Record word usage for learning
     */
    suspend fun recordWordUsage(word: String) = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settings.value
        if (!settings.learnFromUsage) return@withContext
        
        localEngine.recordUsage(word)
    }
    
    /**
     * Record sentence for context learning
     */
    suspend fun recordSentence(words: List<String>) = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settings.value
        if (!settings.learnFromUsage) return@withContext
        
        localEngine.recordSentence(words)
    }
    
    /**
     * Clear all learned vocabulary data
     */
    suspend fun clearLearnedData() = withContext(Dispatchers.IO) {
        wordFrequencyDao.clearAllLearnedData()
        aiEngine?.clearCache()
    }
}
