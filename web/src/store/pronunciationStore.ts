import { create } from 'zustand';

import { pronunciationDictionary } from './boardStore';

// Phase 4: custom-pronunciation persistence, backing PronunciationManagementScreen.tsx.
//
// Ground truth: PronunciationRepository.kt persists PronunciationDictionary.kt's custom map to a
// dedicated SharedPreferences file (one JSON blob under a single key), separate from
// SettingsRepository's "app_settings" file. web/src/nlp/pronunciationDictionary.ts already ports
// PronunciationDictionary 1:1 (Phase 1 output, off-limits here) -- it holds the custom map
// in-memory only and exposes addCustomPronunciation/removeCustomPronunciation/
// getCustomPronunciations/loadCustomPronunciations for a caller to wire up persistence, exactly
// as the Kotlin repository does.
//
// Where this lands: NOT web/src/data (Dexie) -- that directory, along with web/src/models,
// web/src/services, web/src/nlp, web/src/plugins, web/src/appServices.ts, and the *existing*
// exports of boardStore.ts/settingsStore.ts, is off-limits for this phase, and extending Dexie's
// schema (db.ts) or settingsRepository.ts/settingsTypes.ts to add a new table/column would violate
// that boundary. Instead this is a small, self-contained Zustand store living in web/src/store/
// (not protected) that persists directly to localStorage as one JSON blob -- the same
// "one flat blob under one key" shape SharedPreferences gave the Kotlin repository, and Capacitor's
// WebView-backed localStorage is sandboxed per-app the same way SharedPreferences is (see the
// migration plan's "Data & offline strategy" section), so this is a faithful, durable equivalent
// without touching any protected file. It hydrates boardStore's single shared
// `pronunciationDictionary` instance (see that module's doc comment on the export) synchronously
// at import time, mirroring settingsStore.ts's "hydrate once, at the bottom of the module" idiom,
// so speakSentence()'s pronunciation corrections and this screen's list are always the same data.

const STORAGE_KEY = 'myaac_custom_pronunciations';

function loadFromStorage(): Record<string, string> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed: unknown = JSON.parse(raw);
    if (parsed != null && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as Record<string, string>;
    }
    return {};
  } catch (e) {
    console.error('pronunciationStore: failed to load from localStorage', e);
    return {};
  }
}

function persist(entries: Record<string, string>): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
  } catch (e) {
    console.error('pronunciationStore: failed to persist to localStorage', e);
  }
}

export interface PronunciationStore {
  /** Original (no-nikud) word -> corrected (with-nikud/phonetic) word. */
  customEntries: Record<string, string>;
  addCustomPronunciation: (original: string, corrected: string) => void;
  removeCustomPronunciation: (original: string) => void;
}

const initialEntries = loadFromStorage();
pronunciationDictionary.loadCustomPronunciations(initialEntries);

export const usePronunciationStore = create<PronunciationStore>((set, get) => ({
  customEntries: initialEntries,

  addCustomPronunciation: (original, corrected) => {
    pronunciationDictionary.addCustomPronunciation(original, corrected);
    const updated = { ...get().customEntries, [original]: corrected };
    persist(updated);
    set({ customEntries: updated });
  },

  removeCustomPronunciation: (original) => {
    pronunciationDictionary.removeCustomPronunciation(original);
    const updated = { ...get().customEntries };
    delete updated[original];
    persist(updated);
    set({ customEntries: updated });
  },
}));
