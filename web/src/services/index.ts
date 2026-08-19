// Phase 1: GeminiService, CompositeSymbolService, ARASAAC/GlobalSymbols/Google-Search clients.

export type { SymbolResult, SymbolService } from './symbolService';
export { ArasaacService } from './arasaacService';
export type { ArasaacPictogram, ArasaacKeyword } from './arasaacService';
export { GlobalSymbolsService } from './globalSymbolsService';
export type { GlobalSymbolResponse, GlobalSymbolPicto } from './globalSymbolsService';
export { GoogleImageService } from './googleImageService';
export type { GoogleImageSearchResponse, GoogleImageItem } from './googleImageService';
export { CompositeSymbolService } from './compositeSymbolService';
export { GeminiService, generateGeminiText } from './geminiService';
export type { GeminiImageInput, AgentAction } from './geminiService';
export {
  PhraseCacheService,
  InMemoryPhraseCacheDao,
  CacheType,
} from './phraseCacheService';
export type { PhraseCacheEntity, PhraseCacheDao, CacheStats, CacheTypeValue } from './phraseCacheService';
export { MorphologyService } from './morphologyService';
export type { MorphologyCategory } from './morphologyService';
export { firestore, storage } from './firebaseConfig';
export { backupBoard, backupBoards, restoreBoards } from './cloudBackupService';
export type { BackupSummary } from './cloudBackupService';
