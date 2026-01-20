package com.example.recorder

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * 🚀 CallRecorderAccessibilityService - Background Call Recording (Cube ACR Style)
 * 
 * Why Accessibility Service?
 * - Runs as SYSTEM SERVICE (not tied to app lifecycle)
 * - Survives app crashes, kills, and device memory pressure
 * - Can detect call events even when app is completely closed
 * - Like Cube ACR: Truly persistent background recording
 * 
 * Flow:
 * 1. Device boots → BootReceiver starts this service
 * 2. Service monitors phone state continuously
 * 3. Call starts → AUTO recording begins
 * 4. Call ends → Recording saved automatically
 * 5. Works even if app is force-closed by user
 */
class CallRecorderAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CallRecorderService"
        private var instance: CallRecorderAccessibilityService? = null

        fun getInstance(): CallRecorderAccessibilityService? = instance
        fun isServiceEnabled(): Boolean = instance != null
    }

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentFilePath: String? = null

    // Track call state independently (not dependent on app)
    private var isInCall = false
    private var telephonyManager: TelephonyManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // Initialize telephony manager for system-level call state monitoring
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        Log.d(TAG, "✅ Accessibility Service Connected - Background Recording ENABLED")
        Log.d(TAG, "📱 App can be closed - Recording will continue automatically")
        Log.d(TAG, "🔄 Device reboot - Recording will auto-start")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            when (it.eventType) {
                // Monitor window state changes to detect call UI
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    val packageName = it.packageName?.toString() ?: ""

                    // Detect call-related packages (phone dialers, telecom)
                    if (isCallRelatedPackage(packageName)) {
                        Log.d(TAG, "📄 Call UI Detected: $packageName")
                        // Verify with actual call state
                        checkAndHandleCallState()
                    }
                }

                // Monitor notification state for call notifications
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                    val notification = it.text?.toString() ?: ""
                    if (notification.contains("call", ignoreCase = true)) {
                        Log.d(TAG, "🔔 Call Notification: $notification")
                        checkAndHandleCallState()
                    }
                }
            }
        }
    }

    /// Check actual phone call state using TelephonyManager (System-level)
    private fun checkAndHandleCallState() {
        try {
            val callState = telephonyManager?.callState ?: TelephonyManager.CALL_STATE_IDLE

            when (callState) {
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    // Call is active (answered)
                    if (!isInCall && !isRecording) {
                        Log.d(TAG, "📄 CALL STARTED (OFFHOOK)")
                        isInCall = true
                        startCallRecording()
                    }
                }

                TelephonyManager.CALL_STATE_RINGING -> {
                    // Incoming call ringing (not yet answered)
                    if (!isInCall) {
                        Log.d(TAG, "📄 CALL RINGING (waiting for answer)")
                        isInCall = true
                        // Don't start recording yet - wait for OFFHOOK
                    }
                }

                TelephonyManager.CALL_STATE_IDLE -> {
                    // No call or call ended
                    if (isInCall) {
                        Log.d(TAG, "📄 CALL ENDED (IDLE)")
                        isInCall = false
                        if (isRecording) {
                            stopCallRecording()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking call state", e)
        }
    }

    /// Detect call-related packages for UI monitoring
    private fun isCallRelatedPackage(packageName: String): Boolean {
        val callPackages = listOf(
            "com.android.server.telecom",
            "com.android.incallui",
            "com.google.android.dialer",
            "com.samsung.android.incallui",
            "com.android.phone",
            "com.samsung.android.dialer",
            "com.android.contacts",
            "com.google.android.contacts"
        )
        return callPackages.any { packageName.contains(it, ignoreCase = true) }
    }

    /// Start automatic call recording
    private fun startCallRecording() {
        if (isRecording) {
            Log.w(TAG, "⚠️ Already recording, skipping")
            return
        }

        try {
            // Start foreground service (keeps recording alive)
            CallRecordingForegroundService.start(this)

            // Generate file path
            val filePath = generateRecordingFilePath()
            currentFilePath = filePath

            // Start recording
            val success = startRecording(filePath)

            if (success) {
                Log.d(TAG, "✅ AUTO Recording started: $filePath")
            } else {
                Log.e(TAG, "❌ Failed to start AUTO recording")
                currentFilePath = null
                CallRecordingForegroundService.stop(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in startCallRecording", e)
        }
    }

    /// Stop automatic call recording
    private fun stopCallRecording() {
        if (!isRecording) {
            Log.w(TAG, "⚠️ Not recording, skipping")
            return
        }

        try {
            val savedPath = stopRecording()

            if (savedPath != null) {
                Log.d(TAG, "✅ AUTO Recording saved: $savedPath")

                // Verify file exists and get size
                val file = File(savedPath)
                if (file.exists()) {
                    val sizeMB = file.length() / (1024.0 * 1024.0)
                    Log.d(TAG, "📁 File size: ${String.format("%.2f", sizeMB)} MB")

                    // Notify Flutter app if running
                    notifyFlutterApp(savedPath)
                }
            }

            // Stop foreground service
            CallRecordingForegroundService.stop(this)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in stopCallRecording", e)
        }
    }

    /// Generate unique recording file path
    private fun generateRecordingFilePath(): String {
        val recordingsDir = File(filesDir, "CallRecordings")

        // Create directory if not exists
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }

        // Generate filename with timestamp
        val timestamp = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val dateStr = dateFormat.format(Date(timestamp))
        val fileName = "call_${dateStr}_$timestamp.m4a"

        return File(recordingsDir, fileName).absolutePath
    }

    /// Notify Flutter app (if running) about new recording
    private fun notifyFlutterApp(filePath: String) {
        try {
            val intent = Intent("com.example.recorder.NEW_RECORDING")
            intent.putExtra("filePath", filePath)
            sendBroadcast(intent)
            Log.d(TAG, "📤 Notified Flutter app about new recording")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not notify Flutter (app may be closed): ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopRecording()
        CallRecordingForegroundService.stop(this)
        Log.d(TAG, "❌ Service Destroyed")
    }

    /// Start Recording with optimal audio source
    fun startRecording(filePath: String): Boolean {
        if (isRecording) {
            Log.w(TAG, "Recording already in progress")
            return false
        }

        return try {
            currentFilePath = filePath

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(getOptimalAudioSource())
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(filePath)

                try {
                    prepare()
                    start()
                    isRecording = true
                    Log.d(TAG, "Recording Started: $filePath")
                    true
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to prepare MediaRecorder", e)
                    release()
                    false
                }
            }

            isRecording
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            false
        }
    }

    /// Stop Recording
    fun stopRecording(): String? {
        if (!isRecording) {
            return null
        }

        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            val savedPath = currentFilePath
            currentFilePath = null
            Log.d(TAG, "Recording Stopped: $savedPath")
            savedPath
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            currentFilePath = null
            null
        }
    }

    /// Optimal Audio Source based on Android version
    private fun getOptimalAudioSource(): Int {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                Log.d(TAG, "Using VOICE_RECOGNITION for Android 14+")
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                Log.d(TAG, "Using VOICE_COMMUNICATION for Android 12-13")
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                Log.d(TAG, "Using MIC for Android 10-11")
                MediaRecorder.AudioSource.MIC
            }
            else -> {
                Log.d(TAG, "Using DEFAULT for older Android")
                MediaRecorder.AudioSource.DEFAULT
            }
        }
    }

    fun isCurrentlyRecording(): Boolean = isRecording
    fun getCurrentFilePath(): String? = currentFilePath
}
