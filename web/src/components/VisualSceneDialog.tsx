import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Camera, CameraResultType, CameraSource } from '@capacitor/camera';

import type { Board } from '../models';
import './communicationUI.css';
import './editButtonUI.css';
import './sidebarUI.css';

// React port of ui/components/SidebarContent.kt's VisualSceneDialog: one background photo ->
// boardStore.createMagicScene (AI object-detection hotspots).
//
// Camera capture uses @capacitor/camera's system picker per the migration plan's locked-in
// decision (no custom in-app pinch-zoom preview) -- same pattern as EditButtonDialog.tsx.

export interface VisualSceneDialogProps {
  existingBoards: Board[];
  onDismiss: () => void;
  onCreate: (name: string, imageDataUrl: string) => void;
  onOpenExistingBoard: (board: Board) => void;
}

export function VisualSceneDialog({ existingBoards, onDismiss, onCreate, onOpenExistingBoard }: VisualSceneDialogProps) {
  const { t } = useTranslation();
  const [name, setName] = useState('');
  const [imageDataUrl, setImageDataUrl] = useState<string | null>(null);
  const [duplicateBoard, setDuplicateBoard] = useState<Board | null>(null);

  const trimmedName = name.trim();
  const findDuplicate = () => existingBoards.find((b) => b.name.toLowerCase() === trimmedName.toLowerCase()) ?? null;
  const isDuplicate = trimmedName.length > 0 && findDuplicate() != null;

  const pickImage = async (source: CameraSource) => {
    try {
      const photo = await Camera.getPhoto({ resultType: CameraResultType.DataUrl, source, quality: 85 });
      if (photo.dataUrl) {
        setImageDataUrl(photo.dataUrl);
      }
    } catch {
      // User cancelled -- not an error worth surfacing.
    }
  };

  const attemptCreate = () => {
    if (trimmedName.length === 0 || !imageDataUrl) return;
    const duplicate = findDuplicate();
    if (duplicate) {
      setDuplicateBoard(duplicate);
      return;
    }
    onCreate(trimmedName, imageDataUrl);
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
        <h3>{t('new_visual_scene')}</h3>
        <p style={{ fontSize: '0.85rem', color: '#555' }}>
          {t('visual_scene_desc')}
        </p>

        <div
          className="sidebar__photo-preview"
          onClick={() => void pickImage(CameraSource.Photos)}
          role="button"
          tabIndex={0}
        >
          {imageDataUrl ? (
            <img src={imageDataUrl} alt={t('scene_preview_alt')} />
          ) : (
            <span className="edit-button__image-placeholder">📷</span>
          )}
        </div>

        <div className="sidebar__button-row">
          <button type="button" className="edit-button__full-width-btn" onClick={() => void pickImage(CameraSource.Photos)}>
            {t('btn_select_gallery')}
          </button>
          <button type="button" className="edit-button__full-width-btn" onClick={() => void pickImage(CameraSource.Camera)}>
            {t('btn_take_photo')}
          </button>
        </div>

        <label className="edit-button__field" style={{ marginTop: 8 }}>
          <span>{t('scene_name')}</span>
          <input type="text" value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        {isDuplicate && <p className="sidebar__error-text">{t('board_name_error')}</p>}

        <div className="modal__actions">
          <button type="button" onClick={onDismiss}>
            {t('cancel')}
          </button>
          <button type="button" onClick={attemptCreate} disabled={trimmedName.length === 0 || !imageDataUrl}>
            {t('create')}
          </button>
        </div>
      </div>
    </div>
  );
}
