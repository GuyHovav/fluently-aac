package com.example.myaac.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for phrase cache operations
 */
@Dao
interface PhraseCacheDao {
    
    /**
     * Get a cached result by key
     */
    @Query("SELECT * FROM phrase_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun getCached(key: String): PhraseCacheEntity?
    
    /**
     * Insert or update a cache entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: PhraseCacheEntity)
    
    /**
     * Increment hit count for a cache entry
     */
    @Query("UPDATE phrase_cache SET hitCount = hitCount + 1 WHERE cacheKey = :key")
    suspend fun incrementHitCount(key: String)
    
    /**
     * Delete old cache entries (older than the given timestamp)
     */
    @Query("DELETE FROM phrase_cache WHERE timestamp < :timestampMs")
    suspend fun deleteOlderThan(timestampMs: Long)
    
    /**
     * Clear all cache of a specific type
     */
    @Query("DELETE FROM phrase_cache WHERE cacheType = :type")
    suspend fun clearByType(type: String)
    
    /**
     * Clear all cache
     */
    @Query("DELETE FROM phrase_cache")
    suspend fun clearAll()
    
    /**
     * Get cache statistics
     */
    @Query("SELECT COUNT(*) FROM phrase_cache WHERE cacheType = :type")
    suspend fun getCacheCount(type: String): Int
    
    /**
     * Get total cache entries
     */
    @Query("SELECT COUNT(*) FROM phrase_cache")
    suspend fun getTotalCacheCount(): Int
    
    /**
     * Get cache with most hits (for debugging/analysis)
     */
    @Query("SELECT * FROM phrase_cache ORDER BY hitCount DESC LIMIT :limit")
    suspend fun getMostUsedCache(limit: Int = 10): List<PhraseCacheEntity>
    
    /**
     * Delete least used cache entries to maintain cache size
     * Keeps the most recent and most used entries
     */
    @Query("""
        DELETE FROM phrase_cache 
        WHERE cacheKey IN (
            SELECT cacheKey FROM phrase_cache 
            ORDER BY hitCount ASC, timestamp ASC 
            LIMIT :count
        )
    """)
    suspend fun deleteLeastUsed(count: Int)
}
