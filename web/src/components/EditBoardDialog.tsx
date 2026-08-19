import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { Board } from '../models';
import { GenerateItemsDialog } from './GenerateItemsDialog';
import { SymbolSearchDialog } from './SymbolSearchDialog';
import './communicationUI.css';
import './editButtonUI.css';
import './sidebarUI.css';

// React port of ui/components/SidebarContent.kt's EditBoardDialog: rename, re-icon (via
// SymbolSearchDialog), expand with AI-generated items (via GenerateItemsDialog ->
// boardStore.expandBoard), and delete (guarded against the Home board).

export interface EditBoardDialogProps {
  board: Board;
  onDismiss: () => void;
  onSave: (board: Board) => void;
  onDelete: () => void;
  onExpand: (itemCount: number) => void;
}

export function EditBoardDialog({ board, onDismiss, onSave, onDelete, onExpand }: EditBoardDialogProps) {
  const { t } = useTranslation();
  const [name, setName] = useState(board.name);
  const [iconPath, setIconPath] = useState(board.iconPath);
  const [showIconSearch, setShowIconSearch] = useState(false);
  const [showGenerateDialog, setShowGenerateDialog] = useState(false);
  const [showDeleteConfirmation, setShowDeleteConfirmation] = useState(false);

  const isVisualScene = Boolean(board.backgroundImagePath);
  const isHome = board.id === 'home';

  return (
    <>
      <div className="modal-backdrop" onClick={onDismiss}>
        <div className="modal" onClick={(e) => e.stopPropagation()}>
          <h3>{t('edit_board_title')}</h3>

          <div className="edit-button__image-section">
            <div
              className="edit-button__image-box"
              onClick={() => setShowIconSearch(true)}
              role="button"
              tabIndex={0}
            >
              {iconPath ? <img src={iconPath} alt="" /> : <span className="edit-button__image-placeholder">🗂</span>}
              <span className="edit-button__image-edit-btn" aria-hidden="true">
                ✎
              </span>
            </div>
          </div>

          <label className="edit-button__field">
            <span>{t('board_name')}</span>
            <input type="text" value={name} onChange={(e) => setName(e.target.value)} />
          </label>

          {!isVisualScene && (
            <button type="button" className="edit-button__full-width-btn" onClick={() => setShowGenerateDialog(true)}>
              {t('expand_board')}
            </button>
          )}

          {!isHome && (
            <button
              type="button"
              className="edit-button__full-width-btn sidebar__danger-btn"
              onClick={() => setShowDeleteConfirmation(true)}
            >
              {t('delete_board_button')}
            </button>
          )}

          <div className="modal__actions">
            <button type="button" onClick={onDismiss}>
              {t('cancel')}
            </button>
            <button
              type="button"
              onClick={() => onSave({ ...board, name: name.trim().length > 0 ? name.trim() : board.name, iconPath })}
            >
              {t('save')}
            </button>
          </div>
        </div>
      </div>

      {showIconSearch && (
        <SymbolSearchDialog
          initialQuery={name}
          onDismiss={() => setShowIconSearch(false)}
          onSymbolSelected={(url) => {
            setIconPath(url);
            setShowIconSearch(false);
          }}
        />
      )}

      {showGenerateDialog && (
        <GenerateItemsDialog
          onDismiss={() => setShowGenerateDialog(false)}
          onGenerate={(count) => {
            setShowGenerateDialog(false);
            onExpand(count);
          }}
        />
      )}

      {showDeleteConfirmation && (
        <div className="modal-backdrop" onClick={() => setShowDeleteConfirmation(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>{t('delete_board_confirm_title')}</h3>
            <p>{t('delete_board_message', { name: board.name })}</p>
            <div className="modal__actions">
              <button type="button" onClick={() => setShowDeleteConfirmation(false)}>
                {t('cancel')}
              </button>
              <button
                type="button"
                className="modal__danger"
                onClick={() => {
                  setShowDeleteConfirmation(false);
                  onDelete();
                }}
              >
                {t('delete')}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
