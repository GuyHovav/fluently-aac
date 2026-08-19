import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { Board } from '../models';
import './communicationUI.css';
import './sidebarUI.css';

// React port of ui/components/SidebarContent.kt's CreateBoardDialog: board name + optional topic.
// A blank topic creates a plain empty board (boardStore.createBoard); a non-blank topic creates
// an AI-generated "Magic Board" (boardStore.createMagicBoard) -- same branching MainActivity.kt's
// onCreateBoard callback does.

export interface CreateBoardDialogProps {
  existingBoards: Board[];
  onDismiss: () => void;
  /** Empty topic -> plain board; non-empty topic -> AI-generated Magic Board. */
  onCreate: (name: string, topic: string) => void;
  onOpenExistingBoard: (board: Board) => void;
}

export function CreateBoardDialog({ existingBoards, onDismiss, onCreate, onOpenExistingBoard }: CreateBoardDialogProps) {
  const { t } = useTranslation();
  const [name, setName] = useState('');
  const [topic, setTopic] = useState('');
  const [duplicateBoard, setDuplicateBoard] = useState<Board | null>(null);

  const trimmedName = name.trim();
  const findDuplicate = () => existingBoards.find((b) => b.name.toLowerCase() === trimmedName.toLowerCase()) ?? null;
  const isDuplicate = trimmedName.length > 0 && findDuplicate() != null;

  const attemptCreate = () => {
    if (trimmedName.length === 0) return;
    const duplicate = findDuplicate();
    if (duplicate) {
      setDuplicateBoard(duplicate);
      return;
    }
    onCreate(trimmedName, topic);
  };

  if (duplicateBoard) {
    return (
      <div className="modal-backdrop" onClick={() => setDuplicateBoard(null)}>
        <div className="modal" onClick={(e) => e.stopPropagation()}>
          <h3>{t('duplicate_board_name_title')}</h3>
          <p>{t('duplicate_board_name_message', { name: duplicateBoard.name })}</p>
          <div className="modal__actions">
            <button
              type="button"
              onClick={() => {
                setDuplicateBoard(null);
                setName('');
              }}
            >
              {t('choose_different_name')}
            </button>
            <button
              type="button"
              onClick={() => {
                onOpenExistingBoard(duplicateBoard);
                onDismiss();
              }}
            >
              {t('open_existing_board')}
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="modal-backdrop" onClick={onDismiss}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>{t('create_new_board')}</h3>

        <label className="edit-button__field">
          <span>{t('board_name')}</span>
          <input type="text" value={name} onChange={(e) => setName(e.target.value)} autoFocus />
        </label>
        {isDuplicate && <p className="sidebar__error-text">{t('board_name_error')}</p>}

        <label className="edit-button__field" style={{ marginTop: 8 }}>
          <span>{t('create_board_topic_optional')}</span>
          <input
            type="text"
            value={topic}
            onChange={(e) => setTopic(e.target.value)}
            placeholder={t('create_board_topic_hint')}
          />
        </label>
        {topic.trim().length > 0 && (
          <p className="sidebar__hint-text">{t('magic_board_will_be_generated')}</p>
        )}

        <div className="modal__actions">
          <button type="button" onClick={onDismiss}>
            {t('cancel')}
          </button>
          <button type="button" onClick={attemptCreate} disabled={trimmedName.length === 0}>
            {topic.trim().length > 0 ? t('magic_create') : t('create')}
          </button>
        </div>
      </div>
    </div>
  );
}
