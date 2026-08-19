import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import './communicationUI.css';

// Caregiver-mode PIN gate. Port of the PIN AlertDialog embedded in SidebarContent.kt (see that
// file's `showPinDialog` block) plus BoardViewModel.kt's `unlockCaregiverMode(pin)`.
//
// Per the migration plan's locked-in decision, the PIN check stays a hardcoded "1234" for now --
// security hardening (hashing, configurability, the Kotlin source's DEBUG_MODE auto-unlock) is
// explicitly deferred, so this ports the production-mode branch only: `pin === "1234"`.

const CAREGIVER_PIN = '1234';

export interface PinEntryDialogProps {
  onDismiss: () => void;
  onUnlocked: () => void;
}

export function PinEntryDialog({ onDismiss, onUnlocked }: PinEntryDialogProps) {
  const { t } = useTranslation();
  const [pin, setPin] = useState('');
  const [error, setError] = useState(false);

  const attemptUnlock = () => {
    if (pin === CAREGIVER_PIN) {
      onUnlocked();
    } else {
      setError(true);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onDismiss}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>{t('enter_admin_pin')}</h3>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            attemptUnlock();
          }}
        >
          <input
            type="password"
            inputMode="numeric"
            autoFocus
            value={pin}
            onChange={(e) => {
              setPin(e.target.value);
              setError(false);
            }}
            placeholder={t('pin')}
            aria-label={t('enter_admin_pin')}
            style={{
              width: '100%',
              padding: '10px 12px',
              borderRadius: 8,
              border: error ? '1px solid #b3261e' : '1px solid #ccc',
              fontSize: '1rem',
              boxSizing: 'border-box',
            }}
          />
          {error && (
            <p style={{ color: '#b3261e', fontSize: '0.85rem', margin: '6px 0 0' }}>{t('incorrect_pin')}</p>
          )}
        </form>
        <div className="modal__actions">
          <button type="button" onClick={onDismiss}>
            {t('cancel')}
          </button>
          <button type="button" onClick={attemptUnlock}>
            {t('unlock')}
          </button>
        </div>
      </div>
    </div>
  );
}
