// Phase 1: Dexie schema, settings store, repositories.
// TypeScript port of app/src/main/java/com/example/myaac/data/{local,cache,repository}/*.kt.

export { AacDatabase, db } from './db';

export type {
  WordFrequencyRow,
  WordBigramRow,
  WordTrigramRow,
  PhraseCacheRow,
  CacheStats,
} from './entities';
export { CacheType } from './entities';

export type { AppSettings, SettingsRow } from './settingsTypes';
export { DEFAULT_APP_SETTINGS, SETTINGS_ROW_ID } from './settingsTypes';

export { md5 } from './md5';

export * as boardRepository from './boardRepository';
export * as predictionRepository from './predictionRepository';
export * as settingsRepository from './settingsRepository';

// Dexie-backed adapters for the storage interfaces services/phraseCacheService.ts and
// nlp/localPredictionEngine.ts define — see appServices.ts for the wired-up singletons.
export { DexiePhraseCacheDao, DexieWordFrequencyRepository } from './dexieAdapters';
