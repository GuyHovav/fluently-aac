/** Mirrors com.example.myaac.model.AppShortcut. */
export interface AppShortcut {
  packageName: string;
  appName: string;
  /** For future use if icons get cached. */
  iconPath: string | null;
}
