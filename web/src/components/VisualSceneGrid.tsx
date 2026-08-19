import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { AacButton, Board } from '../models';
import { useBoardStore } from '../store/boardStore';
import { EditButtonDialog } from './EditButtonDialog';
import './visualSceneUI.css';

// React port of ui/components/VisualSceneGrid.kt: a background photo with percentage-based
// bounding-box hotspots (from GeminiService.identifyMultipleItems, via boardStore.createMagicScene)
// rendered as absolutely-positioned <div> overlays -- pure layout math, no canvas needed.
//
// Deviation from the Compose original: VisualSceneGrid.kt only took an onButtonClick callback (the
// caregiver-vs-tap branching lived in MainActivity.kt). This port owns that branching itself
// (like CommunicationGrid.tsx does for its own EditButtonDialog wiring), so App.tsx can swap
// between CommunicationGrid/VisualSceneGrid based on `board.backgroundImagePath` with one prop
// contract, the same way MainActivity.kt does.

export interface VisualSceneGridProps {
  board: Board;
}

export function VisualSceneGrid({ board }: VisualSceneGridProps) {
  const { t } = useTranslation();
  const isCaregiverMode = useBoardStore((s) => s.isCaregiverMode);
  const tapButton = useBoardStore((s) => s.tapButton);
  const boards = useBoardStore((s) => s.boards);
  const updateButton = useBoardStore((s) => s.updateButton);
  const deleteButton = useBoardStore((s) => s.deleteButton);

  const [editingButton, setEditingButton] = useState<AacButton | null>(null);

  const handleClick = (button: AacButton) => {
    if (isCaregiverMode) {
      setEditingButton(button);
    } else {
      tapButton(button);
    }
  };

  return (
    <>
      <div className="visual-scene">
        {board.backgroundImagePath && (
          <img className="visual-scene__background" src={board.backgroundImagePath} alt={t('visual_scene_background_alt')} draggable={false} />
        )}

        {board.buttons.map((button) => {
          const box = button.boundingBox;
          if (!box || box.length !== 4) return null;
          const [ymin, xmin, ymax, xmax] = box;

          return (
            <button
              key={button.id}
              type="button"
              className={`visual-scene__hotspot${isCaregiverMode ? ' visual-scene__hotspot--caregiver' : ''}`}
              style={{
                top: `${ymin * 100}%`,
                left: `${xmin * 100}%`,
                width: `${Math.max(xmax - xmin, 0.01) * 100}%`,
                height: `${Math.max(ymax - ymin, 0.01) * 100}%`,
              }}
              onClick={() => handleClick(button)}
            >
              {(isCaregiverMode || button.label.length > 0) && (
                <span className="visual-scene__hotspot-label">{button.label}</span>
              )}
            </button>
          );
        })}
      </div>

      {editingButton && (
        <EditButtonDialog
          button={editingButton}
          allBoards={boards}
          onDismiss={() => setEditingButton(null)}
          onSave={(updated) => {
            void updateButton(board.id, updated);
            setEditingButton(null);
          }}
          onDelete={() => {
            void deleteButton(board.id, editingButton.id);
            setEditingButton(null);
          }}
        />
      )}
    </>
  );
}
