import { useMemo, useRef, useState } from 'react';
import { Camera, CameraResultType, CameraSource } from '@capacitor/camera';
import { useTranslation } from 'react-i18next';

import type { AacButton, Board } from '../models';
import { ButtonAction, isLaunchAppAction, isLinkToBoardAction } from '../models';
import { ArasaacService, CompositeSymbolService, GeminiService, GlobalSymbolsService, GoogleImageService } from '../services';
import type { GeminiImageInput } from '../services';
import { useSettingsStore } from '../store/settingsStore';
import { AppPickerDialog } from './AppPickerDialog';
import { ImageEditorDialog } from './ImageEditorDialog';
import { SymbolSearchDialog } from './SymbolSearchDialog';
import './communicationUI.css';
import './editButtonUI.css';

// React port of ui/components/EditButtonDialog.kt -- the per-button editor: label/speech text,
// icon source (camera / gallery / AI symbol search / manual crop), color, action type (Speak /
// LinkToBoard / LaunchApp / ClearSentence / DeleteLastWord), hidden toggle, and a topic field.
//
// Deviations from the Compose original:
// - Action type: the Kotlin file shown only toggled between Speak and LinkToBoard (a comment
//   there even says topic support was "removed"). This port implements the full 5-way
//   ButtonAction union the web model already supports (LaunchApp via AppPickerDialog/AppLauncher,
//   ClearSentence, DeleteLastWord) and keeps the `topic` field (used by boardStore's
//   updatePredictions()-equivalent board-context wiring, and present on the AacButton model),
//   since both are already first-class in this port's data model.
// - Camera capture uses `@capacitor/camera`'s system picker per the migration plan's locked-in
//   decision (no custom in-app pinch-zoom preview) -- separate "Camera"/"Gallery" buttons map to
//   CameraSource.Camera / CameraSource.Photos, matching the Kotlin UI's two distinct buttons.
// - Image storage: captured/picked photos and AI/manual crops are stored as data: URL strings
//   directly on `button.iconPath` (see AacButton.iconPath's doc comment: "Path to a local asset
//   or a URL" -- a data URL is a drop-in fit for that same string field). Symbol-search results
//   keep their original remote https URL untouched, exactly like the Kotlin version's
//   `imageUri = Uri.parse(url)`. This app has no backend/storage wiring yet (Phase 5), and Dexie
//   embeds `Board.buttons` as a plain array via structured clone (see models/Board.ts's doc
//   comment) -- a data URL string needs no extra table, foreign key, or blob lifecycle management
//   to ride along with it, which a separate Dexie blob table would require for comparatively
//   little benefit at this app's image sizes (single small PNGs, not bulk media).

const ITEM_PALETTE_COLORS = [
  0xffffffff, // White
  0xfffff59d, // PronounYellow
  0xffa5d6a7, // ActionGreen
  0xffffccbc, // NounOrange
  0xff90caf9, // DescBlue
  0xfff48fb1, // SocialPink
  0xffce93d8, // QuestionPurple
  0xffef9a9a, // AlertRed
  0xffeeeeee, // MiscGray
];

type ActionType = 'speak' | 'link' | 'launch_app' | 'clear' | 'delete';

export interface EditButtonDialogProps {
  button: AacButton;
  allBoards: Board[];
  onDismiss: () => void;
  onSave: (button: AacButton) => void;
  onDelete: () => void;
  /** True when `button` hasn't been added to a board yet (the "+" add-button flow) -- hides Delete. */
  isNew?: boolean;
}

function dataUrlToGeminiInput(dataUrl: string): GeminiImageInput | null {
  const match = /^data:([^;]+);base64,([\s\S]*)$/.exec(dataUrl);
  if (!match) return null;
  return { mimeType: match[1], base64Data: match[2] };
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('Failed to load image'));
    img.src = src;
  });
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

/** Crops by a [ymin, xmin, ymax, xmax] 0-1 fractional bounding box -- mirrors EditButtonDialog.kt's cropBitmap(). */
async function cropDataUrlByBoundingBox(dataUrl: string, box: number[]): Promise<string> {
  const img = await loadImage(dataUrl);
  const [ymin, xmin, ymax, xmax] = box;
  const sx = clamp(xmin * img.naturalWidth, 0, img.naturalWidth);
  const sy = clamp(ymin * img.naturalHeight, 0, img.naturalHeight);
  const sw = Math.max(1, clamp(xmax * img.naturalWidth, 0, img.naturalWidth) - sx);
  const sh = Math.max(1, clamp(ymax * img.naturalHeight, 0, img.naturalHeight) - sy);

  const canvas = document.createElement('canvas');
  canvas.width = sw;
  canvas.height = sh;
  const ctx = canvas.getContext('2d');
  if (!ctx) return dataUrl;
  ctx.drawImage(img, sx, sy, sw, sh, 0, 0, sw, sh);
  return canvas.toDataURL('image/png');
}

function capitalize(text: string): string {
  return text.length === 0 ? text : text.charAt(0).toUpperCase() + text.slice(1);
}

function actionTypeOf(action: AacButton['action']): ActionType {
  return action.type;
}

export function EditButtonDialog({ button, allBoards, onDismiss, onSave, onDelete, isNew = false }: EditButtonDialogProps) {
  const { t } = useTranslation();
  const languageCode = useSettingsStore((s) => s.settings.languageCode);
  const symbolLibrary = useSettingsStore((s) => s.settings.symbolLibrary);
  const aiAvailable = Boolean(import.meta.env.VITE_GEMINI_API_KEY);

  const [label, setLabel] = useState(button.label);
  const [speechText, setSpeechText] = useState(button.speechText ?? '');
  const [topic, setTopic] = useState(button.topic ?? '');
  const [selectedColor, setSelectedColor] = useState(button.backgroundColor);
  const [isHidden, setIsHidden] = useState(button.hidden);
  const [imageDataUrl, setImageDataUrl] = useState<string | null>(button.iconPath);

  const [actionType, setActionType] = useState<ActionType>(() => actionTypeOf(button.action));
  const [linkBoardId, setLinkBoardId] = useState(
    isLinkToBoardAction(button.action) ? button.action.boardId : (allBoards[0]?.id ?? ''),
  );
  const [launchApp, setLaunchApp] = useState<{ packageName: string; label: string } | null>(
    isLaunchAppAction(button.action) ? { packageName: button.action.packageName, label: button.action.packageName } : null,
  );

  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [showSymbolSearch, setShowSymbolSearch] = useState(false);
  const [showImageEditor, setShowImageEditor] = useState(false);
  const [showAppPicker, setShowAppPicker] = useState(false);
  const [showAutoNameDialog, setShowAutoNameDialog] = useState(false);
  const [showDeleteConfirmation, setShowDeleteConfirmation] = useState(false);
  const [showSymbolProposal, setShowSymbolProposal] = useState(false);
  const [proposedSymbolUrl, setProposedSymbolUrl] = useState<string | null>(null);

  const pendingAnalysisImageRef = useRef<string | null>(null);
  const gemini = useMemo(() => new GeminiService(), []);
  const symbolService = useMemo(() => {
    const services =
      symbolLibrary === 'MULBERRY'
        ? [new GlobalSymbolsService('mulberry'), new ArasaacService(), new GoogleImageService()]
        : [
            new ArasaacService(),
            new GlobalSymbolsService('arasaac'),
            new GlobalSymbolsService('mulberry'),
            new GoogleImageService(),
          ];
    return new CompositeSymbolService(services, false, 1);
  }, [symbolLibrary]);

  const promptAutoNameIfAvailable = (dataUrl: string) => {
    if (!aiAvailable) return;
    pendingAnalysisImageRef.current = dataUrl;
    setShowAutoNameDialog(true);
  };

  const handleTakePhoto = async () => {
    try {
      const photo = await Camera.getPhoto({ resultType: CameraResultType.DataUrl, source: CameraSource.Camera, quality: 85 });
      if (photo.dataUrl) {
        setImageDataUrl(photo.dataUrl);
        promptAutoNameIfAvailable(photo.dataUrl);
      }
    } catch {
      // User cancelled the camera -- not an error worth surfacing.
    }
  };

  const handlePickFromGallery = async () => {
    try {
      const photo = await Camera.getPhoto({ resultType: CameraResultType.DataUrl, source: CameraSource.Photos, quality: 85 });
      if (photo.dataUrl) {
        setImageDataUrl(photo.dataUrl);
        promptAutoNameIfAvailable(photo.dataUrl);
      }
    } catch {
      // User cancelled the picker -- not an error worth surfacing.
    }
  };

  const performAutoAnalysis = async (dataUrl: string) => {
    const input = dataUrlToGeminiInput(dataUrl);
    if (!input) {
      setErrorMessage(t('edit_button_error_reading_image'));
      return;
    }

    setIsAnalyzing(true);
    setErrorMessage(null);
    try {
      const { name, boundingBox } = await gemini.identifyItem(input);
      if (name.startsWith('Err:') || name.startsWith('Error:')) {
        setErrorMessage(name);
        return;
      }

      setLabel(name);

      let finalImage = dataUrl;
      if (boundingBox) {
        finalImage = await cropDataUrlByBoundingBox(dataUrl, boundingBox);
        setImageDataUrl(finalImage);
      }

      const symbolResults = await symbolService.search(name, languageCode);
      if (symbolResults.length > 0) {
        setProposedSymbolUrl(symbolResults[0].url);
        setShowSymbolProposal(true);
      }
    } catch (e) {
      setErrorMessage(t('edit_button_error_generic', { message: e instanceof Error ? e.message : 'Unknown error' }));
    } finally {
      setIsAnalyzing(false);
    }
  };

  const handleSave = () => {
    const trimmedLabel = label.trim();
    const trimmedSpeech = speechText.trim();
    const trimmedTopic = topic.trim();

    let action: AacButton['action'];
    switch (actionType) {
      case 'link':
        action = ButtonAction.linkToBoard(linkBoardId);
        break;
      case 'launch_app':
        action = ButtonAction.launchApp(launchApp?.packageName ?? '');
        break;
      case 'clear':
        action = ButtonAction.clearSentence;
        break;
      case 'delete':
        action = ButtonAction.deleteLastWord;
        break;
      case 'speak':
      default:
        action = ButtonAction.speak(trimmedSpeech.length > 0 ? trimmedSpeech : trimmedLabel);
        break;
    }

    onSave({
      ...button,
      label: trimmedLabel,
      speechText: trimmedSpeech.length > 0 ? trimmedSpeech : null,
      backgroundColor: selectedColor,
      iconPath: imageDataUrl,
      hidden: isHidden,
      topic: trimmedTopic.length > 0 ? trimmedTopic : null,
      action,
    });
  };

  const canSave = actionType !== 'link' || linkBoardId.length > 0;

  return (
    <>
      <div className="modal-backdrop" onClick={onDismiss}>
        <div className="modal modal--edit-button" onClick={(e) => e.stopPropagation()}>
          <h3>{t('edit_button_title')}</h3>

          <div className="edit-button__image-section">
            <div
              className="edit-button__image-box"
              onClick={() => void handlePickFromGallery()}
              role="button"
              tabIndex={0}
            >
              {imageDataUrl ? (
                <>
                  <img src={imageDataUrl} alt={t('edit_button_button_image_alt')} />
                  <button
                    type="button"
                    className="edit-button__image-edit-btn"
                    onClick={(e) => {
                      e.stopPropagation();
                      setShowImageEditor(true);
                    }}
                    aria-label={t('edit_button_edit_image_aria')}
                    title={t('edit_button_crop_zoom')}
                  >
                    ✎
                  </button>
                </>
              ) : (
                <span className="edit-button__image-placeholder" aria-hidden="true">
                  🖼
                </span>
              )}
            </div>

            <div className="edit-button__image-actions">
              <button type="button" onClick={() => void handlePickFromGallery()}>
                {t('edit_button_gallery')}
              </button>
              <button type="button" onClick={() => void handleTakePhoto()}>
                {t('edit_button_camera')}
              </button>
            </div>

            <button type="button" className="edit-button__full-width-btn" onClick={() => setShowSymbolSearch(true)}>
              {t('edit_button_search_symbol_library')}
            </button>

            {isAnalyzing && <div className="edit-button__analyzing">{t('edit_button_analyzing')}</div>}
            {errorMessage && <div className="edit-button__error">{errorMessage}</div>}
          </div>

          <label className="edit-button__field">
            <span>{t('edit_button_label_field')}</span>
            <input type="text" value={label} onChange={(e) => setLabel(e.target.value)} />
          </label>

          <label className="edit-button__field">
            <span>{t('edit_button_speech_text_field')}</span>
            <input type="text" value={speechText} onChange={(e) => setSpeechText(e.target.value)} />
          </label>

          <label className="edit-button__field">
            <span>{t('edit_button_topic_field')}</span>
            <input type="text" value={topic} onChange={(e) => setTopic(e.target.value)} />
          </label>

          <div className="edit-button__row edit-button__row--between">
            <span>{t('edit_button_hidden')}</span>
            <input type="checkbox" checked={isHidden} onChange={(e) => setIsHidden(e.target.checked)} />
          </div>

          <div className="edit-button__color-row">
            {ITEM_PALETTE_COLORS.map((color) => (
              <button
                key={color}
                type="button"
                className={`edit-button__color-swatch${selectedColor === color ? ' edit-button__color-swatch--selected' : ''}`}
                style={{ background: colorFromPacked(color) }}
                onClick={() => setSelectedColor(color)}
                aria-label={t('edit_button_select_color')}
              />
            ))}
          </div>

          <div className="edit-button__action-section">
            <span className="edit-button__section-label">{t('action_type')}</span>
            <div className="edit-button__action-options">
              {(
                [
                  ['speak', t('action_speak')],
                  ['link', t('action_link')],
                  ['launch_app', t('edit_button_action_launch_app')],
                  ['clear', t('edit_button_action_clear_sentence')],
                  ['delete', t('edit_button_action_delete_last_word')],
                ] as [ActionType, string][]
              ).map(([value, text]) => (
                <label key={value} className="edit-button__radio">
                  <input
                    type="radio"
                    name="action-type"
                    checked={actionType === value}
                    onChange={() => setActionType(value)}
                  />
                  {text}
                </label>
              ))}
            </div>

            {actionType === 'link' && (
              <select className="edit-button__select" value={linkBoardId} onChange={(e) => setLinkBoardId(e.target.value)}>
                {allBoards.length === 0 && <option value="">{t('edit_button_no_boards_available')}</option>}
                {allBoards.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </select>
            )}

            {actionType === 'launch_app' && (
              <button type="button" className="edit-button__full-width-btn" onClick={() => setShowAppPicker(true)}>
                {launchApp ? t('edit_button_app_label', { label: launchApp.label }) : t('edit_button_choose_an_app')}
              </button>
            )}
          </div>

          <div className="edit-button__footer">
            {!isNew ? (
              <button type="button" className="edit-button__delete-btn" onClick={() => setShowDeleteConfirmation(true)}>
                {`🗑 ${t('delete')}`}
              </button>
            ) : (
              <span />
            )}
            <div className="edit-button__footer-right">
              <button type="button" onClick={onDismiss}>
                {t('cancel')}
              </button>
              <button type="button" className="edit-button__save-btn" onClick={handleSave} disabled={!canSave}>
                {t('save')}
              </button>
            </div>
          </div>
        </div>
      </div>

      {showSymbolSearch && (
        <SymbolSearchDialog
          initialQuery={label}
          defaultLanguage={languageCode}
          symbolLibrary={symbolLibrary}
          onDismiss={() => setShowSymbolSearch(false)}
          onSymbolSelected={(url, symbolLabel) => {
            setImageDataUrl(url);
            if (label.trim().length === 0) setLabel(capitalize(symbolLabel));
            setShowSymbolSearch(false);
          }}
        />
      )}

      {showImageEditor && imageDataUrl && (
        <ImageEditorDialog
          imageSrc={imageDataUrl}
          onDismiss={() => setShowImageEditor(false)}
          onSave={(cropped) => {
            setImageDataUrl(cropped);
            setShowImageEditor(false);
          }}
        />
      )}

      {showAppPicker && (
        <AppPickerDialog
          onDismiss={() => setShowAppPicker(false)}
          onAppSelected={(packageName, appLabel) => {
            setLaunchApp({ packageName, label: appLabel });
            setShowAppPicker(false);
          }}
        />
      )}

      {showAutoNameDialog && (
        <div className="modal-backdrop" onClick={() => setShowAutoNameDialog(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>{t('edit_button_auto_name_title')}</h3>
            <p>{t('edit_button_auto_name_message')}</p>
            <div className="modal__actions">
              <button type="button" onClick={() => setShowAutoNameDialog(false)}>
                {t('no')}
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowAutoNameDialog(false);
                  const pending = pendingAnalysisImageRef.current;
                  if (pending) void performAutoAnalysis(pending);
                }}
              >
                {t('yes')}
              </button>
            </div>
          </div>
        </div>
      )}

      {showSymbolProposal && proposedSymbolUrl && (
        <div className="modal-backdrop" onClick={() => setShowSymbolProposal(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>{t('edit_button_symbol_found_title')}</h3>
            <p>{t('edit_button_symbol_found_message', { label })}</p>
            <img className="edit-button__proposed-symbol" src={proposedSymbolUrl} alt={t('edit_button_symbol_found_title')} />
            <div className="modal__actions">
              <button
                type="button"
                onClick={() => {
                  setShowSymbolProposal(false);
                  setShowSymbolSearch(true);
                }}
              >
                {t('edit_button_search_other_symbols')}
              </button>
              <button type="button" onClick={() => setShowSymbolProposal(false)}>
                {t('edit_button_keep_photo')}
              </button>
              <button
                type="button"
                onClick={() => {
                  setImageDataUrl(proposedSymbolUrl);
                  setShowSymbolProposal(false);
                }}
              >
                {t('edit_button_use_symbol')}
              </button>
            </div>
          </div>
        </div>
      )}

      {showDeleteConfirmation && (
        <div className="modal-backdrop" onClick={() => setShowDeleteConfirmation(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>{t('edit_button_delete_title')}</h3>
            <p>{t('edit_button_delete_confirm')}</p>
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
                  onDismiss();
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

/** Unpacks an ARGB `number` into a CSS rgba() string -- same convention as CommunicationGrid's colorFromPacked. */
function colorFromPacked(packed: number): string {
  const a = (packed >>> 24) & 0xff;
  const r = (packed >>> 16) & 0xff;
  const g = (packed >>> 8) & 0xff;
  const b = packed & 0xff;
  return `rgba(${r}, ${g}, ${b}, ${a / 255})`;
}
