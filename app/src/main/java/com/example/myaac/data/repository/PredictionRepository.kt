package com.example.myaac.data.repository

import com.example.myaac.data.local.WordFrequencyDao
import com.example.myaac.data.nlp.HybridPredictionEngine
import com.example.myaac.data.nlp.LocalPredictionEngine
import com.example.myaac.data.nlp.AiPredictionEngine
import com.example.myaac.data.remote.GeminiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing word predictions
 * Coordinates between local and AI prediction engines
 */
class PredictionRepository(
    private val wordFrequencyDao: WordFrequencyDao,
    private val geminiService: GeminiService?,
    private val settingsRepository: SettingsRepository
) {
    private val localEngine = LocalPredictionEngine(wordFrequencyDao)
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
            AiPredictionEngine(geminiService, settings.languageCode)
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
     * Get word predictions based on context
     * @param context List of previous words (most recent last)
     * @return List of predicted words
     */
    suspend fun getPredictions(context: List<String>): List<String> = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settings.value
        
        if (!settings.predictionEnabled) {
            return@withContext emptyList()
        }
        
        // Update engines if settings changed
        updateEngines()
        
        // Get predictions from hybrid engine
        hybridEngine?.predict(context, settings.predictionCount) ?: emptyList()
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
