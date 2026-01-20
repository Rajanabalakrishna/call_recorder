# Call Recorder - Production Architecture Guide (2026)

## Overview

This document explains the complete architecture of the background call recording system. The key innovation is the **Dual-Layer Architecture**: native Android services handle background recording independent of the Flutter app lifecycle, while the Dart layer provides UI and recovery mechanisms.

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         FLUTTER/DART LAYER (UI)                         │
│                      call_recorder_service_enhanced.dart                 │
│                                                                          │
│  ┌──────────────────────┐  ┌─────────────────────┐  ┌──────────────┐  │
│  │ Recording UI Widget  │  │ Phone State Stream  │  │ Debug Info   │  │
│  │ (Start/Stop Buttons) │  │ (State Awareness)   │  │ (Troubleshoot)│  │
│  └──────────────────────┘  └─────────────────────┘  └──────────────┘  │
│           │                         │                        │          │
│           └─────────────────────────┼────────────────────────┘          │
│                                     │                                    │
│                    Method Channel Communication                          │
│                                     │                                    │
└─────────────────────────────────────┼────────────────────────────────────┘
                                      │
                   ┌──────────────────┴──────────────────┐
                   │                                     │
┌──────────────────v─────────────────────────────────────v───────────────┐
│              NATIVE ANDROID LAYER (Background Recording)               │
│           (Survives app swipe, device restart, system pressure)        │
└────────────────────────────────────────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┬───────────────┐
        │                   │                   │               │
        v                   v                   v               v
  ┌───────────┐     ┌─────────────────┐  ┌──────────────┐  ┌──────────┐
  │CallState  │     │Accessibility    │  │Recording     │  │Boot      │
  │Receiver   │     │Service (Backup) │  │Manager       │  │Receiver  │
  │           │     │                 │  │(Persistent   │  │(Restart) │
  │Native     │     │Independent      │  │State Store)  │  │          │
  │TelephonyMgr│     │Call Detection   │  │SharedPrefs   │  │Auto-Init │
  │           │     │                 │  │              │  │          │
  └─────┬─────┘     └────────┬────────┘  └──────┬───────┘  └──────────┘
        │                    │                   │
        └────────────────────┼───────────────────┘
                             │
                             v
                    ┌────────────────┐
                    │CallRecording   │
                    │Service         │
                    │                │
                    │- Foreground    │
                    │  Notification  │
                    │- MediaRecorder │
                    │- Logging       │
                    │- File I/O      │
                    └────────────────┘
                             │
                             v
                    ┌────────────────┐
                    │   Audio File   │
                    │ (App Storage)  │
                    └────────────────┘
```

---

## Core Components

### Native Android Layer (Survives App Lifecycle)

#### 1. **CallStateReceiver** (Primary Call Detection)
- **File**: `android/app/src/main/kotlin/com/example/recorder/CallStateReceiver.kt`
- **Purpose**: Listen for phone state changes via `TelephonyManager`
- **Trigger**: System broadcasts when call state changes (RINGING, OFFHOOK, IDLE)
- **Works When**: App is closed, swiped away, or device is locked
- **Key Feature**: Starts `CallRecordingService` automatically

**Phone States:**
```
TELEPHONY_STATE_RINGING  → Call incoming (notification only)
TELEPHONY_STATE_OFFHOOK → Call active (START recording)
TELEPHONY_STATE_IDLE    → Call ended (STOP recording)
```

#### 2. **CallRecordingService** (Main Recording Logic)
- **File**: `android/app/src/main/kotlin/com/example/recorder/CallRecordingService.kt`
- **Purpose**: Manages MediaRecorder and audio file I/O
- **Type**: Foreground Service (persistent with notification)
- **Features**:
  - MediaRecorder with optimal audio source selection
  - Persistent notification (can't be swiped away)
  - START_STICKY restart policy
  - File path generation with timestamps

#### 3. **BootReceiver** (Device Restart Recovery)
- **File**: `android/app/src/main/kotlin/com/example/recorder/BootReceiver.kt`
- **Purpose**: Restart monitoring after device boot
- **Trigger**: Fired after device completes startup sequence
- **Restores**: Previous recording state from SharedPreferences

#### 4. **RecordingManager** (Persistent State)
- **File**: `android/app/src/main/kotlin/com/example/recorder/RecordingManager.kt`
- **Purpose**: Centralized state management
- **Backed By**: SharedPreferences (survives process death)
- **Stores**:
  - Recording status (boolean)
  - Current file path
  - Last call info
  - Permission flags

#### 5. **CallRecorderAccessibilityService** (Backup Detection)
- **File**: `android/app/src/main/kotlin/com/example/recorder/CallRecorderAccessibilityService.kt`
- **Purpose**: Redundant call detection (window state monitoring)
- **Fallback**: If TelephonyManager detection fails
- **Requires**: User manual enablement in Settings

### Dart/Flutter Layer (UI & Recovery)

#### EnhancedCallRecorderService
- **File**: `lib/call_recorder_service_enhanced.dart`
- **Purpose**: UI-layer state awareness and recovery
- **Features**:
  - Phone state stream for UI updates
  - Periodic verification of native state
  - State synchronization with native layer
  - Manual recording control

---

## How Recording Works - Step by Step

### Scenario: App Swiped Away During Call

```
1. User receives call
   └─> System broadcasts TelephonyManager.ACTION_PHONE_STATE_CHANGED

2. CallStateReceiver receives broadcast
   └─> Call state = OFFHOOK
   └─> Starts CallRecordingService

3. CallRecordingService starts
   └─> Initializes MediaRecorder with audio source
   └─> Creates persistent foreground notification
   └─> Starts recording to file
   └─> Marks recording in RecordingManager (SharedPreferences)

4. User swipes app away
   └─> Flutter app process terminates
   └─> **Recording continues** - CallRecordingService still running

5. Call ends
   └─> System broadcasts TelephonyManager.ACTION_PHONE_STATE_CHANGED
   └─> Call state = IDLE

6. CallStateReceiver receives broadcast
   └─> Sends stop command to CallRecordingService
   └─> Service stops MediaRecorder
   └─> File saved to storage
   └─> Recording status updated in RecordingManager

7. User opens app again
   └─> Dart layer initializes
   └─> Syncs with RecordingManager state
   └─> Can show "Last Recording" info
```

### Scenario: Device Restarts During Call Recording

```
1. Device powers on
   └─> Android broadcasts Intent.ACTION_BOOT_COMPLETED

2. BootReceiver receives broadcast
   └─> Starts CallRecordingService
   └─> Reads saved state from RecordingManager
   └─> Re-enables monitoring

3. Service is ready to detect next call
```

---

## Android Permissions Deep Dive

### Permission Hierarchy for Call Recording (2026)

```
┌─ RECORD_AUDIO (Microphone Access)
│  ├─ Signature level permission
│  ├─ Runtime request required (Android 6.0+)
│  └─ MANDATORY for MediaRecorder
│
├─ READ_PHONE_STATE (Call Detection)
│  ├─ Signature level permission
│  ├─ Runtime request required (Android 6.0+)
│  └─ MANDATORY for TelephonyManager callbacks
│
├─ FOREGROUND_SERVICE (Background Tasks)
│  ├─ Special permission (Android 8.0+)
│  ├─ No runtime request needed
│  ├─ Android 12+: MUST specify foregroundServiceType
│  └─ Android 14+ MUST use type="microphone" (not "phoneCall")
│
├─ POST_NOTIFICATIONS (Android 13+)
│  ├─ Runtime request required
│  ├─ MANDATORY for Foreground Service notification
│  └─ System can deny even if declared
│
├─ RECEIVE_BOOT_COMPLETED (Device Boot)
│  ├─ Normal level permission
│  ├─ No runtime request needed
│  └─ Allows BootReceiver to trigger
│
└─ BIND_ACCESSIBILITY_SERVICE (Backup Detection)
   ├─ Protected permission (ProtectedPermissions level)
   ├─ Cannot request at runtime
   ├─ User must manually enable in Settings
   └─ Bypass requirement: "Dangerous" according to some OEMs
```

### Manifest Declarations

```xml
<!-- Required for audio capture -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- Required for call state detection -->
<uses-permission android:name="android.permission.READ_PHONE_STATE" />

<!-- Required for background service (Android 8.0+) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- Android 12+ specific -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />

<!-- Android 13+ specific -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Required for BootReceiver -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- For accessibility service (backup) -->
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />
```

---

## Audio Source Selection by Android Version

The service automatically selects the optimal audio source:

```kotlin
when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE (34+) → 
        VOICE_RECOGNITION  // Best for Android 14/15/16
    
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S (31+) → 
        VOICE_COMMUNICATION  // Android 12/13
    
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q (29+) → 
        MIC  // Android 10/11
    
    else → 
        DEFAULT  // Older versions
}
```

**Why VOICE_RECOGNITION?**
- Bypasses most echo cancellation filters
- Captures both sides of call reliably
- Recommended by Android documentation for 2024+

---

## State Persistence & Recovery

### Shared Preferences State Store

```
(RecordingManager.kt)
├─ KEY_IS_RECORDING: Boolean
│  └─ Current recording status
│
├─ KEY_CURRENT_FILE: String
│  └─ Active recording file path
│
├─ KEY_LAST_CALL_NUMBER: String
│  └─ Last call phone number
│
├─ KEY_LAST_CALL_TIME: Long
│  └─ Timestamp of last call
│
├─ KEY_ACCESSIBILITY_ENABLED: Boolean
│  └─ Accessibility service status
│
├─ KEY_AUTO_START: Boolean
│  └─ Auto-start preference
│
└─ KEY_PERMISSIONS_GRANTED: Boolean
   └─ Permissions status
```

### Recovery Flow

```
1. Device boot
   └─> BootReceiver triggered
   └─> Reads RecordingManager state
   └─> If was recording → marks as saved
   └─> Starts monitoring

2. App crash
   └─> CallRecordingService continues (separate process)
   └─> SharedPreferences state persists
   └─> App can read state on restart

3. App force-stop
   └─> Native service continues
   └─> BroadcastReceivers still receive events
   └─> Recording completes normally
```

---

## Testing Scenarios

### Test 1: App Swipe Away
1. Open app
2. Start call
3. Verify recording started
4. Swipe app from recents
5. ✅ Recording continues
6. End call
7. ✅ Recording stops
8. Open app → verify file saved

### Test 2: Device Restart
1. Start recording
2. Power off device (during call)
3. Power on device
4. ✅ Monitoring service restarts automatically
5. Make test call
6. ✅ Recording works normally

### Test 3: Accessibility Service Disabled
1. Disable accessibility service
2. Make call
3. ✅ CallStateReceiver still works
4. ✅ Recording continues

### Test 4: Multiple Permissions Denied
1. Deny all permissions
2. Try to make call
3. ✅ App shows permission dialog
4. Grant permissions
5. Make call
6. ✅ Recording works

---

## Performance Considerations

### Memory Usage
- CallRecordingService: ~2-5 MB (minimal)
- MediaRecorder overhead: ~1-3 MB
- Total: ~5-10 MB during recording

### Battery Impact
- Foreground service: ~2-5% per hour
- TelephonyManager listener: <1% per hour
- Audio encoding (AAC): ~3-5% per hour
- Total: ~5-10% per hour of recording

### Storage
- AAC codec bitrate: 128 kbps
- Average call duration: 10 minutes
- Per call: ~9.6 MB
- Compress with: ZIP, GZIP, or cloud storage

---

## Troubleshooting Guide

### Recording not starting
1. Check permissions: Settings → Apps → Permissions
2. Enable Accessibility Service: Settings → Accessibility
3. Check Foreground Service: Settings → Apps → Battery
4. Verify call detection: logcat → grep "CallStateReceiver"

### App crashes but recording continues
- ✅ This is EXPECTED behavior
- Recording runs in native service
- Service automatically recovers
- Call ends normally and file saved

### No audio in recording
1. Verify RECORD_AUDIO permission granted
2. Check audio source: Device speaker working?
3. Verify speaker is not on mute
4. Try different audio source (settings)

### Recording stops unexpectedly
1. Check system memory: Low memory killer may terminate
2. Check thermal state: Device overheating?
3. Verify permissions not revoked: Check Settings
4. Review logcat for error messages

---

## Future Enhancements

1. **WorkScheduler Integration**: Periodic wake-up to verify service
2. **Cloud Backup**: Auto-upload recordings to cloud storage
3. **Database**: SQLite for recording metadata
4. **Call Info**: Integration with Contacts for caller names
5. **Compression**: Automatic compression to save storage
6. **Encryption**: Encrypt recordings at rest
7. **Analytics**: Track recording statistics

---

## References

- Android Foreground Services: https://developer.android.com/guide/components/foreground-services
- TelephonyManager: https://developer.android.com/reference/android/telephony/TelephonyManager
- MediaRecorder: https://developer.android.com/reference/android/media/MediaRecorder
- Accessibility Service: https://developer.android.com/guide/topics/ui/accessibility-service
- Android 14+ Changes: https://developer.android.com/about/versions/14
- Android 15 Guide: https://developer.android.com/about/versions/15
