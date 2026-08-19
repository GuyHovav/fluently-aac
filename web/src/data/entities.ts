// Dexie row types mirroring the Room entities in
// app/src/main/java/com/example/myaac/data/local/WordFrequencyEntity.kt and
// app/src/main/java/com/example/myaac/data/local/PhraseCacheEntity.kt.
//
// `id` is optional on the n-gram rows because Dexie/IndexedDB assigns
// auto-incrementing primary keys on insert (mirrors Room's
// `@PrimaryKey(autoGenerate = true) val id: Long = 0`) — callers omit `id` when
// inserting a new row.

/** Mirrors WordFrequencyEntity ("word_frequency" table). Unique index on `word`. */
export interface WordFrequencyRow {
  id?: number;
  word: string;
  frequency: number;
  lastUsed: number;
}

/**
 * Mirrors WordBigramEntity ("word_bigram" table).
 * Unique compound index on (word1, word2); non-unique index on word1 alone for the
 * "bigrams starting with word1" lookup.
 */
export interface WordBigramRow {
  id?: number;
  word1: string;
  word2: string;
  frequency: number;
  lastUsed: number;
}

/**
 * Mirrors WordTrigramEntity ("word_trigram" table).
 * Unique compound index on (word1, word2, word3); non-unique compound index on
 * (word1, word2) for the "trigrams starting with word1, word2" lookup.
 */
export interface WordTrigramRow {
  id?: number;
  word1: string;
  word2: string;
  word3: string;
  frequency: number;
  lastUsed: number;
}

/** Mirrors the Kotlin `object CacheType` string constants. */
export const CacheType = {
  PREDICTION: 'prediction',
  GRAMMAR: 'grammar',
} as const;

export type CacheType = (typeof CacheType)[keyof typeof CacheType];

/** Mirrors PhraseCacheEntity ("phrase_cache" table). `cacheKey` (MD5 hex) is the primary key. */
export interface PhraseCacheRow {
  cacheKey: string;
  cacheType: CacheType;
  input: string;
  result: string;
  languageCode: string;
  timestamp: number;
  hitCount: number;
}

/** Mirrors the Kotlin `data class CacheStats` returned by PhraseCacheService.getCacheStats(). */
export interface CacheStats {
  predictionCount: number;
  grammarCount: number;
  totalCount: number;
  mostUsedEntries: PhraseCacheRow[];
}
