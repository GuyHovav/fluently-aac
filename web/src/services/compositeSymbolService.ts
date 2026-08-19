// Port of data/remote/CompositeSymbolService.kt — near-mechanical translation.
//
// A composite symbol service that tries multiple vendors in order.
// If the primary vendor returns no results, it falls back to the next vendor.

import type { SymbolResult, SymbolService } from './symbolService';

export class CompositeSymbolService implements SymbolService {
  private services: SymbolService[];
  private combineResults: boolean;
  private minRequiredResults: number;

  /**
   * @param services List of symbol services to try, in priority order
   * @param combineResults If true, combines results from all services. If false, returns first non-empty result.
   * @param minRequiredResults Stop once at least this many unique results have been accumulated.
   */
  constructor(services: SymbolService[], combineResults: boolean = false, minRequiredResults: number = 1) {
    this.services = services;
    this.combineResults = combineResults;
    this.minRequiredResults = minRequiredResults;
  }

  async search(query: string, language: string): Promise<SymbolResult[]> {
    if (this.combineResults) {
      // Combine results from all services
      const allResults: SymbolResult[] = [];
      for (const service of this.services) {
        try {
          const results = await service.search(query, language);
          allResults.push(...results);
        } catch (e) {
          // Log error but continue to next service
          console.error(e);
        }
      }
      return distinctByUrl(allResults);
    }

    // Try each service until we get enough results
    const accumulatedResults: SymbolResult[] = [];

    for (const service of this.services) {
      try {
        const serviceName = service.constructor.name;
        console.debug(`CompositeSymbolService: Searching '${query}' in ${serviceName}...`);

        const results = await service.search(query, language);
        if (results.length > 0) {
          console.debug(`CompositeSymbolService: Found ${results.length} results in ${serviceName}`);
          accumulatedResults.push(...results);
        } else {
          console.debug(`CompositeSymbolService: ${serviceName} returned empty for '${query}'`);
        }

        // If we have enough results, stop. Check unique URLs to avoid counting duplicates.
        const uniqueCount = distinctByUrl(accumulatedResults).length;
        if (uniqueCount >= this.minRequiredResults) {
          break;
        }
      } catch (e) {
        console.error('CompositeSymbolService: Error in service', e);
      }
    }

    if (accumulatedResults.length === 0) {
      console.warn(`CompositeSymbolService: All services failed for '${query}'`);
    }

    return distinctByUrl(accumulatedResults);
  }
}

function distinctByUrl(results: SymbolResult[]): SymbolResult[] {
  const seen = new Set<string>();
  const out: SymbolResult[] = [];
  for (const r of results) {
    if (!seen.has(r.url)) {
      seen.add(r.url);
      out.push(r);
    }
  }
  return out;
}
