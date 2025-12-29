package com.example.myaac.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WordFrequencyDao {
    
    // ========== Word Frequency (Unigrams) ==========
    
    @Query("SELECT * FROM word_frequency WHERE word = :word LIMIT 1")
    suspend fun getWordFrequency(word: String): WordFrequencyEntity?
    
    @Query("SELECT * FROM word_frequency ORDER BY frequency DESC LIMIT :limit")
    suspend fun getTopWords(limit: Int): List<WordFrequencyEntity>
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWord(word: WordFrequencyEntity): Long
    
    @Query("UPDATE word_frequency SET frequency = frequency + 1, lastUsed = :timestamp WHERE word = :word")
    suspend fun incrementWordFrequency(word: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM word_frequency")
    suspend fun clearAllWords()
    
    // ========== Bigrams ==========
    
    @Query("SELECT * FROM word_bigram WHERE word1 = :word1 ORDER BY frequency DESC LIMIT :limit")
    suspend fun getBigramsStartingWith(word1: String, limit: Int = 10): List<WordBigramEntity>
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBigram(bigram: WordBigramEntity): Long
    
    @Query("UPDATE word_bigram SET frequency = frequency + 1, lastUsed = :timestamp WHERE word1 = :word1 AND word2 = :word2")
    suspend fun incrementBigramFrequency(word1: String, word2: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM word_bigram")
    suspend fun clearAllBigrams()
    
    // ========== Trigrams ==========
    
    @Query("SELECT * FROM word_trigram WHERE word1 = :word1 AND word2 = :word2 ORDER BY frequency DESC LIMIT :limit")
    suspend fun getTrigramsStartingWith(word1: String, word2: String, limit: Int = 10): List<WordTrigramEntity>
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrigram(trigram: WordTrigramEntity): Long
    
    @Query("UPDATE word_trigram SET frequency = frequency + 1, lastUsed = :timestamp WHERE word1 = :word1 AND word2 = :word2 AND word3 = :word3")
    suspend fun incrementTrigramFrequency(word1: String, word2: String, word3: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM word_trigram")
    suspend fun clearAllTrigrams()
    
    // ========== Batch Operations ==========
    
    @Transaction
    suspend fun recordWordUsage(word: String) {
        val normalized = word.lowercase().trim()
        if (normalized.isEmpty()) return
        
        val existing = getWordFrequency(normalized)
        if (existing == null) {
            insertWord(WordFrequencyEntity(word = normalized))
        } else {
            incrementWordFrequency(normalized)
        }
    }
    
    @Transaction
    suspend fun recordBigramUsage(word1: String, word2: String) {
        val w1 = word1.lowercase().trim()
        val w2 = word2.lowercase().trim()
        if (w1.isEmpty() || w2.isEmpty()) return
        
        val insertResult = insertBigram(WordBigramEntity(word1 = w1, word2 = w2))
        if (insertResult == -1L) {
            // Already exists, increment
            incrementBigramFrequency(w1, w2)
        }
    }
    
    @Transaction
    suspend fun recordTrigramUsage(word1: String, word2: String, word3: String) {
        val w1 = word1.lowercase().trim()
        val w2 = word2.lowercase().trim()
        val w3 = word3.lowercase().trim()
        if (w1.isEmpty() || w2.isEmpty() || w3.isEmpty()) return
        
        val insertResult = insertTrigram(WordTrigramEntity(word1 = w1, word2 = w2, word3 = w3))
        if (insertResult == -1L) {
            // Already exists, increment
            incrementTrigramFrequency(w1, w2, w3)
        }
    }
    
    @Transaction
    suspend fun clearAllLearnedData() {
        clearAllWords()
        clearAllBigrams()
        clearAllTrigrams()
    }
}
