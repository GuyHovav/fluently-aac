package com.example.myaac.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache entity for storing AI predictions and grammar corrections
 * This dramatically improves performance by avoiding redundant API calls
 */
@Entity(tableName = "phrase_cache")
data class PhraseCacheEntity(
    @PrimaryKey
    val cacheKey: String,
    
    /**
     * Type of cached data: "prediction" or "grammar"
     */
    val cacheType: String,
    
    /**
     * Input context or sentence that was processed
     */
    val input: String,
    
    /**
     * Cached result (comma-separated for predictions, corrected sentence for grammar)
     */
    val result: String,
    
    /**
     * Language code when the cache was created
     */
    val languageCode: String,
    
    /**
     * Timestamp when this cache entry was created
     */
    val timestamp: Long,
    
    /**
     * Number of times this cache entry has been used
     */
    val hitCount: Int = 0
)

/**
 * Cache types
 */
object CacheType {
    const val PREDICTION = "prediction"
    const val GRAMMAR = "grammar"
}
