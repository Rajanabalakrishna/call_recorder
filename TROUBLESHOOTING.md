# 🐛 Troubleshooting Guide

## Common Issues & Solutions

### Issue #1: "No recordings after I make a call"

#### Symptoms
- Made a call
- Call worked fine
- Returned to app
- No recording appears in list

#### Root Cause
Battery optimization is KILLING the app in background.

#### Solution: Disable Battery Optimization

**Android 12+:**
1. Go to **Settings → Battery and Device Care**
2. Tap **Optimize now** or **Battery Usage**
3. Find **Call Recorder** in the list
4. Change from **Optimized** to **Not Optimized** or **Unrestricted**

**Device-Specific Steps:**

**Samsung:**
```
Settings → Battery and Device Care → Battery → Background Usage Limits
    ↓
Find Call Recorder → Unrestricted
```

**Xiaomi:**
```
Settings → Apps → App Management → Permissions
    ↓
Find Call Recorder → Background Restrictions → No Restrictions
```

**OnePlus:**
```
Settings → Battery → Battery Optimization
    ↓
Find Call Recorder → Don't Optimize
```

**Google Pixel (Stock Android):**
```
Settings → Apps and Notifications → Special Permissions
    ↓
Battery Optimization → Call Recorder → Don't Optimize
```

**Verification:**
- Go to Settings → Battery
- Verify Call Recorder appears as "Unrestricted" or "Not Optimized"

#### Why This Matters
Battery optimization closes background apps after 15-30 minutes. If recording happens after optimization kicks in, the app gets killed and recording stops.

---

### Issue #2: "Accessibility Service won't enable"

#### Symptoms
- Try to enable accessibility service
- Settings show it's OFF
- Even after enabling, it turns off automatically

#### Root Cause
Either the service isn't properly registered or another app is conflicting.

#### Solution: Manual Enable & Verification

**Step 1: Manual Enable**
1. Open **Settings app**
2. Go to **Accessibility** (Search for "Accessibility" if not visible)
3. Scroll down, find **Call Recorder**
4. Tap **Call Recorder**
5. Toggle **Use Call Recorder** to **ON**
6. Confirm when prompted

**Step 2: Verify It's Working**
1. Look at notification bar (top of screen)
2. You should see **"Call Recorder"** notification
3. Swipe down notification shade to verify

**Step 3: If Still Not Working**

Try these in order:

a) **Restart Device**
```bash
# Hold power button, select Restart
```

b) **Clear App Data**
```bash
adb shell pm clear com.example.recorder
```

c) **Reinstall App**
```bash
adb uninstall com.example.recorder
flutter run  # to reinstall
```

d) **Check Other Accessibility Apps**
- Disable any other accessibility services
- Try enabling Call Recorder again
- Re-enable others if needed

#### If Still Not Working
- Device might have restrictions
- Try on a different device
- Check Android version (API 21+)

---

### Issue #3: "App crashes on startup"

#### Symptoms
- Tap app icon
- App starts loading
- Crashes with error message
- Can't open app

#### Root Cause
Corrupted app data or incompatible Kotlin version.

#### Quick Fix

**Method 1: Clear App Data**
```bash
adb shell pm clear com.example.recorder
# Then run app again
flutter run
```

**Method 2: Full Rebuild**
```bash
flutter clean
flutter pub get
flutter run
```

**Method 3: Debug Mode**
```bash
flutter run -v  # Shows detailed logs
# Look for error messages
```

#### Check Logs
```bash
flutter run -v 2>&1 | grep -i "error\|exception\|crash"
```

Common errors:
- **MethodChannelException** - Kotlin channel not initialized
- **NullPointerException** - Service not found
- **FileNotFoundException** - Recording directory doesn't exist

#### Solutions

**If MethodChannelException:**
- Rebuild: `flutter clean && flutter pub get`
- Check MainActivity.kt has setupMethodChannel() call

**If NullPointerException:**
- Check AndroidManifest.xml has all services declared
- Verify service names match implementation

**If FileNotFoundException:**
```bash
# This usually auto-creates, but you can create manually:
adb shell mkdir -p /data/data/com.example.recorder/files/CallRecordings
```

---

### Issue #4: "No sound in recordings"

#### Symptoms
- Recording is created
- File has content (not 0 bytes)
- Play recording
- Audio is silent or very quiet

#### Root Cause
- Microphone permission denied
- Device in mute mode
- Incorrect audio source

#### Solution

**Step 1: Verify Microphone Permission**

```bash
# Check if permission is granted
adb shell pm list permissions | grep RECORD_AUDIO

# Or manually check:
# Settings → Apps → Call Recorder → Permissions → Microphone
# Should show: ✅ Allowed
```

If not allowed:
1. Open Settings
2. Go to Apps → Call Recorder
3. Tap Permissions
4. Toggle Microphone: ON

**Step 2: Check Mute Mode**

1. Check physical mute switch (left side of phone)
2. Should be towards the screen (unmute)
3. Check volume buttons - set to medium/high
4. Check Settings → Sounds - not in silent mode

**Step 3: Test Microphone**

Verify microphone works in other apps:
1. Open native Voice Recorder app
2. Make a recording
3. Listen back - audio should be clear

If native recorder has no sound:
- Problem is with device microphone
- Try different recording source
- Test on different device

**Step 4: Check Recording Source**

In `CallRecorderService.kt`, verify:
```kotlin
val audioRecord = AudioRecord(
    AudioSource.MIC,  // ✅ Correct - microphone
    16000,
    ...
)
```

**Step 5: Increase Recording Gain**

If sound is very quiet (but present):

In Android code:
```kotlin
// Increase buffer read sensitivity
val buffer = ByteArray(minBufferSize * 2)  // Larger buffer
// or apply gain
for (i in buffer.indices) {
    buffer[i] = (buffer[i] * 1.5).toByte()  // Increase by 50%
}
```

#### If Still Silent

1. Try different device
2. Check if headphones are connected (might route audio there)
3. Rebuild with verbose logging: `flutter run -v`
4. Check logcat for AudioRecord errors

---

### Issue #5: "Recording stops when I close app"

#### Symptoms
- Make a call
- Close app while recording
- Recording stops
- No audio captured

#### Root Cause
Foreground Service isn't keeping app alive.

#### Solution

**Step 1: Verify Foreground Service Started**

Check notification during recording:
1. Make a call
2. Look at notification bar
3. Should see "Call Recorder" with "Recording..." message

If no notification:
- Foreground service didn't start
- Check MainActivity.kt for service start code

**Step 2: Check Permissions**

```bash
adb shell pm list permissions | grep FOREGROUND
```

If missing, add to AndroidManifest.xml:
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

**Step 3: Disable Power Saving Mode**

This is the most common culprit!

1. Settings → Battery
2. Disable Battery Saver / Power Saving Mode completely
3. Or add Call Recorder to whitelist

**Step 4: Check Service Configuration**

```kotlin
// In CallRecordingForegroundService.kt
override fun onStartCommand(...): Int {
    startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_RECORDING
    )
    return START_STICKY  // ✅ Important!
}
```

If `START_STICKY` is not there, add it.

**Step 5: Test Again**

1. Disable battery saver
2. Make a call
3. See notification appears
4. Close app while recording
5. Open app after call - recording should exist

---

### Issue #6: "App not auto-starting after reboot"

#### Symptoms
- Device restarts
- App doesn't launch
- Need to manually open app
- Recording doesn't work until manually opened

#### Root Cause
BootReceiver not being triggered.

#### Solution

**Step 1: Verify Boot Receiver Declared**

In `AndroidManifest.xml`:
```xml
<receiver
    android:name=".BootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>

<!-- Also add this permission -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

**Step 2: Check Boot Receiver Code**

`BootReceiver.kt` should have:
```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // Restart accessibility service
            val i = Intent(context, CallRecorderAccessibilityService::class.java)
            context?.startService(i)
        }
    }
}
```

**Step 3: Test Boot Receiver**

```bash
# Simulate boot completed event
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.example.recorder

# Check if service started
adb shell dumpsys accessibility | grep -i recorder
```

Should show service is running.

**Step 4: Device-Specific Issues**

Some devices block boot broadcasts:

**Samsung:**
- Settings → Apps → Call Recorder → More options → Allow auto-launch

**Xiaomi:**
- Settings → Apps → Permissions → Startup → Allow Call Recorder

**OnePlus:**
- Settings → Apps → Permissions → Auto-launch → Allow Call Recorder

**Step 5: Reboot and Verify**

1. Disable battery optimization again
2. Restart device
3. Wait 30 seconds
4. Check notification bar - should see "Call Recorder"
5. Make test call

---

## Debugging Techniques

### View Logs

```bash
# Real-time logs with colors
flutter run -v

# Filter for errors only
flutter run -v 2>&1 | grep -i "error\|warning\|exception"

# Save logs to file
flutter run -v 2>&1 | tee app_logs.txt
```

### Check File System

```bash
# List recordings
adb shell ls -la /data/data/com.example.recorder/files/CallRecordings/

# Check file size
adb shell stat /data/data/com.example.recorder/files/CallRecordings/call_*.m4a

# Get file information
adb shell file /data/data/com.example.recorder/files/CallRecordings/call_*.m4a
```

### Check Permissions

```bash
# List granted permissions
adb shell pm list permissions -u | grep -i recorder

# Check specific permission
adb shell pm list packages | grep -i recorder
```

### Check Services

```bash
# List running services
adb shell dumpsys activity services | grep -i recorder

# Check accessibility services
adb shell dumpsys accessibility | grep -i recorder

# Check foreground services
adb shell ps -A | grep -i recorder
```

### Check Notifications

```bash
# List active notifications
adb shell dumpsys notification | grep -i recorder
```

---

## Advanced Debugging

### Enable Audio Logging

In `CallRecorderService.kt`:
```kotlin
val audioRecord = AudioRecord(...)
Log.d("AudioRecord", "Sample rate: ${audioRecord.sampleRate}")
Log.d("AudioRecord", "Channel count: ${audioRecord.channelCount}")
Log.d("AudioRecord", "Encoding: ${audioRecord.audioFormat}")
Log.d("AudioRecord", "State: ${if(audioRecord.state == AudioRecord.STATE_INITIALIZED) "Ready" else "Error"}")
```

### Monitor File Writing

```kotlin
var bytesWritten = 0L
while (isRecording) {
    val bytes = audioRecord.read(buffer, 0, buffer.size)
    if (bytes > 0) {
        bytesWritten += bytes
        Log.d("Recording", "Bytes written: $bytesWritten")
    }
}
```

### Test Kotlin Functions

```bash
# Create simple test
adb shell am startservice -n com.example.recorder/.CallRecorderService

# Stop service
adb shell am stopservice -n com.example.recorder/.CallRecorderService
```

---

## Performance Issues

### High CPU Usage

**Symptoms:**
- App gets hot
- Battery drains quickly
- Stuttering during recording

**Solutions:**
1. Reduce buffer size
2. Increase read interval
3. Lower sample rate (if quality acceptable)
4. Check for encoding issues

### High Memory Usage

**Symptoms:**
- App crashes with OutOfMemory
- System becomes sluggish
- Other apps get killed

**Solutions:**
1. Reduce buffer size
2. Reduce audio sample rate
3. Check for memory leaks in Kotlin code
4. Verify MediaCodec is released properly

### Large File Sizes

**Symptoms:**
- 10+ MB for 10 minute call
- Storage fills up quickly

**Solutions:**
1. Reduce bit rate (from 128 to 64 kbps)
2. Reduce sample rate (from 16kHz to 8kHz)
3. Use better codec (OPUS instead of AAC)

---

## Device-Specific Issues

### Samsung Galaxy Series

**Common Issue:** App doesn't record in background
**Solution:**
1. Go to Settings → Battery and Device Care
2. Tap Optimize now
3. Click the 3-dot menu → Detailed
4. Find Call Recorder → Unrestricted

### Xiaomi (MIUI)

**Common Issue:** Accessibility service keeps disabling
**Solution:**
1. Go to Settings → Permissions
2. Find Call Recorder
3. Tap it → Device admin → Activate
4. Go to Apps → Permissions → Auto-launch → Enable

### OnePlus (OxygenOS)

**Common Issue:** Recording stops when switching apps
**Solution:**
1. Settings → Apps → Permissions
2. Find Call Recorder
3. Set all permissions to Allow
4. Settings → Battery → Optimize → Call Recorder → Don't optimize

### Google Pixel (Stock Android)

**Common Issue:** Adaptive Battery kills app
**Solution:**
1. Settings → Battery → Battery Optimization
2. Call Recorder → Don't Optimize
3. Disable Adaptive Battery (Settings → Battery → Turn off Adaptive Battery)

---

## Testing Checklist

Before deployment, verify:

- [ ] Microphone works (test with native app)
- [ ] Accessibility service can be enabled
- [ ] Notification appears during recording
- [ ] Battery optimization disabled
- [ ] Test call is recorded
- [ ] Audio has proper sound
- [ ] Playback works
- [ ] Recording persists after app close
- [ ] Service recovers after device reboot
- [ ] Multiple recordings work
- [ ] Delete functionality works
- [ ] File sizes 2-5 MB per minute
- [ ] No crashes on startup
- [ ] Works on multiple devices (if possible)

---

## Still Not Working?

### Last Resort Steps

1. **Full Clean Install**
   ```bash
   flutter clean
   rm -rf build/
   flutter pub get
   flutter run
   ```

2. **Factory Reset App**
   ```bash
   adb uninstall com.example.recorder
   flutter run
   ```

3. **Try on Different Device**
   - Device-specific restrictions may apply
   - Test on another Android phone if available

4. **Check Android Version**
   - Minimum: Android 12 (API 31)
   - Maximum: Current (usually Android 14-15)
   - Some devices with custom ROMs may have issues

5. **Verify Repository Code**
   - Check all source files are copied correctly
   - Verify no modifications to critical files
   - Re-clone and retry if needed

---

**If you're still having issues after trying all of these, check the logs carefully and provide the error message for more targeted help!** 🔍
