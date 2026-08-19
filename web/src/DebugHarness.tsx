import { useCallback, useState, type ReactNode } from 'react';
import { Camera, CameraResultType, CameraSource } from '@capacitor/camera';
import { Haptics, ImpactStyle } from '@capacitor/haptics';
import { Geolocation } from '@capacitor/geolocation';
import { FirebaseAuthentication } from '@capacitor-firebase/authentication';
import { FluentlyTts } from './plugins/FluentlyTts';
import { AppLauncher, type LaunchableApp } from './plugins/AppLauncher';
import './App.css';

// Phase 0's throwaway single-screen native-bridge test harness. Kept reachable (not deleted) via
// the "Debug" button in App.tsx's header, per the Phase 2 instructions: Phase 0's device
// validation still needs to happen on a real device and shouldn't be thrown away just because
// Phase 2's real communication screen now exists as the default view.

// Literal Hebrew strings pulled verbatim from
// app/src/main/java/com/example/myaac/data/pronunciation/PronunciationDictionary.kt
// (builtInHebrewDictionary), used to test whether nikud (Unicode combining marks)
// survive the JS -> native Capacitor bridge serialization intact.
const NIKUD_HUNGRY = 'רָעֵב'; // hungry
const NIKUD_HAPPY = 'שָׂמֵחַ'; // happy
const NIKUD_TEST_STRING = `${NIKUD_HUNGRY}, ${NIKUD_HAPPY}`;

const PLAIN_HEBREW_STRING = 'אני רוצה מים'; // "I want water" (no nikud)
const ENGLISH_STRING = 'Hello, I would like some water please.';

type Status = 'idle' | 'running' | 'pass' | 'fail';

interface TestState {
  status: Status;
  detail: string;
}

const IDLE: TestState = { status: 'idle', detail: '' };

function StatusBadge({ status }: { status: Status }) {
  const label = { idle: 'Not run', running: 'Running…', pass: 'PASS', fail: 'FAIL' }[status];
  const className = `badge badge-${status}`;
  return <span className={className}>{label}</span>;
}

function TestCard({
  title,
  description,
  state,
  children,
}: {
  title: string;
  description: string;
  state: TestState;
  children: ReactNode;
}) {
  return (
    <section className="test-card">
      <div className="test-card-header">
        <h2>{title}</h2>
        <StatusBadge status={state.status} />
      </div>
      <p className="test-card-description">{description}</p>
      <div className="test-card-actions">{children}</div>
      {state.detail && <pre className="test-card-detail">{state.detail}</pre>}
    </section>
  );
}

export function DebugHarness() {
  const [ttsState, setTtsState] = useState<TestState>(IDLE);
  const [ttsInitialized, setTtsInitialized] = useState(false);
  const [appLauncherState, setAppLauncherState] = useState<TestState>(IDLE);
  const [apps, setApps] = useState<LaunchableApp[]>([]);
  const [cameraState, setCameraState] = useState<TestState>(IDLE);
  const [photoDataUrl, setPhotoDataUrl] = useState<string | null>(null);
  const [hapticsState, setHapticsState] = useState<TestState>(IDLE);
  const [geoState, setGeoState] = useState<TestState>(IDLE);
  const [authState, setAuthState] = useState<TestState>(IDLE);

  const ensureTtsInitialized = useCallback(async () => {
    if (ttsInitialized) return true;
    const result = await FluentlyTts.initialize();
    setTtsInitialized(result.available);
    return result.available;
  }, [ttsInitialized]);

  const speak = useCallback(
    async (text: string, languageCode: 'en' | 'iw', label: string) => {
      setTtsState({ status: 'running', detail: `Speaking ${label}: "${text}"` });
      try {
        const available = await ensureTtsInitialized();
        if (!available) {
          setTtsState({ status: 'fail', detail: 'TTS engine reported unavailable on initialize()' });
          return;
        }
        await FluentlyTts.setLanguage({ languageCode });
        await FluentlyTts.setRate({ rate: 1.0 });
        await FluentlyTts.speak({ text, queueMode: 'flush' });
        setTtsState({
          status: 'pass',
          detail: `speak() resolved for ${label}: "${text}"\n(confirm audibly on-device that this was spoken correctly, including nikud/vowel pronunciation for Hebrew)`,
        });
      } catch (err) {
        setTtsState({ status: 'fail', detail: String(err) });
      }
    },
    [ensureTtsInitialized],
  );

  const stopTts = useCallback(async () => {
    await FluentlyTts.stop();
  }, []);

  const runAppLauncherList = useCallback(async () => {
    setAppLauncherState({ status: 'running', detail: 'Listing launchable apps…' });
    try {
      const result = await AppLauncher.listLaunchableApps();
      setApps(result.apps);
      setAppLauncherState({
        status: result.apps.length > 0 ? 'pass' : 'fail',
        detail: `Found ${result.apps.length} launchable app(s).`,
      });
    } catch (err) {
      setAppLauncherState({ status: 'fail', detail: String(err) });
    }
  }, []);

  const runAppLaunch = useCallback(async (packageName: string) => {
    setAppLauncherState({ status: 'running', detail: `Launching ${packageName}…` });
    try {
      const result = await AppLauncher.launchApp({ packageName });
      setAppLauncherState({
        status: result.success ? 'pass' : 'fail',
        detail: `launchApp(${packageName}) -> success=${result.success}`,
      });
    } catch (err) {
      setAppLauncherState({ status: 'fail', detail: String(err) });
    }
  }, []);

  const runCamera = useCallback(async () => {
    setCameraState({ status: 'running', detail: 'Opening system camera/picker…' });
    try {
      const photo = await Camera.getPhoto({
        resultType: CameraResultType.DataUrl,
        source: CameraSource.Prompt,
        quality: 70,
      });
      setPhotoDataUrl(photo.dataUrl ?? null);
      setCameraState({ status: 'pass', detail: 'Camera.getPhoto() resolved with image data.' });
    } catch (err) {
      setCameraState({ status: 'fail', detail: String(err) });
    }
  }, []);

  const runHaptics = useCallback(async () => {
    setHapticsState({ status: 'running', detail: 'Triggering medium impact…' });
    try {
      await Haptics.impact({ style: ImpactStyle.Medium });
      setHapticsState({
        status: 'pass',
        detail: 'Haptics.impact() resolved. Confirm you felt a vibration on-device.',
      });
    } catch (err) {
      setHapticsState({ status: 'fail', detail: String(err) });
    }
  }, []);

  const runGeolocation = useCallback(async () => {
    setGeoState({ status: 'running', detail: 'Requesting current position…' });
    try {
      const pos = await Geolocation.getCurrentPosition();
      setGeoState({
        status: 'pass',
        detail: `lat=${pos.coords.latitude}, lng=${pos.coords.longitude}, accuracy=${pos.coords.accuracy}m`,
      });
    } catch (err) {
      setGeoState({ status: 'fail', detail: String(err) });
    }
  }, []);

  const runGoogleSignIn = useCallback(async () => {
    setAuthState({ status: 'running', detail: 'Opening native Google Sign-In…' });
    try {
      const result = await FirebaseAuthentication.signInWithGoogle();
      setAuthState({
        status: 'pass',
        detail: `Signed in as ${result.user?.email ?? '(no email)'} (uid=${result.user?.uid ?? 'unknown'})`,
      });
    } catch (err) {
      setAuthState({
        status: 'fail',
        detail: `${String(err)}\n\nExpected to fail until google-services.json is registered for this app's ` +
          'applicationId (com.example.myaac.hybrid) in the Firebase console — see web/PHASE0_NOTES.md.',
      });
    }
  }, []);

  return (
    <div className="harness">
      <header className="harness-header">
        <h1>MyAAC Hybrid Shell — Phase 0 Bridge Test Harness</h1>
        <p>
          Throwaway screen to validate native-bridge capabilities on a real Android device before any UI
          porting begins. Each card is independent — run them in any order.
        </p>
      </header>

      <TestCard
        title="1. FluentlyTts (custom plugin)"
        description="English + Hebrew speech, including a nikud-annotated Hebrew string, to check the plugin resolves and that combining characters survive the JS -> native bridge."
        state={ttsState}
      >
        <button onClick={() => speak(ENGLISH_STRING, 'en', 'English')}>Speak English</button>
        <button onClick={() => speak(PLAIN_HEBREW_STRING, 'iw', 'Hebrew (plain)')}>
          Speak Hebrew (plain)
        </button>
        <button onClick={() => speak(NIKUD_TEST_STRING, 'iw', 'Hebrew (nikud)')}>
          Speak Hebrew (nikud round-trip test)
        </button>
        <button onClick={stopTts}>Stop</button>
      </TestCard>

      <TestCard
        title="2. AppLauncher (custom plugin)"
        description="Enumerate installed launchable apps via PackageManager, then launch one."
        state={appLauncherState}
      >
        <button onClick={runAppLauncherList}>List launchable apps</button>
        {apps.length > 0 && (
          <div className="app-list">
            {apps.slice(0, 15).map((app) => (
              <button key={app.packageName} className="app-list-item" onClick={() => runAppLaunch(app.packageName)}>
                {app.iconBase64 && (
                  <img src={`data:image/png;base64,${app.iconBase64}`} alt="" width={24} height={24} />
                )}
                {app.label}
              </button>
            ))}
          </div>
        )}
      </TestCard>

      <TestCard
        title="3. Camera (@capacitor/camera)"
        description="Capture a photo via the system picker (per the locked-in decision, no custom in-app preview)."
        state={cameraState}
      >
        <button onClick={runCamera}>Take photo</button>
        {photoDataUrl && <img src={photoDataUrl} alt="Captured" className="photo-preview" />}
      </TestCard>

      <TestCard
        title="4. Haptics (@capacitor/haptics)"
        description="Direct swap for LocalHapticFeedback's long-press feedback."
        state={hapticsState}
      >
        <button onClick={runHaptics}>Trigger impact feedback</button>
      </TestCard>

      <TestCard
        title="5. Geolocation (@capacitor/geolocation)"
        description="Replaces FusedLocationProviderClient. Raw lat/lng only -- no on-device reverse geocoding (Gemini handles simplification/naming downstream, per the plan)."
        state={geoState}
      >
        <button onClick={runGeolocation}>Get current position</button>
      </TestCard>

      <TestCard
        title="6. Google Sign-In (@capacitor-firebase/authentication)"
        description="Must route through the native Google Sign-In SDK, not web OAuth (WebView blocks 'disallowed_useragent'). Requires google-services.json to be registered for this app's applicationId -- see web/PHASE0_NOTES.md."
        state={authState}
      >
        <button onClick={runGoogleSignIn}>Sign in with Google</button>
      </TestCard>
    </div>
  );
}
