import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { AacButton } from '../models';
import { useBoardStore } from '../store/boardStore';
import { EditButtonDialog } from './EditButtonDialog';
import './communicationUI.css';

// React port of ui/components/SelectionBar.kt (caregiver-mode batch actions), wired directly to
// boardStore. Rendered by App.tsx only while isCaregiverMode && selectedButtonIds.size > 0.
//
// Deviations from the Compose original:
// - The caregiver-mode PIN gate itself is out of scope per the migration plan; isCaregiverMode is
//   a plain boolean toggled from App.tsx's header.
//
// Phase 3: the per-button "Edit" action (Kotlin's `onEdit`, enabled only for a single selection)
// is wired below to EditButtonDialog, using boardStore's updateButton/deleteButton.

export function SelectionBar() {
  const { t } = useTranslation();
  const selectedButtonIds = useBoardStore((s) => s.selectedButtonIds);
  const boards = useBoardStore((s) => s.boards);
  const currentBoard = useBoardStore((s) => s.currentBoard);
  const clearSelection = useBoardStore((s) => s.clearSelection);
  const deleteSelectedButtons = useBoardStore((s) => s.deleteSelectedButtons);
  const toggleHideSelectedButtons = useBoardStore((s) => s.toggleHideSelectedButtons);
  const duplicateSelectedButtons = useBoardStore((s) => s.duplicateSelectedButtons);
  const moveSelectedButtonsToBoard = useBoardStore((s) => s.moveSelectedButtonsToBoard);
  const updateButton = useBoardStore((s) => s.updateButton);
  const deleteButton = useBoardStore((s) => s.deleteButton);

  const [confirmDelete, setConfirmDelete] = useState(false);
  const [showMoveDialog, setShowMoveDialog] = useState(false);
  const [editingButton, setEditingButton] = useState<AacButton | null>(null);

  const selectedCount = selectedButtonIds.size;
  if (selectedCount === 0) return null;

  const otherBoards = boards.filter((b) => b.id !== currentBoard?.id);
  const singleSelectedButton =
    selectedCount === 1 ? (currentBoard?.buttons.find((b) => selectedButtonIds.has(b.id)) ?? null) : null;

  return (
    <div className="selection-bar">
      <div className="selection-bar__left">
        <button type="button" className="selection-bar__icon-btn" onClick={clearSelection} aria-label={t('clear_selection')}>
          ✕
        </button>
        <span className="selection-bar__count">
          {t('selection_bar_items_selected', { count: selectedCount })}
        </span>
      </div>

      <div className="selection-bar__right">
        <button
          type="button"
          className="selection-bar__icon-btn"
          onClick={() => singleSelectedButton && setEditingButton(singleSelectedButton)}
          disabled={!singleSelectedButton}
          aria-label={t('edit')}
          title={t('edit')}
        >
          ✎
        </button>
        <button
          type="button"
          className="selection-bar__icon-btn"
          onClick={() => void toggleHideSelectedButtons()}
          aria-label={t('toggle_visibility')}
          title={t('show_hide')}
        >
          👁
        </button>
        <button
          type="button"
          className="selection-bar__icon-btn"
          onClick={() => void duplicateSelectedButtons()}
          aria-label={t('duplicate')}
          title={t('duplicate')}
        >
          ⧉
        </button>
        <button
          type="button"
          className="selection-bar__icon-btn"
          onClick={() => setShowMoveDialog(true)}
          disabled={otherBoards.length === 0}
          aria-label={t('move_to_board')}
          title={t('move_to_board')}
        >
          ➜
        </button>
        <button
          type="button"
          className="selection-bar__icon-btn selection-bar__icon-btn--danger"
          onClick={() => setConfirmDelete(true)}
          aria-label={t('delete')}
          title={t('delete')}
        >
          🗑
        </button>
      </div>

      {confirmDelete && (
        <div className="modal-backdrop" onClick={() => setConfirmDelete(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>{t('selection_bar_delete_title')}</h3>
            <p>{t('selection_bar_delete_message', { count: selectedCount })}</p>
            <div className="modal__actions">
              <button type="button" onClick={() => setConfirmDelete(false)}>
                {t('cancel')}
              </button>
              <button
                type="button"
                className="modal__danger"
                onClick={() => {
                  setConfirmDelete(false);
                  void deleteSelectedButtons();
                }}
              >
                {t('delete')}
              </button>
            </div>
          </div>
        </div>
      )}

      {showMoveDialog && (
        <div className="modal-backdrop" onClick={() => setShowMoveDialog(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>{t('move_to_board_title')}</h3>
            <div className="modal__board-list">
              {otherBoards.map((board) => (
                <button
                  key={board.id}
                  type="button"
                  className="modal__board-item"
                  onClick={() => {
                    setShowMoveDialog(false);
                    void moveSelectedButtonsToBoard(board.id);
                  }}
                >
                  {board.name}
                </button>
              ))}
            </div>
            <button type="button" className="modal__cancel" onClick={() => setShowMoveDialog(false)}>
              {t('cancel')}
            </button>
          </div>
        </div>
      )}

      {editingButton && currentBoard && (
        <EditButtonDialog
          button={editingButton}
          allBoards={boards}
          onDismiss={() => setEditingButton(null)}
          onSave={(updated) => {
            void updateButton(currentBoard.id, updated);
            setEditingButton(null);
            clearSelection();
          }}
          onDelete={() => {
            void deleteButton(currentBoard.id, editingButton.id);
            clearSelection();
          }}
        />
      )}
    </div>
  );
}
