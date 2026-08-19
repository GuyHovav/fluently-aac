import { create } from 'zustand';

import type { AacButton, Board } from '../models';
import { ButtonAction, createAacButton, createBoard, getTextToSpeak, isLinkToBoardAction } from '../models';
import * as boardRepository from '../data/boardRepository';
import type { AppSettings } from '../data/settingsTypes';
import { useSettingsStore } from './settingsStore';
import { phraseCacheService, localPredictionEngine } from '../appServices';
import { GeminiService } from '../services/geminiService';
import { AiPredictionEngine } from '../nlp/aiPredictionEngine';
import { HybridPredictionEngine } from '../nlp/hybridPredictionEngine';
import { GrammarEngine } from '../nlp/grammarEngine';
import { PronunciationDictionary } from '../nlp/pronunciationDictionary';
import type { PredictionEngine } from '../nlp/predictionEngine';
import { FluentlyTts } from '../plugins/FluentlyTts';
import { AppLauncher } from '../plugins/AppLauncher';
import { ArasaacService, CompositeSymbolService, GlobalSymbolsService, GoogleImageService } from '../services';
import type { AgentAction, GeminiImageInput } from '../services';

// Zustand port of the scope of app/src/main/java/com/example/myaac/viewmodel/BoardViewModel.kt
// needed for Phase 2 ("core communication UI"): board navigation, sentence-bar assembly,
// Speak/ClearSentence/DeleteLastWord/LaunchApp action handling, selection-bar batch actions, and
// word-prediction wiring. Deliberately NOT ported here (later phases per the migration plan):
// caregiver-mode PIN gate (isCaregiverMode is a plain boolean toggle, no PIN check),
// AI board-generation orchestration (createMagicBoard/expandBoard/createMagicScene/createQuickBoard),
// the grammar-correction-undo UI flow (triggerGrammarCheck/applyGrammarCorrection/undoAiCorrection),
// agent-command handling (submitAgentQuery), cloud backup/restore, cache-stats/debug endpoints,
// button editing (updateButton/deleteButton/addButtonToBoard -- Phase 3's EditButtonDialog), and
// syncAppsBoard/syncHomeBoardLinks (tied to Settings-driven app shortcuts, Phase 4 territory).
//
// updateButton/deleteButton/addButtonToBoard (Phase 3's EditButtonDialog) ARE implemented below,
// following the same "look up board by id (currentBoard or boards list), mutate, saveBoard, then
// set({currentBoard: updated}) only if it's the board currently on screen" pattern reorderButtons
// already established above.

const HOME_BOARD_ID = 'home';
const I_WANT_BOARD_ID = 'board_i_want';

function generateId(prefix: string): string {
  return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * Single PronunciationDictionary instance, exported so store/pronunciationStore.ts (the
 * Phase 4 custom-pronunciation persistence layer -- see that module's doc comment for why it
 * lives outside web/src/data) can hydrate/mutate the same instance speakSentence() reads from.
 */
export const pronunciationDictionary = new PronunciationDictionary();

/**
 * Composes the prediction engine to use for the current settings snapshot, mirroring the Kotlin
 * HybridPredictionEngine's "AI engine can be null" constructor parameter: if AI predictions are
 * enabled AND a Gemini API key is configured, compose GeminiService + AiPredictionEngine +
 * HybridPredictionEngine (2s AI timeout, local fallback baked into HybridPredictionEngine
 * itself); otherwise fall back to the shared Dexie-backed localPredictionEngine alone.
 */
function getPredictionEngine(settings: AppSettings): PredictionEngine {
  const apiKey = import.meta.env.VITE_GEMINI_API_KEY;
  if (settings.aiPredictionEnabled && apiKey) {
    const gemini = new GeminiService();
    const aiEngine = new AiPredictionEngine(gemini, phraseCacheService, settings.languageCode);
    return new HybridPredictionEngine(localPredictionEngine, aiEngine, true);
  }
  return localPredictionEngine;
}

/** Minimal offline seed data so Phase 2 has a real board to smoke-test against on first launch. */
async function ensureSeedBoards(): Promise<void> {
  const existing = await boardRepository.getAllBoardsData();
  if (existing.length > 0) return;

  const iWantBoard = createBoard({
    id: I_WANT_BOARD_ID,
    name: 'I Want',
    buttons: [
      createAacButton({ id: `${I_WANT_BOARD_ID}_btn_0`, label: 'want', backgroundColor: 0xffe0e0e0, action: ButtonAction.speak('I want') }),
      createAacButton({ id: `${I_WANT_BOARD_ID}_btn_1`, label: 'eat', backgroundColor: 0xffffe0b2, action: ButtonAction.speak('eat') }),
      createAacButton({ id: `${I_WANT_BOARD_ID}_btn_2`, label: 'drink', backgroundColor: 0xffbbdefb, action: ButtonAction.speak('drink') }),
      createAacButton({ id: `${I_WANT_BOARD_ID}_btn_3`, label: 'play', backgroundColor: 0xffc5cae9, action: ButtonAction.speak('play') }),
      createAacButton({ id: `${I_WANT_BOARD_ID}_btn_4`, label: 'sleep', backgroundColor: 0xffbbdefb, action: ButtonAction.speak('sleep') }),
      createAacButton({ id: `${I_WANT_BOARD_ID}_btn_back`, label: 'Home', backgroundColor: 0xffffcc80, action: ButtonAction.linkToBoard(HOME_BOARD_ID) }),
    ],
  });

  const homeBoard = createBoard({
    id: HOME_BOARD_ID,
    name: 'Home',
    buttons: [
      createAacButton({ id: `${HOME_BOARD_ID}_btn_0`, label: 'I Want', backgroundColor: 0xffe3f2fd, action: ButtonAction.linkToBoard(I_WANT_BOARD_ID) }),
      createAacButton({ id: `${HOME_BOARD_ID}_btn_1`, label: 'Yes', backgroundColor: 0xffc8e6c9, action: ButtonAction.speak('Yes') }),
      createAacButton({ id: `${HOME_BOARD_ID}_btn_2`, label: 'No', backgroundColor: 0xffffcdd2, action: ButtonAction.speak('No') }),
      createAacButton({ id: `${HOME_BOARD_ID}_btn_3`, label: 'help', backgroundColor: 0xffb2dfdb, action: ButtonAction.speak('help') }),
      createAacButton({ id: `${HOME_BOARD_ID}_btn_4`, label: 'please', backgroundColor: 0xfffff9c4, action: ButtonAction.speak('please') }),
      createAacButton({ id: `${HOME_BOARD_ID}_btn_5`, label: 'thank you', backgroundColor: 0xfffff9c4, action: ButtonAction.speak('thank you') }),
      createAacButton({ id: `${HOME_BOARD_ID}_btn_6`, label: 'Clear', backgroundColor: 0xffffffff, action: ButtonAction.clearSentence }),
    ],
  });

  await boardRepository.saveBoard(iWantBoard);
  await boardRepository.saveBoard(homeBoard);
}

let predictionTimer: ReturnType<typeof setTimeout> | null = null;
function clearPredictionDebounce(): void {
  if (predictionTimer != null) {
    clearTimeout(predictionTimer);
    predictionTimer = null;
  }
}

// ===== Phase 4: board-authoring helpers, ported from BoardViewModel.kt's private
// searchSymbols()/createButtonWithSymbol() (used by createNewBoard/createMagicBoard/expandBoard/
// createQuickBoard) =====

/** Mirrors BoardViewModel.kt's private `searchSymbols(query)`: composite vendor search, first result URL. */
async function searchSymbols(query: string, settings: AppSettings): Promise<string | null> {
  if (query.trim().length === 0) return null;
  const locale = settings.languageCode === 'iw' ? 'he' : 'en';
  const services =
    settings.symbolLibrary === 'MULBERRY'
      ? [new GlobalSymbolsService('mulberry'), new ArasaacService(), new GoogleImageService()]
      : [
          new ArasaacService(),
          new GlobalSymbolsService('arasaac'),
          new GlobalSymbolsService('mulberry'),
          new GoogleImageService(),
        ];
  try {
    const results = await new CompositeSymbolService(services).search(query, locale);
    return results[0]?.url ?? null;
  } catch (e) {
    console.error('boardStore: searchSymbols failed', e);
    return null;
  }
}

/** Mirrors BoardViewModel.kt's private `createButtonWithSymbol(...)`. */
async function createButtonWithSymbol(
  boardId: string,
  index: number,
  label: string,
  color: number,
  action: AacButton['action'],
  settings: AppSettings,
  searchTerm?: string,
): Promise<AacButton> {
  const query = searchTerm ?? label;
  const iconPath = query.length > 0 ? await searchSymbols(query, settings) : null;
  return createAacButton({ id: `${boardId}_btn_${index}`, label, backgroundColor: color, action, iconPath });
}

/** Parses a `data:mime;base64,...` string into GeminiService's image input shape. */
function dataUrlToGeminiInput(dataUrl: string): GeminiImageInput | null {
  const match = /^data:([^;]+);base64,([\s\S]*)$/.exec(dataUrl);
  if (!match) return null;
  return { mimeType: match[1], base64Data: match[2] };
}

function capitalizeWord(text: string): string {
  return text.length === 0 ? text : text.charAt(0).toUpperCase() + text.slice(1);
}

// ===== Phase 4: AI grammar-correction-undo flow, ported from BoardViewModel.kt's
// triggerGrammarCheck/applyPendingGrammarCorrection/applyGrammarCorrection/undoAiCorrection =====

let grammarCheckTimer: ReturnType<typeof setTimeout> | null = null;
/** Bumped on every triggerGrammarCheck() call; a pending run bails out if it's been superseded --
 *  stands in for the Kotlin source's `grammarCheckJob?.cancel()` (no AbortController is wired up
 *  for the underlying fetch, so this is a cooperative-cancellation token instead). */
let grammarCheckToken = 0;
/** Mirrors the Kotlin source's `applyCorrectionWhenReady` flag. */
let applyCorrectionWhenReady = false;

function clearGrammarDebounce(): void {
  if (grammarCheckTimer != null) {
    clearTimeout(grammarCheckTimer);
    grammarCheckTimer = null;
  }
  grammarCheckToken += 1;
}

/** Mirrors the private `applyGrammarCorrection(original, newText)` -- reconstructs the sentence
 *  from the corrected text, preserving icons for words that already existed, auto-speaks the
 *  result, and schedules the "Undo AI" banner to auto-hide after 5s. */
async function performGrammarCorrection(
  get: () => BoardStore,
  set: (partial: Partial<BoardStore>) => void,
  original: AacButton[],
  newText: string,
): Promise<void> {
  const words = newText.split(/\s+/).filter((w) => w.length > 0);
  const newSentence = words.map((word) => {
    const existing = original.find((b) => getTextToSpeak(b).toLowerCase() === word.toLowerCase());
    if (existing) return existing;
    return createAacButton({
      id: `grammar_${generateId('w')}`,
      label: word,
      backgroundColor: 0xffeff0f1, // Light gray for auto-added words, matching the Kotlin source.
      action: ButtonAction.speak(word),
    });
  });

  set({
    sentence: newSentence,
    originalSentence: original,
    showUndoAi: true,
    pendingGrammarCorrection: null,
    hasPendingCorrection: false,
    isGrammarLoading: false,
  });

  void get().speakSentence();

  setTimeout(() => {
    // Only hide the banner -- keep whatever sentence is on screen by then (matches Kotlin: "Keep
    // sentence, just hide undo").
    set({ showUndoAi: false });
  }, 5000);
}

export interface BoardStore {
  currentBoard: Board | null;
  /** Mirrors BoardViewModel's `cachedBoards` -- kept in sync via boardRepository.getAllBoards$(). */
  boards: Board[];
  backStack: string[];
  sentence: AacButton[];
  isCaregiverMode: boolean;
  selectedButtonIds: Set<string>;
  predictions: string[];
  isPredictionLoading: boolean;
  isLoading: boolean;
  isInitialized: boolean;

  /** Seeds default boards on first run, subscribes to the boards liveQuery, and loads Home. */
  init: () => Promise<void>;

  navigateToBoard: (boardId: string) => Promise<void>;
  navigateBack: () => Promise<boolean>;

  /** Dispatches on button.action.type -- the ButtonAction discriminated union's single entry point. */
  tapButton: (button: AacButton) => void;
  undoLastWord: () => void;
  clearSentence: () => void;
  /** Assembles the sentence, applies nikud substitution for Hebrew, and calls FluentlyTts.speak(). */
  speakSentence: () => Promise<void>;

  reorderButtons: (boardId: string, newOrder: AacButton[]) => Promise<void>;

  /** Replaces the button matching `button.id` on the given board (Phase 3's EditButtonDialog "Save"). */
  updateButton: (boardId: string, button: AacButton) => Promise<void>;
  /** Removes the button with `buttonId` from the given board (Phase 3's EditButtonDialog "Delete"). */
  deleteButton: (boardId: string, buttonId: string) => Promise<void>;
  /** Appends a new button to the given board. */
  addButtonToBoard: (boardId: string, button: AacButton) => Promise<void>;

  updatePredictions: (instant?: boolean) => void;
  onPredictionSelected: (word: string) => void;

  setCaregiverMode: (enabled: boolean) => void;
  toggleButtonSelection: (buttonId: string) => void;
  clearSelection: () => void;
  deleteSelectedButtons: () => Promise<void>;
  toggleHideSelectedButtons: () => Promise<void>;
  duplicateSelectedButtons: () => Promise<void>;
  moveSelectedButtonsToBoard: (targetBoardId: string) => Promise<void>;

  // ===== Phase 4: board CRUD + AI board-generation orchestration (SidebarContent's
  // CreateBoardDialog/EditBoardDialog/VisualSceneDialog/QuickBoardDialog) =====

  /** Plain empty board (SidebarContent's CreateBoardDialog with a blank "topic" field). */
  createBoard: (name: string) => Promise<string>;
  /** Renames/re-icons a board (EditBoardDialog's "Save"). Mirrors BoardViewModel.kt's `updateBoard`. */
  updateBoard: (board: Board) => Promise<void>;
  deleteBoard: (boardId: string) => Promise<void>;
  /** AI-generated vocabulary board for a topic. Mirrors `createMagicBoard`. */
  createMagicBoard: (name: string, topic: string, negativeConstraints?: string | null) => Promise<string>;
  /** Adds more AI-generated vocabulary to an existing board. Mirrors `expandBoard`. */
  expandBoard: (board: Board, itemCount: number) => Promise<void>;
  /** Visual Scene board: one background photo + AI-identified object hotspots. Mirrors `createMagicScene`. */
  createMagicScene: (name: string, imageDataUrl: string) => Promise<string>;
  /** Quick Board: 1-5 photos -> most-frequent AI-identified objects become buttons. Mirrors `createQuickBoard`. */
  createQuickBoard: (name: string, imageDataUrls: string[]) => Promise<string>;
  /** Mirrors `suggestBoardName` (QuickBoardDialog's "suggest name" AI button). */
  suggestBoardName: (imageDataUrls: string[]) => Promise<string>;

  // ===== Phase 4: AI grammar-correction-undo flow (SentenceBar's "magic wand" + Undo-AI banner) =====

  originalSentence: AacButton[] | null;
  showUndoAi: boolean;
  isGrammarLoading: boolean;
  pendingGrammarCorrection: string | null;
  hasPendingCorrection: boolean;
  /** Debounced (500ms) unless force=true. Mirrors the private `triggerGrammarCheck(force)`. */
  triggerGrammarCheck: (force?: boolean) => void;
  /** Applies the pending correction now, or forces+applies one if none is pending yet. Mirrors
   *  the public `applyPendingGrammarCorrection()` (the SentenceBar magic-wand button's handler). */
  applyGrammarCorrection: () => void;
  undoAiCorrection: () => void;

  // ===== Phase 4: in-app AI assistant ("Fluently") =====

  agentResponse: string | null;
  isAgentProcessing: boolean;
  submitAgentQuery: (query: string) => Promise<void>;
  clearAgentResponse: () => void;

  // ===== Phase 4: Settings-driven board sync (SettingsScreen's app-shortcuts editor; boards list changes) =====

  /** Rebuilds the "board_apps" board's buttons from settingsStore's app shortcuts, if that board exists. */
  syncAppsBoard: () => Promise<void>;
  /** Keeps the Home board's board-link buttons in sync with the current boards list. Mirrors
   *  `syncHomeBoardLinks` -- called automatically after every boards$ update, see init() below. */
  syncHomeBoardLinks: () => Promise<void>;
}

export const useBoardStore = create<BoardStore>((set, get) => ({
  currentBoard: null,
  boards: [],
  backStack: [],
  sentence: [],
  isCaregiverMode: false,
  selectedButtonIds: new Set<string>(),
  predictions: [],
  isPredictionLoading: false,
  isLoading: false,
  isInitialized: false,
  originalSentence: null,
  showUndoAi: false,
  isGrammarLoading: false,
  pendingGrammarCorrection: null,
  hasPendingCorrection: false,
  agentResponse: null,
  isAgentProcessing: false,

  init: async () => {
    if (get().isInitialized || get().isLoading) return;
    set({ isLoading: true });
    try {
      await ensureSeedBoards();

      boardRepository.getAllBoards$().subscribe({
        next: (boards) => {
          set({ boards });
          // Mirrors BoardViewModel.kt's init block: re-sync Home's board-link buttons every time
          // the boards list changes (including this call's own writes -- syncHomeBoardLinks has a
          // "nothing to do" early-return so this converges instead of looping).
          void get().syncHomeBoardLinks();
        },
        error: (err) => console.error('boardStore: allBoards$ subscription error', err),
      });

      const home = await boardRepository.getBoardById(HOME_BOARD_ID);
      set({ currentBoard: home ?? null });
      get().updatePredictions(true);
    } finally {
      set({ isLoading: false, isInitialized: true });
    }
  },

  navigateToBoard: async (boardId) => {
    const state = get();
    const board = state.boards.find((b) => b.id === boardId) ?? (await boardRepository.getBoardById(boardId));
    if (!board) return;

    const currentId = state.currentBoard?.id;
    const backStack = currentId != null && currentId !== boardId ? [...state.backStack, currentId] : state.backStack;

    set({ currentBoard: board, backStack, selectedButtonIds: new Set() });
    get().updatePredictions(true);
  },

  navigateBack: async () => {
    const { backStack } = get();
    if (backStack.length === 0) return false;

    const previousId = backStack[backStack.length - 1];
    const newStack = backStack.slice(0, -1);
    const board = await boardRepository.getBoardById(previousId);

    if (board) {
      set({ currentBoard: board, backStack: newStack, selectedButtonIds: new Set() });
      get().updatePredictions(true);
      return true;
    }
    // Board was deleted since it was pushed -- drop it and try the next one, matching the
    // Kotlin implementation's recursive fallback.
    set({ backStack: newStack });
    return get().navigateBack();
  },

  tapButton: (button) => {
    const action = button.action;
    switch (action.type) {
      case 'speak': {
        set({
          sentence: [...get().sentence, button],
          // Reset AI grammar state on user input, mirroring addToSentence()'s "Reset AI state".
          showUndoAi: false,
          originalSentence: null,
          pendingGrammarCorrection: null,
          hasPendingCorrection: false,
          isGrammarLoading: false,
        });
        applyCorrectionWhenReady = false;
        get().updatePredictions(false);
        get().triggerGrammarCheck();
        break;
      }
      case 'link': {
        void get().navigateToBoard(action.boardId);
        break;
      }
      case 'launch_app': {
        void AppLauncher.launchApp({ packageName: action.packageName }).catch((err) => {
          console.error('boardStore: AppLauncher.launchApp failed', err);
        });
        break;
      }
      case 'clear': {
        get().clearSentence();
        break;
      }
      case 'delete': {
        get().undoLastWord();
        break;
      }
    }
  },

  undoLastWord: () => {
    const { sentence } = get();
    if (sentence.length === 0) return;
    set({
      sentence: sentence.slice(0, -1),
      showUndoAi: false,
      originalSentence: null,
      pendingGrammarCorrection: null,
      hasPendingCorrection: false,
    });
    applyCorrectionWhenReady = false;
    get().updatePredictions(false);
    get().triggerGrammarCheck();
  },

  clearSentence: () => {
    const { sentence } = get();
    if (sentence.length > 0) {
      const settings = useSettingsStore.getState().settings;
      void getPredictionEngine(settings).recordSentence(sentence.map(getTextToSpeak));
    }
    clearPredictionDebounce();
    clearGrammarDebounce();
    applyCorrectionWhenReady = false;
    set({
      sentence: [],
      predictions: [],
      isPredictionLoading: false,
      showUndoAi: false,
      originalSentence: null,
      pendingGrammarCorrection: null,
      hasPendingCorrection: false,
    });
  },

  speakSentence: async () => {
    const { sentence } = get();
    if (sentence.length === 0) return;

    const settings = useSettingsStore.getState().settings;
    const words = sentence.map(getTextToSpeak);
    const displayText = GrammarEngine.fixSentence(words, settings.languageCode);
    // Apply pronunciation (nikud) corrections right before handing off to native TTS -- mirrors
    // MainActivity.kt's speak(): `app.pronunciationRepository.dictionary.applyCorrections(text, languageCode)`.
    const textToSpeak = pronunciationDictionary.applyCorrections(displayText, settings.languageCode);

    await FluentlyTts.initialize();
    await FluentlyTts.setLanguage({ languageCode: settings.languageCode });
    await FluentlyTts.setRate({ rate: settings.ttsRate });
    await FluentlyTts.speak({ text: textToSpeak, queueMode: 'flush' });
  },

  reorderButtons: async (boardId, newOrder) => {
    const state = get();
    const board = state.currentBoard?.id === boardId ? state.currentBoard : state.boards.find((b) => b.id === boardId);
    if (!board) return;

    const updated: Board = { ...board, buttons: newOrder };
    await boardRepository.saveBoard(updated);
    if (state.currentBoard?.id === boardId) {
      set({ currentBoard: updated });
    }
  },

  updateButton: async (boardId, button) => {
    const state = get();
    const board = state.currentBoard?.id === boardId ? state.currentBoard : state.boards.find((b) => b.id === boardId);
    if (!board) return;

    const updated: Board = { ...board, buttons: board.buttons.map((b) => (b.id === button.id ? button : b)) };
    await boardRepository.saveBoard(updated);
    if (state.currentBoard?.id === boardId) {
      set({ currentBoard: updated });
    }
  },

  deleteButton: async (boardId, buttonId) => {
    const state = get();
    const board = state.currentBoard?.id === boardId ? state.currentBoard : state.boards.find((b) => b.id === boardId);
    if (!board) return;

    const updated: Board = { ...board, buttons: board.buttons.filter((b) => b.id !== buttonId) };
    await boardRepository.saveBoard(updated);
    if (state.currentBoard?.id === boardId) {
      const selectedButtonIds = new Set(state.selectedButtonIds);
      selectedButtonIds.delete(buttonId);
      set({ currentBoard: updated, selectedButtonIds });
    }
  },

  addButtonToBoard: async (boardId, button) => {
    const state = get();
    const board = state.currentBoard?.id === boardId ? state.currentBoard : state.boards.find((b) => b.id === boardId);
    if (!board) return;

    const updated: Board = { ...board, buttons: [...board.buttons, button] };
    await boardRepository.saveBoard(updated);
    if (state.currentBoard?.id === boardId) {
      set({ currentBoard: updated });
    }
  },

  updatePredictions: (instant = false) => {
    clearPredictionDebounce();
    const settings = useSettingsStore.getState().settings;

    if (!settings.predictionEnabled) {
      set({ predictions: [], isPredictionLoading: false });
      return;
    }

    const run = async (): Promise<void> => {
      set({ isPredictionLoading: true });
      try {
        const context = get().sentence.map(getTextToSpeak);
        const board = get().currentBoard;
        const isHomeBoard = board?.id === HOME_BOARD_ID;
        const boardContext = isHomeBoard ? null : (board?.name ?? null);

        let predictions: string[];
        if (context.length === 0 && isHomeBoard) {
          predictions = HybridPredictionEngine.getStarterWords(settings.languageCode);
        } else if (context.length === 1 && isHomeBoard) {
          const verbs = HybridPredictionEngine.getVerbsForPronoun(context[0], settings.languageCode);
          predictions = verbs.length > 0 ? verbs : await getPredictionEngine(settings).predict(context, settings.predictionCount, boardContext);
        } else if (context.length >= 2 && isHomeBoard) {
          const phrase = context.join(' ');
          const userBoardNames = get()
            .boards.filter((b) => b.id !== HOME_BOARD_ID)
            .map((b) => b.name);
          const completions = HybridPredictionEngine.getCompletionsForPhrase(phrase, settings.languageCode, userBoardNames);
          predictions =
            completions.length > 0 ? completions : await getPredictionEngine(settings).predict(context, settings.predictionCount, boardContext);
        } else {
          predictions = await getPredictionEngine(settings).predict(context, settings.predictionCount, boardContext);
        }

        // Only show words not already visible as buttons on the current board.
        const visibleWords = (board?.buttons ?? []).map((b) => b.label.toLowerCase());
        const lastWord = context[context.length - 1]?.toLowerCase();
        const smartPredictions = predictions
          .filter((p) => !visibleWords.includes(p.toLowerCase()))
          .filter((p) => {
            if (lastWord == null) return true;
            // Filter the redundant "to" GrammarEngine.fixSentence would insert on its own.
            const isRedundantTo = p === 'to' && (GrammarEngine.isVerb(lastWord) || lastWord === 'go' || lastWord === 'come');
            return !isRedundantTo;
          });

        set({ predictions: smartPredictions, isPredictionLoading: false });
      } catch (err) {
        console.error('boardStore: error updating predictions', err);
        set({ isPredictionLoading: false });
      }
    };

    if (instant) {
      void run();
    } else {
      predictionTimer = setTimeout(() => void run(), 300);
    }
  },

  onPredictionSelected: (word) => {
    const button = createAacButton({
      id: generateId('prediction'),
      label: word,
      speechText: word,
      backgroundColor: 0xffe8f5e9,
      action: ButtonAction.speak(word),
    });
    set({
      sentence: [...get().sentence, button],
      showUndoAi: false,
      originalSentence: null,
      pendingGrammarCorrection: null,
      hasPendingCorrection: false,
    });
    applyCorrectionWhenReady = false;
    get().updatePredictions(false);
    get().triggerGrammarCheck();

    const settings = useSettingsStore.getState().settings;
    if (settings.learnFromUsage) {
      void getPredictionEngine(settings).recordUsage(word);
    }
  },

  setCaregiverMode: (enabled) => {
    set({ isCaregiverMode: enabled, selectedButtonIds: enabled ? get().selectedButtonIds : new Set() });
  },

  toggleButtonSelection: (buttonId) => {
    const current = get().selectedButtonIds;
    const next = new Set(current);
    if (next.has(buttonId)) {
      next.delete(buttonId);
    } else {
      next.add(buttonId);
    }
    set({ selectedButtonIds: next });
  },

  clearSelection: () => set({ selectedButtonIds: new Set() }),

  deleteSelectedButtons: async () => {
    const { currentBoard, selectedButtonIds } = get();
    if (!currentBoard || selectedButtonIds.size === 0) return;

    const updated: Board = { ...currentBoard, buttons: currentBoard.buttons.filter((b) => !selectedButtonIds.has(b.id)) };
    await boardRepository.saveBoard(updated);
    set({ currentBoard: updated, selectedButtonIds: new Set() });
  },

  toggleHideSelectedButtons: async () => {
    const { currentBoard, selectedButtonIds } = get();
    if (!currentBoard || selectedButtonIds.size === 0) return;

    const updated: Board = {
      ...currentBoard,
      buttons: currentBoard.buttons.map((b) => (selectedButtonIds.has(b.id) ? { ...b, hidden: !b.hidden } : b)),
    };
    await boardRepository.saveBoard(updated);
    set({ currentBoard: updated, selectedButtonIds: new Set() });
  },

  duplicateSelectedButtons: async () => {
    const { currentBoard, selectedButtonIds } = get();
    if (!currentBoard || selectedButtonIds.size === 0) return;

    const duplicates = currentBoard.buttons
      .filter((b) => selectedButtonIds.has(b.id))
      .map((b) => ({ ...b, id: `${currentBoard.id}_btn_${generateId('dup')}` }));

    const updated: Board = { ...currentBoard, buttons: [...currentBoard.buttons, ...duplicates] };
    await boardRepository.saveBoard(updated);
    set({ currentBoard: updated, selectedButtonIds: new Set() });
  },

  moveSelectedButtonsToBoard: async (targetBoardId) => {
    const { currentBoard, selectedButtonIds } = get();
    if (!currentBoard || selectedButtonIds.size === 0 || currentBoard.id === targetBoardId) return;

    const targetBoard = await boardRepository.getBoardById(targetBoardId);
    if (!targetBoard) return;

    const movedButtons = currentBoard.buttons
      .filter((b) => selectedButtonIds.has(b.id))
      .map((b) => ({ ...b, id: `${targetBoardId}_btn_${generateId('moved')}` }));

    const updatedCurrent: Board = { ...currentBoard, buttons: currentBoard.buttons.filter((b) => !selectedButtonIds.has(b.id)) };
    const updatedTarget: Board = { ...targetBoard, buttons: [...targetBoard.buttons, ...movedButtons] };

    await boardRepository.saveBoard(updatedCurrent);
    await boardRepository.saveBoard(updatedTarget);
    set({ currentBoard: updatedCurrent, selectedButtonIds: new Set() });
  },

  // ===== Phase 4: board CRUD + AI board-generation orchestration =====

  createBoard: async (name) => {
    set({ isLoading: true });
    try {
      const settings = useSettingsStore.getState().settings;
      const newId = generateId('board');
      const iconPath = await searchSymbols(name, settings);
      const newBoard = createBoard({ id: newId, name, iconPath });
      await boardRepository.saveBoard(newBoard);
      set({ currentBoard: newBoard });
      return newId;
    } finally {
      set({ isLoading: false });
    }
  },

  updateBoard: async (board) => {
    await boardRepository.saveBoard(board);
    if (get().currentBoard?.id === board.id) {
      set({ currentBoard: board });
    }
  },

  deleteBoard: async (boardId) => {
    set({ isLoading: true });
    try {
      await boardRepository.deleteBoard(boardId);
      if (get().currentBoard?.id === boardId) {
        await get().navigateToBoard(HOME_BOARD_ID);
      }
    } finally {
      set({ isLoading: false });
    }
  },

  createMagicBoard: async (name, topic, negativeConstraints = null) => {
    set({ isLoading: true });
    const newId = generateId('board');
    try {
      const settings = useSettingsStore.getState().settings;
      const gemini = new GeminiService();
      const words = await gemini.generateBoard(topic, settings.languageCode, settings.itemsToGenerate, negativeConstraints);

      const buttons = await Promise.all(
        words.map((word, index) =>
          createButtonWithSymbol(newId, index, word, 0xfffff9c4, ButtonAction.speak(word), settings, word),
        ),
      );

      const iconPath = await searchSymbols(topic, settings);
      const newBoard = createBoard({ id: newId, name, buttons, iconPath });
      await boardRepository.saveBoard(newBoard);
      set({ currentBoard: newBoard });
      return newId;
    } finally {
      set({ isLoading: false });
    }
  },

  expandBoard: async (board, itemCount) => {
    const settings = useSettingsStore.getState().settings;
    set({ isLoading: true });
    try {
      if (board.buttons.length >= settings.maxBoardCapacity) return;
      const itemsToAdd = Math.min(itemCount, settings.maxBoardCapacity - board.buttons.length);
      if (itemsToAdd <= 0) return;

      const existingWords = board.buttons.map((b) => b.label).filter((l) => l.length > 0);
      const gemini = new GeminiService();
      const newWords = await gemini.generateMoreItems(board.name, existingWords, itemsToAdd, settings.languageCode);
      if (newWords.length === 0) return;

      // Calculate available "_btn_N" indices to avoid colliding with existing buttons, mirroring
      // the Kotlin source's usedIndices/availableIndices scan.
      const usedIndices = new Set(
        board.buttons
          .map((b) => {
            const match = /_btn_(\d+)$/.exec(b.id);
            return match ? Number(match[1]) : null;
          })
          .filter((n): n is number => n !== null),
      );
      const availableIndices: number[] = [];
      let candidate = 0;
      while (availableIndices.length < newWords.length) {
        if (!usedIndices.has(candidate)) availableIndices.push(candidate);
        candidate += 1;
      }

      const newButtons = await Promise.all(
        newWords.map((word, i) =>
          createButtonWithSymbol(board.id, availableIndices[i], word, 0xfffff9c4, ButtonAction.speak(word), settings, word),
        ),
      );

      const updated: Board = { ...board, buttons: [...board.buttons, ...newButtons] };
      await boardRepository.saveBoard(updated);
      if (get().currentBoard?.id === board.id) {
        set({ currentBoard: updated });
      }
    } catch (e) {
      console.error('boardStore: expandBoard failed', e);
    } finally {
      set({ isLoading: false });
    }
  },

  createMagicScene: async (name, imageDataUrl) => {
    const input = dataUrlToGeminiInput(imageDataUrl);
    if (!input) return '';

    set({ isLoading: true });
    try {
      const gemini = new GeminiService();
      const objects = await gemini.identifyMultipleItems(input);
      if (objects.length === 0) return '';

      const newId = generateId('scene');
      const buttons = objects.map((obj, index) =>
        createAacButton({
          id: `${newId}_btn_${index}`,
          label: obj.name,
          backgroundColor: 0x00ffffff, // Transparent -- hotspots render over the background photo.
          action: ButtonAction.speak(obj.name),
          boundingBox: obj.boundingBox,
        }),
      );

      const newBoard = createBoard({ id: newId, name, buttons, backgroundImagePath: imageDataUrl });
      await boardRepository.saveBoard(newBoard);
      set({ currentBoard: newBoard });
      return newId;
    } finally {
      set({ isLoading: false });
    }
  },

  createQuickBoard: async (name, imageDataUrls) => {
    set({ isLoading: true });
    try {
      const gemini = new GeminiService();
      const frequency = new Map<string, number>();

      for (const dataUrl of imageDataUrls) {
        const input = dataUrlToGeminiInput(dataUrl);
        if (!input) continue;
        const objects = await gemini.identifyMultipleItems(input);
        for (const obj of objects) {
          const key = obj.name.toLowerCase().trim();
          frequency.set(key, (frequency.get(key) ?? 0) + 1);
        }
      }

      if (frequency.size === 0) return '';

      const settings = useSettingsStore.getState().settings;
      const sortedLabels = [...frequency.entries()]
        .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
        .slice(0, settings.itemsToGenerate)
        .map(([key]) => capitalizeWord(key));

      const newId = generateId('quick');
      const buttons = await Promise.all(
        sortedLabels.map((label, index) =>
          createButtonWithSymbol(newId, index, label, 0xfffff9c4, ButtonAction.speak(label), settings, label),
        ),
      );

      const newBoard = createBoard({ id: newId, name, buttons });
      await boardRepository.saveBoard(newBoard);
      set({ currentBoard: newBoard });
      return newId;
    } finally {
      set({ isLoading: false });
    }
  },

  suggestBoardName: async (imageDataUrls) => {
    const inputs = imageDataUrls.map(dataUrlToGeminiInput).filter((x): x is GeminiImageInput => x !== null);
    if (inputs.length === 0) return '';
    const settings = useSettingsStore.getState().settings;
    const gemini = new GeminiService();
    try {
      return await gemini.suggestBoardName(inputs, settings.languageCode);
    } catch (e) {
      console.error('boardStore: suggestBoardName failed', e);
      return '';
    }
  },

  // ===== Phase 4: AI grammar-correction-undo flow =====

  triggerGrammarCheck: (force = false) => {
    clearGrammarDebounce();
    const token = grammarCheckToken;
    const { sentence } = get();

    if (sentence.length === 0) {
      set({ showUndoAi: false, originalSentence: null, pendingGrammarCorrection: null, hasPendingCorrection: false });
      return;
    }

    const settings = useSettingsStore.getState().settings;

    const run = async (): Promise<void> => {
      const currentSentence = get().sentence;
      const text = currentSentence.map(getTextToSpeak).join(' ');
      set({ isGrammarLoading: true });
      try {
        const gemini = new GeminiService();
        const corrected = await gemini.correctGrammar(text, settings.languageCode, phraseCacheService);

        // Bail out if superseded (user kept typing / a newer check started) or the sentence
        // changed while this request was in flight -- mirrors the Kotlin source's job-cancellation
        // + "sentence == currentSentence" guard.
        if (token !== grammarCheckToken || get().sentence !== currentSentence) {
          return;
        }

        if (corrected != null && corrected.trim().toLowerCase() !== text.trim().toLowerCase()) {
          if (settings.autoGrammarCheck || applyCorrectionWhenReady) {
            applyCorrectionWhenReady = false;
            await performGrammarCorrection(get, set, currentSentence, corrected);
          } else {
            set({ pendingGrammarCorrection: corrected, hasPendingCorrection: true, isGrammarLoading: false });
          }
        } else {
          set({ isGrammarLoading: false, pendingGrammarCorrection: null, hasPendingCorrection: false });
        }
      } catch (e) {
        console.error('boardStore: triggerGrammarCheck failed', e);
        set({ isGrammarLoading: false });
      }
    };

    if (force) {
      void run();
    } else {
      grammarCheckTimer = setTimeout(() => void run(), 500);
    }
  },

  applyGrammarCorrection: () => {
    const { pendingGrammarCorrection, sentence, isGrammarLoading } = get();
    if (pendingGrammarCorrection != null) {
      void performGrammarCorrection(get, set, sentence, pendingGrammarCorrection);
    } else if (isGrammarLoading) {
      applyCorrectionWhenReady = true;
    } else {
      applyCorrectionWhenReady = true;
      get().triggerGrammarCheck(true);
    }
  },

  undoAiCorrection: () => {
    const { originalSentence } = get();
    if (originalSentence != null) {
      set({ sentence: originalSentence, originalSentence: null, showUndoAi: false });
    }
  },

  // ===== Phase 4: in-app AI assistant ("Fluently") =====

  submitAgentQuery: async (query) => {
    set({ isAgentProcessing: true, agentResponse: null });
    try {
      const gemini = new GeminiService();
      const action: AgentAction = await gemini.parseAgentCommand(query);

      switch (action.type) {
        case 'CreateBoard': {
          set({ agentResponse: `Creating board '${action.topic}'...` });
          await get().createMagicBoard(action.topic, action.topic, action.negativeConstraints);
          set({ agentResponse: 'Board created!' });
          break;
        }
        case 'AnswerQuestion': {
          const answer = await gemini.answerQuestion(action.question);
          set({ agentResponse: answer });
          break;
        }
        case 'Unknown': {
          // Fallback to Q&A if intent is unclear, mirroring the Kotlin source.
          const answer = await gemini.answerQuestion(query);
          set({ agentResponse: answer });
          break;
        }
      }
    } catch (e) {
      set({ agentResponse: `Error: ${e instanceof Error ? e.message : String(e)}` });
    } finally {
      set({ isAgentProcessing: false });
    }
  },

  clearAgentResponse: () => set({ agentResponse: null }),

  // ===== Phase 4: Settings-driven board sync =====

  syncAppsBoard: async () => {
    const appsId = 'board_apps';
    const appsBoard = await boardRepository.getBoardById(appsId);
    if (!appsBoard) return; // No Apps board exists in this port's seed data -- safe no-op, matching Kotlin's guard.

    const shortcuts = useSettingsStore.getState().getAppShortcuts();
    const appButtons = shortcuts.map((shortcut, index) =>
      createAacButton({
        id: `${appsId}_app_${index}`,
        label: shortcut.appName,
        backgroundColor: 0xfffff9c4,
        action: ButtonAction.launchApp(shortcut.packageName),
      }),
    );
    appButtons.push(
      createAacButton({
        id: `${appsId}_btn_15`,
        label: 'Back Home',
        backgroundColor: 0xffffcc80,
        action: ButtonAction.linkToBoard(HOME_BOARD_ID),
      }),
    );

    const updated: Board = { ...appsBoard, buttons: appButtons };
    await boardRepository.saveBoard(updated);
    if (get().currentBoard?.id === appsId) {
      set({ currentBoard: updated });
    }
  },

  syncHomeBoardLinks: async () => {
    const homeBoard = await boardRepository.getBoardById(HOME_BOARD_ID);
    if (!homeBoard) return;

    const allBoards = get().boards;
    const validBoardIds = new Set(allBoards.map((b) => b.id));

    const preservedButtons = homeBoard.buttons.filter((button) =>
      isLinkToBoardAction(button.action) ? validBoardIds.has(button.action.boardId) : true,
    );

    const existingLinkTargets = new Set(
      preservedButtons
        .map((b) => b.action)
        .filter(isLinkToBoardAction)
        .map((action) => action.boardId),
    );

    const missingBoards = allBoards.filter(
      (board) => board.id !== HOME_BOARD_ID && board.id !== 'board_you' && board.id !== 'board_apps' && !existingLinkTargets.has(board.id),
    );

    if (missingBoards.length === 0 && preservedButtons.length === homeBoard.buttons.length) {
      return;
    }

    const newLinkButtons = missingBoards.map((board) =>
      createAacButton({
        id: `link_${HOME_BOARD_ID}_board_${board.id}`,
        label: board.name,
        backgroundColor: 0xffe3f2fd,
        action: ButtonAction.linkToBoard(board.id),
        iconPath: board.iconPath,
      }),
    );

    const newButtonList = [...preservedButtons, ...newLinkButtons];
    const updated: Board = { ...homeBoard, buttons: newButtonList };
    await boardRepository.saveBoard(updated);
    if (get().currentBoard?.id === HOME_BOARD_ID) {
      set({ currentBoard: updated });
    }
  },
}));
