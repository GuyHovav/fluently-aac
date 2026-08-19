// Replacement for data/nlp/MorphologyService.kt, which wrapped SimpleNLG (a JVM-only NLG
// library with no JS equivalent).
//
// Per the migration plan's "Replacing SimpleNLG" section: MorphologyService.getVariations() had
// exactly one call site (MainActivity.kt:980-982 — the "grammar popup" word-variation picker
// dialog reached via a button's long-press/grammar action) and, per the codebase, no Hebrew
// lexicon actually wired up in practice (English-only despite the app being bilingual). Rather
// than port or replace SimpleNLG, this issues a single Gemini prompt asking for the word's
// morphological variations directly in the target language — a strict capability upgrade for
// Hebrew (Gemini vs. no working Hebrew lexicon today) — falling back to a small hardcoded
// English +ed/+ing/+s suffix-rule table when Gemini is unreachable (offline, or the call fails),
// matching the app's existing "AI call with a static-rule fallback" idiom used elsewhere
// (see nlp/hybridPredictionEngine.ts).
//
// Call-site contract (from MainActivity.kt:980-982, preserved here):
//   const verbs = await morphologyService.getVariations(word, 'VERB');
//   const nouns = await morphologyService.getVariations(word, 'NOUN');
//   const adjectives = await morphologyService.getVariations(word, 'ADJECTIVE');
//   const combined = [...new Set([...verbs, ...nouns, ...adjectives])];
//   // shown only if combined.length > 0 && combined is not just [word]

import { generateGeminiText } from './geminiService';

export type MorphologyCategory = 'VERB' | 'NOUN' | 'ADJECTIVE';

export class MorphologyService {
  /**
   * Generate variations for a given word and part of speech, e.g. "eat" (VERB) ->
   * ["eat", "ate", "eating", "will eat"], or "cat" (NOUN) -> ["cat", "cats"].
   * Always includes the base word itself, mirroring the Kotlin source's behavior of adding the
   * base form as the first variation.
   */
  async getVariations(
    word: string,
    category: MorphologyCategory,
    languageCode: string = 'en',
  ): Promise<string[]> {
    const trimmed = word.trim();
    if (!trimmed) return [];

    try {
      const aiVariations = await this.getVariationsFromGemini(trimmed, category, languageCode);
      if (aiVariations.length > 0) {
        return dedupe([trimmed, ...aiVariations]);
      }
    } catch (e) {
      console.warn('MorphologyService: Gemini call failed, falling back to static rules', e);
    }

    // Offline / Gemini-unreachable fallback. English-only, matching the plan's scope for this
    // fallback path.
    return dedupe([trimmed, ...englishSuffixFallback(trimmed, category)]);
  }

  private async getVariationsFromGemini(
    word: string,
    category: MorphologyCategory,
    languageCode: string,
  ): Promise<string[]> {
    const languageName = languageCode === 'iw' || languageCode === 'he' ? 'Hebrew' : 'English';
    const formsClause =
      category === 'VERB'
        ? 'its base form, past tense, present progressive ("-ing" / continuous) form, and future tense form'
        : category === 'NOUN'
          ? 'its singular and plural forms'
          : 'its base form and common comparative/intensified forms, if applicable';

    const prompt = `Given this word and part of speech, return its base/past/progressive/future or singular/plural forms in ${languageName}.

Word: "${word}"
Part of speech: ${category}

Return ${formsClause}.
Return ONLY the forms, separated by commas. No explanations, no numbering.`;

    const text = await generateGeminiText(prompt);
    if (text == null) return [];
    return text
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
  }
}

function dedupe(words: string[]): string[] {
  return [...new Set(words)];
}

// ===== English suffix-rule fallback (+ed / +ing / +s), for offline use of the grammar popup =====

const VOWELS = new Set(['a', 'e', 'i', 'o', 'u']);

function isConsonant(ch: string): boolean {
  return /[a-z]/i.test(ch) && !VOWELS.has(ch.toLowerCase());
}

/** Consonant-Vowel-Consonant check for the final 3 letters (double-the-final-consonant rule). */
function endsInCvc(word: string): boolean {
  if (word.length < 3) return false;
  const [c1, v, c2] = word.slice(-3).toLowerCase();
  return isConsonant(c1) && VOWELS.has(v) && isConsonant(c2) && c2 !== 'w' && c2 !== 'x' && c2 !== 'y';
}

function endsInConsonantY(word: string): boolean {
  return word.length >= 2 && word.toLowerCase().endsWith('y') && isConsonant(word[word.length - 2]);
}

function pluralOrThirdPersonS(word: string): string {
  const lower = word.toLowerCase();
  if (/(s|x|z|ch|sh)$/.test(lower)) return `${word}es`;
  if (endsInConsonantY(word)) return `${word.slice(0, -1)}ies`;
  return `${word}s`;
}

function pastTenseEd(word: string): string {
  const lower = word.toLowerCase();
  if (lower.endsWith('e')) return `${word}d`;
  if (endsInConsonantY(word)) return `${word.slice(0, -1)}ied`;
  if (endsInCvc(word)) return `${word}${word[word.length - 1]}ed`;
  return `${word}ed`;
}

function presentParticipleIng(word: string): string {
  const lower = word.toLowerCase();
  if (lower.endsWith('ie')) return `${word.slice(0, -2)}ying`;
  if (lower.endsWith('e') && !lower.endsWith('ee') && !lower.endsWith('oe')) {
    return `${word.slice(0, -1)}ing`;
  }
  if (endsInCvc(word)) return `${word}${word[word.length - 1]}ing`;
  return `${word}ing`;
}

function englishSuffixFallback(word: string, category: MorphologyCategory): string[] {
  switch (category) {
    case 'VERB':
      return [pastTenseEd(word), presentParticipleIng(word), `will ${word}`];
    case 'NOUN':
      return [pluralOrThirdPersonS(word)];
    case 'ADJECTIVE':
      return [];
  }
}
