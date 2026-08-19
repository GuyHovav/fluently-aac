import type { AppShortcut } from '../models';
import { DisabilityType } from '../models';
import { db } from './db';
import { DEFAULT_APP_SETTINGS, SETTINGS_ROW_ID } from './settingsTypes';
import type { AppSettings, SettingsRow } from './settingsTypes';

// Mirrors app/src/main/java/com/example/myaac/data/repository/SettingsRepository.kt.
//
// The Kotlin repository keeps a `SharedPreferences` instance plus an in-memory
// `MutableStateFlow<AppSettings>` it updates on every setter call. This module is the
// storage-and-persistence half of that: every function here reads/writes the single
// `settings` row in Dexie (see db.ts) and returns the resulting `AppSettings` snapshot.
// The reactive half (the StateFlow equivalent that UI code subscribes to) is
// `store/settingsStore.ts`, which calls into these functions and republishes the
// result — deliberately split the same way Kotlin splits "SharedPreferences I/O" from
// "the StateFlow observers actually read".
//
// Every one of SettingsRepository's ~24 SharedPreferences keys/setters is ported
// 1:1 below; see this module's export list. One intentional omission: the Kotlin
// `loadSettings()` has a one-time backward-compatibility shim that reads a legacy
// `max_board_items` key (from a preferences schema that predates the
// itemsToGenerate/maxBoardCapacity split) to compute defaults. That shim exists only
// to carry old *SharedPreferences* installs forward; per the migration plan there is
// no Room/SharedPreferences -> Dexie data migration; the hybrid app ships as a fresh
// install, so that legacy key can never be present here and the shim is not ported.

function coerceIn(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

/** Mirrors `loadSettings()` for the initial read, creating the row with defaults on first run. */
export async function loadSettings(): Promise<AppSettings> {
  const row = await db.settings.get(SETTINGS_ROW_ID);
  if (row) {
    const { id: _id, ...settings } = row;
    return settings;
  }
  await db.settings.put({ id: SETTINGS_ROW_ID, ...DEFAULT_APP_SETTINGS });
  return { ...DEFAULT_APP_SETTINGS };
}

async function updateSettings(patch: Partial<AppSettings>): Promise<AppSettings> {
  const current = await loadSettings();
  const updated: AppSettings = { ...current, ...patch };
  const row: SettingsRow = { id: SETTINGS_ROW_ID, ...updated };
  await db.settings.put(row);
  return updated;
}

export async function setLanguage(code: string): Promise<AppSettings> {
  return updateSettings({ languageCode: code });
}

export async function setTextScale(scale: number): Promise<AppSettings> {
  return updateSettings({ textScale: scale });
}

export async function setFontFamily(family: string): Promise<AppSettings> {
  return updateSettings({ fontFamily: family });
}

export async function setDisplayScale(scale: number): Promise<AppSettings> {
  return updateSettings({ displayScale: scale });
}

export async function setUseSystemSettings(useSystem: boolean): Promise<AppSettings> {
  return updateSettings({ useSystemSettings: useSystem });
}

export async function setTtsRate(rate: number): Promise<AppSettings> {
  return updateSettings({ ttsRate: rate });
}

export async function setLocationSuggestionsEnabled(enabled: boolean): Promise<AppSettings> {
  return updateSettings({ locationSuggestionsEnabled: enabled });
}

export async function setHorizontalNavigationEnabled(enabled: boolean): Promise<AppSettings> {
  return updateSettings({ showHorizontalNavigation: enabled });
}

export async function setShowSymbolsInSentenceBar(enabled: boolean): Promise<AppSettings> {
  return updateSettings({ showSymbolsInSentenceBar: enabled });
}

export async function setLandscapeBigSentence(enabled: boolean): Promise<AppSettings> {
  return updateSettings({ landscapeBigSentence: enabled });
}

/** Clamped to [1, 50], mirroring `setItemsToGenerate`. */
export async function setItemsToGenerate(count: number): Promise<AppSettings> {
  return updateSettings({ itemsToGenerate: coerceIn(count, 1, 50) });
}

/** Clamped to [50, 1000], mirroring `setMaxBoardCapacity`. */
export async function setMaxBoardCapacity(count: number): Promise<AppSettings> {
  return updateSettings({ maxBoardCapacity: coerceIn(count, 50, 1000) });
}

/** Minimum textScale each DisabilityType enforces, mirroring `applyPersonalization`. */
function minTextScaleFor(type: DisabilityType): number | null {
  switch (type) {
    case DisabilityType.VISUAL_IMPAIRMENT:
      return 1.3;
    case DisabilityType.MOTOR_IMPAIRMENT:
      return 1.1;
    default:
      return null;
  }
}

/** Mirrors `setDisabilityType(type)`, including its `applyPersonalization` side effect. */
export async function setDisabilityType(type: DisabilityType): Promise<AppSettings> {
  const updated = await updateSettings({ disabilityType: type });
  const minTextScale = minTextScaleFor(type);
  if (minTextScale !== null && updated.textScale < minTextScale) {
    return setTextScale(minTextScale);
  }
  return updated;
}

export async function setSymbolLibrary(library: string): Promise<AppSettings> {
  return updateSettings({ symbolLibrary: library });
}

export async function setShowHomeOnStartup(show: boolean): Promise<AppSettings> {
  return updateSettings({ showHomeOnStartup: show });
}

export async function setHomeButtons(buttonsJson: string): Promise<AppSettings> {
  return updateSettings({ homeButtonsJson: buttonsJson });
}

/** Mirrors `getAppShortcuts(): List<AppShortcut>` (parses the current settings row's JSON blob). */
export async function getAppShortcuts(): Promise<AppShortcut[]> {
  const settings = await loadSettings();
  return parseAppShortcuts(settings.appShortcutsJson);
}

/** Pure JSON-parsing half of `getAppShortcuts`, exposed so the Zustand store can reuse it without a DB round-trip. */
export function parseAppShortcuts(json: string): AppShortcut[] {
  if (json.length === 0) return [];
  try {
    const parsed: unknown = JSON.parse(json);
    return Array.isArray(parsed) ? (parsed as AppShortcut[]) : [];
  } catch {
    return [];
  }
}

/** Mirrors `setAppShortcuts(shortcuts)`. */
export async function setAppShortcuts(shortcuts: AppShortcut[]): Promise<AppSettings> {
  return updateSettings({ appShortcutsJson: JSON.stringify(shortcuts) });
}

// Prediction settings

export async function setPredictionEnabled(enabled: boolean): Promise<AppSettings> {
  return updateSettings({ predictionEnabled: enabled });
}

export async function setAiPredictionEnabled(enabled: boolean): Promise<AppSettings> {
  return updateSettings({ aiPredictionEnabled: enabled });
}

/** Clamped to [3, 8], mirroring `setPredictionCount`. */
export async function setPredictionCount(count: number): Promise<AppSettings> {
  return updateSettings({ predictionCount: coerceIn(count, 3, 8) });
}

export async function setShowSymbolsInPredictions(show: boolean): Promise<AppSettings> {
  return updateSettings({ showSymbolsInPredictions: show });
}

export async function setLearnFromUsage(learn: boolean): Promise<AppSettings> {
  return updateSettings({ learnFromUsage: learn });
}

export async function setAutoGrammarCheck(enabled: boolean): Promise<AppSettings> {
  return updateSettings({ autoGrammarCheck: enabled });
}
