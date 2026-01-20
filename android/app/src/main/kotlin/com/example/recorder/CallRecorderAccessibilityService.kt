package com.example.recorder

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class CallRecorderAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CallRecorder"
        private var instance: CallRecorderAccessibilityService? = null
        fun getInstance(): CallRecorderAccessibilityService? = instance
    }

    private var mediaRecorder: MediaRecorder? = null
    private val isRecording = AtomicBoolean(false)
    private val isInCall = AtomicBoolean(false)
    private var currentFilePath: String? = null
    private var telephonyManager: TelephonyManager? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "🎙️ Accessibility Service Connected")

        handlerThread = HandlerThread("CallRecorderThread").apply {
            start()
            backgroundHandler = Handler(looper)
        }

        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        registerPhoneStateListener()
        Log.d(TAG, "✅ Phone State Listener Registered")
    }

    @Suppress("DEPRECATION")
    private fun registerPhoneStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallState(state)
                }
            }
            try {
                telephonyManager?.registerTelephonyCallback({ it.run() }, telephonyCallback!!)
                Log.d(TAG, "✅ TelephonyCallback Registered (Android 12+)")
            } catch (e: Exception) {
                Log.e(TAG, "Error registering callback: ${e.message}")
            }
        } else {
            phoneStateListener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallState(state)
                }
            }
            try {
                telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
                Log.d(TAG, "✅ PhoneStateListener Registered (Android 11-)")
            } catch (e: Exception) {
                Log.e(TAG, "Error registering listener: ${e.message}")
            }
        }
    }

    private fun handleCallState(state: Int) {
        Log.d(TAG, "📱 Call State Changed: $state (0=IDLE, 1=RINGING, 2=OFFHOOK)")
        
        backgroundHandler?.post {
            try {
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        if (isInCall.compareAndSet(false, true)) {
                            Log.d(TAG, "📞 CALL STARTED - Starting Recording")
                            startRecording()
                        }
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (isInCall.compareAndSet(true, false)) {
                            Log.d(TAG, "☎️ CALL ENDED - Stopping Recording")
                            stopRecording()
                        }
                    }
                    TelephonyManager.CALL_STATE_RINGING -> {
                        isInCall.set(true)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error handling call state: ${e.message}", e)
            }
        }
    }

    private fun startRecording() {
        if (isRecording.get()) {
            Log.w(TAG, "⚠️ Already recording")
            return
        }

        try {
            // ✅ CRITICAL: Use getExternalCacheDir() - accessible when app closed!
            val recordingsDir = File(getExternalCacheDir(), "CallRecordings")
            
            // Safe directory creation (no race condition)
            if (!recordingsDir.mkdirs() && !recordingsDir.isDirectory) {
                Log.e(TAG, "❌ Failed to create recordings directory")
                return
            }

            // Generate file path
            val timestamp = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val dateStr = dateFormat.format(Date(timestamp))
            val fileName = "call_${dateStr}_$timestamp.m4a"
            currentFilePath = File(recordingsDir, fileName).absolutePath

            Log.d(TAG, "📁 Recording to: $currentFilePath")

            // Start foreground service notification
            CallRecordingForegroundService.start(this)

            // Initialize MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                try {
                    // 🔑 CRITICAL: VOICE_CALL captures BOTH sides of call audio (like Cube ACR)
                    setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(44100)
                    setAudioEncodingBitRate(128000)
                    setOutputFile(currentFilePath)

                    prepare()
                    start()
                    isRecording.set(true)
                    Log.d(TAG, "🔴 RECORDING STARTED: $fileName")
                    Log.d(TAG, "📊 Audio Source: VOICE_CALL (Both sides)")
                } catch (e: IOException) {
                    Log.e(TAG, "❌ Failed to start recording: ${e.message}", e)
                    release()
                    mediaRecorder = null
                    isRecording.set(false)
                    currentFilePath = null
                    CallRecordingForegroundService.stop(this@CallRecorderAccessibilityService)
                } catch (e: RuntimeException) {
                    Log.e(TAG, "❌ Runtime error starting recorder: ${e.message}", e)
                    release()
                    mediaRecorder = null
                    isRecording.set(false)
                    currentFilePath = null
                    CallRecordingForegroundService.stop(this@CallRecorderAccessibilityService)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during startRecording: ${e.message}", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording.set(false)
            currentFilePath = null
            CallRecordingForegroundService.stop(this)
        }
    }

    private fun stopRecording() {
        if (!isRecording.get()) {
            Log.w(TAG, "⚠️ Not recording")
            return
        }

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping recorder: ${e.message}")
                }
                try {
                    release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing recorder: ${e.message}")
                }
            }
            mediaRecorder = null
            isRecording.set(false)

            val filePath = currentFilePath
            currentFilePath = null

            if (filePath != null) {
                val file = File(filePath)
                if (file.exists()) {
                    val sizeMB = file.length() / (1024.0 * 1024.0)
                    Log.d(TAG, "✅ RECORDING SAVED: ${file.name} (${String.format("%.2f", sizeMB)} MB)")
                } else {
                    Log.w(TAG, "⚠️ Recording file not found: $filePath")
                }
            }

            CallRecordingForegroundService.stop(this)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping recording: ${e.message}", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording.set(false)
            CallRecordingForegroundService.stop(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for call recording, but required by interface
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        super.onDestroy()
        instance = null

        stopRecording()
        CallRecordingForegroundService.stop(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
        } else {
            phoneStateListener?.let { telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE) }
        }

        handlerThread?.quitSafely()
        handlerThread = null
        backgroundHandler = null

        Log.d(TAG, "❌ Service Destroyed")
    }
}
