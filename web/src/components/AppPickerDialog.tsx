import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { AppLauncher, type LaunchableApp } from '../plugins/AppLauncher';
import './editButtonUI.css';

// React port of ui/components/AppPickerDialog.kt, for EditButtonDialog's "Launch App" action.
//
// Deviation from the Compose original: the Kotlin version enumerated installed apps directly via
// `PackageManager.queryIntentActivities` (no web equivalent exists for that at all). Here it goes
// through the AppLauncher custom Capacitor plugin instead (per the migration plan's "native
// bridge plugins" section), which already excludes the host app and returns
// `{ packageName, label, iconBase64? }` -- so there's no need to re-filter/sort by hand beyond a
// simple alphabetical sort for a stable, scannable list (the Kotlin version also sorted by name).

export interface AppPickerDialogProps {
  onDismiss: () => void;
  onAppSelected: (packageName: string, label: string) => void;
}

export function AppPickerDialog({ onDismiss, onAppSelected }: AppPickerDialogProps) {
  const { t } = useTranslation();
  const [apps, setApps] = useState<LaunchableApp[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const result = await AppLauncher.listLaunchableApps();
        if (cancelled) return;
        setApps([...result.apps].sort((a, b) => a.label.localeCompare(b.label)));
      } catch (e) {
        if (cancelled) return;
        setErrorMessage(e instanceof Error ? e.message : String(e));
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="modal-backdrop" onClick={onDismiss}>
      <div className="modal modal--app-picker" onClick={(e) => e.stopPropagation()}>
        <h3>{t('select_app_to_add')}</h3>

        <div className="app-picker__list">
          {isLoading ? (
            <div className="app-picker__status">{t('app_picker_loading')}</div>
          ) : errorMessage ? (
            <div className="app-picker__status app-picker__status--error">{errorMessage}</div>
          ) : apps.length === 0 ? (
            <div className="app-picker__status">{t('app_picker_none_found')}</div>
          ) : (
            apps.map((app) => (
              <button
                key={app.packageName}
                type="button"
                className="app-picker__item"
                onClick={() => onAppSelected(app.packageName, app.label)}
              >
                {app.iconBase64 ? (
                  <img className="app-picker__icon" src={`data:image/png;base64,${app.iconBase64}`} alt="" />
                ) : (
                  <span className="app-picker__icon app-picker__icon--placeholder" aria-hidden="true" />
                )}
                <span className="app-picker__labels">
                  <span className="app-picker__name">{app.label}</span>
                  <span className="app-picker__package">{app.packageName}</span>
                </span>
              </button>
            ))
          )}
        </div>

        <button type="button" className="modal__cancel" onClick={onDismiss}>
          {t('cancel')}
        </button>
      </div>
    </div>
  );
}
