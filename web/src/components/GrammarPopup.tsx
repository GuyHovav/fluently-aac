import { useTranslation } from 'react-i18next';

import './grammarPopupUI.css';

// React port of ui/components/GrammarPopup.kt.
//
// NOTE on naming vs. behavior: despite the "Grammar" name, this is the word-*morphology*-variations
// picker ("Forms of X" -- eat/ate/eating/will eat), driven by morphologyService.getVariations() and
// triggered by long-pressing a word (see SentenceBar.tsx's per-word long-press wiring). It is a
// separate feature from the AI grammar-*correction*-undo flow (the "magic wand" + Undo-AI banner
// also added to SentenceBar.tsx in this phase, backed by boardStore's triggerGrammarCheck/
// applyGrammarCorrection/undoAiCorrection) -- ground-truth MainActivity.kt wires GrammarPopup to
// `morphologyService.getVariations(...)` (search `grammarVariations`/`grammarBaseWord`), while the
// accept/undo grammar-correction UI is embedded directly in SentenceBar via `showUndo`/`onUndo`/
// `onGrammarCheck` props, not a separate dialog. This file matches the real Kotlin component.

export interface GrammarPopupProps {
  baseWord: string;
  variations: string[];
  onSelect: (variant: string) => void;
  onDismiss: () => void;
}

export function GrammarPopup({ baseWord, variations, onSelect, onDismiss }: GrammarPopupProps) {
  const { t } = useTranslation();
  return (
    <div className="modal-backdrop" onClick={onDismiss}>
      <div className="grammar-popup" onClick={(e) => e.stopPropagation()}>
        <h3 className="grammar-popup__title">{t('grammar_popup_forms_of', { word: baseWord })}</h3>

        {variations.length === 0 ? (
          <p className="grammar-popup__empty">{t('grammar_popup_no_variations')}</p>
        ) : (
          <div className="grammar-popup__grid">
            {variations.map((variant) => (
              <button key={variant} type="button" className="grammar-popup__chip" onClick={() => onSelect(variant)}>
                {variant}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
