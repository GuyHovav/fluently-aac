import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { FirebaseAuthentication, type User } from '@capacitor-firebase/authentication';

import type { AppShortcut, HomeButtonConfig } from '../models';
import { ButtonAction, DEFAULT_HOME_BUTTON_BACKGROUND_COLOR, DisabilityType } from '../models';
import { useBoardStore } from '../store/boardStore';
import { useSettingsStore } from '../store/settingsStore';
import { backupBoards, restoreBoards } from '../services/cloudBackupService';
import { AppPickerDialog } from './AppPickerDialog';
import { PronunciationManagementScreen } from './PronunciationManagementScreen';
import './settingsUI.css';

// React port of ui/SettingsScreen.kt, against settingsStore's already-complete Dexie-backed
// setter surface (no new settings-persistence logic added here).
//
// Deviations from the Compose original:
// - Cloud Backup section (below): ported from BoardViewModel.backupToCloud/restoreFromCloud +
//   CloudRepository.kt against cloudBackupService.ts (Phase 5), using
//   @capacitor-firebase/authentication's FirebaseAuthentication.getCurrentUser() to read
//   sign-in state -- Google Sign-In itself is Phase 0's native-plugin-backed flow (also used by
//   DebugHarness.tsx), not re-implemented here.
// - No "Clear Learned Vocabulary" button: LocalPredictionEngine/WordFrequencyRepository (Phase 1
//   output, off-limits this phase) expose no clear method to wire it to without extending
//   web/src/nlp or web/src/data, which are off-limits for this phase.
// - Language and Disability Type controls: ground-truth SettingsScreen.kt has no UI for these two
//   settingsStore setters (setLanguage/setDisabilityType are called from elsewhere in the native
//   app, e.g. onboarding, not from this screen) -- they're added here since settingsStore's full
//   setter surface should have a reachable control somewhere, and this is the only settings screen
//   in this port.
// - Home Buttons (homeButtonsJson): confirmed vestigial in the native app (SettingsRepository
//   persists it, but no UI ever reads/writes HomeButtonConfig from it, and BoardViewModel's
//   syncHomeBoardLinks builds Home's links from the boards list directly, not from this JSON blob).
//   A minimal add/remove list editor is included below for completeness of settingsStore's setter
//   surface, but it has no effect on what's shown on the Home board (matching the native app).

const FONT_FAMILIES = ['System', 'OpenDyslexic', 'Atkinson Hyperlegible', 'Andika'];
// Labels are translation keys (resolved with t() in the component below) rather than literal
// text, since these arrays are module-level (outside any component, so no useTranslation() call
// is available here) but the option text itself must still follow the active language.
const LANGUAGES: { code: string; labelKey: 'english' | 'hebrew' }[] = [
  { code: 'en', labelKey: 'english' },
  { code: 'iw', labelKey: 'hebrew' },
];
const DISABILITY_TYPES: {
  value: (typeof DisabilityType)[keyof typeof DisabilityType];
  labelKey: 'disability_none' | 'disability_motor' | 'disability_visual' | 'disability_cognitive' | 'disability_aphasia';
}[] = [
  { value: DisabilityType.NONE, labelKey: 'disability_none' },
  { value: DisabilityType.MOTOR_IMPAIRMENT, labelKey: 'disability_motor' },
  { value: DisabilityType.VISUAL_IMPAIRMENT, labelKey: 'disability_visual' },
  { value: DisabilityType.COGNITIVE_IMPAIRMENT, labelKey: 'disability_cognitive' },
  { value: DisabilityType.APHASIA, labelKey: 'disability_aphasia' },
];

function parseHomeButtons(json: string): HomeButtonConfig[] {
  if (json.trim().length === 0) return [];
  try {
    const parsed: unknown = JSON.parse(json);
    return Array.isArray(parsed) ? (parsed as HomeButtonConfig[]) : [];
  } catch {
    return [];
  }
}

export interface SettingsScreenProps {
  onBack: () => void;
}

export function SettingsScreen({ onBack }: SettingsScreenProps) {
  const { t } = useTranslation();
  const settings = useSettingsStore((s) => s.settings);
  const setLanguage = useSettingsStore((s) => s.setLanguage);
  const setDisabilityType = useSettingsStore((s) => s.setDisabilityType);
  const setSymbolLibrary = useSettingsStore((s) => s.setSymbolLibrary);
  const setFontFamily = useSettingsStore((s) => s.setFontFamily);
  const setTextScale = useSettingsStore((s) => s.setTextScale);
  const setDisplayScale = useSettingsStore((s) => s.setDisplayScale);
  const setUseSystemSettings = useSettingsStore((s) => s.setUseSystemSettings);
  const setHorizontalNavigationEnabled = useSettingsStore((s) => s.setHorizontalNavigationEnabled);
  const setShowSymbolsInSentenceBar = useSettingsStore((s) => s.setShowSymbolsInSentenceBar);
  const setLandscapeBigSentence = useSettingsStore((s) => s.setLandscapeBigSentence);
  const setShowHomeOnStartup = useSettingsStore((s) => s.setShowHomeOnStartup);
  const setTtsRate = useSettingsStore((s) => s.setTtsRate);
  const getAppShortcuts = useSettingsStore((s) => s.getAppShortcuts);
  const setAppShortcuts = useSettingsStore((s) => s.setAppShortcuts);
  const setPredictionEnabled = useSettingsStore((s) => s.setPredictionEnabled);
  const setAiPredictionEnabled = useSettingsStore((s) => s.setAiPredictionEnabled);
  const setShowSymbolsInPredictions = useSettingsStore((s) => s.setShowSymbolsInPredictions);
  const setPredictionCount = useSettingsStore((s) => s.setPredictionCount);
  const setLearnFromUsage = useSettingsStore((s) => s.setLearnFromUsage);
  const setAutoGrammarCheck = useSettingsStore((s) => s.setAutoGrammarCheck);
  const setItemsToGenerate = useSettingsStore((s) => s.setItemsToGenerate);
  const setMaxBoardCapacity = useSettingsStore((s) => s.setMaxBoardCapacity);
  const setHomeButtons = useSettingsStore((s) => s.setHomeButtons);

  const syncAppsBoard = useBoardStore((s) => s.syncAppsBoard);

  const [showAppPicker, setShowAppPicker] = useState(false);
  const [showPronunciation, setShowPronunciation] = useState(false);

  // Cloud Backup section state. `cloudUser === undefined` means "still checking"
  // (FirebaseAuthentication.getCurrentUser() hasn't resolved yet); `null` means signed out.
  const [cloudUser, setCloudUser] = useState<User | null | undefined>(undefined);
  const [cloudSigningIn, setCloudSigningIn] = useState(false);
  const [cloudBackupStatus, setCloudBackupStatus] = useState<'idle' | 'running' | 'success' | 'error'>('idle');
  const [cloudBackupMessage, setCloudBackupMessage] = useState('');
  const [cloudRestoreStatus, setCloudRestoreStatus] = useState<'idle' | 'running' | 'success' | 'error'>('idle');
  const [cloudRestoreMessage, setCloudRestoreMessage] = useState('');
  const [lastBackupAt, setLastBackupAt] = useState<Date | null>(null);

  useEffect(() => {
    let cancelled = false;
    void FirebaseAuthentication.getCurrentUser()
      .then((result) => {
        if (!cancelled) setCloudUser(result.user);
      })
      .catch(() => {
        if (!cancelled) setCloudUser(null);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const handleCloudSignIn = () => {
    setCloudSigningIn(true);
    void FirebaseAuthentication.signInWithGoogle()
      .then((result) => setCloudUser(result.user ?? null))
      .catch((err: unknown) => {
        setCloudBackupStatus('error');
        setCloudBackupMessage(t('settings_cloud_backup_sign_in_error', { error: String(err) }));
      })
      .finally(() => setCloudSigningIn(false));
  };

  const handleCloudBackup = () => {
    if (!cloudUser) return;
    setCloudBackupStatus('running');
    setCloudBackupMessage('');
    void backupBoards(cloudUser.uid)
      .then(({ successCount, totalCount }) => {
        setCloudBackupStatus('success');
        setCloudBackupMessage(t('settings_cloud_backup_success', { count: successCount, total: totalCount }));
        setLastBackupAt(new Date());
      })
      .catch((err: unknown) => {
        setCloudBackupStatus('error');
        setCloudBackupMessage(t('settings_cloud_backup_error', { error: String(err) }));
      });
  };

  const handleCloudRestore = () => {
    if (!cloudUser) return;
    setCloudRestoreStatus('running');
    setCloudRestoreMessage('');
    void restoreBoards(cloudUser.uid)
      .then((boards) => {
        setCloudRestoreStatus('success');
        setCloudRestoreMessage(t('settings_cloud_backup_restore_success', { count: boards.length }));
      })
      .catch((err: unknown) => {
        setCloudRestoreStatus('error');
        setCloudRestoreMessage(t('settings_cloud_backup_restore_error', { error: String(err) }));
      });
  };

  const appShortcuts = useMemo(() => getAppShortcuts(), [getAppShortcuts, settings.appShortcutsJson]);
  const homeButtons = useMemo(() => parseHomeButtons(settings.homeButtonsJson), [settings.homeButtonsJson]);

  const saveAppShortcuts = (shortcuts: AppShortcut[]) => {
    void (async () => {
      await setAppShortcuts(shortcuts);
      void syncAppsBoard();
    })();
  };

  const saveHomeButtons = (buttons: HomeButtonConfig[]) => {
    void setHomeButtons(JSON.stringify(buttons));
  };

  if (showPronunciation) {
    return <PronunciationManagementScreen onBack={() => setShowPronunciation(false)} />;
  }

  return (
    <div className="settings-screen">
      <header className="settings-screen__header">
        <button type="button" className="settings-screen__back-btn" onClick={onBack} aria-label={t('back')}>
          ←
        </button>
        <h1>{t('settings')}</h1>
      </header>

      <div className="settings-screen__body">
        <SettingsSection title={t('settings_display_section')}>
          <SettingsRow label={t('language')}>
            <select value={settings.languageCode} onChange={(e) => void setLanguage(e.target.value)}>
              {LANGUAGES.map((lang) => (
                <option key={lang.code} value={lang.code}>
                  {t(lang.labelKey)}
                </option>
              ))}
            </select>
          </SettingsRow>

          <SettingsRow label={t('settings_symbol_library')}>
            <div className="settings-screen__radio-group">
              <label>
                <input
                  type="radio"
                  checked={settings.symbolLibrary === 'ARASAAC'}
                  onChange={() => void setSymbolLibrary('ARASAAC')}
                />
                {t('settings_arasaac_standard')}
              </label>
              <label>
                <input
                  type="radio"
                  checked={settings.symbolLibrary === 'MULBERRY'}
                  onChange={() => void setSymbolLibrary('MULBERRY')}
                />
                {t('settings_mulberry_modern')}
              </label>
            </div>
          </SettingsRow>

          <SettingsRow label={t('font_family')}>
            <select value={settings.fontFamily} onChange={(e) => void setFontFamily(e.target.value)}>
              {FONT_FAMILIES.map((font) => (
                <option key={font} value={font}>
                  {font}
                </option>
              ))}
            </select>
          </SettingsRow>

          <SettingsRow label={t('settings_text_scale', { percent: Math.round(settings.textScale * 100) })}>
            <input
              type="range"
              min={0.5}
              max={2.0}
              step={0.1}
              value={settings.textScale}
              onChange={(e) => void setTextScale(Number(e.target.value))}
            />
          </SettingsRow>

          <SettingsToggle
            label={t('settings_use_system_display_settings')}
            checked={settings.useSystemSettings}
            onChange={(v) => void setUseSystemSettings(v)}
          />

          {!settings.useSystemSettings && (
            <SettingsRow label={t('settings_display_scale', { percent: Math.round(settings.displayScale * 100) })}>
              <input
                type="range"
                min={0.8}
                max={1.5}
                step={0.1}
                value={settings.displayScale}
                onChange={(e) => void setDisplayScale(Number(e.target.value))}
              />
            </SettingsRow>
          )}

          <SettingsToggle
            label={t('settings_show_horizontal_navigation')}
            checked={settings.showHorizontalNavigation}
            onChange={(v) => void setHorizontalNavigationEnabled(v)}
          />
          <SettingsToggle
            label={t('show_symbols_in_sentence_bar')}
            checked={settings.showSymbolsInSentenceBar}
            onChange={(v) => void setShowSymbolsInSentenceBar(v)}
          />
          <SettingsToggle
            label={t('settings_show_big_sentence_landscape')}
            description={t('settings_show_big_sentence_landscape_desc')}
            checked={settings.landscapeBigSentence}
            onChange={(v) => void setLandscapeBigSentence(v)}
          />
          <SettingsToggle
            label={t('settings_show_home_on_startup')}
            checked={settings.showHomeOnStartup}
            onChange={(v) => void setShowHomeOnStartup(v)}
          />

          <SettingsRow label={t('disability_type')}>
            <select
              value={settings.disabilityType}
              onChange={(e) => void setDisabilityType(e.target.value as DisabilityType)}
            >
              {DISABILITY_TYPES.map((d) => (
                <option key={d.value} value={d.value}>
                  {t(d.labelKey)}
                </option>
              ))}
            </select>
          </SettingsRow>

          <button type="button" className="settings-screen__link-btn" onClick={() => setShowPronunciation(true)}>
            {t('manage_pronunciations')}
          </button>
        </SettingsSection>

        <SettingsSection title={t('settings_speech_section')}>
          <SettingsRow label={t('tts_rate', { percent: Math.round(settings.ttsRate * 100) })}>
            <input
              type="range"
              min={0.5}
              max={2.0}
              step={0.1}
              value={settings.ttsRate}
              onChange={(e) => void setTtsRate(Number(e.target.value))}
            />
          </SettingsRow>
        </SettingsSection>

        <SettingsSection title={t('app_shortcuts')}>
          <p className="settings-screen__description">{t('settings_app_shortcuts_description')}</p>
          {appShortcuts.length === 0 ? (
            <p className="settings-screen__empty">{t('no_apps_configured')}</p>
          ) : (
            appShortcuts.map((shortcut) => (
              <div key={shortcut.packageName} className="settings-screen__list-row">
                <span>{shortcut.appName}</span>
                <button
                  type="button"
                  className="settings-screen__text-btn"
                  onClick={() => saveAppShortcuts(appShortcuts.filter((s) => s.packageName !== shortcut.packageName))}
                >
                  {t('remove_app')}
                </button>
              </div>
            ))
          )}
          <button type="button" className="settings-screen__link-btn" onClick={() => setShowAppPicker(true)}>
            {t('add_app')}
          </button>
        </SettingsSection>

        <SettingsSection title={t('settings_home_buttons_section')}>
          <p className="settings-screen__description">{t('settings_home_buttons_description')}</p>
          {homeButtons.length === 0 ? (
            <p className="settings-screen__empty">{t('settings_no_custom_home_buttons')}</p>
          ) : (
            homeButtons.map((btn) => (
              <div key={btn.id} className="settings-screen__list-row">
                <span>{btn.label}</span>
                <button
                  type="button"
                  className="settings-screen__text-btn"
                  onClick={() => saveHomeButtons(homeButtons.filter((b) => b.id !== btn.id))}
                >
                  {t('remove')}
                </button>
              </div>
            ))
          )}
          <button
            type="button"
            className="settings-screen__link-btn"
            onClick={() => {
              const label = window.prompt(t('settings_new_home_button_prompt'));
              if (!label || label.trim().length === 0) return;
              const trimmed = label.trim();
              const newButton: HomeButtonConfig = {
                id: `home_btn_${Date.now().toString(36)}`,
                label: trimmed,
                iconPath: null,
                action: ButtonAction.speak(trimmed),
                backgroundColor: DEFAULT_HOME_BUTTON_BACKGROUND_COLOR,
                order: homeButtons.length,
                isVisible: true,
              };
              saveHomeButtons([...homeButtons, newButton]);
            }}
          >
            + Add Home Button
          </button>
        </SettingsSection>

        <SettingsSection title="Word Prediction">
          <SettingsToggle
            label="Enable Word Prediction"
            description="Show predicted words below the sentence bar"
            checked={settings.predictionEnabled}
            onChange={(v) => void setPredictionEnabled(v)}
          />

          {settings.predictionEnabled && (
            <>
              <SettingsToggle
                label="AI-Powered Predictions"
                description="Use Gemini AI for smarter, contextual predictions"
                checked={settings.aiPredictionEnabled}
                onChange={(v) => void setAiPredictionEnabled(v)}
              />
              <SettingsToggle
                label="Show symbols in predictions"
                checked={settings.showSymbolsInPredictions}
                onChange={(v) => void setShowSymbolsInPredictions(v)}
              />
              <SettingsRow label={`Number of Predictions: ${settings.predictionCount}`}>
                <input
                  type="range"
                  min={3}
                  max={8}
                  step={1}
                  value={settings.predictionCount}
                  onChange={(e) => void setPredictionCount(Number(e.target.value))}
                />
              </SettingsRow>
              <SettingsToggle
                label="Learn from Usage"
                description="Improve predictions based on your vocabulary"
                checked={settings.learnFromUsage}
                onChange={(v) => void setLearnFromUsage(v)}
              />
              <SettingsToggle
                label="Automatic Grammar Check"
                description="Apply grammar corrections immediately. If disabled, a magic-wand button appears instead."
                checked={settings.autoGrammarCheck}
                onChange={(v) => void setAutoGrammarCheck(v)}
              />
            </>
          )}
        </SettingsSection>

        <SettingsSection title="Grid Generation">
          <SettingsRow label={`Items to Generate: ${settings.itemsToGenerate}`}>
            <input
              type="range"
              min={1}
              max={50}
              step={1}
              value={settings.itemsToGenerate}
              onChange={(e) => void setItemsToGenerate(Number(e.target.value))}
            />
          </SettingsRow>
          <SettingsRow label={`Max Board Capacity: ${settings.maxBoardCapacity}`}>
            <input
              type="range"
              min={50}
              max={1000}
              step={10}
              value={settings.maxBoardCapacity}
              onChange={(e) => void setMaxBoardCapacity(Number(e.target.value))}
            />
          </SettingsRow>
        </SettingsSection>

        <SettingsSection title={t('settings_cloud_backup_section')}>
          {cloudUser === undefined ? (
            <p className="settings-screen__description">{t('settings_cloud_backup_checking')}</p>
          ) : cloudUser === null ? (
            <>
              <p className="settings-screen__description">{t('settings_cloud_backup_sign_in_prompt')}</p>
              <button
                type="button"
                className="settings-screen__link-btn"
                onClick={handleCloudSignIn}
                disabled={cloudSigningIn}
              >
                {cloudSigningIn ? t('settings_cloud_backup_signing_in') : t('settings_cloud_backup_sign_in')}
              </button>
              {cloudBackupStatus === 'error' && <p className="settings-screen__empty">{cloudBackupMessage}</p>}
            </>
          ) : (
            <>
              <p className="settings-screen__description">
                {t('settings_cloud_backup_signed_in_as', { email: cloudUser.email ?? cloudUser.uid })}
              </p>
              {lastBackupAt && (
                <p className="settings-screen__description">
                  {t('settings_cloud_backup_last_backup', { time: lastBackupAt.toLocaleString() })}
                </p>
              )}

              <button
                type="button"
                className="settings-screen__link-btn"
                onClick={handleCloudBackup}
                disabled={cloudBackupStatus === 'running'}
              >
                {cloudBackupStatus === 'running' ? t('settings_cloud_backup_in_progress') : t('settings_cloud_backup_now')}
              </button>
              {cloudBackupMessage && (
                <p className="settings-screen__description">{cloudBackupMessage}</p>
              )}

              <button
                type="button"
                className="settings-screen__link-btn"
                onClick={handleCloudRestore}
                disabled={cloudRestoreStatus === 'running'}
              >
                {cloudRestoreStatus === 'running'
                  ? t('settings_cloud_backup_restore_in_progress')
                  : t('settings_cloud_backup_restore')}
              </button>
              {cloudRestoreMessage && (
                <p className="settings-screen__description">{cloudRestoreMessage}</p>
              )}
            </>
          )}
        </SettingsSection>
      </div>

      {showAppPicker && (
        <AppPickerDialog
          onDismiss={() => setShowAppPicker(false)}
          onAppSelected={(packageName, appName) => {
            saveAppShortcuts([...appShortcuts, { packageName, appName, iconPath: null }]);
            setShowAppPicker(false);
          }}
        />
      )}
    </div>
  );
}

function SettingsSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="settings-section">
      <h2 className="settings-section__title">{title}</h2>
      {children}
    </section>
  );
}

function SettingsRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="settings-row">
      <span className="settings-row__label">{label}</span>
      {children}
    </div>
  );
}

function SettingsToggle({
  label,
  description,
  checked,
  onChange,
}: {
  label: string;
  description?: string;
  checked: boolean;
  onChange: (value: boolean) => void;
}) {
  return (
    <label className="settings-toggle">
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      <span className="settings-toggle__text">
        <span className="settings-toggle__label">{label}</span>
        {description && <span className="settings-toggle__description">{description}</span>}
      </span>
    </label>
  );
}
