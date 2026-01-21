# Docker Guide for FluentlyAAC

This guide explains how to build and run the FluentlyAAC Android project using Docker with **BuildKit optimizations** for fast compilation times.

> [!TIP]
> **New to the project?** Use the automated build script `./docker-build.sh` for the easiest experience!

## What's New in v2.0

- ✨ **BuildKit support** for parallel builds and better caching
- 🚀 **Automated build script** (`docker-build.sh`) with helpful output
- ⚡ **Faster incremental builds** - no need to rebuild Docker image for code changes
- 💾 **Persistent Gradle cache** - dependencies downloaded once

## Prerequisites

- **Docker** 20.10+ with BuildKit support ([Install Docker](https://docs.docker.com/get-docker/))
- **Docker Compose** v1.28+ or Docker Compose V2 (usually comes with Docker Desktop)
- At least 8 GB of free disk space for the Docker image and build artifacts
- **BuildKit** enabled (see setup below)

## Quick Start

### Option 1: Using the Build Script (Recommended)

The easiest way to build is using the automated script:

```bash
# Make it executable (first time only)
chmod +x docker-build.sh

# Build debug APK
./docker-build.sh build-debug

# Or with timing information
./docker-build.sh build-debug --time
```

The APK will be located at `app/build/outputs/apk/debug/app-debug.apk`

### Option 2: Using Docker Compose Directly

**1. Enable BuildKit**

```bash
# Linux/Mac
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

# Windows (PowerShell)
$env:DOCKER_BUILDKIT=1
$env:COMPOSE_DOCKER_CLI_BUILD=1
```

**2. Build the Docker Image (first time only)**

```bash
docker-compose build
```

This builds the Android build environment image. It will take several minutes on first run as it downloads the Android SDK and dependencies.

**3. Build Debug APK**

```bash
docker-compose run --rm build-debug
```

The APK will be located at `app/build/outputs/apk/debug/app-debug.apk`

## Available Commands

### Using the Build Script

```bash
./docker-build.sh build-debug    # Build debug APK (fastest)
./docker-build.sh build-release  # Build release APK
./docker-build.sh test           # Run unit tests
./docker-build.sh lint           # Run lint checks
./docker-build.sh clean          # Clean build artifacts
./docker-build.sh rebuild        # Rebuild Docker image (after dependency changes)
./docker-build.sh shell          # Open interactive shell
```

**Options:**
- `--time` - Show build time measurement
- `--no-cache` - Build without cache (slower, for troubleshooting)

**Example:**
```bash
./docker-build.sh build-debug --time
```

### Using Docker Compose Directly

**Build Debug APK**
```bash
docker-compose run --rm build-debug
```

**Build Release APK**
```bash
docker-compose run --rm build-release
```

**Clean Build Artifacts**
```bash
docker-compose run --rm clean
```

**Run Unit Tests**
```bash
docker-compose run --rm test
```
Test results will be in `app/build/test-results/`

**Run Lint Checks**
```bash
docker-compose run --rm lint
```
Lint reports will be in `app/build/reports/`

**Run Custom Gradle Command**
```bash
docker-compose run --rm build-debug ./gradlew <your-command> --no-daemon
```

**Enter Interactive Shell**
```bash
docker-compose run --rm build-debug bash
```

## How It Works

### BuildKit Optimizations

BuildKit is Docker's modern build engine that provides:
- **Parallel builds** - Multiple build steps run simultaneously
- **Build cache mounts** - Gradle cache persists across builds without copying
- **Faster dependency resolution** - Downloads happen once and are cached
- **Efficient layer usage** - Better layer reuse and smaller images

### Dockerfile

The Dockerfile sets up an Android build environment with:
- Ubuntu 22.04 base
- OpenJDK 17
- Android SDK with platform-tools, Android 34 platform, and build-tools 34.0.0
- Gradle wrapper with parallel build settings

**BuildKit Cache Mounts:**
```dockerfile
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon --parallel
```

This mounts the Gradle cache directly during build, avoiding expensive copy operations.

**Layer Caching Strategy:**
1. SDK tools downloaded with cache mount (rarely changes)
2. Gradle wrapper and properties copied first (rarely changes)
3. Build files copied next (changes occasionally)
4. Dependencies pre-downloaded with cache mount
5. Source code copied last (changes frequently)

This ensures:
- ✅ **Incremental builds** - Only changed layers rebuild
- ✅ **Fast code changes** - No image rebuild needed for code-only changes
- ✅ **Persistent dependencies** - Downloaded once, never again

### docker-compose.yml

Defines multiple services for different tasks:
- `build-debug`: Builds debug APK with parallel compilation
- `build-release`: Builds release APK
- `test`: Runs unit tests
- `lint`: Runs lint checks
- `clean`: Cleans build artifacts

**Volume Mounts:**
- `.:/app` - Your source code (live updates)
- `gradle_cache` - Gradle dependencies cache (persisted)
- `build_output` - Build outputs (APKs)
- `test_results` - Test results
- `lint_results` - Lint reports

**Environment Variables:**
- `GRADLE_USER_HOME` - Points to cached Gradle directory
- `GRADLE_OPTS` - Enables parallel builds and disables daemon
- `DOCKER_BUILDKIT=1` - Enables BuildKit features

## Important Notes

### UI/Instrumented Tests

**Docker does NOT support running Android instrumented tests** (the ones in `app/src/androidTest`). These tests require an Android emulator or physical device, which cannot run in standard Docker containers.

For UI tests:
1. Use your local Android emulator or device
2. Run `./gradlew connectedAndroidTest` on your host machine

Docker is only for:
- Building APKs
- Running unit tests (in `app/src/test`)
- Running lint checks
- CI/CD pipelines

### API Keys

The build requires API keys in `local.properties`. Make sure this file exists on your host machine:

```properties
GEMINI_API_KEY=your_key_here
GOOGLE_SEARCH_API_KEY=your_key_here
GOOGLE_SEARCH_ENGINE_ID=your_id_here
```

The file is mounted into the container via the volume binding.

### First Build

The first `docker-compose build` will:
1. Download Ubuntu base image (~100MB)
2. Download Android SDK command-line tools (~150MB)
3. Download platform-tools, SDK platform, and build-tools (~500MB)
4. Pre-download Gradle dependencies (~200MB+)

This can take 10-30 minutes depending on your internet connection. Subsequent builds are much faster due to Docker layer caching.

## Troubleshooting

### Build Fails with "Out of Disk Space"

Docker images and Android builds can be large. Free up space:

```bash
# Remove unused Docker images
docker system prune -a

# Remove build artifacts
docker-compose run --rm clean
```

### Slow Builds

- Ensure Docker has enough RAM (8GB+ recommended in Docker Desktop settings)
- Check that volume mounts are working (cached gradle dependencies persist)
- If on Windows/Mac, Docker performance can be slower than Linux

### Permission Issues

On Linux, files created by Docker may be owned by root. Fix with:

```bash
# Change ownership back to your user
sudo chown -R $USER:$USER app/build
```

### Cannot Find APK

After building, the APK should be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

If not found:
1. Check the Docker run output for errors
2. Verify the `build_output` volume is working
3. Try running the build directly: `./gradlew assembleDebug` inside the container

## Incremental Build Workflow

> [!IMPORTANT]
> **You only need to rebuild the Docker image when dependencies change!**

For the fastest development cycle:

### First Time Setup
```bash
# Enable BuildKit
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

# Build the image (one time)
./docker-build.sh rebuild
```

### Daily Development
```bash
# Just run builds - NO image rebuild needed!
./docker-build.sh build-debug

# Make code changes...

# Run again - uses cached dependencies
./docker-build.sh build-debug
```

### When Dependencies Change
```bash
# Only rebuild image when build.gradle.kts changes
./docker-build.sh rebuild
```

## Performance Tips

### Build Speed Optimizations

1. **Use BuildKit** - Always enable BuildKit for 2-3x faster builds
   ```bash
   export DOCKER_BUILDKIT=1
   export COMPOSE_DOCKER_CLI_BUILD=1
   ```

2. **Preserve Gradle cache** - Don't delete `gradle_cache` volume
   - First build: ~10-15 minutes (downloads everything)
   - Subsequent builds: ~2-5 minutes (uses cache)
   - Code-only changes: ~1-2 minutes

3. **Use parallel builds** - Already enabled in docker-compose.yml
   ```bash
   --parallel --build-cache
   ```

4. **Allocate enough resources**
   - Docker Desktop Settings → Resources
   - RAM: 8GB+ recommended (12GB+ for faster builds)
   - CPUs: Use all available cores
   - Disk: 20GB+ free space

5. **Don't rebuild the image unnecessarily**
   - Code changes: Just run `docker-compose run`
   - Dependency changes: Run `./docker-build.sh rebuild`

6. **Use the automated script** - It handles BuildKit automatically
   ```bash
   ./docker-build.sh build-debug --time
   ```

### Measuring Performance

```bash
# See how long builds take
./docker-build.sh build-debug --time

# Compare with and without cache
./docker-build.sh build-debug --time --no-cache  # Slower
./docker-build.sh build-debug --time              # Faster
```

### Expected Build Times

| Scenario | Time (approx) |
|----------|---------------|
| First image build | 10-15 min |
| Rebuild with cache | 2-5 min |
| Code change only | 1-2 min |
| Full rebuild (no cache) | 15-20 min |

*Times vary based on hardware and internet speed*

## Clean Up

**Remove all Docker volumes (including caches):**
```bash
docker-compose down -v
```

**Remove just the Docker image:**
```bash
docker rmi fluently-aac-android-build
```

## Deploying to Android Virtual Device

After building the APK, you can deploy it to an Android emulator using the automated deployment script.

### Quick Deployment

```bash
# Build and deploy to first available device
./deploy.sh --launch
```

### Deployment Script Features

The `deploy.sh` script provides:
- ✅ Automatic device detection
- ✅ Builds APK (Docker or local)
- ✅ Installs on emulator
- ✅ Optional app launch
- ✅ Clean uninstall of previous version

### Prerequisites

1. **ADB installed** (Android SDK Platform Tools)
2. **Emulator running**
   ```bash
   emulator -list-avds          # List available emulators
   emulator -avd <name> &       # Start an emulator
   ```

### Usage Examples

```bash
# Docker build + deploy + launch
./deploy.sh --launch

# Local Gradle build + deploy
./deploy.sh --local --launch

# Deploy to specific device
./deploy.sh --device emulator-5554 --launch

# Install only (skip build)
./deploy.sh --install-only --launch

# Release build deployment
./deploy.sh --release --device emulator-5554
```

### Script Options

- `--docker` (default) - Build using Docker
- `--local` - Build using local Gradle
- `--release` - Build release APK (default: debug)
- `--install-only` - Skip build, just install existing APK
- `--device <id>` - Deploy to specific device
- `--launch` - Launch the app after installation

For complete deployment documentation, see [.agent/workflows/deploy-to-device.md](../.agent/workflows/deploy-to-device.md)


## CI/CD Integration

This Docker setup is designed for CI/CD:

```yaml
# Example GitHub Actions
- name: Build APK
  run: docker-compose run --rm build-debug

- name: Run Tests
  run: docker-compose run --rm test

- name: Lint
  run: docker-compose run --rm lint
```

## Further Help

- [Docker Documentation](https://docs.docker.com/)
- [Android Docker Best Practices](https://developer.android.com/studio/build/building-cmdline)
- Project README: [README.md](../README.md)
