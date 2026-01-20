package com.example.recorder

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 🚀 CallRecorderAccessibilityService - Background Call Recording (Cube ACR Style)
 * 
 * OPTIMIZED: Uses background thread for processing to prevent ANR
 */
class CallRecorderAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CallRecorderService"
        private var instance: CallRecorderAccessibilityService? = null

        fun getInstance(): CallRecorderAccessibilityService? = instance
        fun isServiceEnabled(): Boolean = instance != null
    }

    private var mediaRecorder: MediaRecorder? = null
    private val isRecording = AtomicBoolean(false)
    private var currentFilePath: String? = null
    private val isInCall = AtomicBoolean(false)
    private var telephonyManager: TelephonyManager? = null

    // Background thread for processing events (prevents ANR)
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // Debounce mechanism - prevent duplicate processing
    private var lastEventTime = 0L
    private val EVENT_DEBOUNCE_MS = 500L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // Create background thread for processing
        handlerThread = HandlerThread("CallRecorderThread").apply {
            start()
            backgroundHandler = Handler(looper)
        }

        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        Log.d(TAG, "✅ Accessibility Service Connected - Background Recording ENABLED")
        Log.d(TAG, "📱 App can be closed - Recording will continue automatically")
        Log.d(TAG, "🔄 Device reboot - Recording will auto-start")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Quick filter - only process relevant events
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return
                
                // Quick package check before processing
                if (!isCallRelatedPackage(packageName)) return
                
                // Debounce - prevent duplicate processing
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastEventTime < EVENT_DEBOUNCE_MS) return
                lastEventTime = currentTime

                // Process on background thread to prevent ANR
                backgroundHandler?.post {
                    Log.d(TAG, "📄 Call UI Detected: $packageName")
                    checkAndHandleCallState()
                }
            }
        }
    }

    /// Check actual phone call state using TelephonyManager
    private fun checkAndHandleCallState() {
        try {
            val callState = telephonyManager?.callState ?: TelephonyManager.CALL_STATE_IDLE

            when (callState) {
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    if (isInCall.compareAndSet(false, true) && !isRecording.get()) {
                        Log.d(TAG, "📄 CALL STARTED (OFFHOOK)")
                        startCallRecording()
                    }
                }

                TelephonyManager.CALL_STATE_RINGING -> {
                    if (isInCall.compareAndSet(false, true)) {
                        Log.d(TAG, "📄 CALL RINGING (waiting for answer)")
                    }
                }

                TelephonyManager.CALL_STATE_IDLE -> {
                    if (isInCall.compareAndSet(true, false)) {
                        Log.d(TAG, "📄 CALL ENDED (IDLE)")
                        if (isRecording.get()) {
                            stopCallRecording()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking call state", e)
        }
    }

    /// Optimized package detection - inline for speed
    private fun isCallRelatedPackage(packageName: String): Boolean {
        return packageName.contains("dialer") ||
               packageName.contains("phone") ||
               packageName.contains("telecom") ||
               packageName.contains("incallui") ||
               packageName.contains("contacts")
    }

    /// Start automatic call recording
    private fun startCallRecording() {
        if (isRecording.get()) {
            Log.w(TAG, "⚠️ Already recording, skipping")
            return
        }

        try {
            CallRecordingForegroundService.start(this)
            val filePath = generateRecordingFilePath()
            currentFilePath = filePath

            if (startRecording(filePath)) {
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
        if (!isRecording.get()) {
            Log.w(TAG, "⚠️ Not recording, skipping")
            return
        }

        try {
            val savedPath = stopRecording()
            if (savedPath != null) {
                Log.d(TAG, "✅ AUTO Recording saved: $savedPath")
                val file = File(savedPath)
                if (file.exists()) {
                    val sizeMB = file.length() / (1024.0 * 1024.0)
                    Log.d(TAG, "📁 File size: ${String.format("%.2f", sizeMB)} MB")
                    notifyFlutterApp(savedPath)
                }
            }
            CallRecordingForegroundService.stop(this)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in stopCallRecording", e)
        }
    }

    private fun generateRecordingFilePath(): String {
        val recordingsDir = File(filesDir, "CallRecordings")
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }
        val timestamp = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val dateStr = dateFormat.format(Date(timestamp))
        val fileName = "call_${dateStr}_$timestamp.m4a"
        return File(recordingsDir, fileName).absolutePath
    }

    private fun notifyFlutterApp(filePath: String) {
        try {
            val intent = Intent("com.example.recorder.NEW_RECORDING")
            intent.putExtra("filePath", filePath)
            sendBroadcast(intent)
            Log.d(TAG, "📤 Notified Flutter app")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not notify Flutter: ${e.message}")
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
        
        // Clean up background thread
        handlerThread?.quitSafely()
        handlerThread = null
        backgroundHandler = null
        
        Log.d(TAG, "❌ Service Destroyed")
    }

    fun startRecording(filePath: String): Boolean {
        if (isRecording.get()) {
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
                    isRecording.set(true)
                    Log.d(TAG, "Recording Started: $filePath")
                    true
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to prepare MediaRecorder", e)
                    release()
                    false
                }
            }

            isRecording.get()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            false
        }
    }

    fun stopRecording(): String? {
        if (!isRecording.get()) {
            return null
        }

        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording.set(false)

            val savedPath = currentFilePath
            currentFilePath = null
            Log.d(TAG, "Recording Stopped: $savedPath")
            savedPath
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording.set(false)
            currentFilePath = null
            null
        }
    }

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

    fun isCurrentlyRecording(): Boolean = isRecording.get()
    fun getCurrentFilePath(): String? = currentFilePath
}
