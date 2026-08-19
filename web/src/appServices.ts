// App-wide composition root: wires the storage-agnostic services/nlp classes (ported in
// Phase 1) to their real Dexie-backed dependencies. Phase 2 UI code should import the
// singletons here rather than constructing PhraseCacheService/LocalPredictionEngine directly,
// so there's exactly one IndexedDB-backed cache and one learned-prediction store app-wide.
//
// GeminiService/AiPredictionEngine/HybridPredictionEngine are intentionally NOT composed here:
// they need a runtime API key and the settings store's aiPredictionEnabled/predictionCount
// values, which is Phase 2 UI-wiring territory, not a fixed singleton.

import { DexiePhraseCacheDao, DexieWordFrequencyRepository } from './data/dexieAdapters';
import { PhraseCacheService } from './services/phraseCacheService';
import { LocalPredictionEngine } from './nlp/localPredictionEngine';

export const phraseCacheService = new PhraseCacheService(new DexiePhraseCacheDao());
export const localPredictionEngine = new LocalPredictionEngine(new DexieWordFrequencyRepository());
