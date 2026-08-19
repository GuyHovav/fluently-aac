// Port of data/remote/GlobalSymbolsService.kt — near-mechanical translation.
// REST: GET https://globalsymbols.com/api/v1/concepts/suggest?query=&symbolset=&language=&language_iso_format=639-3

import type { SymbolResult, SymbolService } from './symbolService';

export interface GlobalSymbolPicto {
  id: number;
  image_url: string;
}

export interface GlobalSymbolResponse {
  id: number;
  subject: string; // "name" is actually "subject" in JSON
  pictos: GlobalSymbolPicto[];
}

const BASE_URL = 'https://globalsymbols.com/api/v1/';

export class GlobalSymbolsService implements SymbolService {
  private symbolSet: string;

  constructor(symbolSet: string = 'mulberry') {
    this.symbolSet = symbolSet;
  }

  async search(query: string, language: string): Promise<SymbolResult[]> {
    try {
      // Map language codes to ISO 639-3 (3-letter)
      // GlobalSymbols API requires 'eng' for English
      const iso3Language = ((): string => {
        switch (language) {
          case 'en':
            return 'eng';
          case 'he':
          case 'iw':
            return 'heb';
          case 'es':
            return 'spa';
          case 'fr':
            return 'fra';
          default:
            return language; // Try as is or default
        }
      })();

      const params = new URLSearchParams({
        query,
        symbolset: this.symbolSet,
        language: iso3Language,
        language_iso_format: '639-3',
      });

      const response = await fetch(`${BASE_URL}concepts/suggest?${params.toString()}`);
      if (!response.ok) {
        throw new Error(`GlobalSymbolsService: HTTP ${response.status}`);
      }
      const results = (await response.json()) as GlobalSymbolResponse[];

      return results.flatMap((concept) =>
        concept.pictos.map((picto) => ({
          url: picto.image_url,
          label: concept.subject,
        })),
      );
    } catch (e) {
      console.error('GlobalSymbolsService ERROR:', e);
      return [];
    }
  }
}
