import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import './communicationUI.css';
import './agentUI.css';

// React port of ui/components/AgentDialog.kt -- the "Fluently" in-app AI assistant, wired to
// boardStore.submitAgentQuery/agentResponse/isAgentProcessing (GeminiService.parseAgentCommand's
// CreateBoard/AnswerQuestion/Unknown routing).
//
// Deviation from the Compose original: the Kotlin version had a mic button wired to Android's
// `RecognizerIntent` speech-to-text. There is no web equivalent wired up anywhere else in this
// port (no SpeechRecognizer bridge plugin exists -- the migration plan explicitly drops
// RECORD_AUDIO/on-device speech recognition as out of scope), so the mic button is omitted rather
// than stubbed with a non-functional control.

export interface AgentDialogProps {
  onDismiss: () => void;
  onSubmit: (query: string) => void;
  response: string | null;
  isLoading: boolean;
}

export function AgentDialog({ onDismiss, onSubmit, response, isLoading }: AgentDialogProps) {
  const { t } = useTranslation();
  const [query, setQuery] = useState('');

  const handleSend = () => {
    const trimmed = query.trim();
    if (trimmed.length === 0) return;
    onSubmit(trimmed);
    setQuery('');
  };

  return (
    <div className="modal-backdrop" onClick={onDismiss}>
      <div className="modal modal--agent" onClick={(e) => e.stopPropagation()}>
        <h3 className="agent-dialog__title">
          <span aria-hidden="true">✨</span> {t('agent_title')}
        </h3>

        {response && <div className="agent-dialog__response">{response}</div>}

        {isLoading ? (
          <div className="agent-dialog__loading">
            <span className="prediction-strip__spinner" aria-hidden="true" />
            <span>{t('agent_thinking')}</span>
          </div>
        ) : (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleSend();
            }}
          >
            <textarea
              className="agent-dialog__input"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={t('agent_placeholder')}
              rows={3}
            />
          </form>
        )}

        <div className="modal__actions">
          <button type="button" onClick={onDismiss}>
            {t('close')}
          </button>
          {!isLoading && (
            <button type="button" onClick={handleSend} disabled={query.trim().length === 0}>
              {t('agent_send')}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
