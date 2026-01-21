package com.example.myaac.data.nlp

import com.example.myaac.data.local.WordFrequencyDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pre-loads common predictions into memory on app startup
 * Eliminates cold-start latency for the most frequent predictions
 */
class PredictionPreloader(
    private val wordFrequencyDao: WordFrequencyDao,
    private val localEngine: LocalPredictionEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Pre-load top predictions in background
     * This warms up the cache for instant first predictions
     */
    fun preload() {
        scope.launch {
            try {
                android.util.Log.d("PredictionPreloader", "Starting preload...")
                val startTime = System.currentTimeMillis()
                
                // Warm up the cache with common starter contexts
                val commonStarters = listOf(
                    emptyList(), // Empty context (sentence starters)
                    listOf("I"),
                    listOf("want"),
                    listOf("I", "want"),
                    listOf("need"),
                    listOf("I", "need"),
                    listOf("help"),
                    listOf("go"),
                    listOf("like"),
                    listOf("I", "feel")
                )
                
                // Trigger predictions for each common context to populate cache
                commonStarters.forEach { context ->
                    localEngine.predict(context, 5, null)
                }
                
                val elapsed = System.currentTimeMillis() - startTime
                android.util.Log.d("PredictionPreloader", "✓ Preload completed in ${elapsed}ms")
                
                // Optionally seed the database if it's empty
                seedDatabaseIfEmpty()
                
            } catch (e: Exception) {
                android.util.Log.e("PredictionPreloader", "Error during preload", e)
            }
        }
    }
    
    /**
     * Seed the database with AAC-specific vocabulary if empty
     * This ensures new users get good predictions immediately
     */
    private suspend fun seedDatabaseIfEmpty() = withContext(Dispatchers.IO) {
        try {
            // Check if database has any data
            val topWords = wordFrequencyDao.getTopWords(1)
            
            if (topWords.isNotEmpty()) {
                // Database already has data, skip seeding
                return@withContext
            }
            
            android.util.Log.d("PredictionPreloader", "Database empty, seeding with AAC vocabulary...")
                
            
            // Get AAC core vocabulary
            val coreWords = AacVocabulary.getCoreVocabulary("en")
            
            // Add top 50 most important words with descending frequency
            coreWords.take(50).forEachIndexed { index, word ->
                // Insert with decreasing frequency to maintain priority
                val frequency = 50 - index
                repeat(frequency) {
                    wordFrequencyDao.recordWordUsage(word)
                }
            }
            
            // Seed common bigrams
            val commonBigrams = listOf(
                "I" to "want",
                "I" to "need",
                "I" to "like",
                "I" to "feel",
                "want" to "to",
                "need" to "to",
                "need" to "help",
                "go" to "to",
                "help" to "me",
                "thank" to "you"
            )
            
            commonBigrams.forEach { (w1, w2) ->
                repeat(10) {
                    wordFrequencyDao.recordBigramUsage(w1, w2)
                }
            }
            
            android.util.Log.d("PredictionPreloader", "✓ Database seeded with ${coreWords.size} words")
        } catch (e: Exception) {
            android.util.Log.e("PredictionPreloader", "Error seeding database", e)
        }
    }
}
