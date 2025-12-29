package com.example.myaac.data.nlp

import com.example.myaac.data.local.WordFrequencyDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local prediction engine using n-gram frequency analysis
 * Provides fast, offline predictions based on learned usage patterns
 */
class LocalPredictionEngine(
    private val wordFrequencyDao: WordFrequencyDao
) : PredictionEngine {
    
    // Default common words to use when there's no learned data
    private val defaultCommonWords = listOf(
        "I", "you", "want", "need", "like", "have", "go", "see", "feel", "think",
        "yes", "no", "please", "thank", "help", "more", "stop", "good", "bad",
        "happy", "sad", "eat", "drink", "play", "sleep", "home", "school", "work"
    )
    
    override suspend fun predict(context: List<String>, count: Int): List<String> = withContext(Dispatchers.IO) {
        val predictions = mutableSetOf<String>() // Use set to avoid duplicates
        
        // Normalize context (lowercase, trim)
        val normalizedContext = context.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        
        if (normalizedContext.isEmpty()) {
            // No context - return most frequent words or defaults
            val topWords = wordFrequencyDao.getTopWords(count)
            if (topWords.isNotEmpty()) {
                return@withContext topWords.map { it.word }
            } else {
                // No learned data yet, use defaults
                return@withContext defaultCommonWords.take(count)
            }
        }
        
        // Try trigram predictions (most specific)
        if (normalizedContext.size >= 2) {
            val word1 = normalizedContext[normalizedContext.size - 2]
            val word2 = normalizedContext[normalizedContext.size - 1]
            
            val trigrams = wordFrequencyDao.getTrigramsStartingWith(word1, word2, count)
            predictions.addAll(trigrams.map { it.word3 })
        }
        
        // Try bigram predictions (medium specificity)
        if (predictions.size < count && normalizedContext.isNotEmpty()) {
            val lastWord = normalizedContext.last()
            val bigrams = wordFrequencyDao.getBigramsStartingWith(lastWord, count)
            predictions.addAll(bigrams.map { it.word2 })
        }
        
        // Fill remaining with most frequent words or defaults
        if (predictions.size < count) {
            val topWords = wordFrequencyDao.getTopWords(count * 2) // Get more to filter
            if (topWords.isNotEmpty()) {
                val filtered = topWords
                    .map { it.word }
                    .filter { it !in predictions && it !in normalizedContext } // Exclude already predicted and context words
                    .take(count - predictions.size)
                predictions.addAll(filtered)
            } else {
                // No learned data, use defaults
                val defaults = defaultCommonWords
                    .filter { it.lowercase() !in predictions && it.lowercase() !in normalizedContext }
                    .take(count - predictions.size)
                predictions.addAll(defaults.map { it.lowercase() })
            }
        }
        
        // Return as list, preserving order (trigrams first, then bigrams, then frequent/defaults)
        predictions.take(count).toList()
    }
    
    override suspend fun recordUsage(word: String) = withContext(Dispatchers.IO) {
        wordFrequencyDao.recordWordUsage(word)
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
    }
}
