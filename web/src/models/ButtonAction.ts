// Mirrors com.example.myaac.model.ButtonAction (sealed interface) from the native app.
//
// JSON shape is dictated by the native app's Gson polymorphic TypeAdapter
// (ButtonActionAdapter in data/local/Converters.kt), NOT by the Kotlin class names.
// The adapter writes a `"type"` discriminant using these exact lowercase/snake_case
// tags, so these are reproduced verbatim here (rather than "Speak"/"LinkToBoard"/etc.)
// so that JSON exported from the native app could, in principle, be parsed by these
// types:
//   Speak          -> { "type": "speak", "text": string }
//   LinkToBoard    -> { "type": "link", "boardId": string }
//   LaunchApp      -> { "type": "launch_app", "packageName": string }
//   ClearSentence  -> { "type": "clear" }
//   DeleteLastWord -> { "type": "delete" }

export interface ButtonActionSpeak {
  type: 'speak';
  text: string;
}

export interface ButtonActionLinkToBoard {
  type: 'link';
  boardId: string;
}

export interface ButtonActionLaunchApp {
  type: 'launch_app';
  packageName: string;
}

export interface ButtonActionClearSentence {
  type: 'clear';
}

export interface ButtonActionDeleteLastWord {
  type: 'delete';
}

/** Discriminated union mirroring the Kotlin `sealed interface ButtonAction`. */
export type ButtonAction =
  | ButtonActionSpeak
  | ButtonActionLinkToBoard
  | ButtonActionLaunchApp
  | ButtonActionClearSentence
  | ButtonActionDeleteLastWord;

/** Factory helpers, mirroring the Kotlin data class/object constructors. */
export const ButtonAction = {
  speak(text: string): ButtonActionSpeak {
    return { type: 'speak', text };
  },
  linkToBoard(boardId: string): ButtonActionLinkToBoard {
    return { type: 'link', boardId };
  },
  launchApp(packageName: string): ButtonActionLaunchApp {
    return { type: 'launch_app', packageName };
  },
  clearSentence: { type: 'clear' } as ButtonActionClearSentence,
  deleteLastWord: { type: 'delete' } as ButtonActionDeleteLastWord,
} as const;

/** Type guards, one per variant. */
export function isSpeakAction(action: ButtonAction): action is ButtonActionSpeak {
  return action.type === 'speak';
}

export function isLinkToBoardAction(action: ButtonAction): action is ButtonActionLinkToBoard {
  return action.type === 'link';
}

export function isLaunchAppAction(action: ButtonAction): action is ButtonActionLaunchApp {
  return action.type === 'launch_app';
}

export function isClearSentenceAction(action: ButtonAction): action is ButtonActionClearSentence {
  return action.type === 'clear';
}

export function isDeleteLastWordAction(action: ButtonAction): action is ButtonActionDeleteLastWord {
  return action.type === 'delete';
}
