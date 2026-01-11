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
    
    // Default core vocabulary words (120+ words) to use when there's no learned data
    // Ordered loosely by frequency and category
    private val defaultCommonWords = listOf(
        // Pronouns
        "I", "you", "he", "she", "we", "they", "it", "this", "that", "me", "my", "your",
        
        // Verbs
        "want", "need", "like", "love", "have", "has", "get", "got", "go", "come", 
        "can", "do", "did", "see", "look", "watch", "eat", "drink", "play", "sleep", 
        "help", "stop", "start", "make", "put", "give", "take", "feel", "think", 
        "know", "say", "tell", "ask", "turn", "open", "close", "move", "read", "write", "use",
        
        // Social / Courtesy
        "yes", "no", "please", "thank", "sorry", "excuse", "hello", "goodbye", "welcome", "ok",
        
        // Question Words
        "what", "where", "when", "who", "why", "how", "which",
        
        // Adjectives
        "good", "bad", "happy", "sad", "big", "small", "hot", "cold", "fast", "slow", 
        "more", "less", "same", "different", "nice", "fun", "hard", "easy", "new", "old",
        
        // Prepositions
        "in", "on", "at", "to", "from", "with", "for", "up", "down", "out", "off", 
        "over", "under", "beside", "between",
        
        // Time
        "now", "today", "tomorrow", "yesterday", "later", "soon", "morning", "night", "always", "never",
        
        // Places
        "home", "school", "work", "hospital", "store", "park", "here", "there"
    )
    
    override suspend fun predict(context: List<String>, count: Int, topic: String?): List<String> = withContext(Dispatchers.IO) {
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
