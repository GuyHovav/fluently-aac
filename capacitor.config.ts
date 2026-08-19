import type { CapacitorConfig } from '@capacitor/cli';

// Phase 0 note: this uses a distinct applicationId (com.example.myaac.hybrid)
// rather than reusing the existing native app's id (com.example.myaac) so the
// Capacitor test-harness APK can be installed side-by-side with the existing
// native app on the same test device without a signature/package collision.
// See web/PHASE0_NOTES.md for what this means for Firebase/Google Sign-In setup.
const config: CapacitorConfig = {
  appId: 'com.example.myaac.hybrid',
  appName: 'MyAAC Hybrid Spike',
  webDir: 'web/dist',
};

export default config;
