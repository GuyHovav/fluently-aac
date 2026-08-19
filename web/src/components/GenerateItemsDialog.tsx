import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import './communicationUI.css';

// React port of ui/components/GenerateItemsDialog.kt: a slider dialog ("How many more items?")
// used by EditBoardDialog's "Expand Board" button, feeding boardStore.expandBoard().

export interface GenerateItemsDialogProps {
  onDismiss: () => void;
  onGenerate: (itemCount: number) => void;
}

export function GenerateItemsDialog({ onDismiss, onGenerate }: GenerateItemsDialogProps) {
  const { t } = useTranslation();
  const [itemCount, setItemCount] = useState(20);

  return (
    <div className="modal-backdrop" onClick={onDismiss}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>{t('generate_more_items_title')}</h3>
        <p style={{ fontSize: '0.9rem', color: '#555' }}>
          {t('generate_items_description')}
        </p>
        <p style={{ fontWeight: 700, margin: '12px 0 4px' }}>{t('items_to_generate', { count: itemCount })}</p>
        <input
          type="range"
          min={1}
          max={50}
          step={1}
          value={itemCount}
          onChange={(e) => setItemCount(Number(e.target.value))}
          style={{ width: '100%' }}
        />
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', color: '#777' }}>
          <span>1</span>
          <span>50</span>
        </div>
        <div className="modal__actions">
          <button type="button" onClick={onDismiss}>
            {t('cancel')}
          </button>
          <button type="button" onClick={() => onGenerate(itemCount)}>
            {t('generate')}
          </button>
        </div>
      </div>
    </div>
  );
}
