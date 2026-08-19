import { registerPlugin } from '@capacitor/core';
import type { PluginListenerHandle } from '@capacitor/core';

export interface FluentlyTtsInitializeResult {
  available: boolean;
}

export interface FluentlyTtsSetLanguageOptions {
  /** BCP-47-ish language code, e.g. "en" or "iw" (Hebrew, matching the existing app's Locale codes). */
  languageCode: string;
}

export interface FluentlyTtsSetLanguageResult {
  success: boolean;
}

export interface FluentlyTtsSetRateOptions {
  /** Speech rate multiplier, matches android.speech.tts.TextToSpeech#setSpeechRate (1.0 = normal). */
  rate: number;
}

export interface FluentlyTtsSpeakOptions {
  text: string;
  /** 'flush' -> TextToSpeech.QUEUE_FLUSH (interrupts current speech), 'add' -> TextToSpeech.QUEUE_ADD. */
  queueMode: 'flush' | 'add';
}

export interface FluentlyTtsIsSpeakingResult {
  speaking: boolean;
}

export interface FluentlyTtsUtteranceCompleteEvent {
  utteranceId: string;
}

export interface FluentlyTtsErrorEvent {
  utteranceId: string;
  errorCode?: number;
  message?: string;
}

export interface FluentlyTtsPlugin {
  initialize(): Promise<FluentlyTtsInitializeResult>;
  setLanguage(opts: FluentlyTtsSetLanguageOptions): Promise<FluentlyTtsSetLanguageResult>;
  setRate(opts: FluentlyTtsSetRateOptions): Promise<void>;
  speak(opts: FluentlyTtsSpeakOptions): Promise<void>;
  stop(): Promise<void>;
  isSpeaking(): Promise<FluentlyTtsIsSpeakingResult>;
  addListener(
    eventName: 'utteranceComplete',
    listenerFunc: (event: FluentlyTtsUtteranceCompleteEvent) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    eventName: 'ttsError',
    listenerFunc: (event: FluentlyTtsErrorEvent) => void,
  ): Promise<PluginListenerHandle>;
}

export const FluentlyTts = registerPlugin<FluentlyTtsPlugin>('FluentlyTts');
