import type { ButtonAction } from './ButtonAction';

/** Mirrors com.example.myaac.model.HomeButtonConfig. */
export interface HomeButtonConfig {
  id: string;
  label: string;
  iconPath: string | null;
  action: ButtonAction;
  /** Packed ARGB. Kotlin default is 0xFFE3F2FD (a light blue). */
  backgroundColor: number;
  order: number;
  isVisible: boolean;
}

export const DEFAULT_HOME_BUTTON_BACKGROUND_COLOR = 0xffe3f2fd;
