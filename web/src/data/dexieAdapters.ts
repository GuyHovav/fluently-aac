// Dexie-backed implementations of the storage interfaces that services/phraseCacheService.ts
// and nlp/localPredictionEngine.ts define, so those modules stay storage-agnostic (easy to
// unit-test against their in-memory defaults) while the actual app uses IndexedDB via Dexie.
//
// Reconciles the two Phase 1 halves, which were ported in parallel against placeholder
// interfaces per the migration plan's phased sequencing: this file is the seam where the
// data-layer half (db.ts, predictionRepository.ts) meets the services/nlp half's DAO contracts.

import { db } from './db';
import type { CacheType as CacheTypeUnion } from './entities';
import * as predictionRepository from './predictionRepository';
import type { PhraseCacheDao, PhraseCacheEntity } from '../services/phraseCacheService';
import type {
  WordFrequencyRepository,
  WordFrequencyRecord,
  WordBigramRecord,
  WordTrigramRecord,
} from '../nlp/localPredictionEngine';

/** Implements PhraseCacheDao (services/phraseCacheService.ts) against the Dexie `phraseCache` table. */
export class DexiePhraseCacheDao implements PhraseCacheDao {
  async getCached(key: string): Promise<PhraseCacheEntity | null> {
    const row = await db.phraseCache.get(key);
    return row ?? null;
  }

  async insert(entity: PhraseCacheEntity): Promise<void> {
    // entity.cacheType is only ever 'prediction' | 'grammar' in practice (PhraseCacheService's
    // own CacheType constants), just typed as `string` on the storage-agnostic interface.
    await db.phraseCache.put({ ...entity, cacheType: entity.cacheType as CacheTypeUnion });
  }

  async incrementHitCount(key: string): Promise<void> {
    await db.phraseCache.where('cacheKey').equals(key).modify((row) => {
      row.hitCount += 1;
    });
  }

  async getCacheCount(cacheType: string): Promise<number> {
    return db.phraseCache.where('cacheType').equals(cacheType).count();
  }

  async getTotalCacheCount(): Promise<number> {
    return db.phraseCache.count();
  }

  async deleteLeastUsed(count: number): Promise<void> {
    if (count <= 0) return;
    const keys = await db.phraseCache.orderBy('[hitCount+timestamp]').limit(count).primaryKeys();
    await db.phraseCache.bulkDelete(keys);
  }

  async deleteOlderThan(cutoffTimestamp: number): Promise<void> {
    await db.phraseCache.where('timestamp').below(cutoffTimestamp).delete();
  }

  async getMostUsedCache(limit: number): Promise<PhraseCacheEntity[]> {
    return db.phraseCache.orderBy('hitCount').reverse().limit(limit).toArray();
  }

  async clearAll(): Promise<void> {
    await db.phraseCache.clear();
  }

  async clearByType(cacheType: string): Promise<void> {
    await db.phraseCache.where('cacheType').equals(cacheType).delete();
  }
}

/** Implements WordFrequencyRepository (nlp/localPredictionEngine.ts) against the Dexie n-gram tables. */
export class DexieWordFrequencyRepository implements WordFrequencyRepository {
  async getTopWords(limit: number): Promise<WordFrequencyRecord[]> {
    return predictionRepository.getTopWords(limit);
  }

  async getBigramsStartingWith(word1: string, limit: number): Promise<WordBigramRecord[]> {
    return predictionRepository.getBigramsStartingWith(word1, limit);
  }

  async getTrigramsStartingWith(word1: string, word2: string, limit: number): Promise<WordTrigramRecord[]> {
    return predictionRepository.getTrigramsStartingWith(word1, word2, limit);
  }

  async recordWordUsage(word: string): Promise<void> {
    await predictionRepository.recordWordUsage(word);
  }

  async recordBigramUsage(word1: string, word2: string): Promise<void> {
    await predictionRepository.recordBigramUsage(word1, word2);
  }

  async recordTrigramUsage(word1: string, word2: string, word3: string): Promise<void> {
    await predictionRepository.recordTrigramUsage(word1, word2, word3);
  }
}
