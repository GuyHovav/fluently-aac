// Port of data/remote/GoogleImageService.kt — near-mechanical translation.
// REST: GET https://www.googleapis.com/customsearch/v1?key=&cx=&q=&searchType=image&num=3&safe=active

import type { SymbolResult, SymbolService } from './symbolService';

export interface GoogleImageItem {
  link: string;
  mime?: string;
  title?: string;
}

export interface GoogleImageSearchResponse {
  items?: GoogleImageItem[];
}

const BASE_URL = 'https://www.googleapis.com/customsearch/';

export class GoogleImageService implements SymbolService {
  private explicitApiKey?: string;
  private explicitSearchEngineId?: string;

  constructor(explicitApiKey?: string, explicitSearchEngineId?: string) {
    this.explicitApiKey = explicitApiKey;
    this.explicitSearchEngineId = explicitSearchEngineId;
  }

  async search(query: string, _language: string): Promise<SymbolResult[]> {
    try {
      const apiKey = this.explicitApiKey ?? import.meta.env.VITE_GOOGLE_SEARCH_API_KEY ?? '';
      const searchEngineId =
        this.explicitSearchEngineId ?? import.meta.env.VITE_GOOGLE_SEARCH_ENGINE_ID ?? '';

      if (!apiKey || !searchEngineId) {
        console.warn('GoogleImageService: API key or Search Engine ID not configured');
        return [];
      }

      // Search with quality modifiers to get cleaner images
      const searchQuery = `${query} icon clipart`;

      const params = new URLSearchParams({
        key: apiKey,
        cx: searchEngineId,
        q: searchQuery,
        searchType: 'image',
        num: '3',
        safe: 'active',
      });

      const response = await fetch(`${BASE_URL}v1?${params.toString()}`);
      if (!response.ok) {
        throw new Error(`GoogleImageService: HTTP ${response.status}`);
      }
      const data = (await response.json()) as GoogleImageSearchResponse;

      // Return all results
      return (
        data.items?.map((item) => ({
          url: item.link,
          label: item.title ?? query,
        })) ?? []
      );
    } catch (e) {
      console.error(`GoogleImageService: Error searching for image: ${query}`, e);
      return [];
    }
  }
}
