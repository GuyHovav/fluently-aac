// Port of data/cache/PhraseCacheService.kt (+ CacheType/PhraseCacheEntity from PhraseCacheEntity.kt
// and the DAO surface from PhraseCacheDao.kt) — near-mechanical translation.
//
// NOTE: The Dexie `phraseCache` table (per the migration plan's Data & offline strategy section)
// is being stood up by a different agent under web/src/data/. Rather than block on that, this file
// defines a minimal local `PhraseCacheDao` interface shaped exactly like the Room DAO this class
// depended on, so a Dexie-backed implementation can be dropped in later with no changes to
// PhraseCacheService itself. An in-memory implementation is provided as a working default.

/** MD5-keyed cache entry. Mirrors PhraseCacheEntity.kt. */
export interface PhraseCacheEntity {
  cacheKey: string;
  /** Type of cached data: "prediction" or "grammar" */
  cacheType: string;
  /** Input context or sentence that was processed */
  input: string;
  /** Cached result (comma-separated for predictions, corrected sentence for grammar) */
  result: string;
  /** Language code when the cache was created */
  languageCode: string;
  /** Timestamp (ms since epoch) when this cache entry was created */
  timestamp: number;
  /** Number of times this cache entry has been used */
  hitCount: number;
}

/** Mirrors CacheType (PhraseCacheEntity.kt). */
export const CacheType = {
  PREDICTION: 'prediction',
  GRAMMAR: 'grammar',
} as const;
export type CacheTypeValue = (typeof CacheType)[keyof typeof CacheType];

/**
 * DAO surface PhraseCacheService needs. Shaped after PhraseCacheDao.kt so a Dexie
 * `phraseCache` table (see web/src/data/) can implement this directly.
 */
export interface PhraseCacheDao {
  getCached(key: string): Promise<PhraseCacheEntity | null>;
  insert(entity: PhraseCacheEntity): Promise<void>;
  incrementHitCount(key: string): Promise<void>;
  getCacheCount(cacheType: string): Promise<number>;
  getTotalCacheCount(): Promise<number>;
  deleteLeastUsed(count: number): Promise<void>;
  deleteOlderThan(cutoffTimestamp: number): Promise<void>;
  getMostUsedCache(limit: number): Promise<PhraseCacheEntity[]>;
  clearAll(): Promise<void>;
  clearByType(cacheType: string): Promise<void>;
}

/**
 * Simple in-memory PhraseCacheDao. Used as the default until a Dexie-backed implementation
 * is wired in from web/src/data/. Not persisted across app restarts.
 */
export class InMemoryPhraseCacheDao implements PhraseCacheDao {
  private entries = new Map<string, PhraseCacheEntity>();

  async getCached(key: string): Promise<PhraseCacheEntity | null> {
    return this.entries.get(key) ?? null;
  }

  async insert(entity: PhraseCacheEntity): Promise<void> {
    this.entries.set(entity.cacheKey, entity);
  }

  async incrementHitCount(key: string): Promise<void> {
    const existing = this.entries.get(key);
    if (existing) {
      existing.hitCount += 1;
    }
  }

  async getCacheCount(cacheType: string): Promise<number> {
    let count = 0;
    for (const e of this.entries.values()) {
      if (e.cacheType === cacheType) count += 1;
    }
    return count;
  }

  async getTotalCacheCount(): Promise<number> {
    return this.entries.size;
  }

  async deleteLeastUsed(count: number): Promise<void> {
    const sorted = [...this.entries.values()].sort((a, b) => a.hitCount - b.hitCount);
    for (const entity of sorted.slice(0, count)) {
      this.entries.delete(entity.cacheKey);
    }
  }

  async deleteOlderThan(cutoffTimestamp: number): Promise<void> {
    for (const [key, entity] of this.entries) {
      if (entity.timestamp < cutoffTimestamp) {
        this.entries.delete(key);
      }
    }
  }

  async getMostUsedCache(limit: number): Promise<PhraseCacheEntity[]> {
    return [...this.entries.values()].sort((a, b) => b.hitCount - a.hitCount).slice(0, limit);
  }

  async clearAll(): Promise<void> {
    this.entries.clear();
  }

  async clearByType(cacheType: string): Promise<void> {
    for (const [key, entity] of this.entries) {
      if (entity.cacheType === cacheType) {
        this.entries.delete(key);
      }
    }
  }
}

export interface CacheStats {
  predictionCount: number;
  grammarCount: number;
  totalCount: number;
  mostUsedEntries: PhraseCacheEntity[];
}

// Cache expiry times
const PREDICTION_CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000; // 24 hours
const GRAMMAR_CACHE_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

// Maximum cache entries per type
const MAX_PREDICTION_CACHE = 500;
const MAX_GRAMMAR_CACHE = 1000;

/**
 * Service for managing phrase cache (predictions and grammar)
 * Provides smart caching with expiry and size limits
 */
export class PhraseCacheService {
  private cacheDao: PhraseCacheDao;

  constructor(cacheDao: PhraseCacheDao = new InMemoryPhraseCacheDao()) {
    this.cacheDao = cacheDao;
  }

  /** Generate a deterministic cache key from input and metadata. */
  private async generateCacheKey(input: string, cacheType: string, languageCode: string): Promise<string> {
    const combined = `${cacheType}:${languageCode}:${input.trim().toLowerCase()}`;
    return hashToMD5(combined);
  }

  /**
   * Get cached predictions. Returns null if not cached or expired.
   */
  async getCachedPredictions(context: string[], languageCode: string): Promise<string[] | null> {
    const contextString = context.slice(-5).join(' ');
    const key = await this.generateCacheKey(contextString, CacheType.PREDICTION, languageCode);

    const cached = await this.cacheDao.getCached(key);
    if (!cached) return null;

    // Check if expired
    const age = Date.now() - cached.timestamp;
    if (age > PREDICTION_CACHE_EXPIRY_MS) {
      return null;
    }

    // Increment hit count
    await this.cacheDao.incrementHitCount(key);

    // Parse and return predictions
    return cached.result
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
  }

  /** Cache predictions. */
  async cachePredictions(context: string[], predictions: string[], languageCode: string): Promise<void> {
    try {
      // Check cache size and clean if needed
      await this.cleanupCacheIfNeeded(CacheType.PREDICTION, MAX_PREDICTION_CACHE);

      const contextString = context.slice(-5).join(' ');
      const key = await this.generateCacheKey(contextString, CacheType.PREDICTION, languageCode);
      const resultString = predictions.join(',');

      const entity: PhraseCacheEntity = {
        cacheKey: key,
        cacheType: CacheType.PREDICTION,
        input: contextString,
        result: resultString,
        languageCode,
        timestamp: Date.now(),
        hitCount: 0,
      };

      await this.cacheDao.insert(entity);
    } catch (e) {
      console.error('PhraseCacheService: Error caching predictions', e);
    }
  }

  /**
   * Get cached grammar correction. Returns null if not cached or expired.
   */
  async getCachedGrammar(sentence: string, languageCode: string): Promise<string | null> {
    const key = await this.generateCacheKey(sentence, CacheType.GRAMMAR, languageCode);

    const cached = await this.cacheDao.getCached(key);
    if (!cached) return null;

    // Check if expired
    const age = Date.now() - cached.timestamp;
    if (age > GRAMMAR_CACHE_EXPIRY_MS) {
      return null;
    }

    // Increment hit count
    await this.cacheDao.incrementHitCount(key);

    return cached.result;
  }

  /** Cache grammar correction. */
  async cacheGrammar(sentence: string, correctedSentence: string, languageCode: string): Promise<void> {
    try {
      // Don't cache if input and output are the same
      if (sentence.trim() === correctedSentence.trim()) {
        return;
      }

      // Check cache size and clean if needed
      await this.cleanupCacheIfNeeded(CacheType.GRAMMAR, MAX_GRAMMAR_CACHE);

      const key = await this.generateCacheKey(sentence, CacheType.GRAMMAR, languageCode);

      const entity: PhraseCacheEntity = {
        cacheKey: key,
        cacheType: CacheType.GRAMMAR,
        input: sentence,
        result: correctedSentence,
        languageCode,
        timestamp: Date.now(),
        hitCount: 0,
      };

      await this.cacheDao.insert(entity);
    } catch (e) {
      console.error('PhraseCacheService: Error caching grammar', e);
    }
  }

  /** Clean up cache if it exceeds the maximum size. */
  private async cleanupCacheIfNeeded(cacheType: string, maxSize: number): Promise<void> {
    const count = await this.cacheDao.getCacheCount(cacheType);
    if (count >= maxSize) {
      // Delete 20% of least used entries
      const toDelete = Math.trunc(maxSize * 0.2);
      await this.cacheDao.deleteLeastUsed(toDelete);
    }
  }

  /** Clean up expired cache entries. */
  async cleanupExpiredEntries(): Promise<void> {
    const predictionCutoff = Date.now() - PREDICTION_CACHE_EXPIRY_MS;
    const grammarCutoff = Date.now() - GRAMMAR_CACHE_EXPIRY_MS;

    // Delete entries older than prediction cutoff
    await this.cacheDao.deleteOlderThan(Math.min(predictionCutoff, grammarCutoff));
  }

  /** Get cache statistics for debugging. */
  async getCacheStats(): Promise<CacheStats> {
    const predictionCount = await this.cacheDao.getCacheCount(CacheType.PREDICTION);
    const grammarCount = await this.cacheDao.getCacheCount(CacheType.GRAMMAR);
    const totalCount = await this.cacheDao.getTotalCacheCount();
    const mostUsed = await this.cacheDao.getMostUsedCache(5);

    return {
      predictionCount,
      grammarCount,
      totalCount,
      mostUsedEntries: mostUsed,
    };
  }

  /** Clear all cache. */
  async clearAll(): Promise<void> {
    await this.cacheDao.clearAll();
  }

  /** Clear cache by type. */
  async clearByType(cacheType: string): Promise<void> {
    await this.cacheDao.clearByType(cacheType);
  }
}

/** Hash a string to MD5 hex for compact cache keys (mirrors String.hashToMD5() in the Kotlin source). */
async function hashToMD5(input: string): Promise<string> {
  // Web Crypto exposes SHA family natively, not MD5. MD5 is only used here as a compact,
  // deterministic, non-cryptographic key derivation (cache keys, not security), so a small
  // dependency-free MD5 implementation is used to keep parity with the Kotlin cache-key format.
  return md5Hex(input);
}

// Minimal dependency-free MD5 implementation (RFC 1321), sufficient for cache-key hashing.
function md5Hex(str: string): string {
  const bytes = new TextEncoder().encode(str);

  function rotl(x: number, c: number): number {
    return (x << c) | (x >>> (32 - c));
  }

  const s = [
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15,
    21,
  ];
  const K = new Int32Array(64);
  for (let i = 0; i < 64; i++) {
    K[i] = Math.floor(Math.abs(Math.sin(i + 1)) * 2 ** 32);
  }

  let a0 = 0x67452301;
  let b0 = 0xefcdab89;
  let c0 = 0x98badcfe;
  let d0 = 0x10325476;

  const msgLen = bytes.length;
  const withOne = new Uint8Array(((msgLen + 8) >> 6) * 64 + 64);
  withOne.set(bytes);
  withOne[msgLen] = 0x80;
  const bitLen = BigInt(msgLen) * 8n;
  const view = new DataView(withOne.buffer);
  view.setUint32(withOne.length - 8, Number(bitLen & 0xffffffffn), true);
  view.setUint32(withOne.length - 4, Number((bitLen >> 32n) & 0xffffffffn), true);

  for (let chunkStart = 0; chunkStart < withOne.length; chunkStart += 64) {
    const M = new Int32Array(16);
    for (let j = 0; j < 16; j++) {
      M[j] = view.getInt32(chunkStart + j * 4, true);
    }

    let A = a0;
    let B = b0;
    let C = c0;
    let D = d0;

    for (let i = 0; i < 64; i++) {
      let F: number;
      let g: number;
      if (i < 16) {
        F = (B & C) | (~B & D);
        g = i;
      } else if (i < 32) {
        F = (D & B) | (~D & C);
        g = (5 * i + 1) % 16;
      } else if (i < 48) {
        F = B ^ C ^ D;
        g = (3 * i + 5) % 16;
      } else {
        F = C ^ (B | ~D);
        g = (7 * i) % 16;
      }
      F = (F + A + K[i] + M[g]) | 0;
      A = D;
      D = C;
      C = B;
      B = (B + rotl(F, s[i])) | 0;
    }

    a0 = (a0 + A) | 0;
    b0 = (b0 + B) | 0;
    c0 = (c0 + C) | 0;
    d0 = (d0 + D) | 0;
  }

  const toHexLE = (n: number): string => {
    const buf = new Uint8Array(4);
    new DataView(buf.buffer).setInt32(0, n, true);
    return [...buf].map((b) => b.toString(16).padStart(2, '0')).join('');
  };

  return toHexLE(a0) + toHexLE(b0) + toHexLE(c0) + toHexLE(d0);
}
