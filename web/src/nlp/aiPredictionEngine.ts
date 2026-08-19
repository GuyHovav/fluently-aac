// Port of data/nlp/AiPredictionEngine.kt — near-mechanical translation.
//
// AI-powered prediction engine using Gemini. Provides contextual, intelligent predictions with
// persistent caching.

import type { PredictionEngine } from './predictionEngine';
import type { GeminiService } from '../services/geminiService';
import { CacheType, type PhraseCacheService } from '../services/phraseCacheService';

export class AiPredictionEngine implements PredictionEngine {
  private geminiService: GeminiService;
  private cacheService: PhraseCacheService;
  private languageCode: string;

  constructor(geminiService: GeminiService, cacheService: PhraseCacheService, languageCode: string = 'en') {
    this.geminiService = geminiService;
    this.cacheService = cacheService;
    this.languageCode = languageCode;
  }

  async predict(context: string[], count: number = 5, topic?: string | null): Promise<string[]> {
    if (context.length === 0) {
      return [];
    }

    // Try to get from persistent cache first
    const cached = await this.cacheService.getCachedPredictions(context, this.languageCode);
    if (cached != null && cached.length > 0) {
      return cached.slice(0, count);
    }

    try {
      // Call Gemini for predictions
      const predictions = await this.geminiService.predictNextWords(
        context.slice(-5), // Use last 5 words for context
        count,
        this.languageCode,
        topic,
      );

      // Cache the result if not empty
      if (predictions.length > 0) {
        await this.cacheService.cachePredictions(context, predictions, this.languageCode);
      }

      return predictions;
    } catch (e) {
      console.error('AiPredictionEngine: Error getting AI predictions', e);
      return [];
    }
  }

  async recordUsage(_word: string): Promise<void> {
    // AI engine doesn't need to record usage locally
    // (Gemini learns from broader patterns)
  }

  async recordSentence(_words: string[]): Promise<void> {
    // AI engine doesn't need to record sentences locally
  }

  /** Clear the prediction cache. */
  async clearCache(): Promise<void> {
    await this.cacheService.clearByType(CacheType.PREDICTION);
  }
}
