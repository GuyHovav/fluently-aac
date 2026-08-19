// Port of data/nlp/HybridPredictionEngine.kt — near-mechanical translation.
//
// Hybrid prediction engine combining local and AI predictions. Provides fast local predictions
// with AI enhancement, bounded by a 2-second timeout so a slow/unreachable Gemini call never
// blocks the UI.

import type { PredictionEngine } from './predictionEngine';
import type { LocalPredictionEngine } from './localPredictionEngine';
import type { AiPredictionEngine } from './aiPredictionEngine';

const AI_PREDICTION_TIMEOUT_MS = 2000;

async function withTimeout<T>(promise: Promise<T>, timeoutMs: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('timeout')), timeoutMs);
    promise.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (err) => {
        clearTimeout(timer);
        reject(err);
      },
    );
  });
}

export class HybridPredictionEngine implements PredictionEngine {
  private localEngine: LocalPredictionEngine;
  private aiEngine: AiPredictionEngine | null;
  private aiEnabled: boolean;

  constructor(localEngine: LocalPredictionEngine, aiEngine: AiPredictionEngine | null, aiEnabled: boolean = true) {
    this.localEngine = localEngine;
    this.aiEngine = aiEngine;
    this.aiEnabled = aiEnabled;
  }

  async predict(context: string[], count: number = 5, topic?: string | null): Promise<string[]> {
    const predictions = new Set<string>();

    // Always get local predictions (fast, offline)
    const localPredictions = await this.localEngine.predict(context, count, topic);
    localPredictions.forEach((p) => predictions.add(p));

    // Get AI predictions if enabled and available
    if (this.aiEnabled && this.aiEngine != null && context.length > 0) {
      try {
        // Run AI predictions with timeout (don't block too long)
        let aiPredictions: string[];
        try {
          aiPredictions = await withTimeout(this.aiEngine.predict(context, count, topic), AI_PREDICTION_TIMEOUT_MS);
        } catch (e) {
          console.warn('HybridPredictionEngine: AI predictions timed out or failed', e);
          aiPredictions = [];
        }

        // Merge AI predictions (prioritize AI for first few slots)
        const merged: string[] = [];

        // Add AI predictions first (up to half the count)
        const aiCount = Math.min(aiPredictions.length, Math.floor(count / 2));
        merged.push(...aiPredictions.slice(0, aiCount));

        // Fill remaining with local predictions
        const remaining = localPredictions.filter((p) => !merged.includes(p));
        merged.push(...remaining.slice(0, count - merged.length));

        return merged.slice(0, count);
      } catch (e) {
        console.error('HybridPredictionEngine: Error in AI predictions', e);
        // Fall back to local predictions only
      }
    }

    // Return local predictions (AI disabled or failed)
    return [...predictions].slice(0, count);
  }

  async recordUsage(word: string): Promise<void> {
    await this.localEngine.recordUsage(word);
    await this.aiEngine?.recordUsage(word);
  }

  async recordSentence(words: string[]): Promise<void> {
    await this.localEngine.recordSentence(words);
    await this.aiEngine?.recordSentence(words);
  }

  /** Get starter words for beginning a sentence. */
  static getStarterWords(languageCode: string = 'en'): string[] {
    switch (languageCode) {
      case 'en':
        return ['I', 'You', 'We', 'They', 'He', 'She', 'Want', 'Need', 'Like', 'Feel', 'Have', 'Go', 'What', 'Where', 'When', 'Why', 'How', 'Who'];
      case 'iw':
        return ['אני', 'אתה', 'את', 'אנחנו', 'הם', 'הן', 'רוצה', 'צריך', 'אוהב', 'מרגיש', 'יש', 'הולך', 'מה', 'איפה', 'מתי', 'למה', 'איך', 'מי'];
      default:
        return HybridPredictionEngine.getStarterWords('en');
    }
  }

  /** Get verbs that commonly follow a pronoun. */
  static getVerbsForPronoun(pronoun: string, languageCode: string = 'en'): string[] {
    switch (languageCode) {
      case 'en':
        switch (pronoun.toLowerCase()) {
          case 'i':
            return ['want', 'need', 'am', 'like', 'feel', 'have', 'think', 'see', 'go', 'can', 'will', 'would'];
          case 'you':
            return ['are', 'want', 'need', 'can', 'should', 'have', 'do', 'will', 'would', 'like'];
          case 'we':
            return ['are', 'want', 'need', 'can', 'should', 'have', 'will', 'would', 'like', 'go'];
          case 'they':
            return ['are', 'want', 'need', 'can', 'have', 'will', 'would', 'like', 'go', 'do'];
          case 'he':
          case 'she':
            return ['is', 'wants', 'needs', 'can', 'has', 'will', 'would', 'likes', 'goes', 'does'];
          default:
            return [];
        }
      case 'iw':
        switch (pronoun) {
          case 'אני':
            return ['רוצה', 'צריך', 'אוהב', 'מרגיש', 'יש לי', 'הולך', 'יכול', 'רואה'];
          case 'אתה':
          case 'את':
            return ['רוצה', 'צריך', 'אוהב', 'מרגיש', 'יש לך', 'הולך', 'יכול'];
          case 'אנחנו':
            return ['רוצים', 'צריכים', 'אוהבים', 'מרגישים', 'יש לנו', 'הולכים', 'יכולים'];
          case 'הם':
          case 'הן':
            return ['רוצים', 'צריכים', 'אוהבים', 'מרגישים', 'יש להם', 'הולכים', 'יכולים'];
          default:
            return [];
        }
      default:
        return HybridPredictionEngine.getVerbsForPronoun(pronoun, 'en');
    }
  }

  /**
   * Get common completions for a phrase.
   * @param boardNames Optional list of board names to suggest for location-related contexts
   */
  static getCompletionsForPhrase(phrase: string, languageCode: string = 'en', boardNames: string[] = []): string[] {
    const lowerPhrase = phrase.toLowerCase().trim();
    switch (languageCode) {
      case 'en': {
        // Location/destination contexts - suggest board names
        if (/.*(want to go|let'?s go to|going to|go to)$/.test(lowerPhrase)) {
          return boardNames.length > 0 ? boardNames : ['the park', 'school', 'home', 'the store'];
        }
        if (lowerPhrase.endsWith('i want') || lowerPhrase.endsWith('want')) {
          return ['to', 'water', 'food', 'help', 'more', 'go', 'eat', 'drink', 'sleep', 'play'];
        }
        if (lowerPhrase.endsWith('i need') || lowerPhrase.endsWith('need')) {
          return ['help', 'water', 'food', 'to', 'bathroom', 'more', 'rest', 'medicine'];
        }
        if (lowerPhrase.endsWith('i like') || lowerPhrase.endsWith('like')) {
          return ['this', 'that', 'it', 'you', 'to', 'music', 'food'];
        }
        if (lowerPhrase.endsWith('i feel') || lowerPhrase.endsWith('feel')) {
          return ['good', 'bad', 'tired', 'happy', 'sad', 'sick', 'hungry', 'thirsty'];
        }
        if (lowerPhrase.startsWith('what')) {
          return ['is', 'do', 'are', 'time', 'happened', 'about', 'can', 'should'];
        }
        if (lowerPhrase.startsWith('where')) {
          return ['is', 'are', 'do', 'can', 'should', 'am'];
        }
        if (lowerPhrase.startsWith('when')) {
          return ['is', 'do', 'are', 'can', 'will', 'should'];
        }
        if (lowerPhrase.startsWith('why')) {
          return ['is', 'do', 'are', 'can', 'did', 'should'];
        }
        if (lowerPhrase.startsWith('how')) {
          return ['are', 'do', 'is', 'can', 'did', 'should'];
        }
        return [];
      }
      case 'iw': {
        // Location/destination contexts - suggest board names
        if (/.*(הולך ל|נלך ל|רוצה ללכת ל|הולכים ל)$/.test(lowerPhrase)) {
          return boardNames.length > 0 ? boardNames : ['הפארק', 'בית ספר', 'הבית', 'החנות'];
        }
        if (lowerPhrase.endsWith('רוצה')) {
          return ['ל', 'מים', 'אוכל', 'עזרה', 'עוד', 'ללכת', 'לאכול', 'לשתות'];
        }
        if (lowerPhrase.endsWith('צריך')) {
          return ['עזרה', 'מים', 'אוכל', 'ל', 'שירותים', 'עוד'];
        }
        if (lowerPhrase.startsWith('מה')) {
          return ['זה', 'קורה', 'השעה', 'עושים', 'יש'];
        }
        if (lowerPhrase.startsWith('איפה')) {
          return ['זה', 'אתה', 'הם', 'נמצא'];
        }
        return [];
      }
      default:
        return HybridPredictionEngine.getCompletionsForPhrase(phrase, 'en', boardNames);
    }
  }
}
