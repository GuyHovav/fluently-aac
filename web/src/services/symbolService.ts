// Port of data/remote/SymbolService.kt — the shared vendor-agnostic symbol search contract
// implemented by ArasaacService, GlobalSymbolsService, GoogleImageService, and composed by
// CompositeSymbolService.

export interface SymbolResult {
  url: string;
  label: string;
}

export interface SymbolService {
  search(query: string, language: string): Promise<SymbolResult[]>;
}
