package com.example.myaac.data.nlp

/**
 * Interface for word prediction engines
 */
interface PredictionEngine {
    /**
     * Predict next words based on context
     * @param context List of previous words (most recent last)
     * @param count Number of predictions to return
     * @return List of predicted words, ordered by relevance
     */
    suspend fun predict(context: List<String>, count: Int = 5): List<String>
    
    /**
     * Record word usage for learning
     * @param word The word that was used
     */
    suspend fun recordUsage(word: String)
    
    /**
     * Record sentence for context learning
     * @param words List of words in the sentence
     */
    suspend fun recordSentence(words: List<String>)
}
