import { useTranslation } from 'react-i18next';

import { useBoardStore } from '../store/boardStore';
import './communicationUI.css';

// React port of ui/components/PredictionStrip.kt, wired directly to boardStore.
//
// Deviation: the Kotlin source fetches a per-word symbol image (predictionSymbols: Map<String,
// String?>) via a live ARASAAC/GlobalSymbols/Google-Image search for every prediction. That
// network/AI-backed symbol lookup is board-content-generation territory (the same
// CompositeSymbolService BoardViewModel's createButtonWithSymbol uses for authoring buttons),
// out of scope for this phase's "predict + speak" milestone -- so prediction chips here are
// text-only. Wiring symbol lookups back in later is a matter of calling CompositeSymbolService
// per predicted word in boardStore.updatePredictions() and passing the result through.

export function PredictionStrip() {
  const { t } = useTranslation();
  const predictions = useBoardStore((s) => s.predictions);
  const isPredictionLoading = useBoardStore((s) => s.isPredictionLoading);
  const onPredictionSelected = useBoardStore((s) => s.onPredictionSelected);

  if (!isPredictionLoading && predictions.length === 0) return null;

  return (
    <div className="prediction-strip">
      {isPredictionLoading ? (
        <div className="prediction-strip__loading">
          <span className="prediction-strip__spinner" aria-hidden="true" />
          <span>{t('predicting')}</span>
        </div>
      ) : (
        <div className="prediction-strip__chips">
          {predictions.map((word) => (
            <button key={word} type="button" className="prediction-chip" onClick={() => onPredictionSelected(word)}>
              {word}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
