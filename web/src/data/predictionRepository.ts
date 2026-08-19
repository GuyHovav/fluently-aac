import Dexie from 'dexie';
import type { WordBigramRow, WordFrequencyRow, WordTrigramRow } from './entities';
import { db } from './db';

// Mirrors app/src/main/java/com/example/myaac/data/local/WordFrequencyDao.kt.
//
// Room's `@Insert(onConflict = OnConflictStrategy.IGNORE)` returns -1 when the insert
// was ignored due to a unique-index conflict, else the new rowId. Dexie's `.add()`
// throws `Dexie.ConstraintError` on a unique-index conflict instead, so that's caught
// and translated to the same "-1 means ignored" convention below, keeping the
// `recordXUsage` transaction logic (which branches on that -1) a close mirror of the
// Kotlin `@Transaction` functions.

const IGNORED = -1;

async function insertIgnoringConflicts(
  insert: () => Promise<number | undefined>,
): Promise<number> {
  try {
    const key = await insert();
    return key ?? IGNORED;
  } catch (e) {
    if (e instanceof Dexie.ConstraintError) {
      return IGNORED;
    }
    throw e;
  }
}

// ========== Word Frequency (Unigrams) ==========

/** Mirrors `getWordFrequency(word): WordFrequencyEntity?`. */
export async function getWordFrequency(word: string): Promise<WordFrequencyRow | undefined> {
  return db.wordFrequency.where('word').equals(word).first();
}

/** Mirrors `getTopWords(limit): List<WordFrequencyEntity>` (ORDER BY frequency DESC LIMIT). */
export async function getTopWords(limit: number): Promise<WordFrequencyRow[]> {
  return db.wordFrequency.orderBy('frequency').reverse().limit(limit).toArray();
}

/** Mirrors `insertWord(word): Long` (IGNORE on conflict; returns -1 if ignored). */
export async function insertWord(word: WordFrequencyRow): Promise<number> {
  return insertIgnoringConflicts(() => db.wordFrequency.add(word));
}

/** Mirrors `incrementWordFrequency(word, timestamp)`. */
export async function incrementWordFrequency(
  word: string,
  timestamp: number = Date.now(),
): Promise<void> {
  await db.wordFrequency.where('word').equals(word).modify((row) => {
    row.frequency += 1;
    row.lastUsed = timestamp;
  });
}

/** Mirrors `clearAllWords()`. */
export async function clearAllWords(): Promise<void> {
  await db.wordFrequency.clear();
}

// ========== Bigrams ==========

/** Mirrors `getBigramsStartingWith(word1, limit = 10): List<WordBigramEntity>` (ORDER BY frequency DESC). */
export async function getBigramsStartingWith(
  word1: string,
  limit = 10,
): Promise<WordBigramRow[]> {
  const rows = await db.wordBigram.where('word1').equals(word1).toArray();
  rows.sort((a, b) => b.frequency - a.frequency);
  return rows.slice(0, limit);
}

/** Mirrors `insertBigram(bigram): Long` (IGNORE on conflict; returns -1 if ignored). */
export async function insertBigram(bigram: WordBigramRow): Promise<number> {
  return insertIgnoringConflicts(() => db.wordBigram.add(bigram));
}

/** Mirrors `incrementBigramFrequency(word1, word2, timestamp)`. */
export async function incrementBigramFrequency(
  word1: string,
  word2: string,
  timestamp: number = Date.now(),
): Promise<void> {
  await db.wordBigram
    .where('[word1+word2]')
    .equals([word1, word2])
    .modify((row) => {
      row.frequency += 1;
      row.lastUsed = timestamp;
    });
}

/** Mirrors `clearAllBigrams()`. */
export async function clearAllBigrams(): Promise<void> {
  await db.wordBigram.clear();
}

// ========== Trigrams ==========

/** Mirrors `getTrigramsStartingWith(word1, word2, limit = 10): List<WordTrigramEntity>` (ORDER BY frequency DESC). */
export async function getTrigramsStartingWith(
  word1: string,
  word2: string,
  limit = 10,
): Promise<WordTrigramRow[]> {
  const rows = await db.wordTrigram.where('[word1+word2]').equals([word1, word2]).toArray();
  rows.sort((a, b) => b.frequency - a.frequency);
  return rows.slice(0, limit);
}

/** Mirrors `insertTrigram(trigram): Long` (IGNORE on conflict; returns -1 if ignored). */
export async function insertTrigram(trigram: WordTrigramRow): Promise<number> {
  return insertIgnoringConflicts(() => db.wordTrigram.add(trigram));
}

/** Mirrors `incrementTrigramFrequency(word1, word2, word3, timestamp)`. */
export async function incrementTrigramFrequency(
  word1: string,
  word2: string,
  word3: string,
  timestamp: number = Date.now(),
): Promise<void> {
  await db.wordTrigram
    .where('[word1+word2+word3]')
    .equals([word1, word2, word3])
    .modify((row) => {
      row.frequency += 1;
      row.lastUsed = timestamp;
    });
}

/** Mirrors `clearAllTrigrams()`. */
export async function clearAllTrigrams(): Promise<void> {
  await db.wordTrigram.clear();
}

// ========== Batch Operations ==========

/** Mirrors the `@Transaction suspend fun recordWordUsage(word)`. */
export async function recordWordUsage(word: string): Promise<void> {
  const normalized = word.toLowerCase().trim();
  if (normalized.length === 0) return;

  await db.transaction('rw', db.wordFrequency, async () => {
    const existing = await getWordFrequency(normalized);
    if (existing === undefined) {
      await insertWord({ word: normalized, frequency: 1, lastUsed: Date.now() });
    } else {
      await incrementWordFrequency(normalized);
    }
  });
}

/** Mirrors the `@Transaction suspend fun recordBigramUsage(word1, word2)`. */
export async function recordBigramUsage(word1: string, word2: string): Promise<void> {
  const w1 = word1.toLowerCase().trim();
  const w2 = word2.toLowerCase().trim();
  if (w1.length === 0 || w2.length === 0) return;

  await db.transaction('rw', db.wordBigram, async () => {
    const insertResult = await insertBigram({ word1: w1, word2: w2, frequency: 1, lastUsed: Date.now() });
    if (insertResult === IGNORED) {
      await incrementBigramFrequency(w1, w2);
    }
  });
}

/** Mirrors the `@Transaction suspend fun recordTrigramUsage(word1, word2, word3)`. */
export async function recordTrigramUsage(
  word1: string,
  word2: string,
  word3: string,
): Promise<void> {
  const w1 = word1.toLowerCase().trim();
  const w2 = word2.toLowerCase().trim();
  const w3 = word3.toLowerCase().trim();
  if (w1.length === 0 || w2.length === 0 || w3.length === 0) return;

  await db.transaction('rw', db.wordTrigram, async () => {
    const insertResult = await insertTrigram({
      word1: w1,
      word2: w2,
      word3: w3,
      frequency: 1,
      lastUsed: Date.now(),
    });
    if (insertResult === IGNORED) {
      await incrementTrigramFrequency(w1, w2, w3);
    }
  });
}

/** Mirrors the `@Transaction suspend fun clearAllLearnedData()`. */
export async function clearAllLearnedData(): Promise<void> {
  await db.transaction('rw', db.wordFrequency, db.wordBigram, db.wordTrigram, async () => {
    await clearAllWords();
    await clearAllBigrams();
    await clearAllTrigrams();
  });
}
