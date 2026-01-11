package com.example.myaac.data.cache

import com.example.myaac.data.local.CacheType
import com.example.myaac.data.local.PhraseCacheDao
import com.example.myaac.data.local.PhraseCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Service for managing phrase cache (predictions and grammar)
 * Provides smart caching with expiry and size limits
 */
class PhraseCacheService(
    private val cacheDao: PhraseCacheDao
) {
    companion object {
        // Cache expiry times
        private const val PREDICTION_CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val GRAMMAR_CACHE_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
        
        // Maximum cache entries per type
        private const val MAX_PREDICTION_CACHE = 500
        private const val MAX_GRAMMAR_CACHE = 1000
    }
    
    /**
     * Generate a deterministic cache key from input and metadata
     */
    private fun generateCacheKey(
        input: String,
        cacheType: String,
        languageCode: String
    ): String {
        val combined = "$cacheType:$languageCode:${input.trim().lowercase()}"
        return combined.hashToMD5()
    }
    
    /**
     * Hash string to MD5 for compact keys
     */
    private fun String.hashToMD5(): String {
        val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Get cached predictions
     * Returns null if not cached or expired
     */
    suspend fun getCachedPredictions(
        context: List<String>,
        languageCode: String
    ): List<String>? = withContext(Dispatchers.IO) {
        val contextString = context.takeLast(5).joinToString(" ")
        val key = generateCacheKey(contextString, CacheType.PREDICTION, languageCode)
        
        val cached = cacheDao.getCached(key) ?: return@withContext null
        
        // Check if expired
        val age = System.currentTimeMillis() - cached.timestamp
        if (age > PREDICTION_CACHE_EXPIRY_MS) {
            return@withContext null
        }
        
        // Increment hit count
        cacheDao.incrementHitCount(key)
        
        // Parse and return predictions
        cached.result.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
    
    /**
     * Cache predictions
     */
    suspend fun cachePredictions(
        context: List<String>,
        predictions: List<String>,
        languageCode: String
    ) = withContext(Dispatchers.IO) {
        try {
            // Check cache size and clean if needed
            cleanupCacheIfNeeded(CacheType.PREDICTION, MAX_PREDICTION_CACHE)
            
            val contextString = context.takeLast(5).joinToString(" ")
            val key = generateCacheKey(contextString, CacheType.PREDICTION, languageCode)
            val resultString = predictions.joinToString(",")
            
            val entity = PhraseCacheEntity(
                cacheKey = key,
                cacheType = CacheType.PREDICTION,
                input = contextString,
                result = resultString,
                languageCode = languageCode,
                timestamp = System.currentTimeMillis(),
                hitCount = 0
            )
            
            cacheDao.insert(entity)
        } catch (e: Exception) {
            android.util.Log.e("PhraseCacheService", "Error caching predictions", e)
        }
    }
    
    /**
     * Get cached grammar correction
     * Returns null if not cached or expired
     */
    suspend fun getCachedGrammar(
        sentence: String,
        languageCode: String
    ): String? = withContext(Dispatchers.IO) {
        val key = generateCacheKey(sentence, CacheType.GRAMMAR, languageCode)
        
        val cached = cacheDao.getCached(key) ?: return@withContext null
        
        // Check if expired
        val age = System.currentTimeMillis() - cached.timestamp
        if (age > GRAMMAR_CACHE_EXPIRY_MS) {
            return@withContext null
        }
        
        // Increment hit count
        cacheDao.incrementHitCount(key)
        
        cached.result
    }
    
    /**
     * Cache grammar correction
     */
    suspend fun cacheGrammar(
        sentence: String,
        correctedSentence: String,
        languageCode: String
    ) = withContext(Dispatchers.IO) {
        try {
            // Don't cache if input and output are the same
            if (sentence.trim() == correctedSentence.trim()) {
                return@withContext
            }
            
            // Check cache size and clean if needed
            cleanupCacheIfNeeded(CacheType.GRAMMAR, MAX_GRAMMAR_CACHE)
            
            val key = generateCacheKey(sentence, CacheType.GRAMMAR, languageCode)
            
            val entity = PhraseCacheEntity(
                cacheKey = key,
                cacheType = CacheType.GRAMMAR,
                input = sentence,
                result = correctedSentence,
                languageCode = languageCode,
                timestamp = System.currentTimeMillis(),
                hitCount = 0
            )
            
            cacheDao.insert(entity)
        } catch (e: Exception) {
            android.util.Log.e("PhraseCacheService", "Error caching grammar", e)
        }
    }
    
    /**
     * Clean up cache if it exceeds the maximum size
     */
    private suspend fun cleanupCacheIfNeeded(cacheType: String, maxSize: Int) {
        val count = cacheDao.getCacheCount(cacheType)
        if (count >= maxSize) {
            // Delete 20% of least used entries
            val toDelete = (maxSize * 0.2).toInt()
            cacheDao.deleteLeastUsed(toDelete)
        }
    }
    
    /**
     * Clean up expired cache entries
     */
    suspend fun cleanupExpiredEntries() = withContext(Dispatchers.IO) {
        val predictionCutoff = System.currentTimeMillis() - PREDICTION_CACHE_EXPIRY_MS
        val grammarCutoff = System.currentTimeMillis() - GRAMMAR_CACHE_EXPIRY_MS
        
        // Delete entries older than prediction cutoff
        cacheDao.deleteOlderThan(minOf(predictionCutoff, grammarCutoff))
    }
    
    /**
     * Get cache statistics for debugging
     */
    suspend fun getCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        val predictionCount = cacheDao.getCacheCount(CacheType.PREDICTION)
        val grammarCount = cacheDao.getCacheCount(CacheType.GRAMMAR)
        val totalCount = cacheDao.getTotalCacheCount()
        val mostUsed = cacheDao.getMostUsedCache(5)
        
        CacheStats(
            predictionCount = predictionCount,
            grammarCount = grammarCount,
            totalCount = totalCount,
            mostUsedEntries = mostUsed
        )
    }
    
    /**
     * Clear all cache
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        cacheDao.clearAll()
    }
    
    /**
     * Clear cache by type
     */
    suspend fun clearByType(cacheType: String) = withContext(Dispatchers.IO) {
        cacheDao.clearByType(cacheType)
    }
}

/**
 * Cache statistics data class
 */
data class CacheStats(
    val predictionCount: Int,
    val grammarCount: Int,
    val totalCount: Int,
    val mostUsedEntries: List<PhraseCacheEntity>
)
