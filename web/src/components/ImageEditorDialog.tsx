import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import './editButtonUI.css';

// React port of ui/components/ImageEditorDialog.kt (Compose Canvas + detectTransformGestures
// pinch/pan/zoom crop UI).
//
// JUDGMENT CALL (per the task brief): implemented as a plain HTML <canvas> + Pointer Events,
// rather than pulling in `react-easy-crop`. Reasons: (1) no crop library is an existing
// dependency yet, and this dialog's needs (a fixed centered square crop window, pinch-zoom,
// pan, a live percentage readout, output a fixed-size PNG) are a near-1:1 structural match for
// the Kotlin Canvas implementation being ported -- a direct port keeps the two implementations
// easy to compare line-by-line; (2) the Pointer Events API natively unifies mouse/touch/pen
// input for the two-pointer-pinch + one-pointer-pan gesture this needs, so no extra gesture
// library is pulled in either.
//
// Math note: this does not attempt bit-for-bit parity with Compose's DrawTransform matrix
// composition order (translate-then-scale-around-pivot) -- that's an internal implementation
// detail of a renderer this is no longer using. Instead, the draw function and the crop function
// below share one deliberately simple, self-consistent mapping (image pixel <-> canvas pixel),
// so "what you see is what gets cropped" holds exactly, which is the property that actually
// matters for this dialog.

const MIN_SCALE = 0.5;
const MAX_SCALE = 5;
const OUTPUT_SIZE = 512;
const CROP_FRACTION = 0.8; // Crop square = 80% of the canvas's shorter side, same as the Kotlin source.

export interface ImageEditorDialogProps {
  /** A data URL or (same-origin/CORS-enabled) URL of the source image to crop. */
  imageSrc: string;
  onDismiss: () => void;
  /** Called with a cropped 512x512 PNG data URL. */
  onSave: (croppedDataUrl: string) => void;
}

interface Point {
  x: number;
  y: number;
}

export function ImageEditorDialog({ imageSrc, onDismiss, onSave }: ImageEditorDialogProps) {
  const { t } = useTranslation();
  const containerRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const imageRef = useRef<HTMLImageElement | null>(null);
  const pointersRef = useRef<Map<number, Point>>(new Map());
  const pinchStartRef = useRef<{ distance: number; scale: number } | null>(null);
  const panStartRef = useRef<{ pointer: Point; offset: Point } | null>(null);

  const [imageLoaded, setImageLoaded] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [scale, setScale] = useState(1);
  const [offset, setOffset] = useState<Point>({ x: 0, y: 0 });
  // Bumped on every gesture/resize to trigger a redraw without re-running the whole effect chain.
  const [, forceRedraw] = useState(0);

  // Load the source image once.
  useEffect(() => {
    let cancelled = false;
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      if (cancelled) return;
      imageRef.current = img;
      setImageLoaded(true);
    };
    img.onerror = () => {
      if (cancelled) return;
      setLoadError(t('image_editor_load_error'));
    };
    img.src = imageSrc;
    return () => {
      cancelled = true;
    };
  }, [imageSrc]);

  // Draw + redraw on scale/offset/resize changes.
  useEffect(() => {
    if (!imageLoaded) return;
    const canvas = canvasRef.current;
    const container = containerRef.current;
    const img = imageRef.current;
    if (!canvas || !container || !img) return;

    const draw = () => {
      const rect = container.getBoundingClientRect();
      const dpr = window.devicePixelRatio || 1;
      canvas.width = rect.width * dpr;
      canvas.height = rect.height * dpr;
      canvas.style.width = `${rect.width}px`;
      canvas.style.height = `${rect.height}px`;

      const ctx = canvas.getContext('2d');
      if (!ctx) return;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, rect.width, rect.height);
      ctx.fillStyle = '#000';
      ctx.fillRect(0, 0, rect.width, rect.height);

      const { drawX, drawY, drawW, drawH } = imageDrawRect(rect.width, rect.height, img, scale, offset);
      ctx.drawImage(img, drawX, drawY, drawW, drawH);

      // Dim everything outside the crop square, then outline it -- same visual treatment as the
      // Kotlin source's four dimming rects + white border stroke.
      const { left, top, size } = cropRectFor(rect.width, rect.height);
      ctx.fillStyle = 'rgba(0, 0, 0, 0.7)';
      ctx.fillRect(0, 0, rect.width, top); // top
      ctx.fillRect(0, top + size, rect.width, rect.height - (top + size)); // bottom
      ctx.fillRect(0, top, left, size); // left
      ctx.fillRect(left + size, top, rect.width - (left + size), size); // right

      ctx.strokeStyle = '#fff';
      ctx.lineWidth = 2;
      ctx.strokeRect(left + 1, top + 1, size - 2, size - 2);
    };

    draw();

    const resizeObserver = new ResizeObserver(() => draw());
    resizeObserver.observe(container);
    return () => resizeObserver.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [imageLoaded, scale, offset]);

  const clampScale = (s: number) => Math.min(MAX_SCALE, Math.max(MIN_SCALE, s));

  const onPointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    (e.target as Element).setPointerCapture(e.pointerId);
    pointersRef.current.set(e.pointerId, { x: e.clientX, y: e.clientY });

    if (pointersRef.current.size === 1) {
      panStartRef.current = { pointer: { x: e.clientX, y: e.clientY }, offset };
    } else if (pointersRef.current.size === 2) {
      const pts = [...pointersRef.current.values()];
      pinchStartRef.current = { distance: distanceBetween(pts[0], pts[1]), scale };
      panStartRef.current = null;
    }
  };

  const onPointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!pointersRef.current.has(e.pointerId)) return;
    pointersRef.current.set(e.pointerId, { x: e.clientX, y: e.clientY });

    if (pointersRef.current.size === 2 && pinchStartRef.current) {
      const pts = [...pointersRef.current.values()];
      const newDistance = distanceBetween(pts[0], pts[1]);
      const ratio = newDistance / Math.max(pinchStartRef.current.distance, 1);
      setScale(clampScale(pinchStartRef.current.scale * ratio));
    } else if (pointersRef.current.size === 1 && panStartRef.current) {
      const dx = e.clientX - panStartRef.current.pointer.x;
      const dy = e.clientY - panStartRef.current.pointer.y;
      setOffset({ x: panStartRef.current.offset.x + dx, y: panStartRef.current.offset.y + dy });
    }
  };

  const endPointer = (e: React.PointerEvent<HTMLDivElement>) => {
    pointersRef.current.delete(e.pointerId);
    if (pointersRef.current.size === 1) {
      const [remaining] = [...pointersRef.current.values()];
      panStartRef.current = { pointer: remaining, offset };
      pinchStartRef.current = null;
    } else if (pointersRef.current.size === 0) {
      panStartRef.current = null;
      pinchStartRef.current = null;
    }
  };

  const zoomBy = (delta: number) => {
    setScale((s) => clampScale(s + delta));
    forceRedraw((n) => n + 1);
  };

  const handleSave = () => {
    const container = containerRef.current;
    const img = imageRef.current;
    if (!container || !img) return;

    const rect = container.getBoundingClientRect();
    const dataUrl = cropToDataUrl(rect.width, rect.height, img, scale, offset);
    onSave(dataUrl);
  };

  return (
    <div className="image-editor-overlay">
      {loadError ? (
        <div className="image-editor__error">
          {loadError}
          <button type="button" onClick={onDismiss}>
            {t('close')}
          </button>
        </div>
      ) : (
        <>
          <div
            ref={containerRef}
            className="image-editor__stage"
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={endPointer}
            onPointerCancel={endPointer}
            onPointerLeave={endPointer}
          >
            <canvas ref={canvasRef} className="image-editor__canvas" />
          </div>

          <div className="image-editor__controls">
            <button type="button" className="image-editor__round-btn" onClick={onDismiss} aria-label={t('image_editor_cancel_aria')}>
              ✕
            </button>

            <div className="image-editor__zoom-group">
              <button type="button" className="image-editor__zoom-btn" onClick={() => zoomBy(-0.2)} aria-label={t('image_editor_zoom_out_aria')}>
                −
              </button>
              <span className="image-editor__zoom-label">{Math.round(scale * 100)}%</span>
              <button type="button" className="image-editor__zoom-btn" onClick={() => zoomBy(0.2)} aria-label={t('image_editor_zoom_in_aria')}>
                +
              </button>
            </div>

            <button
              type="button"
              className="image-editor__round-btn image-editor__round-btn--primary"
              onClick={handleSave}
              disabled={!imageLoaded}
              aria-label={t('image_editor_save_crop_aria')}
            >
              ✓
            </button>
          </div>
        </>
      )}
    </div>
  );
}

function distanceBetween(a: Point, b: Point): number {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

/** The fixed centered square crop window, in canvas CSS pixels. */
function cropRectFor(canvasWidth: number, canvasHeight: number): { left: number; top: number; size: number } {
  const size = Math.min(canvasWidth, canvasHeight) * CROP_FRACTION;
  return { left: (canvasWidth - size) / 2, top: (canvasHeight - size) / 2, size };
}

/**
 * Where the (scaled + panned) image should be drawn on the canvas. `scale` is the user zoom
 * factor on top of a "cover the crop square" base scale, and `offset` is the user pan in CSS
 * pixels, both centered on the canvas.
 */
function imageDrawRect(
  canvasWidth: number,
  canvasHeight: number,
  img: HTMLImageElement,
  scale: number,
  offset: Point,
): { drawX: number; drawY: number; drawW: number; drawH: number } {
  const { size: squareSize } = cropRectFor(canvasWidth, canvasHeight);
  const baseScale = Math.max(squareSize / img.naturalWidth, squareSize / img.naturalHeight);
  const effectiveScale = baseScale * scale;

  const drawW = img.naturalWidth * effectiveScale;
  const drawH = img.naturalHeight * effectiveScale;
  const centerX = canvasWidth / 2 + offset.x;
  const centerY = canvasHeight / 2 + offset.y;

  return { drawX: centerX - drawW / 2, drawY: centerY - drawH / 2, drawW, drawH };
}

/** Renders the cropped square (mapped from canvas space back to source-image pixel space) into a fixed OUTPUT_SIZE PNG. */
function cropToDataUrl(
  canvasWidth: number,
  canvasHeight: number,
  img: HTMLImageElement,
  scale: number,
  offset: Point,
): string {
  const { left, top, size } = cropRectFor(canvasWidth, canvasHeight);
  const { drawX, drawY, drawW, drawH } = imageDrawRect(canvasWidth, canvasHeight, img, scale, offset);

  // Map the crop square's canvas-space corners back into source-image pixel coordinates.
  const toImageSpace = (cx: number, cy: number): Point => ({
    x: ((cx - drawX) / drawW) * img.naturalWidth,
    y: ((cy - drawY) / drawH) * img.naturalHeight,
  });

  const topLeft = toImageSpace(left, top);
  const bottomRight = toImageSpace(left + size, top + size);

  const sx = topLeft.x;
  const sy = topLeft.y;
  const sw = Math.max(1, bottomRight.x - topLeft.x);
  const sh = Math.max(1, bottomRight.y - topLeft.y);

  const output = document.createElement('canvas');
  output.width = OUTPUT_SIZE;
  output.height = OUTPUT_SIZE;
  const ctx = output.getContext('2d');
  if (!ctx) return img.src;
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  // Source rect may extend beyond the image bounds (user panned/zoomed out past the edges) --
  // per the Canvas spec this is allowed and the out-of-bounds area renders transparent, matching
  // the Kotlin source's TRANSPARENT-cleared output canvas.
  ctx.drawImage(img, sx, sy, sw, sh, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE);

  return output.toDataURL('image/png');
}
