// Web SDK counterpart of the native app's Firebase setup (google-services.json +
// FirebaseFirestore.getInstance() / FirebaseStorage.getInstance() in CloudRepository.kt).
//
// The native app gets its Firebase project config from google-services.json at build time.
// There is no equivalent generated file for a Vite web build, so the same project config is
// supplied via VITE_FIREBASE_* env vars instead (see web/.env.example) and passed to
// `initializeApp()` explicitly, following the existing VITE_GEMINI_API_KEY pattern used by
// web/src/services/geminiService.ts.
//
// Auth itself is out of scope here (handled by @capacitor-firebase/authentication, wired in
// Phase 0) -- this module only sets up the Firestore + Storage handles that
// cloudBackupService.ts needs.

import { initializeApp, getApps, type FirebaseApp } from 'firebase/app';
import { getFirestore, type Firestore } from 'firebase/firestore';
import { getStorage, type FirebaseStorage } from 'firebase/storage';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};

// Guard against re-initializing on hot-reload (Vite HMR can re-evaluate this module without a
// full page reload), matching the intent of the Kotlin `by lazy { ... }` singletons.
const firebaseApp: FirebaseApp = getApps()[0] ?? initializeApp(firebaseConfig);

/** Mirrors `FirebaseFirestore.getInstance()`. */
export const firestore: Firestore = getFirestore(firebaseApp);

/** Mirrors `FirebaseStorage.getInstance()`. */
export const storage: FirebaseStorage = getStorage(firebaseApp);

export default firebaseApp;
