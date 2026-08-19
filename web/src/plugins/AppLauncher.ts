import { registerPlugin } from '@capacitor/core';

export interface LaunchableApp {
  packageName: string;
  label: string;
  /** Base64-encoded PNG of the app's launcher icon, when it could be decoded. */
  iconBase64?: string;
}

export interface AppLauncherListResult {
  apps: LaunchableApp[];
}

export interface AppLauncherLaunchOptions {
  packageName: string;
}

export interface AppLauncherLaunchResult {
  success: boolean;
}

export interface AppLauncherInstalledOptions {
  packageName: string;
}

export interface AppLauncherInstalledResult {
  installed: boolean;
}

export interface AppLauncherPlugin {
  listLaunchableApps(): Promise<AppLauncherListResult>;
  launchApp(opts: AppLauncherLaunchOptions): Promise<AppLauncherLaunchResult>;
  isAppInstalled(opts: AppLauncherInstalledOptions): Promise<AppLauncherInstalledResult>;
}

export const AppLauncher = registerPlugin<AppLauncherPlugin>('AppLauncher');
