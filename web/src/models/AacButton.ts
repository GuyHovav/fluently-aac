import { ButtonAction } from './ButtonAction';
import type { ButtonAction as ButtonActionType } from './ButtonAction';

// Mirrors com.example.myaac.model.AacButton.
//
// Note on `backgroundColor`: the Kotlin side stores an ARGB color packed into a
// `Long` (e.g. 0xFFFFFFFF for opaque white) "for easy serialization". A JS `number`
// safely represents all such 32-bit-packed values (well within
// Number.MAX_SAFE_INTEGER), so it is ported as `number` rather than `bigint` to stay
// a drop-in match for Gson's numeric JSON encoding.
export interface AacButton {
  id: string;
  label: string;
  /** If null/undefined, the label is spoken instead. Use getTextToSpeak() below. */
  speechText: string | null;
  /** Path to a local asset or a URL. */
  iconPath: string | null;
  /** Packed ARGB, e.g. 0xFFFFFFFF for opaque white. */
  backgroundColor: number;
  action: ButtonActionType;
  hidden: boolean;
  topic: string | null;
  boundingBox: number[] | null;
}

/** Default packed ARGB color (opaque white), matching AacButton's Kotlin default. */
export const DEFAULT_BUTTON_BACKGROUND_COLOR = 0xffffffff;

/** Mirrors AacButton's `textToSpeak` computed property (`speechText ?: label`). */
export function getTextToSpeak(button: AacButton): string {
  return button.speechText ?? button.label;
}

/**
 * Builds an AacButton with the same defaults as the Kotlin data class constructor:
 * iconPath = null, backgroundColor = 0xFFFFFFFF, action = Speak(label), hidden = false,
 * topic = null, boundingBox = null.
 */
export function createAacButton(
  params: Pick<AacButton, 'id' | 'label'> & Partial<Omit<AacButton, 'id' | 'label'>>,
): AacButton {
  return {
    speechText: null,
    iconPath: null,
    backgroundColor: DEFAULT_BUTTON_BACKGROUND_COLOR,
    action: ButtonAction.speak(params.label),
    hidden: false,
    topic: null,
    boundingBox: null,
    ...params,
  };
}
