# Call Recorder - Implementation Checklist for 2026

## Overview
This checklist ensures the background call recording system is properly integrated and tested.

---

## Phase 1: Installation & Setup

### Android Native Setup
- [ ] Verify all Kotlin files are in correct package structure
  ```
  android/app/src/main/kotlin/com/example/recorder/
  ├─ BootReceiver.kt ✓
  ├─ CallStateReceiver.kt ✓
  ├─ RecordingManager.kt ✓
  ├─ CallRecordingService.kt ✓
  ├─ CallRecorderAccessibilityService.kt ✓
  ├─ CallRecordingForegroundService.kt ✓
  └─ MainActivity.kt ✓
  ```

- [ ] Update AndroidManifest.xml with:
  - [ ] BootReceiver declaration
  - [ ] CallStateReceiver declaration with PHONE_STATE intent filter
  - [ ] CallRecordingService declaration with microphone foregroundServiceType
  - [ ] All required permissions
  - [ ] Queries element (Android 11+)

- [ ] Verify package name matches manifest
  - Expected: `com.example.recorder`
  - Declared in: `android/app/build.gradle.kts`

### Flutter Setup
- [ ] Add enhanced Dart service
  ```
  lib/call_recorder_service_enhanced.dart ✓
  ```

- [ ] Update main.dart to use enhanced service
  ```dart
  import 'call_recorder_service_enhanced.dart';
  
  final recordingService = EnhancedCallRecorderService();
  await recordingService.initialize();
  ```

- [ ] Update pubspec.yaml (verify dependencies)
  ```yaml
  dependencies:
    phone_state: ^2.1.1
    permission_handler: ^11.3.1
    path_provider: ^2.1.5
    audioplayers: ^6.1.0
  ```

---

## Phase 2: Permissions & System Settings

### Runtime Permissions (Request when app opens)
- [ ] RECORD_AUDIO
- [ ] READ_PHONE_STATE
- [ ] POST_NOTIFICATIONS (Android 13+)
- [ ] All permissions must be GRANTED

### Manual User Settings (Users must enable)
- [ ] **ACCESSIBILITY SERVICE**
  - Settings → Accessibility → Downloaded apps → Call Recorder → Toggle ON
  - This is NOT optional - recording won't work without it

- [ ] **BATTERY OPTIMIZATION** (Recommended)
  - Settings → Apps → Call Recorder → Battery → Unrestricted
  - Prevents system from killing service

- [ ] **FOREGROUND SERVICE**
  - Should be granted automatically when using RECORD_AUDIO + FOREGROUND_SERVICE permissions

### Verification Checklist
- [ ] In-app prompt shows if permissions missing
- [ ] In-app prompt shows if Accessibility Service disabled
- [ ] "Open Settings" button linked to correct system settings
- [ ] Debug info shows actual permission status

---

## Phase 3: Build & Deployment

### Build Configuration
- [ ] Android minSdkVersion: 21 or higher
- [ ] Android compileSdkVersion: 34+ (for Android 14+ APIs)
- [ ] Android targetSdkVersion: 34+ (for latest features)

### Build Commands
```bash
# Clean build
[ ] flutter clean

# Get dependencies
[ ] flutter pub get

# Build for Android
[ ] flutter build apk --release

# Or build AAB for Play Store
[ ] flutter build appbundle --release
```

### Pre-Release Testing
- [ ] Build runs without Gradle errors
- [ ] App starts without crashes
- [ ] No warnings in logcat during startup
- [ ] No permission dialogs on startup

---

## Phase 4: Functional Testing

### Test 1: Normal Call Recording
**Prerequisites**: App open, permissions granted
```
1. [ ] Press "Start Recording" button
   Expected: Recording indicator appears
   Verify: logcat shows "Recording started"

2. [ ] Make a test call (to self or test number)
   Expected: Call connects, audio flows
   Verify: Recording status shows "Active"

3. [ ] Speak into microphone
   Expected: Recording captures audio
   Verify: Recording file size increases

4. [ ] End the call
   Expected: Recording stops automatically
   Verify: logcat shows "Recording stopped"

5. [ ] Play recorded file
   Expected: Both sides of call audible
   Verify: File quality acceptable
```

### Test 2: Background Recording (App Swiped Away)
**Prerequisites**: App permissions granted, call active
```
1. [ ] Start app and begin call
2. [ ] Press "Home" button (app stays running)
3. [ ] Swipe app from recents to close it
   Expected: App closes completely
   Verify: logcat shows no crash

4. [ ] Continue talking
   Expected: Recording continues (notification still visible)
   Verify: logcat shows "Service running"

5. [ ] End the call
   Expected: Recording stops automatically
   Verify: CallRecordingService receives IDLE event

6. [ ] Reopen app
   Expected: Last recording visible in list
   Verify: File was saved to storage
```

### Test 3: Device Lock/Unlock
**Prerequisites**: Call ongoing, app has permission
```
1. [ ] Start recording during call
2. [ ] Lock device (power button)
   Expected: Screen off but recording continues
   Verify: Notification still visible (if show on lock screen enabled)

3. [ ] Continue call while device locked
4. [ ] Unlock device
   Expected: Recording still active
   Verify: File size continuing to increase

5. [ ] End call
   Expected: Recording stops
```

### Test 4: Device Restart
**Prerequisites**: Device with app installed
```
1. [ ] Start app and go through setup
2. [ ] Verify Accessibility Service enabled
3. [ ] Restart device (reboot)
   Expected: Device boots normally

4. [ ] Make a call without opening app
   Expected: Recording starts automatically
   Verify: CallRecordingService starts (BootReceiver triggered)

5. [ ] Open app
   Expected: App shows recording is active
   Verify: Correct file path shown
```

### Test 5: Accessibility Service Disabled
**Prerequisites**: Accessibility Service currently enabled
```
1. [ ] Go to Settings → Accessibility
2. [ ] Disable Call Recorder service
3. [ ] Return to app
   Expected: In-app indicator shows "Not enabled"
   Verify: logcat shows warning

4. [ ] Make test call
   Expected: Recording may not start (depending on fallback)
   Verify: logcat shows status

5. [ ] Re-enable Accessibility Service
6. [ ] Make call again
   Expected: Recording works again
```

---

## Phase 5: Debugging & Diagnostics

### Enable Detailed Logging
```bash
# View all logs
[ ] adb logcat | grep -E "CallRecorder|Recording|CallState"

# Save logs to file
[ ] adb logcat > recording_logs.txt &
```

### Critical Log Messages to Check
- [ ] `BootReceiver: Device boot completed`
- [ ] `CallRecorderAccessibilityService: Service Connected`
- [ ] `CallStateReceiver: Call State Changed`
- [ ] `CallRecordingService: Recording started`
- [ ] `CallRecordingService: Recording stopped`
- [ ] `RecordingManager: Recording marked as started`

### Debug Info Dump
```dart
// In app (method channel)
native.invokeMethod('getDebugInfo')
  .then((info) => print(info))
```

**Should show**:
- ✓ Android Version
- ✓ Device Model
- ✓ Is Recording (true/false)
- ✓ Accessibility Service Status
- ✓ Last Call Info
- ✓ Permissions Status

---

## Phase 6: Edge Cases & Error Handling

### Test: Permissions Denied
```
[ ] Deny RECORD_AUDIO permission
    Expected: Error message shown
    Verify: Permission request dialog appears

[ ] Deny READ_PHONE_STATE permission
    Expected: Call detection fails
    Verify: Manual recording still works

[ ] Deny POST_NOTIFICATIONS (Android 13+)
    Expected: Notification may not show
    Verify: Recording still works
```

### Test: Low Storage
```
[ ] Fill device storage to 100%
[ ] Try to record
    Expected: Recording fails gracefully
    Verify: Error logged, app doesn't crash
```

### Test: Low Memory
```
[ ] Open multiple apps to consume RAM
[ ] Make test call
    Expected: Either recording works or fails gracefully
    Verify: No ANR (Application Not Responding)
```

### Test: Quick Calls
```
[ ] Make very short calls (1-2 seconds)
[ ] Repeat 5+ times rapidly
    Expected: All calls recorded
    Verify: No files deleted or corrupted
```

### Test: Long Calls
```
[ ] Record 30+ minute call
    Expected: File size ~200+ MB
    Verify: No memory leaks or crashes
```

---

## Phase 7: Performance & Stability

### Memory Profiling
```bash
[ ] adb shell am meminfo com.example.recorder
    Expected: ~50-100 MB total
    Alert if: >200 MB
```

### CPU Usage
```bash
[ ] adb shell top | grep recorder
    Expected: <10% CPU while recording
    Alert if: >30% CPU continuously
```

### Battery Usage
```bash
[ ] Record 1 hour of calls
[ ] Check battery stats
    Expected: ~5-10% battery consumed
    Alert if: >30% battery consumed
```

### Crash Stability
- [ ] Force stop app 10 times during recording
  Expected: Recording continues, no data loss
- [ ] Kill app with `adb shell am kill`
  Expected: Service survives, recovers
- [ ] Disable app with `adb shell pm disable`
  Expected: Graceful shutdown

---

## Phase 8: Compliance & Data Safety

### Privacy & Security
- [ ] Recordings stored in app-private directory (can't be accessed by other apps)
- [ ] Recordings NOT backed up to cloud (unless explicitly configured)
- [ ] Recordings NOT shared without explicit user action
- [ ] Sensitive call data NOT logged

### Data Safety Labels (Google Play)
- [ ] Declare RECORD_AUDIO permission collection
- [ ] Mark as "User-controlled" (not "By default")
- [ ] Specify retention policy (e.g., "Until deleted by user")
- [ ] Declare that recordings are NOT shared

### GDPR Compliance (if applicable)
- [ ] Obtain explicit user consent before first recording
- [ ] Provide ability to delete all recordings
- [ ] Log user actions (for audit trail)
- [ ] Allow data export on user request

---

## Phase 9: Deployment Checklist

### Before Release
- [ ] All critical tests passing
- [ ] No crashes in 10+ test runs
- [ ] Recording quality acceptable
- [ ] Performance meets targets
- [ ] Documentation complete
- [ ] Accessibility guidelines met
- [ ] Privacy policy updated
- [ ] Data safety labels completed

### Google Play Store
- [ ] Create app listing
- [ ] Add Privacy Policy URL
- [ ] Complete Data Safety form
- [ ] Set content rating
- [ ] Add screenshots and description
- [ ] Set price/distribution
- [ ] Upload signed APK/AAB
- [ ] Submit for review

### Version 1.0 Release Notes
```
Call Recorder v1.0
- Automatic call recording (all calls)
- Background recording (survives app closure)
- Device restart recovery
- Manual recording controls
- Recording playback
- Call history
- Built for Android 14/15/16

Required setup:
1. Grant microphone permission
2. Enable Accessibility Service
3. Allow foreground service
4. Optimize battery settings
```

---

## Phase 10: Post-Release Monitoring

### Monitor First Week
- [ ] Check crash reports in Google Play Console
- [ ] Review user feedback and ratings
- [ ] Monitor for permission issues in reviews
- [ ] Check device compatibility reports

### Common Issues to Address
- [ ] "Recording doesn't start" → Verify Accessibility Service
- [ ] "No audio in file" → Check audio source selection
- [ ] "App crashes" → Check device RAM/storage
- [ ] "Permission denied" → Improve permission UI

### Bug Fix Priority
1. **CRITICAL**: Recording doesn't work at all
2. **HIGH**: Recording stops unexpectedly
3. **HIGH**: App crashes during recording
4. **MEDIUM**: Audio quality issues
5. **LOW**: UI improvements

---

## Verification Signatures

### Developer Testing
- [ ] Tested on: Android 14 (API 34)
- [ ] Tested on: Android 13 (API 33)
- [ ] Tested on: Android 12 (API 31)
- [ ] Date tested: ___________
- [ ] Tester name: ___________

### Quality Assurance
- [ ] All critical tests passed
- [ ] Performance acceptable
- [ ] No memory leaks
- [ ] No crashes
- [ ] QA sign-off date: ___________
- [ ] QA tester: ___________

### Release Approval
- [ ] Technical review: APPROVED ☐
- [ ] Security review: APPROVED ☐
- [ ] Privacy review: APPROVED ☐
- [ ] Product review: APPROVED ☐
- [ ] Release date: ___________
- [ ] Release manager: ___________

---

**Status**: In Development ✓
**Target Release**: Q1 2026
**Last Updated**: January 20, 2026
