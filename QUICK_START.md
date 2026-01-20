# 🚀 Quick Start Guide (5 Minutes)

## Step 1: Prerequisites Check

Before starting, ensure you have:
- ✅ Flutter 3.8.1+ installed
- ✅ Android SDK 21+ (API level)
- ✅ Kotlin 1.7+
- ✅ A real Android device (API 31+, Android 12+)
- ✅ USB debugging enabled on device

### Check Flutter Version
```bash
flutter --version
```

## Step 2: Clone Repository

```bash
git clone https://github.com/Rajanabalakrishna/call_recorder.git
cd call_recorder
```

## Step 3: Get Dependencies

```bash
flutter clean
flutter pub get
```

## Step 4: Connect Device

1. Connect Android device via USB
2. Enable USB Debugging:
   - Developer Options → USB Debugging → ON
   - (Developer Options: Settings → About Phone → Build number (tap 7x))

3. Verify connection:
   ```bash
   adb devices
   ```
   You should see your device listed.

## Step 5: Run App

```bash
flutter run
```

Wait for app to build and install (first run takes 2-3 minutes).

## Step 6: Post-Installation Setup (CRITICAL ⚠️)

### Part 1: Grant Permissions

When app launches, grant permissions when prompted:
- ✅ Microphone access
- ✅ Phone state access
- ✅ Storage access

If missed, grant manually:
1. Settings → Apps → Call Recorder → Permissions
2. Enable all permissions

### Part 2: Enable Accessibility Service (REQUIRED)

This is the MOST CRITICAL step! Without it, calls won't be detected.

1. Open Settings app
2. Go to **Accessibility**
3. Scroll down and find **Call Recorder**
4. Tap it
5. Toggle **"Use Call Recorder"** to ON
6. Confirm when prompted

**Verify:** Check notification bar - you should see "Call Recorder" notification.

### Part 3: Disable Battery Optimization (REQUIRED)

Without this, app will be killed in background!

1. Open Settings app
2. Go to **Battery** or **Battery and Device Care**
3. Tap **Battery Usage** or **Optimization**
4. Find **Call Recorder** app
5. Set to **"Unrestricted"** or **"Not optimized"**

**Alternative (Device Manager):**
- Settings → Apps → App Management
- Find Call Recorder
- Tap **Optimize (or similar)**
- Change to **Don't optimize**

**Device-Specific:**
- **Samsung:** Settings → Battery → Battery Usage → Never sleep (for Call Recorder)
- **Xiaomi:** Settings → Battery & Performance → App Optimization → Call Recorder → Don't optimize
- **OnePlus:** Settings → Battery → Battery Optimization → Call Recorder → Don't optimize
- **Stock Android:** Settings → Battery → Battery Optimization → Call Recorder → Remove

## Step 7: Test Recording

### Test 1: Make an Incoming Call
1. Ask a friend to call you
2. Let it ring, then answer
3. Talk for 30 seconds
4. End call
5. Go back to app
6. You should see a new recording

### Test 2: Check Recording Quality
1. Tap the recording in app list
2. Tap **Play**
3. Verify you hear the call audio clearly
4. File size should be ~200-400 KB for 30-second call

### Test 3: Check Recording Persistence
1. Make another recording
2. Close app completely
3. Force close in Settings → Apps → Call Recorder → Force Close
4. Open app again
5. Recording should still be there

## ✅ Success Indicators

Your setup is complete when:
- ✅ App launches without crashes
- ✅ Accessibility service shows as enabled
- ✅ Battery optimization is disabled
- ✅ Test call is recorded successfully
- ✅ Recording plays back with audio
- ✅ Recording persists after app close

## ❌ If Something's Wrong

### "No recordings appear after call"
**Solutions:**
1. Check battery optimization is DISABLED (most common)
2. Verify accessibility service is ENABLED
3. Check microphone permission is granted
4. Try on another device if possible
5. Check logcat: `flutter run -v` and look for errors

### "App crashes on startup"
**Solutions:**
```bash
# Clear app data
adb shell pm clear com.example.recorder

# Rebuild
flutter clean && flutter pub get && flutter run
```

### "Accessibility service won't enable"
**Solutions:**
1. Restart device
2. Go to Settings → Accessibility → Manually find Call Recorder
3. Ensure you're in correct Accessibility menu (not a specific app's accessibility)
4. Try disabling other accessibility services first

### "No sound in recording"
**Solutions:**
1. Test microphone in native voice recorder app
2. Verify microphone permission granted
3. Check device isn't in mute mode
4. Try recording again

## 🔍 Debugging

### View Logs
```bash
flutter run -v
```

### Check Recording Files
```bash
adb shell
cd /data/data/com.example.recorder/files/CallRecordings
ls -la
```

### Check Permissions
```bash
adb shell pm list permissions | grep -i recorder
```

### Check Service Status
```bash
adb shell dumpsys accessibility | grep -i recorder
```

## 📚 Next Steps

1. Review [ARCHITECTURE.md](./ARCHITECTURE.md) to understand how it works
2. Check [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) for advanced issues
3. Read main [README.md](./README.md) for full feature list

## 💡 Pro Tips

1. **Always test on real device** - Emulator can't record audio properly
2. **Test on multiple devices** - Different Android skins behave differently
3. **Keep app open during testing** - First time, open app to see notification
4. **Check battery saver state** - Disable aggressive battery saver
5. **Monitor storage** - Recordings are stored locally

## ⏱️ Typical Timeline

| Step | Time |
|------|------|
| Clone & dependencies | 2 min |
| Build & run | 2-3 min |
| Grant permissions | 1 min |
| Enable accessibility | 1 min |
| Disable battery optimization | 1 min |
| Test call | 2-3 min |
| **Total** | **~10 minutes** |

## ✅ You're Done!

Your call recorder is now running! It will:
- 📞 Automatically detect incoming/outgoing calls
- 🎙️ Record both sides of the conversation
- 💾 Save as M4A format
- 📱 Display in app for playback
- 🔄 Auto-start after device reboot

Happy recording! 🎙️
