// Port of data/remote/ArasaacService.kt (+ ArasaacApi.kt) — near-mechanical translation.
// REST: GET https://api.arasaac.org/v1/pictograms/{locale}/search/{query}

import type { SymbolResult, SymbolService } from './symbolService';

export interface ArasaacKeyword {
  keyword: string;
}

export interface ArasaacPictogram {
  _id: number;
  keywords: ArasaacKeyword[];
}

const BASE_URL = 'https://api.arasaac.org/v1/';

/**
 * Manual overrides for common ambiguous words.
 * Ported verbatim from ArasaacService.kt's SYMBOL_OVERRIDES map.
 */
const SYMBOL_OVERRIDES: Record<string, number> = {
  can: 26521, // Verb "can" / "able to"
  will: 26497, // Future tense arrow
  may: 22350, // "Maybe" / possibility
  like: 2267, // Thumbs up / like
  kind: 34166, // Kind/nice person
  saw: 5858, // Verb "see" (past) instead of tool
  well: 34166, // Good/Well
  mine: 33744, // Possessive
  // Pronouns
  i: 6632,
  you: 6625,
  we: 7185,
  they: 7032,
  he: 6480,
  she: 7028,
  // Core Verbs
  want: 5441,
  need: 37160,
  feel: 30197,
  have: 32761,
  go: 8142,
  // Questions
  what: 22620,
  where: 7764,
  when: 32874,
  why: 36719,
  // Be verbs if needed
  am: 26521, // Reuse 'can/ability' or find better
  is: 26521,
  are: 26521,
};

export class ArasaacService implements SymbolService {
  async searchPictograms(query: string, locale: string = 'en'): Promise<ArasaacPictogram[]> {
    // Check overrides first (only for English single words for now)
    if (locale === 'en') {
      const lowerQuery = query.toLowerCase().trim();
      const overrideId = SYMBOL_OVERRIDES[lowerQuery];
      if (overrideId != null) {
        return [{ _id: overrideId, keywords: [{ keyword: query }] }];
      }
    }

    const url = `${BASE_URL}pictograms/${encodeURIComponent(locale)}/search/${encodeURIComponent(query)}`;
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`ArasaacService: HTTP ${response.status}`);
    }
    return (await response.json()) as ArasaacPictogram[];
  }

  getImageUrl(id: number): string {
    return `https://static.arasaac.org/pictograms/${id}/${id}_300.png`;
  }

  async search(query: string, language: string): Promise<SymbolResult[]> {
    const pictograms = await this.searchPictograms(query, language);
    return pictograms.map((p) => {
      const label = p.keywords[0]?.keyword ?? query;
      return {
        url: this.getImageUrl(p._id),
        label,
      };
    });
  }
}
