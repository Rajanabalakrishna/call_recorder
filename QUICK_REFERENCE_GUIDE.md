# Quick Reference Guide - Call Recorder Implementation

## 5-Minute Problem Summary

**Your Issue:** Recording stops when app is closed/swiped away
**Root Cause:** Missing foreground service + improper lifecycle management
**Solution:** 3-layer architecture with Accessibility Service + Foreground Service + MediaRecorder

---

## Critical Android Permissions Reference

### For Android 10-11
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />
```

### For Android 12-13
```xml
<!-- Above + -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### For Android 14+
```xml
<!-- All above required -->
<!-- Must declare in manifest with foregroundServiceType="microphone" -->
```

---

## Key Code Snippets (Copy-Paste)

### 1. Service Startup with FGS
```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
        ACTION_START_RECORDING -> {
            // Must call startForeground FIRST
            createNotificationChannel()
            updateNotification("Recording...", true)
            
            // Then start recording
            startRecording(...)
            
            return START_STICKY
        }
    }
}
```

### 2. Notification That Doesn't Dismiss
```kotlin
NotificationCompat.Builder(this, CHANNEL_ID)
    .setOngoing(true)           // NON-DISMISSIBLE
    .setAutoCancel(false)       // NO CLOSE BUTTON
    .setPriority(PRIORITY_LOW)  // QUIET
    .build()
```

### 3. Start Foreground Service
```kotlin
val intent = Intent(context, CallRecordingService::class.java)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    context.startForegroundService(intent)
} else {
    context.startService(intent)
}
```

### 4. Optimal Audio Source
```kotlin
val audioSource = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    MediaRecorder.AudioSource.VOICE_RECOGNITION
} else {
    MediaRecorder.AudioSource.VOICE_COMMUNICATION
}
mediaRecorder.setAudioSource(audioSource)
```

---

## Common Error Messages & Fixes

| Error | Cause | Fix |
|-------|-------|-----|
| Recording stops on close | FGS without notification | Call startForeground() BEFORE recording |
| Only one-way audio | Wrong AudioSource | Use VOICE_RECOGNITION (Android 10+) |
| Service crashes | NPE on accessibility | Add null checks |
| POST_NOTIFICATIONS error | Permission missing | Request permission on Android 13+ |
| startForegroundService() failed | No notification | Create channel & notification first |

---

For complete implementation, see **STEP_BY_STEP_IMPLEMENTATION.md**
