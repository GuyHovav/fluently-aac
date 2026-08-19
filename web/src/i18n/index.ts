import { useEffect } from 'react';
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

import { useSettingsStore } from '../store/settingsStore';
import en from './en';
import he from './he';

// Phase 5: react-i18next resources (en/he) and RTL setup.
//
// Language source of truth is settingsStore.settings.languageCode -- the same StateFlow-mirrored
// Dexie-backed setting the AI/TTS/prediction layers already read (see settingsStore.ts). This
// module does not add a second source of truth (no i18next-browser-languagedetector, no
// localStorage key of its own): i18next's active language and the document's dir/lang attributes
// are kept in sync with that one setting via useSyncI18nLanguage() below.
//
// 'iw' vs 'he': Android's language-resource qualifier for Hebrew is the legacy ISO 639-1 code
// "iw" (see app/src/main/res/values-iw/), and settingsStore.settings.languageCode uses that same
// "iw" value (matching AppSettings.languageCode's persisted values) -- so that value is preserved
// as-is on the settings side. i18next/Intl need the modern BCP-47 code "he" (Intl.PluralRules and
// <html lang> don't recognize "iw"), so mapLanguageCode() below is the single place that
// translates between the two, the same way SymbolSearchDialog.tsx already does locally for its
// own unrelated language dropdown.

/** Maps settingsStore's Android-style language code to the BCP-47 code i18next/Intl expect. */
export function mapLanguageCode(languageCode: string): 'en' | 'he' {
  return languageCode === 'iw' ? 'he' : 'en';
}

void i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    he: { translation: he },
  },
  lng: mapLanguageCode(useSettingsStore.getState().settings.languageCode),
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
  returnEmptyString: false,
});

/**
 * Keeps i18next's active language and the document's `dir`/`lang` attributes live-synced with
 * settingsStore.settings.languageCode. Call this once near the app root (App.tsx) -- every `t()`
 * call across the tree re-renders on change via react-i18next's context, and RTL-sensitive CSS
 * (`[dir="rtl"]` overrides, logical properties like `margin-inline-start`) reacts to the `dir`
 * attribute automatically. No reload required.
 */
export function useSyncI18nLanguage(): void {
  const languageCode = useSettingsStore((s) => s.settings.languageCode);

  useEffect(() => {
    const lng = mapLanguageCode(languageCode);
    if (i18n.language !== lng) {
      void i18n.changeLanguage(lng);
    }
    const dir = lng === 'he' ? 'rtl' : 'ltr';
    document.documentElement.dir = dir;
    document.documentElement.lang = lng;
  }, [languageCode]);
}

export default i18n;
