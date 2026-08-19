// Phase 1: Board, AacButton, ButtonAction discriminated union, etc.
// TypeScript port of app/src/main/java/com/example/myaac/model/*.kt
// (plus DisabilityType from data/model/DisabilityType.kt, used by AppSettings).

export type {
  ButtonActionSpeak,
  ButtonActionLinkToBoard,
  ButtonActionLaunchApp,
  ButtonActionClearSentence,
  ButtonActionDeleteLastWord,
} from './ButtonAction';
export {
  ButtonAction,
  isSpeakAction,
  isLinkToBoardAction,
  isLaunchAppAction,
  isClearSentenceAction,
  isDeleteLastWordAction,
} from './ButtonAction';

export type { AacButton } from './AacButton';
export { DEFAULT_BUTTON_BACKGROUND_COLOR, getTextToSpeak, createAacButton } from './AacButton';

export type { Board } from './Board';
export { createBoard } from './Board';

export type { HomeButtonConfig } from './HomeButtonConfig';
export { DEFAULT_HOME_BUTTON_BACKGROUND_COLOR } from './HomeButtonConfig';

export type { AppShortcut } from './AppShortcut';

export { DisabilityType } from './DisabilityType';
