// Port of util/GrammarEngine.kt — near-mechanical translation.
// Rule-based sentence patcher (inserts "to"/articles/copulas between a bag of tapped words).

const PRONOUNS = new Set(['i', 'you', 'he', 'she', 'it', 'we', 'they', 'me', 'him', 'her', 'us', 'them']);
const VERBS = new Set([
  'want', 'like', 'need', 'love', 'go', 'see', 'hear', 'feel', 'eat', 'drink',
  'sleep', 'play', 'work', 'help', 'stop', 'come', 'get', 'take', 'make', 'do', 'say',
]);
const ADJECTIVES = new Set([
  'happy', 'sad', 'mad', 'hungry', 'tired', 'sick', 'scared', 'excited',
  'good', 'bad', 'big', 'small', 'hot', 'cold', 'thirsty', 'angry',
]);
const NOUNS = new Set([
  'apple', 'banana', 'cookie', 'sandwich', 'water', 'milk', 'juice',
  'book', 'crayon', 'tablet', 'bathroom', 'home', 'school', 'mom', 'dad',
  'toy', 'ball', 'car', 'dog', 'cat', 'friend', 'teacher', 'mall', 'park', 'store',
]);

const PLACES = new Set(['home', 'school', 'mall', 'park', 'store', 'bathroom']);

const ARTICLES = new Set(['a', 'an', 'the']);
const PREPOSITIONS = new Set(['to', 'for', 'with', 'at', 'by', 'from', 'in', 'on']);

const NON_COUNTABLE = new Set(['water', 'milk', 'juice', 'bread', 'help', 'work', 'sleep']);

export const GrammarEngine = {
  /**
   * Attempts to fix a list of words to be more grammatically correct.
   * Example: ["i", "want", "play"] -> "I want to play"
   */
  fixSentence(words: string[], languageCode: string = 'en'): string {
    if (words.length === 0) return '';
    if (languageCode !== 'en' && languageCode !== 'he' && languageCode !== 'iw') {
      const joined = words.join(' ');
      return capitalizeFirst(joined);
    }

    // Basic Hebrew handling: join with spaces and capitalize (not really capitalization in
    // Hebrew, but first char if English)
    if (languageCode === 'he' || languageCode === 'iw') {
      return words.join(' ');
    }

    const processedWords: string[] = [];
    const input = words
      .flatMap((w) => w.toLowerCase().split(' '))
      .filter((w) => w.trim().length > 0);

    if (input.length === 0) return '';

    for (let i = 0; i < input.length; i++) {
      const current = input[i];
      const next = input[i + 1];

      processedWords.push(current);

      if (next != null) {
        // Rule 1: Verb + Verb -> Insert "to"
        if (GrammarEngine.isVerb(current) && GrammarEngine.isVerb(next)) {
          if (current !== 'stop' && current !== 'do' && current !== 'can' && next !== 'to') {
            processedWords.push('to');
          }
        }

        // Rule 1.5: "go" + Place -> Insert "to"
        if ((current === 'go' || current === 'come') && GrammarEngine.isPlace(next)) {
          if (next !== 'home') {
            // "go home" is correct, "go to mall" is correct
            processedWords.push('to');
          }
        }

        // Rule 2: Pronoun + Adjective -> Insert am/is/are
        if (GrammarEngine.isPronoun(current) && GrammarEngine.isAdjective(next)) {
          const copula = ((): string | null => {
            switch (current) {
              case 'i':
                return 'am';
              case 'you':
              case 'we':
              case 'they':
                return 'are';
              case 'he':
              case 'she':
              case 'it':
                return 'is';
              default:
                return null;
            }
          })();
          if (copula != null && next !== copula) {
            processedWords.push(copula);
          }
        }

        // Rule 3: Verb + Noun -> Insert article
        if (GrammarEngine.isVerb(current) && GrammarEngine.isNoun(next)) {
          // Don't insert article if it's a place we just handled with "to", or if it's non-countable
          if (
            !GrammarEngine.isPlace(next) &&
            !isNonCountable(next) &&
            !ARTICLES.has(current) &&
            !PREPOSITIONS.has(current) &&
            next !== 'mom' &&
            next !== 'dad'
          ) {
            const article = 'aeiou'.includes(next[0]) ? 'an' : 'a';
            processedWords.push(article);
          }
        }
      }
    }

    // Post-processing: remove duplicates if they happened (e.g. "want to to play")
    const finalWords: string[] = [];
    for (const word of processedWords) {
      if (finalWords.length === 0 || finalWords[finalWords.length - 1] !== word) {
        finalWords.push(word);
      }
    }

    const joined = finalWords.map((word) => (word === 'i' ? 'I' : word)).join(' ');
    return capitalizeFirst(joined);
  },

  isVerb(word: string): boolean {
    return VERBS.has(word);
  },
  isPronoun(word: string): boolean {
    return PRONOUNS.has(word);
  },
  isAdjective(word: string): boolean {
    return ADJECTIVES.has(word);
  },
  isNoun(word: string): boolean {
    return NOUNS.has(word);
  },
  isPlace(word: string): boolean {
    return PLACES.has(word);
  },
};

function isNonCountable(word: string): boolean {
  return NON_COUNTABLE.has(word);
}

function capitalizeFirst(text: string): string {
  if (text.length === 0) return text;
  return text[0].toUpperCase() + text.slice(1);
}
