---
description: How to build and deploy the APK to an Android virtual device
---

# Deployment Workflow for Agents

This workflow describes how to build and deploy the FluentlyAAC Android app to a virtual device (Android emulator).

## Overview

The project uses a deployment script (`deploy.sh`) that automates:
1. Building the APK (using Docker or local Gradle)
2. Detecting available Android devices/emulators
3. Installing the APK on the target device
4. Optionally launching the application

## Quick Start

### Basic Deployment (Recommended for Development)

// turbo
```bash
# Build with Docker and deploy to first available device
./deploy.sh
```

### With App Launch

```bash
# Build, deploy, and launch the app
./deploy.sh --launch
```

## Prerequisites

Before using the deployment script, ensure:

1. **ADB is installed** - comes with Android SDK Platform Tools
   ```bash
   which adb  # Should show /usr/bin/adb or similar
   ```

2. **Android emulator is running** - Start an emulator:
   ```bash
   # List available emulators
   emulator -list-avds
   
   # Start an emulator (replace <name> with actual AVD name)
   emulator -avd <name> &
   ```

3. **Build script exists** - The `docker-build.sh` script must be present

## Script Options

### Build Methods

- `--docker` (default) - Build using Docker with BuildKit optimizations
- `--local` - Build using local Gradle installation

### Build Types

- Debug (default) - Faster builds, includes debugging symbols
- `--release` - Release build with optimizations

### Device Selection

- No flag - Uses first available device
- `--device <id>` - Deploy to specific device (e.g., emulator-5554)

### Installation Options

- `--install-only` - Skip build, just install existing APK
- `--launch` - Launch the app after installation

## Common Usage Patterns

### 1. Regular Development Workflow

```bash
# Build and deploy (fastest)
./deploy.sh

# Build, deploy, and launch
./deploy.sh --launch
```

### 2. Testing on Specific Device

```bash
# List devices first
adb devices

# Deploy to specific device
./deploy.sh --device emulator-5554 --launch
```

### 3. Local Build (Without Docker)

```bash
./deploy.sh --local --launch
```

### 4. Release Build Deployment

```bash
./deploy.sh --release --device emulator-5554
```

### 5. Quick Reinstall (No Build)

```bash
# Useful when APK already exists
./deploy.sh --install-only --launch
```

## Agent Instructions

When deploying the app for a user:

### Step 1: Check Emulator Status

```bash
adb devices
```

**If no devices shown:**
- Ask user to start an emulator
- Or provide command: `emulator -list-avds` then `emulator -avd <name> &`

### Step 2: Deploy with Launch

```bash
./deploy.sh --launch
```

This will:
- Build the APK using Docker (optimized)
- Auto-detect the emulator
- Install the APK
- Launch the application

### Step 3: Monitor if Needed

If user wants to see logs:
```bash
adb logcat | grep com.example.myaac
```

## Troubleshooting

### "No Android devices or emulators detected"

**Solution:**
```bash
# Check ADB can see devices
adb devices

# Restart ADB server
adb kill-server
adb start-server

# Start an emulator
emulator -list-avds
emulator -avd <name> &
```

### "APK not found"

**Solution:**
```bash
# Build first
./docker-build.sh build-debug

# Then install only
./deploy.sh --install-only
```

### "Installation failed"

**Solution:**
```bash
# Uninstall manually
adb uninstall com.example.myaac

# Try again
./deploy.sh
```

### Multiple Devices Detected

The script will automatically use the first device. To specify:
```bash
./deploy.sh --device emulator-5554
```

## Build Output Locations

- **Debug APK:** `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK:** `app/build/outputs/apk/release/app-release.apk`

## For AI Agents: Automated Deployment Protocol

When a user requests deployment to virtual device:

1. **Verify prerequisites:**
   ```bash
   which adb && adb devices
   ```

2. **If emulator not running:**
   - List available: `emulator -list-avds`
   - Suggest starting one: `emulator -avd <name> &`
   - Wait 30-45 seconds for boot

3. **Deploy with single command:**
   ```bash
   ./deploy.sh --launch
   ```

4. **Confirm success:**
   - Check script output for ✓ marks
   - Verify "Deployment complete!" message
   - Confirm app appears on device

5. **If user wants logs:**
   ```bash
   adb logcat | grep com.example.myaac
   ```

## Script Features

The `deploy.sh` script provides:

- ✅ **Automatic device detection** - No need to specify device ID
- ✅ **Multiple device support** - Choose specific device if needed
- ✅ **Clean installation** - Auto-uninstalls previous version
- ✅ **Build integration** - Works with both Docker and local builds
- ✅ **Colored output** - Easy to read success/error messages
- ✅ **Launch capability** - Can start app after install
- ✅ **Error handling** - Clear error messages with solutions

## Performance Notes

### Build Times (Docker)
- First build: ~10-15 minutes
- Cached build: ~20-40 seconds
- Install: ~5-10 seconds

### Build Times (Local)
- First build: ~5-10 minutes
- Incremental: ~10-30 seconds
- Install: ~5-10 seconds

**Recommendation:** Use Docker builds for consistency, local builds for speed in development.

## Integration with CI/CD

For automated testing:

```bash
# Start emulator headless
emulator -avd test_device -no-window -no-audio &

# Wait for boot
adb wait-for-device

# Deploy and test
./deploy.sh --launch

# Run instrumentation tests
./gradlew connectedAndroidTest
```

## Summary

**For quick deployment:**
```bash
./deploy.sh --launch
```

**For specific needs:**
- Use `--local` for faster builds (if Gradle is set up locally)
- Use `--device <id>` for multiple devices
- Use `--install-only` when APK already exists
- Use `--release` for production testing

The script handles everything else automatically!
