import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { AgentDialog } from './components/AgentDialog';
import { CommunicationGrid } from './components/CommunicationGrid';
import { PinEntryDialog } from './components/PinEntryDialog';
import { SelectionBar } from './components/SelectionBar';
import { SentenceBar } from './components/SentenceBar';
import { SettingsScreen } from './components/SettingsScreen';
import { SidebarContent } from './components/SidebarContent';
import { VisualSceneGrid } from './components/VisualSceneGrid';
import { DebugHarness } from './DebugHarness';
import { useSyncI18nLanguage } from './i18n';
import { useBoardStore } from './store/boardStore';
import './components/communicationUI.css';

// Phase 2: the real "build a board and speak a sentence" screen, replacing the Phase 0 test
// harness as the default view. The harness itself isn't deleted -- Phase 0's on-device plugin
// validation still needs to happen -- it's reachable behind the "Debug" header button instead.
//
// Phase 4: adds the remaining screens (settings, sidebar/board-navigation, the "Fluently" AI
// assistant, the caregiver PIN gate) using the same lightweight boolean view-toggle pattern
// Phase 2/3 already established here, rather than introducing a routing library -- the new
// screens are few, mutually exclusive, and don't need URL-addressability or nested routes, so a
// router would add a dependency and a new architectural idiom for no real benefit at this size.
export default function App() {
  const { t } = useTranslation();
  useSyncI18nLanguage();

  const [showDebug, setShowDebug] = useState(false);
  const [showSidebar, setShowSidebar] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [showAgentDialog, setShowAgentDialog] = useState(false);
  const [showPinDialog, setShowPinDialog] = useState(false);

  const init = useBoardStore((s) => s.init);
  const isInitialized = useBoardStore((s) => s.isInitialized);
  const currentBoard = useBoardStore((s) => s.currentBoard);
  const backStack = useBoardStore((s) => s.backStack);
  const navigateBack = useBoardStore((s) => s.navigateBack);
  const isCaregiverMode = useBoardStore((s) => s.isCaregiverMode);
  const setCaregiverMode = useBoardStore((s) => s.setCaregiverMode);
  const selectedButtonIds = useBoardStore((s) => s.selectedButtonIds);
  const submitAgentQuery = useBoardStore((s) => s.submitAgentQuery);
  const agentResponse = useBoardStore((s) => s.agentResponse);
  const isAgentProcessing = useBoardStore((s) => s.isAgentProcessing);
  const clearAgentResponse = useBoardStore((s) => s.clearAgentResponse);

  useEffect(() => {
    void init();
  }, [init]);

  if (showDebug) {
    return (
      <div>
        <button type="button" className="app-debug-toggle" onClick={() => setShowDebug(false)}>
          {t('back_to_app')}
        </button>
        <DebugHarness />
      </div>
    );
  }

  if (showSettings) {
    return <SettingsScreen onBack={() => setShowSettings(false)} />;
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <button type="button" className="app-header__nav-btn" onClick={() => setShowSidebar(true)} aria-label={t('menu')}>
          ☰
        </button>
        <button
          type="button"
          className="app-header__nav-btn"
          onClick={() => void navigateBack()}
          disabled={backStack.length === 0}
          aria-label={t('back')}
        >
          ←
        </button>
        <h1 className="app-header__title">{currentBoard?.name ?? t('app_name')}</h1>
        <div className="app-header__actions">
          <button type="button" className="app-header__toggle" onClick={() => setShowAgentDialog(true)}>
            {t('fluently_button')}
          </button>
          <button
            type="button"
            className={`app-header__toggle${isCaregiverMode ? ' app-header__toggle--active' : ''}`}
            onClick={() => {
              // Caregiver PIN gate: turning ON requires the hardcoded "1234" PIN (per the
              // migration plan's locked-in decision -- no hashing, no configurability, security
              // hardening explicitly deferred). Turning off needs no PIN, mirroring
              // BoardViewModel.kt's unlockCaregiverMode(pin)/lockCaregiverMode() asymmetry.
              if (isCaregiverMode) {
                setCaregiverMode(false);
              } else {
                setShowPinDialog(true);
              }
            }}
          >
            {isCaregiverMode ? t('caregiver_on') : t('caregiver_off')}
          </button>
          <button type="button" className="app-header__toggle" onClick={() => setShowDebug(true)}>
            {t('debug')}
          </button>
        </div>
      </header>

      <main className="app-main">
        {!isInitialized ? (
          <div className="app-loading">{t('loading')}</div>
        ) : currentBoard?.backgroundImagePath ? (
          <VisualSceneGrid board={currentBoard} />
        ) : (
          <CommunicationGrid />
        )}
      </main>

      {isCaregiverMode && selectedButtonIds.size > 0 && <SelectionBar />}
      <SentenceBar />

      {showSidebar && <SidebarContent onClose={() => setShowSidebar(false)} onOpenSettings={() => { setShowSidebar(false); setShowSettings(true); }} />}

      {showPinDialog && (
        <PinEntryDialog
          onDismiss={() => setShowPinDialog(false)}
          onUnlocked={() => {
            setCaregiverMode(true);
            setShowPinDialog(false);
          }}
        />
      )}

      {showAgentDialog && (
        <AgentDialog
          onDismiss={() => {
            setShowAgentDialog(false);
            clearAgentResponse();
          }}
          onSubmit={(query) => void submitAgentQuery(query)}
          response={agentResponse}
          isLoading={isAgentProcessing}
        />
      )}
    </div>
  );
}
