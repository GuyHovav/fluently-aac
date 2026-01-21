package com.example.myaac.data.nlp

import com.example.myaac.data.local.WordFrequencyDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Local prediction engine using n-gram frequency analysis
 * Optimized for speed with in-memory LRU cache and AAC-specific vocabulary
 */
class LocalPredictionEngine(
    private val wordFrequencyDao: WordFrequencyDao,
    private val languageCode: String = "en"
) : PredictionEngine {
    
    // LRU Cache for predictions (limited to 100 entries ~1MB RAM)
    private val predictionCache = object : LinkedHashMap<String, List<String>>(
        100, // Initial capacity
        0.75f, // Load factor
        true // Access order (for LRU)
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean {
            return size > 100 // Keep max 100 cached predictions
        }
    }
    private val cacheMutex = Mutex()
    
    override suspend fun predict(context: List<String>, count: Int, topic: String?): List<String> = withContext(Dispatchers.IO) {
        val startTime = System.nanoTime()
        
        // Normalize context (lowercase, trim)
        val normalizedContext = context.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        
        // Generate cache key
        val cacheKey = normalizedContext.joinToString("|") + ":$count"
        
        // Check cache first (ultra-fast)
        cacheMutex.withLock {
            predictionCache[cacheKey]?.let { cached ->
                val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
                android.util.Log.d("LocalPrediction", "✓ Cache HIT in ${elapsedMs}ms")
                return@withContext cached
            }
        }
        
        // Cache miss - compute predictions
        val predictions = computePredictions(normalizedContext, count, topic)
        
        // Store in cache
        cacheMutex.withLock {
            predictionCache[cacheKey] = predictions
        }
        
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
        android.util.Log.d("LocalPrediction", "✗ Cache MISS, computed in ${elapsedMs}ms")
        
        predictions
    }
    
    private suspend fun computePredictions(
        normalizedContext: List<String>,
        count: Int,
        topic: String?
    ): List<String> {
        val predictions = mutableSetOf<String>()
        
        // Empty context - use AAC starter words
        if (normalizedContext.isEmpty()) {
            val aacStarters = AacVocabulary.getStarterWords(languageCode)
            predictions.addAll(aacStarters.take(count))
            
            // If still need more, get from learned data or core vocabulary
            if (predictions.size < count) {
                val topWords = wordFrequencyDao.getTopWords(count)
                if (topWords.isNotEmpty()) {
                    predictions.addAll(topWords.map { it.word }.take(count - predictions.size))
                } else {
                    // No learned data, use AAC core vocabulary
                    val coreVocab = AacVocabulary.getCoreVocabulary(languageCode)
                    predictions.addAll(coreVocab.take(count - predictions.size))
                }
            }
            
            return predictions.take(count).toList()
        }
        
        // Get context-specific predictions from AAC vocabulary (instant)
        val lastWord = normalizedContext.last()
        val contextPredictions = AacVocabulary.getContextPredictions(lastWord, languageCode)
        predictions.addAll(contextPredictions.take(count / 2)) // Fill up to half with context suggestions
        
        // Try trigram predictions (most specific)
        if (normalizedContext.size >= 2 && predictions.size < count) {
            val word1 = normalizedContext[normalizedContext.size - 2]
            val word2 = normalizedContext[normalizedContext.size - 1]
            
            val trigrams = wordFrequencyDao.getTrigramsStartingWith(word1, word2, count)
            predictions.addAll(trigrams.map { it.word3 })
        }
        
        // Try bigram predictions (medium specificity)
        if (predictions.size < count) {
            val bigrams = wordFrequencyDao.getBigramsStartingWith(lastWord, count)
            predictions.addAll(bigrams.map { it.word2 })
        }
        
        // Fill remaining with learned frequent words
        if (predictions.size < count) {
            val topWords = wordFrequencyDao.getTopWords(count * 2)
            if (topWords.isNotEmpty()) {
                val filtered = topWords
                    .map { it.word }
                    .filter { it !in predictions && it !in normalizedContext }
                    .take(count - predictions.size)
                predictions.addAll(filtered)
            }
        }
        
        // Final fallback: AAC core vocabulary
        if (predictions.size < count) {
            val coreVocab = AacVocabulary.getCoreVocabulary(languageCode)
            val remaining = coreVocab
                .filter { it.lowercase() !in predictions && it.lowercase() !in normalizedContext }
                .take(count - predictions.size)
            predictions.addAll(remaining.map { it.lowercase() })
        }
        
        return predictions.take(count).toList()
    }
    
    override suspend fun recordUsage(word: String) = withContext(Dispatchers.IO) {
        wordFrequencyDao.recordWordUsage(word)
        // Invalidate cache when new data is recorded
        cacheMutex.withLock {
            predictionCache.clear()
        }
    }
    
    override suspend fun recordSentence(words: List<String>) = withContext(Dispatchers.IO) {
        if (words.isEmpty()) return@withContext
        
        // Record individual word frequencies
        words.forEach { word ->
            wordFrequencyDao.recordWordUsage(word)
        }
        
        // Record bigrams
        for (i in 0 until words.size - 1) {
            wordFrequencyDao.recordBigramUsage(words[i], words[i + 1])
        }
        
        // Record trigrams
        for (i in 0 until words.size - 2) {
            wordFrequencyDao.recordTrigramUsage(words[i], words[i + 1], words[i + 2])
        }
        
        // Invalidate cache since we have new learning data
        cacheMutex.withLock {
            predictionCache.clear()
        }
    }
    
    /**
     * Clear the in-memory prediction cache
     * Useful when settings change or for testing
     */
    suspend fun clearCache() {
        cacheMutex.withLock {
            predictionCache.clear()
        }
        android.util.Log.d("LocalPrediction", "Cache cleared")
    }
}
