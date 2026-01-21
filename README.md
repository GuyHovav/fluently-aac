# Fluently AAC

An Augmentative and Alternative Communication (AAC) app for Android, powered by AI.

## Features

- **Customizable Communication Boards**: Create and manage multiple boards with custom buttons
- **AI-Powered Features**:
  - Magic Board generation using Gemini AI
  - Smart image cropping and object detection
  - Predictive button suggestions
  - **Smart Grammar Correction**: Automatically fixes grammar and sentence structure
- **User Experience**:
  - **Display Customization**: Independent font and display size settings
  - **Pronunciation Dictionary**: Customize how specific words are spoken
  - **Home Hub**: Central navigation board with linking to other boards
- **User & Caregiver Modes**: 
  - User Mode: Simplified interface for communication
  - Caregiver Mode: Full editing capabilities (PIN protected)
- **Text-to-Speech**: Built-in speech synthesis
- **Symbol Library**: Integration with ARASAAC pictogram library

## Setup

### Prerequisites

- Android Studio (latest version recommended)
- Android SDK (API 24+)
- Gemini API Key ([Get one here](https://aistudio.google.com/app/apikey))

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/YOUR_USERNAME/fluently-aac.git
   cd fluently-aac
   ```

2. **Set up API keys**
   ```bash
   cp local.properties.template local.properties
   ```
   
   Edit `local.properties` and add your Gemini API key:
   ```properties
   GEMINI_API_KEY=your_actual_api_key_here
   ```

3. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned directory

4. **Build and Run**
   - Wait for Gradle sync to complete
   - Click Run (or press Shift+F10)

### Building with Docker 🐳

Docker provides a consistent build environment without installing Android SDK locally.

**Prerequisites:** Docker 20.10+ with BuildKit support

**Quick Start:**
```bash
# Enable BuildKit (add to ~/.bashrc for persistence)
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

# Build debug APK (uses automated script)
./docker-build.sh build-debug

# Or with timing
./docker-build.sh build-debug --time
```

APK location: `app/build/outputs/apk/debug/app-debug.apk`

**Available Commands:**
- `./docker-build.sh build-debug` - Build debug APK (fastest)
- `./docker-build.sh build-release` - Build release APK
- `./docker-build.sh test` - Run unit tests
- `./docker-build.sh lint` - Run lint checks
- `./docker-build.sh rebuild` - Rebuild Docker image (after dependency changes)
- `./docker-build.sh shell` - Open interactive shell

📖 **Detailed Guide:** See [docs/DOCKER.md](docs/DOCKER.md) for comprehensive documentation, troubleshooting, and advanced usage.

**Note:** UI tests require an emulator/device and must be run on the host machine.

### Deploying to Android Emulator 📱

After building, deploy to an Android virtual device:

**Prerequisites:** Android emulator running

```bash
# Quick deploy (build + install + launch)
./deploy.sh --launch

# Or step by step:
emulator -list-avds                    # List emulators
emulator -avd <name> &                 # Start emulator
./deploy.sh --launch                   # Deploy and launch app
```

**Deployment Options:**
- `./deploy.sh --launch` - Build (Docker), install, and launch
- `./deploy.sh --local --launch` - Build (local Gradle), install, and launch
- `./deploy.sh --device emulator-5554 --launch` - Deploy to specific device
- `./deploy.sh --install-only --launch` - Skip build, just install

📖 **For AI Agents:** See [.agent/workflows/deploy-to-device.md](.agent/workflows/deploy-to-device.md) for complete deployment workflow.


## Configuration

### Debug Mode

For development, PIN entry is bypassed. To change this:

Edit `app/build.gradle.kts`:
```kotlin
buildConfigField("boolean", "DEBUG_MODE", "false")  // Set to false for production
```

### Default PIN

The default Caregiver Mode PIN is `1234`. To change it, edit `BoardViewModel.kt`:
```kotlin
return if (pin == "1234") {  // Change this
```

## Project Structure

```
app/
├── src/main/java/com/example/myaac/
│   ├── data/
│   │   ├── local/          # Room database
│   │   ├── remote/         # API services (Gemini, ARASAAC)
│   │   └── repository/     # Data repositories
│   ├── model/              # Data models
│   ├── ui/
│   │   └── components/     # Composable UI components
│   ├── viewmodel/          # ViewModels
│   └── MainActivity.kt
└── src/main/res/           # Resources (layouts, drawables, etc.)
```

## Technologies Used

- **Kotlin** - Primary language
- **Jetpack Compose** - Modern UI toolkit
- **Room** - Local database
- **Coroutines** - Asynchronous programming
- **Gemini AI** - AI-powered features
- **ARASAAC API** - Symbol library
- **Coil** - Image loading

## License

[Add your license here]

## Contributing

[Add contribution guidelines if applicable]

## Support

For issues or questions, please open an issue on GitHub.
