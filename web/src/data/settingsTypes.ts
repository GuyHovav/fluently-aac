import { DisabilityType } from '../models';

// Mirrors AppSettings + SettingsRepository's SharedPreferences keys from
// app/src/main/java/com/example/myaac/data/repository/SettingsRepository.kt.
//
// AppSettings there is one flat data class loaded wholesale from a single
// SharedPreferences file ("app_settings") into one StateFlow. Dexie has no
// SharedPreferences-file equivalent, so this is ported as a single fixed-id row in a
// `settings` table (see db.ts) holding the same flat shape, rather than one row per
// key — see web/src/data/README section in the module doc comment on `db.ts` for the
// full rationale.

/** Fixed primary key of the single settings row (there is exactly one, like the one SharedPreferences file). */
export const SETTINGS_ROW_ID = 'app_settings' as const;

export interface AppSettings {
  languageCode: string;
  textScale: number;
  ttsRate: number;
  disabilityType: DisabilityType;
  locationSuggestionsEnabled: boolean;
  showHorizontalNavigation: boolean;
  showSymbolsInSentenceBar: boolean;
  itemsToGenerate: number;
  maxBoardCapacity: number;
  fontFamily: string;
  displayScale: number;
  useSystemSettings: boolean;
  symbolLibrary: string;
  showHomeOnStartup: boolean;
  homeButtonsJson: string;
  appShortcutsJson: string;
  // Prediction settings
  predictionEnabled: boolean;
  aiPredictionEnabled: boolean;
  predictionCount: number;
  showSymbolsInPredictions: boolean;
  learnFromUsage: boolean;
  // Grammar settings
  autoGrammarCheck: boolean;
  // Display settings
  landscapeBigSentence: boolean;
}

/** The Dexie `settings` table row: the fixed id plus the flat AppSettings shape. */
export type SettingsRow = { id: typeof SETTINGS_ROW_ID } & AppSettings;

/** Mirrors every default value baked into SettingsRepository.loadSettings()'s AppSettings(...) call. */
export const DEFAULT_APP_SETTINGS: AppSettings = {
  languageCode: 'en',
  textScale: 1.0,
  ttsRate: 1.0,
  disabilityType: DisabilityType.NONE,
  locationSuggestionsEnabled: true,
  showHorizontalNavigation: false,
  showSymbolsInSentenceBar: false,
  itemsToGenerate: 20,
  maxBoardCapacity: 500,
  fontFamily: 'System',
  displayScale: 1.0,
  useSystemSettings: true,
  symbolLibrary: 'ARASAAC',
  showHomeOnStartup: true,
  homeButtonsJson: '',
  appShortcutsJson: '',
  predictionEnabled: true,
  aiPredictionEnabled: true,
  predictionCount: 5,
  showSymbolsInPredictions: true,
  learnFromUsage: true,
  autoGrammarCheck: false,
  landscapeBigSentence: true,
};
