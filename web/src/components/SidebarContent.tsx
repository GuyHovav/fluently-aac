import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { Board } from '../models';
import { useBoardStore } from '../store/boardStore';
import { CreateBoardDialog } from './CreateBoardDialog';
import { EditBoardDialog } from './EditBoardDialog';
import { PinEntryDialog } from './PinEntryDialog';
import { QuickBoardDialog } from './QuickBoardDialog';
import { VisualSceneDialog } from './VisualSceneDialog';
import './communicationUI.css';
import './sidebarUI.css';

// React port of ui/components/SidebarContent.kt: the board-list navigation drawer, plus the
// caregiver-only "create board" menu (plain / Visual Scene / Quick Board) and the board-list
// edit pencil (-> EditBoardDialog).
//
// Deviations from the Compose original:
// - No "permanent sidebar" tablet-landscape two-pane layout (windowSizeClass-driven in the Kotlin
//   source) -- per the migration plan's "lightweight toggle pattern" instruction, this always
//   renders as a slide-over overlay, toggled from App.tsx's header hamburger button, regardless of
//   viewport size. Simpler and consistent across breakpoints; revisit only if real tablet usage
//   shows the overlay is annoying at wide viewports.
// - The caregiver-mode PIN gate lives here too (matching SidebarContent.kt's own `showPinDialog`),
//   in addition to App.tsx's header toggle also being PIN-gated -- both are legitimate entry points
//   in the original app, both use the same PinEntryDialog/hardcoded "1234" check.

export interface SidebarContentProps {
  onClose: () => void;
  onOpenSettings: () => void;
}

type CreateMenu = 'none' | 'picker' | 'plain' | 'scene' | 'quick';

export function SidebarContent({ onClose, onOpenSettings }: SidebarContentProps) {
  const { t } = useTranslation();
  const boards = useBoardStore((s) => s.boards);
  const currentBoard = useBoardStore((s) => s.currentBoard);
  const isCaregiverMode = useBoardStore((s) => s.isCaregiverMode);
  const setCaregiverMode = useBoardStore((s) => s.setCaregiverMode);
  const navigateToBoard = useBoardStore((s) => s.navigateToBoard);
  const createBoard = useBoardStore((s) => s.createBoard);
  const createMagicBoard = useBoardStore((s) => s.createMagicBoard);
  const createMagicScene = useBoardStore((s) => s.createMagicScene);
  const createQuickBoard = useBoardStore((s) => s.createQuickBoard);
  const expandBoard = useBoardStore((s) => s.expandBoard);
  const updateBoard = useBoardStore((s) => s.updateBoard);
  const deleteBoard = useBoardStore((s) => s.deleteBoard);

  const [createMenu, setCreateMenu] = useState<CreateMenu>('none');
  const [boardToEdit, setBoardToEdit] = useState<Board | null>(null);
  const [showPinDialog, setShowPinDialog] = useState(false);

  const visibleBoards = useMemo(() => {
    if (isCaregiverMode) return boards;
    return boards.filter(
      (board) => board.id === 'home' || board.buttons.some((b) => !b.hidden && (b.label.length > 0 || !!b.iconPath)),
    );
  }, [boards, isCaregiverMode]);

  const openBoard = (board: Board) => {
    void navigateToBoard(board.id);
    onClose();
  };

  return (
    <>
      <div className="sidebar-overlay" onClick={onClose}>
        <div className="sidebar-panel" onClick={(e) => e.stopPropagation()}>
          <div className="sidebar__header">
            <h2>{t('boards')}</h2>
            <button type="button" className="sidebar__close-btn" onClick={onClose} aria-label={t('close')}>
              ✕
            </button>
          </div>

          {isCaregiverMode && (
            <div className="sidebar__create-wrap">
              <button type="button" className="sidebar__create-btn" onClick={() => setCreateMenu('picker')}>
                {t('sidebar_create_new_board_btn')}
              </button>
              {createMenu === 'picker' && (
                <div className="sidebar__create-menu">
                  <button type="button" onClick={() => setCreateMenu('plain')}>
                    {t('sidebar_new_board_menu_item')}
                  </button>
                  <button type="button" onClick={() => setCreateMenu('scene')}>
                    {`📷 ${t('new_visual_scene')}`}
                  </button>
                  <button type="button" onClick={() => setCreateMenu('quick')}>
                    {`✨ ${t('quick_board_from_photo')}`}
                  </button>
                </div>
              )}
            </div>
          )}

          <div className="sidebar__list">
            {visibleBoards.map((board) => (
              <div key={board.id} className={`sidebar__item${board.id === currentBoard?.id ? ' sidebar__item--selected' : ''}`}>
                <button type="button" className="sidebar__item-btn" onClick={() => openBoard(board)}>
                  {board.iconPath ? (
                    <img className="sidebar__item-icon" src={board.iconPath} alt="" />
                  ) : (
                    <span className="sidebar__item-icon sidebar__item-icon--placeholder" aria-hidden="true">
                      🗂
                    </span>
                  )}
                  <span className="sidebar__item-label">{board.name}</span>
                </button>
                {isCaregiverMode && (
                  <button
                    type="button"
                    className="sidebar__item-edit-btn"
                    onClick={() => setBoardToEdit(board)}
                    aria-label={t('sidebar_edit_board_aria', { name: board.name })}
                  >
                    ✎
                  </button>
                )}
              </div>
            ))}
          </div>

          <div className="sidebar__footer">
            {isCaregiverMode ? (
              <>
                <button type="button" className="sidebar__footer-btn" onClick={onOpenSettings}>
                  {t('sidebar_settings_footer')}
                </button>
                <button type="button" className="sidebar__footer-btn sidebar__footer-btn--secondary" onClick={() => setCaregiverMode(false)}>
                  {t('exit_admin_mode')}
                </button>
              </>
            ) : (
              <button type="button" className="sidebar__footer-btn sidebar__footer-btn--text" onClick={() => setShowPinDialog(true)}>
                {t('enter_admin_mode')}
              </button>
            )}
          </div>
        </div>
      </div>

      {showPinDialog && (
        <PinEntryDialog
          onDismiss={() => setShowPinDialog(false)}
          onUnlocked={() => {
            setCaregiverMode(true);
            setShowPinDialog(false);
          }}
        />
      )}

      {createMenu === 'plain' && (
        <CreateBoardDialog
          existingBoards={boards}
          onDismiss={() => setCreateMenu('none')}
          onCreate={(name, topic) => {
            setCreateMenu('none');
            void (async () => {
              const newId = topic.trim().length > 0 ? await createMagicBoard(name, topic) : await createBoard(name);
              if (newId) onClose();
            })();
          }}
          onOpenExistingBoard={openBoard}
        />
      )}

      {createMenu === 'scene' && (
        <VisualSceneDialog
          existingBoards={boards}
          onDismiss={() => setCreateMenu('none')}
          onCreate={(name, imageDataUrl) => {
            setCreateMenu('none');
            void (async () => {
              const newId = await createMagicScene(name, imageDataUrl);
              if (newId) onClose();
            })();
          }}
          onOpenExistingBoard={openBoard}
        />
      )}

      {createMenu === 'quick' && (
        <QuickBoardDialog
          existingBoards={boards}
          onDismiss={() => setCreateMenu('none')}
          onCreate={(name, images) => {
            setCreateMenu('none');
            void (async () => {
              const newId = await createQuickBoard(name, images);
              if (newId) onClose();
            })();
          }}
          onOpenExistingBoard={openBoard}
        />
      )}

      {boardToEdit && (
        <EditBoardDialog
          board={boardToEdit}
          onDismiss={() => setBoardToEdit(null)}
          onSave={(board) => {
            void updateBoard(board);
            setBoardToEdit(null);
          }}
          onDelete={() => {
            void deleteBoard(boardToEdit.id);
            setBoardToEdit(null);
          }}
          onExpand={(itemCount) => {
            void expandBoard(boardToEdit, itemCount);
            setBoardToEdit(null);
          }}
        />
      )}
    </>
  );
}
