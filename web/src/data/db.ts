import Dexie, { type EntityTable } from 'dexie';
import type { Board } from '../models';
import type { WordFrequencyRow, WordBigramRow, WordTrigramRow, PhraseCacheRow } from './entities';
import type { SettingsRow } from './settingsTypes';

// Dexie schema mirroring the Room `AppDatabase` (version 5) from
// app/src/main/java/com/example/myaac/data/local/AppDatabase.kt.
//
// Schema decisions vs. the Kotlin source:
//
// - `boards`: Room's Board entity embeds `buttons: List<AacButton>` as a single JSON
//   TEXT column (via Converters.fromButtonList/toButtonList — see Converters.kt).
//   This is ported as buttons embedded directly on the Dexie row (a plain array
//   field), NOT as a separate `buttons` table. Rationale: the Kotlin source itself
//   already treats the button list as one opaque unit that always travels with its
//   board (BoardDao has no button-level queries — "Room might not query inside JSON
//   directly... so we fetch boards and flatten in Repo" per BoardDao's own comment),
//   so a separate indexed table would add join complexity Room never had either,
//   for no query benefit; IndexedDB's structured-clone storage handles nested
//   objects/arrays natively (no JSON string column needed the way SQLite required).
//   If a future phase needs to query/filter individual buttons across boards
//   independent of their board, that's a reason to split it out then.
//
// - `wordFrequency` / `wordBigram` / `wordTrigram`: mirror WordFrequencyEntity /
//   WordBigramEntity / WordTrigramEntity 1:1, including their unique indices
//   (Dexie's `&` prefix) and the compound indices needed for the DAO's query shapes
//   (see predictionRepository.ts).
//
// - `phraseCache`: mirrors PhraseCacheEntity 1:1, `cacheKey` (an MD5 hex string) as
//   the primary key, plus indices for the query patterns PhraseCacheDao uses
//   (by cacheType, by timestamp for expiry sweeps, by hitCount+timestamp for LRU
//   eviction).
//
// - `settings`: SettingsRepository backs one flat `AppSettings` data class off a
//   single SharedPreferences file, observed as one atomic StateFlow<AppSettings>.
//   That's mirrored here as a single-row table (fixed primary key `SETTINGS_ROW_ID`)
//   holding the whole flat settings shape, rather than one row per preference key.
//   This was chosen over a raw key-value table because it matches how the app
//   actually reads/writes settings today — as one atomic snapshot hydrated into a
//   single reactive store (see store/settingsStore.ts) — and keeps get/set functions
//   simple `get`/`put` calls on one row instead of N key lookups on every read.
export class AacDatabase extends Dexie {
  boards!: EntityTable<Board, 'id'>;
  wordFrequency!: EntityTable<WordFrequencyRow, 'id'>;
  wordBigram!: EntityTable<WordBigramRow, 'id'>;
  wordTrigram!: EntityTable<WordTrigramRow, 'id'>;
  phraseCache!: EntityTable<PhraseCacheRow, 'cacheKey'>;
  settings!: EntityTable<SettingsRow, 'id'>;

  constructor() {
    super('myaac_database');

    this.version(1).stores({
      boards: 'id',
      wordFrequency: '++id, &word, frequency, lastUsed',
      wordBigram: '++id, word1, &[word1+word2], frequency, lastUsed',
      wordTrigram: '++id, [word1+word2], &[word1+word2+word3], frequency, lastUsed',
      phraseCache: 'cacheKey, cacheType, timestamp, hitCount, [hitCount+timestamp]',
      settings: 'id',
    });
  }
}

/** Singleton database instance, matching AppDatabase.getDatabase(context)'s single-instance pattern. */
export const db = new AacDatabase();
