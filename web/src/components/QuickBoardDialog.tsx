import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Camera, CameraResultType, CameraSource } from '@capacitor/camera';

import type { Board } from '../models';
import { useBoardStore } from '../store/boardStore';
import './communicationUI.css';
import './editButtonUI.css';
import './sidebarUI.css';

// React port of ui/components/SidebarContent.kt's QuickBoardDialog: up to 5 photos ->
// boardStore.createQuickBoard (most-frequent AI-identified objects across all photos become
// buttons), plus an AI "suggest name" button (boardStore.suggestBoardName).

const MAX_PHOTOS = 5;

export interface QuickBoardDialogProps {
  existingBoards: Board[];
  onDismiss: () => void;
  onCreate: (name: string, imageDataUrls: string[]) => void;
  onOpenExistingBoard: (board: Board) => void;
}

export function QuickBoardDialog({ existingBoards, onDismiss, onCreate, onOpenExistingBoard }: QuickBoardDialogProps) {
  const { t } = useTranslation();
  const suggestBoardName = useBoardStore((s) => s.suggestBoardName);

  const [name, setName] = useState('');
  const [images, setImages] = useState<string[]>([]);
  const [duplicateBoard, setDuplicateBoard] = useState<Board | null>(null);
  const [isSuggestingName, setIsSuggestingName] = useState(false);

  const trimmedName = name.trim();
  const findDuplicate = () => existingBoards.find((b) => b.name.toLowerCase() === trimmedName.toLowerCase()) ?? null;
  const isDuplicate = trimmedName.length > 0 && findDuplicate() != null;

  const addImage = async (source: CameraSource) => {
    if (images.length >= MAX_PHOTOS) return;
    try {
      const photo = await Camera.getPhoto({ resultType: CameraResultType.DataUrl, source, quality: 80 });
      if (photo.dataUrl) {
        setImages((prev) => (prev.length < MAX_PHOTOS ? [...prev, photo.dataUrl!] : prev));
      }
    } catch {
      // User cancelled -- not an error worth surfacing.
    }
  };

  const removeImage = (index: number) => {
    setImages((prev) => prev.filter((_, i) => i !== index));
  };

  const handleSuggestName = async () => {
    if (images.length === 0) return;
    setIsSuggestingName(true);
    try {
      const suggestion = await suggestBoardName(images);
      if (suggestion.trim().length > 0) setName(suggestion.trim());
    } finally {
      setIsSuggestingName(false);
    }
  };

  const attemptCreate = () => {
    if (trimmedName.length === 0 || images.length === 0) return;
    const duplicate = findDuplicate();
    if (duplicate) {
      setDuplicateBoard(duplicate);
      return;
    }
    onCreate(trimmedName, images);
  };

  if (duplicateBoard) {
    return (
      <div className="modal-backdrop" onClick={() => setDuplicateBoard(null)}>
        <div className="modal" onClick={(e) => e.stopPropagation()}>
          <h3>{t('duplicate_board_name_title')}</h3>
          <p>{t('duplicate_board_name_message', { name: duplicateBoard.name })}</p>
          <div className="modal__actions">
            <button type="button" onClick={() => { setDuplicateBoard(null); setName(''); }}>
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
        <h3>{t('quick_board_title')}</h3>
        <p style={{ fontSize: '0.85rem', color: '#555' }}>
          {t('quick_board_description', { max: MAX_PHOTOS })}
        </p>

        {images.length === 0 ? (
          <div className="sidebar__photo-preview sidebar__photo-preview--empty">
            <span className="edit-button__image-placeholder">✨</span>
            <span>{t('quick_board_no_photos')}</span>
          </div>
        ) : (
          <div className="sidebar__photo-strip">
            {images.map((src, index) => (
              <div key={index} className="sidebar__photo-thumb">
                <img src={src} alt={`Photo ${index + 1}`} />
                <button type="button" className="sidebar__photo-remove" onClick={() => removeImage(index)} aria-label={t('quick_board_remove_photo_aria')}>
                  ✕
                </button>
              </div>
            ))}
          </div>
        )}
        {images.length > 0 && <p className="sidebar__hint-text">{t('quick_board_photos_selected', { count: images.length })}</p>}

        <div className="sidebar__button-row">
          <button
            type="button"
            className="edit-button__full-width-btn"
            onClick={() => void addImage(CameraSource.Photos)}
            disabled={images.length >= MAX_PHOTOS}
          >
            {images.length === 0 ? t('btn_add_photo') : t('btn_add_another')}
          </button>
          <button
            type="button"
            className="edit-button__full-width-btn"
            onClick={() => void addImage(CameraSource.Camera)}
            disabled={images.length >= MAX_PHOTOS}
          >
            {t('btn_take_photo')}
          </button>
        </div>
        {images.length >= MAX_PHOTOS && <p className="sidebar__error-text">{t('quick_board_max_photos_error', { max: MAX_PHOTOS })}</p>}

        <label className="edit-button__field" style={{ marginTop: 8 }}>
          <span>{t('board_name')}</span>
          <div style={{ display: 'flex', gap: 6 }}>
            <input type="text" value={name} onChange={(e) => setName(e.target.value)} style={{ flex: 1 }} />
            {images.length > 0 && (
              <button
                type="button"
                className="sidebar__suggest-name-btn"
                onClick={() => void handleSuggestName()}
                disabled={isSuggestingName}
                title={t('quick_board_suggest_name_aria')}
                aria-label={t('quick_board_suggest_name_aria')}
              >
                {isSuggestingName ? '…' : '✨'}
              </button>
            )}
          </div>
        </label>
        {isDuplicate && <p className="sidebar__error-text">{t('board_name_error')}</p>}

        <div className="modal__actions">
          <button type="button" onClick={onDismiss}>
            {t('cancel')}
          </button>
          <button type="button" onClick={attemptCreate} disabled={trimmedName.length === 0 || images.length === 0}>
            {t('create')}
          </button>
        </div>
      </div>
    </div>
  );
}
