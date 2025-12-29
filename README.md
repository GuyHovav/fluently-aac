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
