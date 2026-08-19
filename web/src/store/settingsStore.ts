import { create } from 'zustand';
import type { AppShortcut } from '../models';
import type { DisabilityType } from '../models';
import * as settingsRepository from '../data/settingsRepository';
import { DEFAULT_APP_SETTINGS } from '../data/settingsTypes';
import type { AppSettings } from '../data/settingsTypes';

// Zustand port of app/src/main/java/com/example/myaac/data/repository/SettingsRepository.kt's
// reactive shape.
//
// The Kotlin repository exposes `val settings: StateFlow<AppSettings>` plus ~24
// setter methods that each write to SharedPreferences and then push a new
// `AppSettings` snapshot onto that StateFlow. This store mirrors that exactly:
// `settings` is the StateFlow-equivalent single object UI code reads (via
// `useSettingsStore((s) => s.settings.textScale)` etc.), and every setter below
// calls the matching function in data/settingsRepository.ts (which persists to the
// Dexie `settings` row) and republishes the returned snapshot — same
// "persist-then-republish" order the Kotlin setters use.
//
// Hydration: `hydrate()` loads the persisted row (or seeds defaults on first run,
// see settingsRepository.loadSettings) and is invoked once automatically at the
// bottom of this module, so any consumer importing `useSettingsStore` kicks off
// hydration without Phase 2 UI code needing to remember to call it. `isHydrated`
// lets UI code gate first paint on the real persisted values if it wants to avoid a
// defaults-then-real-values flash.
export interface SettingsStore {
  settings: AppSettings;
  isHydrated: boolean;

  hydrate: () => Promise<void>;

  setLanguage: (code: string) => Promise<void>;
  setTextScale: (scale: number) => Promise<void>;
  setFontFamily: (family: string) => Promise<void>;
  setDisplayScale: (scale: number) => Promise<void>;
  setUseSystemSettings: (useSystem: boolean) => Promise<void>;
  setTtsRate: (rate: number) => Promise<void>;
  setLocationSuggestionsEnabled: (enabled: boolean) => Promise<void>;
  setHorizontalNavigationEnabled: (enabled: boolean) => Promise<void>;
  setShowSymbolsInSentenceBar: (enabled: boolean) => Promise<void>;
  setLandscapeBigSentence: (enabled: boolean) => Promise<void>;
  setItemsToGenerate: (count: number) => Promise<void>;
  setMaxBoardCapacity: (count: number) => Promise<void>;
  setDisabilityType: (type: DisabilityType) => Promise<void>;
  setSymbolLibrary: (library: string) => Promise<void>;
  setShowHomeOnStartup: (show: boolean) => Promise<void>;
  setHomeButtons: (buttonsJson: string) => Promise<void>;
  getAppShortcuts: () => AppShortcut[];
  setAppShortcuts: (shortcuts: AppShortcut[]) => Promise<void>;
  setPredictionEnabled: (enabled: boolean) => Promise<void>;
  setAiPredictionEnabled: (enabled: boolean) => Promise<void>;
  setPredictionCount: (count: number) => Promise<void>;
  setShowSymbolsInPredictions: (show: boolean) => Promise<void>;
  setLearnFromUsage: (learn: boolean) => Promise<void>;
  setAutoGrammarCheck: (enabled: boolean) => Promise<void>;
}

export const useSettingsStore = create<SettingsStore>((set, get) => ({
  settings: DEFAULT_APP_SETTINGS,
  isHydrated: false,

  hydrate: async () => {
    const settings = await settingsRepository.loadSettings();
    set({ settings, isHydrated: true });
  },

  setLanguage: async (code) => {
    set({ settings: await settingsRepository.setLanguage(code) });
  },
  setTextScale: async (scale) => {
    set({ settings: await settingsRepository.setTextScale(scale) });
  },
  setFontFamily: async (family) => {
    set({ settings: await settingsRepository.setFontFamily(family) });
  },
  setDisplayScale: async (scale) => {
    set({ settings: await settingsRepository.setDisplayScale(scale) });
  },
  setUseSystemSettings: async (useSystem) => {
    set({ settings: await settingsRepository.setUseSystemSettings(useSystem) });
  },
  setTtsRate: async (rate) => {
    set({ settings: await settingsRepository.setTtsRate(rate) });
  },
  setLocationSuggestionsEnabled: async (enabled) => {
    set({ settings: await settingsRepository.setLocationSuggestionsEnabled(enabled) });
  },
  setHorizontalNavigationEnabled: async (enabled) => {
    set({ settings: await settingsRepository.setHorizontalNavigationEnabled(enabled) });
  },
  setShowSymbolsInSentenceBar: async (enabled) => {
    set({ settings: await settingsRepository.setShowSymbolsInSentenceBar(enabled) });
  },
  setLandscapeBigSentence: async (enabled) => {
    set({ settings: await settingsRepository.setLandscapeBigSentence(enabled) });
  },
  setItemsToGenerate: async (count) => {
    set({ settings: await settingsRepository.setItemsToGenerate(count) });
  },
  setMaxBoardCapacity: async (count) => {
    set({ settings: await settingsRepository.setMaxBoardCapacity(count) });
  },
  setDisabilityType: async (type) => {
    set({ settings: await settingsRepository.setDisabilityType(type) });
  },
  setSymbolLibrary: async (library) => {
    set({ settings: await settingsRepository.setSymbolLibrary(library) });
  },
  setShowHomeOnStartup: async (show) => {
    set({ settings: await settingsRepository.setShowHomeOnStartup(show) });
  },
  setHomeButtons: async (buttonsJson) => {
    set({ settings: await settingsRepository.setHomeButtons(buttonsJson) });
  },
  getAppShortcuts: () => settingsRepository.parseAppShortcuts(get().settings.appShortcutsJson),
  setAppShortcuts: async (shortcuts) => {
    set({ settings: await settingsRepository.setAppShortcuts(shortcuts) });
  },
  setPredictionEnabled: async (enabled) => {
    set({ settings: await settingsRepository.setPredictionEnabled(enabled) });
  },
  setAiPredictionEnabled: async (enabled) => {
    set({ settings: await settingsRepository.setAiPredictionEnabled(enabled) });
  },
  setPredictionCount: async (count) => {
    set({ settings: await settingsRepository.setPredictionCount(count) });
  },
  setShowSymbolsInPredictions: async (show) => {
    set({ settings: await settingsRepository.setShowSymbolsInPredictions(show) });
  },
  setLearnFromUsage: async (learn) => {
    set({ settings: await settingsRepository.setLearnFromUsage(learn) });
  },
  setAutoGrammarCheck: async (enabled) => {
    set({ settings: await settingsRepository.setAutoGrammarCheck(enabled) });
  },
}));

// Hydrate once, as soon as this module is first imported (see module doc comment above).
void useSettingsStore.getState().hydrate();
