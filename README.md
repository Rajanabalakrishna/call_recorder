# 🎙️ Call Recorder - Production Ready

A professional Flutter + Kotlin call recording application. Automatically records all incoming/outgoing calls without manual intervention. Similar to Cube ACR Recorder but fully customizable for private deployment.

## ✨ Features

- ✅ **Automatic Recording** - Records all calls automatically
- ✅ **Background Operation** - Works even with app closed
- ✅ **M4A Format** - High-quality compressed audio
- ✅ **Multi-Device Support** - Samsung, Xiaomi, OnePlus, Stock Android
- ✅ **Auto-Start** - Recovers after device reboot
- ✅ **Battery Optimized** - Efficient background service
- ✅ **Professional UI** - Material Design 3
- ✅ **Local Storage** - All recordings saved locally, no cloud
- ✅ **Playback** - Play and manage recordings in-app
- ✅ **Private Deployment** - Not for Play Store

## 🏗️ Architecture

### Three-Service Design

```
┌─────────────────────────────────────────┐
│     Flutter UI Layer (Dart)             │
│  - Home Screen (Recordings List)        │
│  - Audio Player                         │
│  - Settings & Status                    │
└──────────────┬──────────────────────────┘
               │ Method Channel
┌──────────────▼──────────────────────────┐
│   Android Native Layer (Kotlin)         │
│                                         │
│  1️⃣ Accessibility Service              │
│     • 24/7 call detection              │
│     • System-level monitoring          │
│                                         │
│  2️⃣ Recording Service                 │
│     • AudioRecord API                  │
│     • M4A Encoding (MediaCodec)        │
│     • File Management                  │
│                                         │
│  3️⃣ Foreground Service                │
│     • Process Preservation             │
│     • Battery Optimization Bypass      │
└─────────────────────────────────────────┘
```

## 📂 Project Structure

```
call_recorder/
├── android/
│   └── app/src/main/
│       ├── kotlin/com/example/recorder/
│       │   ├── MainActivity.kt
│       │   ├── CallRecorderService.kt
│       │   ├── CallRecorderAccessibilityService.kt
│       │   ├── CallRecordingForegroundService.kt
│       │   └── BootReceiver.kt
│       └── AndroidManifest.xml
├── lib/
│   ├── main.dart
│   ├── services/
│   │   └── call_recorder_service.dart
│   ├── screens/
│   │   ├── home_screen.dart
│   │   └── audio_player_screen.dart
│   └── models/
│       └── recording_model.dart
├── pubspec.yaml
├── ARCHITECTURE.md
├── QUICK_START.md
└── TROUBLESHOOTING.md
```

## 🚀 Quick Start (5 Minutes)

### 1. Clone & Setup
```bash
git clone https://github.com/Rajanabalakrishna/call_recorder.git
cd call_recorder
flutter pub get
```

### 2. Build & Run
```bash
flutter run
```

### 3. Post-Installation Setup (On Device)
1. ✅ Grant all permissions when prompted
2. ✅ Enable Accessibility Service
   - Settings → Accessibility → Call Recorder → Enable
3. ✅ Disable Battery Optimization
   - Settings → Battery → Battery Saver → Call Recorder → Unrestricted
4. ✅ Make a test call to verify recording

## 🔧 Technologies

| Component | Tech | Purpose |
|-----------|------|---------|
| UI Framework | Flutter 3.8+ | Cross-platform interface |
| Language | Dart + Kotlin | App logic & native |
| Audio Capture | AudioRecord API | Microphone input |
| Encoding | MediaCodec | M4A format |
| Call Detection | Accessibility API | System monitoring |
| Storage | File system | Local recordings |
| Bridge | MethodChannel | Dart ↔ Kotlin IPC |

## 📋 System Requirements

- Flutter 3.8.1+
- Android SDK 21+ (Minimum)
- Target Android 12+ (API 31+)
- Kotlin 1.7+
- Minimum RAM: 2GB
- Storage: 100MB free

## 🎯 How It Works

### Call Recording Flow
```
User receives call
    ↓
Accessibility Service detects call state change
    ↓
Starts Foreground Service (keeps process alive)
    ↓
Initializes Recording Service
    ↓
Captures audio via AudioRecord API
    ↓
Encodes to M4A (AAC codec at 128kbps)
    ↓
Saves to /data/data/.../CallRecordings/call_TIMESTAMP.m4a
    ↓
Call ends → Recording stops
    ↓
Stops Foreground Service
    ↓
Flutter app loads & displays recording
```

## 📚 Documentation

- **[QUICK_START.md](./QUICK_START.md)** - 5-minute setup guide
- **[ARCHITECTURE.md](./ARCHITECTURE.md)** - Deep dive into how it works
- **[TROUBLESHOOTING.md](./TROUBLESHOOTING.md)** - Common issues & solutions

## ⚙️ Configuration

### Permissions (AndroidManifest.xml)
```xml
<!-- Required Permissions -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.PROCESS_OUTGOING_CALLS" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

<!-- For Background Operation -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

### Dependencies (pubspec.yaml)
```yaml
flutter:
  sdk: flutter

dependencies:
  permission_handler: ^11.4.1
  phone_state: ^0.5.0
  path_provider: ^2.0.15
  audioplayers: ^5.0.1
  http: ^1.1.0
```

## 🧪 Testing Checklist

Before deployment verify:
- [ ] Microphone works (test in system voice recorder)
- [ ] Accessibility service can be enabled
- [ ] Battery optimization can be disabled
- [ ] Recordings save during call
- [ ] Audio has proper sound (not silent)
- [ ] Playback works in-app
- [ ] Recording persists after app close
- [ ] Service recovers after device reboot
- [ ] Multiple recordings work correctly
- [ ] Delete functionality works
- [ ] File sizes reasonable (2-5 MB per minute)
- [ ] No crashes on startup

## 🐛 Common Issues

| Issue | Solution |
|-------|----------|
| No recordings saved | Disable battery optimization for app |
| App crashes on startup | Clear app data: `adb shell pm clear com.example.recorder` |
| No sound in recording | Verify microphone permission granted in Settings |
| Service stops when app closes | Ensure battery optimization is DISABLED |
| Accessibility not enabling | Go to Settings → Accessibility → Enable manually |
| Recordings folder not visible | Folder is at: `/data/data/com.example.recorder/files/CallRecordings` |

**See [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) for detailed solutions.**

## 🔐 Security & Privacy

✅ **Local Storage Only**
- All recordings stored on device
- No automatic cloud upload
- User has complete control

✅ **Transparent Permissions**
- All permissions declared in manifest
- Users must grant manually
- No hidden background processes

⚠️ **Legal Considerations**
- Call recording may be illegal in some jurisdictions
- Must inform other party (varies by region)
- Intended for personal/internal use only
- Not for commercial deployment

## 🎓 Learning Resources

This project demonstrates:
- Flutter & Dart best practices
- Kotlin native Android development
- Method channels for Dart-Kotlin communication
- Accessibility Service implementation
- Audio recording & encoding
- MediaCodec usage
- Foreground service patterns
- Broadcast receiver implementation
- Material Design 3 in Flutter
- Background service architecture

## 🚀 Deployment

### For Private Users

1. Build APK:
```bash
flutter build apk --release
```

2. Build AAB (for testing):
```bash
flutter build appbundle
```

3. Distribute APK directly to users via:
- Email
- Cloud storage (Google Drive, Dropbox)
- USB transfer
- Internal server

### NOT for Play Store
This app is designed for private deployment. Play Store prohibits automatic call recording without explicit user control per call.

## 💡 Pro Tips

1. **Always test on real device** - Emulator may lack audio input
2. **Use Android Studio** - Better debugging via logcat
3. **Monitor logs**: `flutter run -v`
4. **Device-specific** - Different Android skins behave differently
5. **Test on multiple devices** if possible

## 🎯 Next Steps

1. Clone the repository
2. Follow [QUICK_START.md](./QUICK_START.md)
3. Build and test on device
4. Follow post-install setup carefully
5. Verify with test call

## 📞 Support

For issues:
1. Check [TROUBLESHOOTING.md](./TROUBLESHOOTING.md)
2. Review logcat: `flutter run -v`
3. Verify all permissions granted
4. Ensure battery optimization disabled
5. Check accessibility service enabled

## 📄 License

MIT License - See LICENSE file

## 👨‍💻 Author

Rajana balakrishna  
[GitHub](https://github.com/Rajanabalakrishna)  
[Email](mailto:rajanayaswanth152414@gmail.com)

---

**Production Ready | Fully Documented | Ready to Deploy** 🎙️
