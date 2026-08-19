import { useMemo, useState, type CSSProperties } from 'react';
import { useTranslation } from 'react-i18next';
import {
  DndContext,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core';
import { SortableContext, arrayMove, rectSortingStrategy, useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { Haptics, ImpactStyle } from '@capacitor/haptics';

import type { AacButton } from '../models';
import { createAacButton, isLinkToBoardAction } from '../models';
import { useBoardStore } from '../store/boardStore';
import { getRecommendedColumns, useWindowSize } from '../hooks/useWindowSize';
import { EditButtonDialog } from './EditButtonDialog';
import './communicationUI.css';

// React port of ui/components/CommunicationGrid.kt, scoped to what Phase 2 needs (see
// boardStore.ts's module doc comment for what's deliberately deferred -- onGrammarRequest is
// still out of scope here, Phase 4 territory).
//
// Phase 3: the "+ add new item" placeholder tile is wired below to EditButtonDialog /
// boardStore.addButtonToBoard, visible only in caregiver mode.
//
// Deviations from the Compose original:
// - Reordering uses @dnd-kit's PointerSensor with a `delay` + `tolerance` activation constraint
//   instead of `sh.calvin.reorderable`'s longPressDraggableHandle + LazyGrid scroll-state
//   plumbing. This is dnd-kit's standard idiom for "long-press to drag, plain scroll otherwise"
//   and needs no separate scroll-vs-drag bookkeeping the way the Compose version did (no
//   onInteractionStart/End hooks here -- those existed in the Kotlin source solely to pause the
//   grammar-check debounce during a scroll gesture, and that grammar-correction UI flow is out of
//   scope for this phase per the migration plan).
// - In caregiver mode, tapping a button toggles its selection (rather than opening an edit
//   dialog, which doesn't exist yet -- Phase 3). A long-press-drag also selects the button it
//   started on, mirroring the Kotlin `LaunchedEffect(isDragging)` behavior of selecting on drag
//   start.

export interface CommunicationGridProps {
  /** Column count for a Compact-width viewport; scaled up per getRecommendedColumns() above that. */
  baseColumns?: number;
}

export function CommunicationGrid({ baseColumns = 4 }: CommunicationGridProps) {
  const { t } = useTranslation();
  const board = useBoardStore((s) => s.currentBoard);
  const boards = useBoardStore((s) => s.boards);
  const isCaregiverMode = useBoardStore((s) => s.isCaregiverMode);
  const selectedButtonIds = useBoardStore((s) => s.selectedButtonIds);
  const tapButton = useBoardStore((s) => s.tapButton);
  const toggleButtonSelection = useBoardStore((s) => s.toggleButtonSelection);
  const reorderButtons = useBoardStore((s) => s.reorderButtons);
  const addButtonToBoard = useBoardStore((s) => s.addButtonToBoard);

  const [newButtonDraft, setNewButtonDraft] = useState<AacButton | null>(null);

  const windowSize = useWindowSize();
  const columns = getRecommendedColumns(baseColumns, windowSize);

  const visibleButtons = useMemo(() => {
    const buttons = board?.buttons ?? [];
    return isCaregiverMode ? buttons : buttons.filter((b) => !b.hidden && (b.label.length > 0 || !!b.iconPath));
  }, [board, isCaregiverMode]);

  const dragEnabled = isCaregiverMode;

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { delay: 300, tolerance: 8 },
    }),
  );

  const handleClick = (button: AacButton) => {
    if (isCaregiverMode) {
      toggleButtonSelection(button.id);
    } else {
      tapButton(button);
    }
  };

  const handleDragStart = (event: DragStartEvent) => {
    const button = visibleButtons.find((b) => b.id === event.active.id);
    if (!button) return;
    void Haptics.impact({ style: ImpactStyle.Medium }).catch(() => {});
    if (!selectedButtonIds.has(button.id)) {
      toggleButtonSelection(button.id);
    }
  };

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!board || !over || active.id === over.id) return;
    const oldIndex = visibleButtons.findIndex((b) => b.id === active.id);
    const newIndex = visibleButtons.findIndex((b) => b.id === over.id);
    if (oldIndex === -1 || newIndex === -1) return;
    void reorderButtons(board.id, arrayMove(visibleButtons, oldIndex, newIndex));
  };

  const gridStyle: CSSProperties = {
    gridTemplateColumns: `repeat(${columns}, 1fr)`,
  };

  // Caregiver mode always shows the "+ add" tile, even on an otherwise-empty board, so a new
  // board can be populated from scratch.
  const showAddTile = isCaregiverMode && board != null;

  if (visibleButtons.length === 0 && !showAddTile) {
    return (
      <div className="communication-grid-empty">{board ? t('grid_no_buttons') : t('grid_no_board_loaded')}</div>
    );
  }

  const grid = (
    <div style={gridStyle} className="communication-grid">
      {visibleButtons.map((button) => (
        <GridCell
          key={button.id}
          button={button}
          isSelected={selectedButtonIds.has(button.id)}
          dragEnabled={dragEnabled}
          onClick={() => handleClick(button)}
        />
      ))}
      {showAddTile && (
        <AddButtonTile
          label={t('grid_add_button_aria')}
          onClick={() => setNewButtonDraft(createAacButton({ id: generateId(`${board.id}_btn`), label: '' }))}
        />
      )}
    </div>
  );

  const gridElement = !dragEnabled ? (
    grid
  ) : (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragStart={handleDragStart} onDragEnd={handleDragEnd}>
      <SortableContext items={visibleButtons.map((b) => b.id)} strategy={rectSortingStrategy}>
        {grid}
      </SortableContext>
    </DndContext>
  );

  return (
    <>
      {gridElement}
      {newButtonDraft && board && (
        <EditButtonDialog
          button={newButtonDraft}
          allBoards={boards}
          isNew
          onDismiss={() => setNewButtonDraft(null)}
          onSave={(button) => {
            void addButtonToBoard(board.id, button);
            setNewButtonDraft(null);
          }}
          onDelete={() => setNewButtonDraft(null)}
        />
      )}
    </>
  );
}

function generateId(prefix: string): string {
  return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;
}

function AddButtonTile({ onClick, label }: { onClick: () => void; label: string }) {
  return (
    <button type="button" className="aac-button-tile aac-button-tile--add" onClick={onClick} aria-label={label}>
      <span className="aac-button-tile__add-icon" aria-hidden="true">
        +
      </span>
    </button>
  );
}

function GridCell({
  button,
  isSelected,
  dragEnabled,
  onClick,
}: {
  button: AacButton;
  isSelected: boolean;
  dragEnabled: boolean;
  onClick: () => void;
}) {
  const sortable = useSortable({ id: button.id, disabled: !dragEnabled });
  const style: CSSProperties = {
    transform: CSS.Transform.toString(sortable.transform),
    transition: sortable.transition,
  };

  return (
    <div
      ref={sortable.setNodeRef}
      style={style}
      {...(dragEnabled ? sortable.attributes : {})}
      {...(dragEnabled ? sortable.listeners : {})}
    >
      <AacButtonTile button={button} isSelected={isSelected} isDragging={sortable.isDragging} onClick={onClick} />
    </div>
  );
}

function AacButtonTile({
  button,
  isSelected,
  isDragging,
  onClick,
}: {
  button: AacButton;
  isSelected: boolean;
  isDragging: boolean;
  onClick: () => void;
}) {
  const [pressed, setPressed] = useState(false);
  const isPlaceholder = button.label.length === 0 && !button.iconPath;
  const isLink = isLinkToBoardAction(button.action);

  return (
    <button
      type="button"
      className={[
        'aac-button-tile',
        isSelected && 'aac-button-tile--selected',
        button.hidden && 'aac-button-tile--hidden',
        isDragging && 'aac-button-tile--dragging',
      ]
        .filter(Boolean)
        .join(' ')}
      style={{ background: colorFromPacked(button.backgroundColor), transform: pressed ? 'scale(0.95)' : undefined }}
      onPointerDown={() => setPressed(true)}
      onPointerUp={() => setPressed(false)}
      onPointerLeave={() => setPressed(false)}
      onClick={onClick}
    >
      <div className="aac-button-tile__symbol">
        {button.iconPath ? (
          <img src={button.iconPath} alt="" draggable={false} />
        ) : !isPlaceholder ? (
          <span className="aac-button-tile__initial" aria-hidden="true">
            {button.label.charAt(0)}
          </span>
        ) : null}
        {isSelected && (
          <span className="aac-button-tile__check" aria-hidden="true">
            ✓
          </span>
        )}
        {isLink && (
          <span className="aac-button-tile__link" aria-hidden="true">
            ›
          </span>
        )}
      </div>
      {!isPlaceholder && <div className="aac-button-tile__label">{button.label}</div>}
    </button>
  );
}

/** Unpacks an ARGB `number` (see models/AacButton.ts) into a CSS rgba() string. */
function colorFromPacked(packed: number): string {
  const a = (packed >>> 24) & 0xff;
  const r = (packed >>> 16) & 0xff;
  const g = (packed >>> 8) & 0xff;
  const b = packed & 0xff;
  return `rgba(${r}, ${g}, ${b}, ${a / 255})`;
}
