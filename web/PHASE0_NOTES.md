# Phase 0 — Bridge Spike Notes

Scope: throwaway single-screen test harness (`web/src/App.tsx`) validating the two
custom Capacitor plugins and the known WebView-auth gotcha, before any UI porting
begins. See the migration plan for full context.

## What's here

- `web/` — React + TS + Vite app. `src/{models,data,services,nlp,plugins,store,components,i18n}`
  are scaffolded per the plan's repo structure but empty (placeholder `index.ts`) except
  `src/plugins/` (real TS wrappers for the two custom plugins) and the harness itself
  (`App.tsx`, `App.css`).
- `capacitor.config.ts` (repo root) — `appId: com.example.myaac.hybrid`, `webDir: web/dist`.
- `android/` — generated via `npx cap add android`, plus:
  - `android/app/src/main/java/com/example/myaac/hybrid/plugins/FluentlyTtsPlugin.kt`
  - `android/app/src/main/java/com/example/myaac/hybrid/plugins/AppLauncherPlugin.kt`
  - `android/app/src/main/java/com/example/myaac/hybrid/MainActivity.java` registers both.
- `package.json` (repo root) — deliberately **mirrors** the Capacitor plugin dependency
  subset from `web/package.json` (not the whole app). This is required: `npx cap sync`
  discovers plugins by reading `package.json` next to `capacitor.config.ts` (repo root),
  not `web/package.json`. If you add a new Capacitor plugin to the web app, add it to
  both `web/package.json` (actual usage) and root `package.json` (so `cap sync` finds
  its native Android module), then `npm install` in both places.

## Applied-vs-plan deviations (call these out explicitly)

1. **`applicationId` is `com.example.myaac.hybrid`, not `com.example.myaac`.** The
   existing native app (`app/`) is already registered in Firebase
   (`app/google-services.json`) under `com.example.myaac`. Reusing that id for this
   Capacitor spike would let it overwrite/conflict with the native app if both are ever
   installed on the same test device (same package name + different signing key = install
   failure). Using a distinct id avoids that entirely during the spike, at the cost of
   needing a **new** Firebase Android app entry (see below) rather than reusing the
   existing `google-services.json` as-is. If you'd rather reuse `com.example.myaac`,
   change `appId` in `capacitor.config.ts` and `android/app/build.gradle`
   (`namespace`/`applicationId`), rerun `cap sync`, and uninstall the native app first on
   any device you test both on.
2. **Kotlin toolchain added to `android/app/build.gradle`.** The plan's two custom
   plugins are Kotlin, but Capacitor's default generated Android app module is Java-only
   (only `MainActivity.java`). Compiling `FluentlyTtsPlugin.kt`/`AppLauncherPlugin.kt`
   required adding a `buildscript { ... kotlin-gradle-plugin ... }` block + `apply plugin:
   'kotlin-android'` + `kotlin-stdlib` dependency to `android/app/build.gradle`, and a
   `kotlin_version = '2.2.20'` pin in `android/variables.gradle` (matching what
   `@capacitor/camera`/`@capacitor/geolocation` already default to internally). This
   wasn't called out in the plan's repo structure sketch but is required for the plugins
   to build at all. Fully wired now — no action needed unless you bump the Kotlin version.
3. **`rgcfaIncludeGoogle = true` added to `android/variables.gradle`.** Without this,
   `@capacitor-firebase/authentication`'s Android module only `compileOnly`-includes
   `play-services-auth`/`androidx.credentials`/`googleid` (i.e. present at compile time,
   absent at runtime), so `signInWithGoogle()` would crash with a
   `ClassNotFoundException` on-device even with a valid `google-services.json`. Not
   mentioned in the plan; discovered by reading the plugin's own `build.gradle`.

## What builds successfully right now (verified in this sandbox)

- `cd web && npm run build` — succeeds (`tsc -b && vite build`).
- `npx cap sync android` (repo root) — succeeds, detects and wires in all 4 off-the-shelf
  plugins (`@capacitor/camera`, `@capacitor/geolocation`, `@capacitor/haptics`,
  `@capacitor-firebase/authentication`).
- `android/gradlew.bat assembleDebug` — **succeeds**, produces
  `android/app/build/outputs/apk/debug/app-debug.apk`. This was run in this sandbox using
  `C:\Program Files\Android\Android Studio\jbr` as `JAVA_HOME` (OpenJDK 21, bundled with
  Android Studio) since `java` is not on this shell's `PATH`. Full command used:
  ```
  cd android
  set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
  set PATH=%JAVA_HOME%\bin;%PATH%
  set ANDROID_HOME=C:\Users\user\AppData\Local\Android\Sdk
  gradlew.bat assembleDebug
  ```
  Both custom Kotlin plugins compile cleanly (no errors, two now-suppressed deprecation
  warnings on the legacy `Locale(String,String)` constructor, kept because it faithfully
  matches the existing native app's `MainActivity.onInit()` locale logic).

**This sandbox has no attached Android device/emulator**, so nothing below this line was
actually exercised at runtime — only compiled. That's the user's job next.

## How to run and validate each capability on a real device

1. Plug in a real Android device (recommended over an emulator per the plan's
   Verification section — TTS voice availability and camera are unreliable on emulators)
   with USB debugging enabled.
2. From repo root:
   ```
   cd web && npm run build && cd ..
   npx cap sync android
   npx cap run android
   ```
   (or open `android/` in Android Studio and hit Run — simplest if `java`/`adb` aren't on
   your shell `PATH`; Android Studio supplies its own JDK.)
3. On the harness screen, work through the 6 cards top to bottom:
   - **FluentlyTts** — tap "Speak English", "Speak Hebrew (plain)", and especially
     **"Speak Hebrew (nikud round-trip test)"**. That last one speaks
     `רָעֵב, שָׂמֵחַ` (verbatim from `PronunciationDictionary.kt`'s `builtInHebrewDictionary`
     — "hungry, happy"). Listen for correct vowel pronunciation; if the nikud marks got
     stripped or mangled crossing the JS→native bridge, it'll sound like the bare
     consonantal forms (e.g. "ra'ev" without the vowels) instead of fully-voweled speech.
     This is the one genuinely novel failure mode flagged in the plan's risk list.
   - **AppLauncher** — tap "List launchable apps", confirm a real list of installed apps
     appears (with icons), tap one, confirm it actually launches.
   - **Camera** — tap "Take photo", confirm the system picker opens and a preview renders
     after capture.
   - **Haptics** — tap "Trigger impact feedback", confirm you feel a vibration.
   - **Geolocation** — tap "Get current position", confirm real lat/lng appear (grant the
     location permission prompt when it appears).
   - **Google Sign-In** — tap "Sign in with Google". **This will fail** until the Firebase
     console step below is done — see next section. Confirm it fails with a clear native
     error (not a WebView "disallowed_useragent" error — if you see that specific error,
     the native-SDK path isn't wired correctly and needs investigation) rather than a
     silent hang.

## Manual step required before Google Sign-In can work: Firebase console

`app/google-services.json` (the existing native app's Firebase config) is registered for
package `com.example.myaac`, not this spike's `com.example.myaac.hybrid` — so it cannot
be copied in as-is (the Google Services Gradle plugin will hard-fail the build with "No
matching client found for package name" if you try). Nothing in this repo currently
applies that plugin without a valid file present (`android/app/build.gradle` guards it),
so its absence does not block building — it only blocks this one capability at runtime.

To make Google Sign-In actually work, in the Firebase console for the existing project:

1. Add a new Android app to the existing Firebase project with package name
   `com.example.myaac.hybrid`.
2. Register the debug signing certificate's SHA-1 (and SHA-256) fingerprint against that
   new Android app entry — Google Sign-In will fail without this even with a valid
   `google-services.json`. Get it via `android/gradlew.bat signingReport` (needs the same
   JDK setup as above) or `keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore
   -alias androiddebugkey -storepass android -keypass android`.
3. Download the generated `google-services.json` and place it at `android/app/google-services.json`
   (already gitignored — do not commit it).
4. Re-run `npx cap sync android` and rebuild. `android/app/build.gradle`'s existing
   conditional (`if (file('google-services.json').text) { apply plugin:
   'com.google.gms.google-services' }`) will pick it up automatically.
5. In Firebase Authentication settings, confirm Google is enabled as a sign-in provider
   for the project (it should already be, since the native app presumably uses it, but
   confirm the OAuth consent screen/Web client is configured).

Everything on the code side (`rgcfaIncludeGoogle = true`, the plugin call in `App.tsx`,
the gradle wiring) is already correct and complete — this is purely a console-side
registration gap, per the task's constraints.

## Exit criterion (per the plan)

"Every capability with no off-the-shelf answer has a working proof-of-concept." The two
custom plugins (`FluentlyTts`, `AppLauncher`) are fully implemented and compile-verified;
they need the on-device pass above to be signed off. The off-the-shelf capabilities
(camera, haptics, geolocation) are wired correctly and should just work. Google Sign-In's
code path is correct but is blocked on the Firebase console step above — flag that as
still-open until someone completes it and re-tests.
