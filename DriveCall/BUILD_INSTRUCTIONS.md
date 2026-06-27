# DriveCall - Build & Run Instructions

## Prerequisites

1. **Android SDK** installed (API 29 through 34)
2. **Java 17** (JDK)
3. **Visual Studio Code** with **Kotlin** and **Android Extension Pack** extensions
4. **Android Debug Bridge (ADB)** for device deployment
5. A physical Android device running **Android 10+ (API 29+)** connected via USB

## Build with Gradle

### Option 1: Using VS Code Tasks

1. Open the `DriveCall` folder in VS Code:
   ```
   code D:\Kushanka Semasinghe\call app\DriveCall
   ```

2. Open Terminal in VS Code (`Ctrl + ``)

3. Clean build (optional):
   ```bash
   .\gradlew clean
   ```

4. Build the debug APK:
   ```bash
   .\gradlew assembleDebug
   ```

5. The APK will be generated at:
   ```
   app\build\outputs\apk\debug\app-debug.apk
   ```

### Option 2: Using Command Line

```powershell
cd D:\Kushanka Semasinghe\call app\DriveCall
.\gradlew assembleDebug
```

## Install on Device via ADB

1. Enable **Developer Options** and **USB Debugging** on your Android device.

2. Connect device via USB and verify it's detected:
   ```bash
   adb devices
   ```

3. Install the APK:
   ```bash
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

4. Launch the app:
   ```bash
   adb shell am start -n com.drivecall/.MainActivity
   ```

## Required Permissions

On first launch, the app will request:

- **Microphone** (`RECORD_AUDIO`) - for voice commands
- **Phone** (`READ_CONTACTS`) - to read your contacts
- **Phone** (`CALL_PHONE`) - to place calls
- **Notifications** (`POST_NOTIFICATIONS`) - for foreground service notification

All permissions must be granted for the app to function.

## How to Use

1. Open the DriveCall app
2. Grant all permissions when prompted
3. Tap the large microphone button to start listening
4. Say: "Call Amma" or "Dial Thaththa" or "Phone Nimal"
5. The app will recognize the contact and place the call

## Project Structure

```
DriveCall/
├── app/src/main/java/com/drivecall/
│   ├── MainActivity.kt              - Main UI entry point
│   ├── DriveCallApplication.kt      - Application class
│   ├── contacts/ContactRepository.kt - Loads & caches contacts
│   ├── fuzzy/FuzzySearchHelper.kt   - Levenshtein matching
│   ├── models/                      - Data classes
│   ├── notification/NotificationHelper.kt
│   ├── permissions/PermissionManager.kt
│   ├── receivers/BootReceiver.kt
│   ├── services/DriveCallForegroundService.kt
│   ├── speech/SpeechManager.kt      - Speech recognition
│   ├── tts/TextToSpeechManager.kt   - Voice feedback
│   ├── ui/MainUIState.kt
│   ├── utilities/                   - Logger, CommandParser, etc.
│   ├── viewmodels/MainViewModel.kt  - Business logic
│   └── wakeword/WakeWordEngine.kt   - Wake word interface
├── app/src/main/res/                - Resources
├── build.gradle.kts                 - Root build config
├── settings.gradle.kts
└── gradle/wrapper/
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Build fails - SDK not found | Set `ANDROID_HOME` environment variable to your SDK path |
| ADB not found | Add Android SDK `platform-tools` to your `PATH` |
| "INSTALL_FAILED_UPDATE_INCOMPATIBLE" | Uninstall existing version first: `adb uninstall com.drivecall` |
| Speech not recognized | Check microphone permission, speak clearly, reduce background noise |
| Contacts not loading | Grant `READ_CONTACTS` permission, restart the app |
