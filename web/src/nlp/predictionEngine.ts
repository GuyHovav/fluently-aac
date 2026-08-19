// Port of data/nlp/PredictionEngine.kt — the shared contract implemented by
// LocalPredictionEngine, AiPredictionEngine, and composed by HybridPredictionEngine.

export interface PredictionEngine {
  /**
   * Predict next words based on context.
   * @param context List of previous words (most recent last)
   * @param count Number of predictions to return
   * @param topic Optional topic/board name to influence predictions (e.g., "Food", "School")
   * @returns List of predicted words, ordered by relevance
   */
  predict(context: string[], count?: number, topic?: string | null): Promise<string[]>;

  /**
   * Record word usage for learning.
   * @param word The word that was used
   */
  recordUsage(word: string): Promise<void>;

  /**
   * Record sentence for context learning.
   * @param words List of words in the sentence
   */
  recordSentence(words: string[]): Promise<void>;
}
