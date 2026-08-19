import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { pronunciationDictionary } from '../store/boardStore';
import { usePronunciationStore } from '../store/pronunciationStore';
import './communicationUI.css';
import './settingsUI.css';

// React port of ui/PronunciationManagementScreen.kt: CRUD for custom (caregiver-added)
// pronunciation overrides, plus a read-only list of the ~150 built-in Hebrew nikud corrections.
// See store/pronunciationStore.ts's doc comment for where/why custom-entry persistence lives.

export interface PronunciationManagementScreenProps {
  onBack: () => void;
}

export function PronunciationManagementScreen({ onBack }: PronunciationManagementScreenProps) {
  const { t } = useTranslation();
  const customEntries = usePronunciationStore((s) => s.customEntries);
  const addCustomPronunciation = usePronunciationStore((s) => s.addCustomPronunciation);
  const removeCustomPronunciation = usePronunciationStore((s) => s.removeCustomPronunciation);

  const [showAddForm, setShowAddForm] = useState(false);
  const [original, setOriginal] = useState('');
  const [corrected, setCorrected] = useState('');

  const builtIn = [...pronunciationDictionary.getBuiltInPronunciations().entries()];
  const custom = Object.entries(customEntries);

  const handleAdd = () => {
    const o = original.trim();
    const c = corrected.trim();
    if (o.length === 0 || c.length === 0) return;
    addCustomPronunciation(o, c);
    setOriginal('');
    setCorrected('');
    setShowAddForm(false);
  };

  return (
    <div className="settings-screen">
      <header className="settings-screen__header">
        <button type="button" className="settings-screen__back-btn" onClick={onBack} aria-label={t('back')}>
          ←
        </button>
        <h1>{t('pronunciation_title')}</h1>
      </header>

      <div className="settings-screen__body">
        <section className="settings-section">
          <h2 className="settings-section__title">{t('pronunciation_custom_section')}</h2>

          {showAddForm ? (
            <div className="pronunciation-screen__add-form">
              <p style={{ margin: 0, fontSize: '0.8rem', color: '#666' }}>
                {t('pronunciation_add_description')}
              </p>
              <input
                type="text"
                value={original}
                onChange={(e) => setOriginal(e.target.value)}
                placeholder={t('pronunciation_original_placeholder')}
                autoFocus
              />
              <input
                type="text"
                value={corrected}
                onChange={(e) => setCorrected(e.target.value)}
                placeholder={t('pronunciation_corrected_placeholder')}
              />
              <p style={{ margin: 0, fontSize: '0.75rem', color: '#999' }}>{t('pronunciation_example')}</p>
              <div className="modal__actions">
                <button type="button" onClick={() => setShowAddForm(false)}>
                  {t('cancel')}
                </button>
                <button type="button" onClick={handleAdd} disabled={original.trim().length === 0 || corrected.trim().length === 0}>
                  {t('add')}
                </button>
              </div>
            </div>
          ) : (
            <button type="button" className="settings-screen__link-btn" onClick={() => setShowAddForm(true)}>
              {t('pronunciation_add_custom')}
            </button>
          )}

          {custom.length === 0 ? (
            <p className="settings-screen__empty">{t('pronunciation_none_custom')}</p>
          ) : (
            custom.map(([word, fixed]) => (
              <div key={word} className="pronunciation-screen__item">
                <div className="pronunciation-screen__item-text">
                  <span className="pronunciation-screen__original">{word}</span>
                  <span className="pronunciation-screen__corrected">{fixed}</span>
                </div>
                <button
                  type="button"
                  className="settings-screen__text-btn"
                  onClick={() => removeCustomPronunciation(word)}
                  aria-label={t('pronunciation_remove_aria', { word })}
                >
                  🗑
                </button>
              </div>
            ))
          )}
        </section>

        <section className="settings-section">
          <h2 className="settings-section__title">{t('pronunciation_built_in_section')}</h2>
          <p className="settings-screen__description">{t('pronunciation_built_in_description')}</p>
          {builtIn.map(([word, fixed]) => (
            <div key={word} className="pronunciation-screen__item">
              <div className="pronunciation-screen__item-text">
                <span className="pronunciation-screen__original">{word}</span>
                <span className="pronunciation-screen__corrected">{fixed}</span>
              </div>
            </div>
          ))}
        </section>
      </div>
    </div>
  );
}
