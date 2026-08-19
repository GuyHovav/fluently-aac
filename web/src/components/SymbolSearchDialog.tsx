import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { ArasaacService, CompositeSymbolService, GlobalSymbolsService, GoogleImageService } from '../services';
import type { SymbolResult } from '../services';
import type { TranslationKey } from '../i18n/en';
import './editButtonUI.css';

// React port of ui/components/SymbolSearchDialog.kt, wired to CompositeSymbolService.
//
// Deviation from the Compose original: the Kotlin version built its own ARASAAC/GlobalSymbols/
// Google service chain inline; that composition is reproduced here verbatim (same vendor order,
// same combineResults=false/minRequiredResults=2 knobs) using the already-ported service classes
// from web/src/services rather than duplicating vendor logic.

const LANGUAGES: Record<string, TranslationKey> = {
  en: 'english',
  es: 'lang_spanish',
  he: 'hebrew',
  de: 'lang_german',
  fr: 'lang_french',
  it: 'lang_italian',
  pt: 'lang_portuguese',
};

function normalizeLanguage(defaultLanguage: string): string {
  const input = defaultLanguage === 'iw' ? 'he' : defaultLanguage;
  return input in LANGUAGES ? input : 'en';
}

export interface SymbolSearchDialogProps {
  initialQuery?: string;
  defaultLanguage?: string;
  /** Mirrors AppSettings.symbolLibrary ("ARASAAC" | "MULBERRY"), decides vendor priority order. */
  symbolLibrary?: string;
  onDismiss: () => void;
  onSymbolSelected: (url: string, label: string) => void;
}

export function SymbolSearchDialog({
  initialQuery = '',
  defaultLanguage = 'en',
  symbolLibrary = 'ARASAAC',
  onDismiss,
  onSymbolSelected,
}: SymbolSearchDialogProps) {
  const { t } = useTranslation();
  const [query, setQuery] = useState(initialQuery);
  const [language, setLanguage] = useState(() => normalizeLanguage(defaultLanguage));
  const [results, setResults] = useState<SymbolResult[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [hasSearchedOnce, setHasSearchedOnce] = useState(false);

  const symbolService = useMemo(() => {
    const services =
      symbolLibrary === 'MULBERRY'
        ? [new GlobalSymbolsService('mulberry'), new ArasaacService(), new GoogleImageService()]
        : [
            new ArasaacService(),
            new GlobalSymbolsService('arasaac'),
            new GlobalSymbolsService('mulberry'),
            new GoogleImageService(),
          ];
    return new CompositeSymbolService(services, false, 2);
  }, [symbolLibrary]);

  const performSearch = async (searchLanguage: string = language) => {
    const trimmed = query.trim();
    if (trimmed.length === 0) return;

    setIsLoading(true);
    setErrorMessage(null);
    setHasSearchedOnce(true);
    try {
      const list = await symbolService.search(trimmed, searchLanguage);
      setResults(list);
      if (list.length === 0) setErrorMessage(t('symbol_search_none_found'));
    } catch (e) {
      setErrorMessage(t('edit_button_error_generic', { message: e instanceof Error ? e.message : String(e) }));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onDismiss}>
      <div className="modal modal--symbol-search" onClick={(e) => e.stopPropagation()}>
        <div className="symbol-search__header">
          <h3>{t('symbol_search_title', { order: t(symbolLibrary === 'MULBERRY' ? 'symbol_search_mulberry_to_arasaac' : 'symbol_search_arasaac_to_mulberry') })}</h3>
          <select
            className="symbol-search__lang-select"
            value={language}
            onChange={(e) => {
              const next = e.target.value;
              setLanguage(next);
              if (query.trim().length > 0) void performSearch(next);
            }}
            aria-label={t('symbol_search_language_aria')}
          >
            {Object.entries(LANGUAGES).map(([code, nameKey]) => (
              <option key={code} value={code}>
                {t(nameKey)}
              </option>
            ))}
          </select>
        </div>

        <form
          className="symbol-search__bar"
          onSubmit={(e) => {
            e.preventDefault();
            void performSearch();
          }}
        >
          <input
            type="text"
            className="symbol-search__input"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('symbol_search_placeholder')}
            autoFocus
          />
          <button type="submit" className="symbol-search__go-btn" aria-label={t('symbol_search_aria')}>
            🔍
          </button>
        </form>

        <div className="symbol-search__results">
          {isLoading ? (
            <div className="symbol-search__status">{t('symbol_search_searching')}</div>
          ) : errorMessage ? (
            <div className="symbol-search__status symbol-search__status--error">{errorMessage}</div>
          ) : !hasSearchedOnce ? (
            <div className="symbol-search__status">{t('symbol_search_prompt')}</div>
          ) : (
            <div className="symbol-search__grid">
              {results.map((item, index) => (
                <button
                  key={`${item.url}-${index}`}
                  type="button"
                  className="symbol-search__item"
                  onClick={() => onSymbolSelected(item.url, item.label)}
                >
                  <img src={item.url} alt={item.label} loading="lazy" />
                  <span>{item.label}</span>
                </button>
              ))}
            </div>
          )}
        </div>

        <button type="button" className="modal__cancel" onClick={onDismiss}>
          {t('close')}
        </button>
      </div>
    </div>
  );
}
