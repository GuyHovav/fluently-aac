import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { AacButton } from '../models';
import { getTextToSpeak } from '../models';
import { GrammarEngine } from '../nlp/grammarEngine';
import { MorphologyService } from '../services';
import { useBoardStore } from '../store/boardStore';
import { useSettingsStore } from '../store/settingsStore';
import { GrammarPopup } from './GrammarPopup';
import { PredictionStrip } from './PredictionStrip';
import './communicationUI.css';

// React port of ui/components/SentenceBar.kt, wired directly to boardStore/settingsStore.
//
// Phase 4: adds the two pieces the Phase 2 port's doc comment deferred --
// - The AI grammar-correction "magic wand" button + "Undo AI" banner, wired to boardStore's
//   triggerGrammarCheck/applyGrammarCorrection/undoAiCorrection/showUndoAi/isGrammarLoading.
//   (Ground truth: this lives inline in SentenceBar via showUndo/onUndo/onGrammarCheck props, not
//   a separate dialog -- see GrammarPopup.tsx's doc comment for why that name refers to a
//   different feature.)
// - Long-pressing a sentence-chip word opens the word-morphology-variations popup
//   (MorphologyService.getVariations, GrammarPopup.tsx), letting the user swap in a different verb
//   tense/plural form before speaking. Only wired for the showSymbolsInSentenceBar chip view (each
//   chip is already a natural per-word tap target); the plain-text view has no per-word target to
//   long-press.
//
// Other deviations from the Compose original:
// - Renders the shared PredictionStrip component instead of the Kotlin file's own duplicate
//   inline PredictionChip implementation (Kotlin has two near-identical prediction-chip renderers
//   across SentenceBar.kt and PredictionStrip.kt; this port keeps one).
// - Backspace-vs-clear is "tap to backspace, long-press to clear" via a small pointer-based
//   long-press helper below, standing in for Compose's combinedClickable(onClick/onLongClick).

const LONG_PRESS_MS = 500;

function useLongPress(onLongPress: () => void, delay = LONG_PRESS_MS) {
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const firedAsLongPress = useRef(false);

  const start = () => {
    firedAsLongPress.current = false;
    timer.current = setTimeout(() => {
      firedAsLongPress.current = true;
      onLongPress();
    }, delay);
  };
  const cancel = () => {
    if (timer.current != null) {
      clearTimeout(timer.current);
      timer.current = null;
    }
  };

  return {
    onPointerDown: start,
    onPointerUp: cancel,
    onPointerLeave: cancel,
    consumeWasLongPress: () => {
      const was = firedAsLongPress.current;
      firedAsLongPress.current = false;
      return was;
    },
  };
}

interface GrammarPopupState {
  baseWord: string;
  variations: string[];
}

export function SentenceBar() {
  const { t } = useTranslation();
  const sentence = useBoardStore((s) => s.sentence);
  const undoLastWord = useBoardStore((s) => s.undoLastWord);
  const clearSentence = useBoardStore((s) => s.clearSentence);
  const speakSentence = useBoardStore((s) => s.speakSentence);
  const isGrammarLoading = useBoardStore((s) => s.isGrammarLoading);
  const showUndoAi = useBoardStore((s) => s.showUndoAi);
  const applyGrammarCorrection = useBoardStore((s) => s.applyGrammarCorrection);
  const undoAiCorrection = useBoardStore((s) => s.undoAiCorrection);
  const onPredictionSelected = useBoardStore((s) => s.onPredictionSelected);
  const languageCode = useSettingsStore((s) => s.settings.languageCode);
  const showSymbols = useSettingsStore((s) => s.settings.showSymbolsInSentenceBar);

  const [grammarPopup, setGrammarPopup] = useState<GrammarPopupState | null>(null);
  const morphologyService = useMemo(() => new MorphologyService(), []);

  const displayText = useMemo(
    () => GrammarEngine.fixSentence(sentence.map(getTextToSpeak), languageCode),
    [sentence, languageCode],
  );

  const backspaceLongPress = useLongPress(clearSentence);

  const requestWordVariations = async (word: string) => {
    const [verbs, nouns, adjectives] = await Promise.all([
      morphologyService.getVariations(word, 'VERB', languageCode),
      morphologyService.getVariations(word, 'NOUN', languageCode),
      morphologyService.getVariations(word, 'ADJECTIVE', languageCode),
    ]);
    const combined = [...new Set([...verbs, ...nouns, ...adjectives])];
    if (combined.length > 0 && !(combined.length === 1 && combined[0] === word)) {
      setGrammarPopup({ baseWord: word, variations: combined });
    }
  };

  return (
    <div className="sentence-bar">
      {showUndoAi && (
        <div className="sentence-bar__undo-banner">
          <span>{t('grammar_corrected')}</span>
          <button type="button" onClick={undoAiCorrection}>
            {t('undo')}
          </button>
        </div>
      )}

      <div className="sentence-bar__row">
        <div className="sentence-bar__content">
          {sentence.length === 0 ? (
            <span className="sentence-bar__placeholder">{t('build_your_sentence')}</span>
          ) : showSymbols ? (
            <div className="sentence-bar__chips">
              {sentence.map((button, index) => (
                <SentenceChip
                  key={`${button.id}-${index}`}
                  button={button}
                  onLongPress={() => void requestWordVariations(getTextToSpeak(button))}
                />
              ))}
            </div>
          ) : (
            <span className="sentence-bar__text">{displayText}</span>
          )}
        </div>

        <div className="sentence-bar__actions">
          <button
            type="button"
            className="sentence-bar__icon-btn"
            disabled={sentence.length === 0 || isGrammarLoading}
            onClick={applyGrammarCorrection}
            title={t('fix_grammar')}
            aria-label={t('fix_grammar')}
          >
            {isGrammarLoading ? <span className="prediction-strip__spinner" aria-hidden="true" /> : '🪄'}
          </button>
          <button
            type="button"
            className="sentence-bar__icon-btn"
            disabled={sentence.length === 0}
            onPointerDown={backspaceLongPress.onPointerDown}
            onPointerUp={backspaceLongPress.onPointerUp}
            onPointerLeave={backspaceLongPress.onPointerLeave}
            onClick={() => {
              if (!backspaceLongPress.consumeWasLongPress()) undoLastWord();
            }}
            title={t('backspace_hint')}
            aria-label={t('backspace')}
          >
            ⌫
          </button>
          <button
            type="button"
            className="sentence-bar__speak-btn"
            disabled={sentence.length === 0}
            onClick={() => void speakSentence()}
            title={t('speak')}
            aria-label={t('speak')}
          >
            🔊
          </button>
        </div>
      </div>

      <PredictionStrip />

      {grammarPopup && (
        <GrammarPopup
          baseWord={grammarPopup.baseWord}
          variations={grammarPopup.variations}
          onDismiss={() => setGrammarPopup(null)}
          onSelect={(variant) => {
            setGrammarPopup(null);
            onPredictionSelected(variant);
          }}
        />
      )}
    </div>
  );
}

function SentenceChip({ button, onLongPress }: { button: AacButton; onLongPress: () => void }) {
  const longPress = useLongPress(onLongPress);
  return (
    <div
      className="sentence-chip"
      onPointerDown={longPress.onPointerDown}
      onPointerUp={longPress.onPointerUp}
      onPointerLeave={longPress.onPointerLeave}
    >
      {button.iconPath && <img src={button.iconPath} alt="" draggable={false} />}
      <span>{button.label}</span>
    </div>
  );
}
