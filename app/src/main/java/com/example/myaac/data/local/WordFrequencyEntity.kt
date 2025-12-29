package com.example.myaac.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Stores individual word usage frequency for prediction ranking
 */
@Entity(
    tableName = "word_frequency",
    indices = [Index(value = ["word"], unique = true)]
)
data class WordFrequencyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word: String,
    val frequency: Int = 1,
    val lastUsed: Long = System.currentTimeMillis()
)

/**
 * Stores two-word sequences (bigrams) for context-aware predictions
 * Example: "I" -> "want", "want" -> "to"
 */
@Entity(
    tableName = "word_bigram",
    indices = [Index(value = ["word1", "word2"], unique = true)]
)
data class WordBigramEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word1: String,
    val word2: String,
    val frequency: Int = 1,
    val lastUsed: Long = System.currentTimeMillis()
)

/**
 * Stores three-word sequences (trigrams) for better context predictions
 * Example: "I want" -> "to"
 */
@Entity(
    tableName = "word_trigram",
    indices = [Index(value = ["word1", "word2", "word3"], unique = true)]
)
data class WordTrigramEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word1: String,
    val word2: String,
    val word3: String,
    val frequency: Int = 1,
    val lastUsed: Long = System.currentTimeMillis()
)
