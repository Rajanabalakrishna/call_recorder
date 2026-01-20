package com.example.recorder

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
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
    private var audioManager: AudioManager? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null
    private var originalAudioMode: Int = AudioManager.MODE_NORMAL

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "🎙️ Accessibility Service Connected")

        handlerThread = HandlerThread("CallRecorderThread").apply {
            start()
            backgroundHandler = Handler(looper)
        }

        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
                Log.e(TAG, "Error registering callback: ${e.message}", e)
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
                Log.e(TAG, "Error registering listener: ${e.message}", e)
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
            // ✅ Use getExternalCacheDir() - accessible when app closed
            val recordingsDir = File(getExternalCacheDir(), "CallRecordings")
            
            // Safe directory creation
            if (!recordingsDir.mkdirs() && !recordingsDir.isDirectory) {
                Log.e(TAG, "❌ Failed to create recordings directory")
                return
            }

            // Generate file path
            val timestamp = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val dateStr = dateFormat.format(Date(timestamp))
            val fileName = "call_${dateStr}_${timestamp}.m4a"
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
                    // Set audio mode FIRST - critical for VOICE_CALL to work
                    originalAudioMode = audioManager?.mode ?: AudioManager.MODE_NORMAL
                    audioManager?.mode = AudioManager.MODE_IN_CALL
                    Log.d(TAG, "🔊 Audio Mode set to: MODE_IN_CALL")

                    // 🔑 CRITICAL: VOICE_CALL records BOTH sides
                    setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(44100)
                    setAudioEncodingBitRate(128000)
                    setAudioChannels(1)  // Mono for call recording
                    setOutputFile(currentFilePath)

                    Log.d(TAG, "🔄 Preparing MediaRecorder...")
                    prepare()
                    
                    Log.d(TAG, "🚀 Starting MediaRecorder...")
                    start()
                    
                    isRecording.set(true)
                    Log.d(TAG, "🔴 RECORDING STARTED: $fileName")
                    Log.d(TAG, "📊 Audio Source: VOICE_CALL (Both sides) - Mode: IN_CALL")
                    
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "❌ IllegalStateException - Recorder state error: ${e.message}", e)
                    release()
                    mediaRecorder = null
                    isRecording.set(false)
                    currentFilePath = null
                    audioManager?.mode = originalAudioMode  // Restore
                    CallRecordingForegroundService.stop(this@CallRecorderAccessibilityService)
                    
                } catch (e: RuntimeException) {
                    Log.e(TAG, "❌ RuntimeException - start() failed: ${e.message}", e)
                    // This often means audio source is not available
                    // Try fallback without VOICE_CALL
                    tryFallbackRecording(recordingsDir, fileName)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception during prepare/start: ${e.message}", e)
                    release()
                    mediaRecorder = null
                    isRecording.set(false)
                    currentFilePath = null
                    audioManager?.mode = originalAudioMode  // Restore
                    CallRecordingForegroundService.stop(this@CallRecorderAccessibilityService)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during startRecording: ${e.message}", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording.set(false)
            currentFilePath = null
            audioManager?.mode = originalAudioMode  // Restore
            CallRecordingForegroundService.stop(this)
        }
    }

    /**
     * Fallback recording if VOICE_CALL fails
     * Uses VOICE_UPLINK + VOICE_DOWNLINK or MIC as last resort
     */
    private fun tryFallbackRecording(recordingsDir: File, fileName: String) {
        Log.w(TAG, "🔄 Fallback: VOICE_CALL not available, trying alternative...")
        
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            // ✅ CRITICAL FIX: Use NEW path, not stale currentFilePath
            val fallbackFilePath = File(recordingsDir, fileName).absolutePath
            currentFilePath = fallbackFilePath

            mediaRecorder?.apply {
                try {
                    // Try VOICE_UPLINK (your voice in call)
                    setAudioSource(MediaRecorder.AudioSource.VOICE_UPLINK)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(44100)
                    setAudioEncodingBitRate(128000)
                    setAudioChannels(1)
                    setOutputFile(fallbackFilePath)  // ✅ Use fresh path

                    prepare()
                    start()
                    
                    isRecording.set(true)
                    Log.d(TAG, "⚠️ FALLBACK RECORDING: Using VOICE_UPLINK (Your voice only)")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Fallback also failed: ${e.message}", e)
                    release()
                    mediaRecorder = null
                    isRecording.set(false)
                    currentFilePath = null
                    CallRecordingForegroundService.stop(this@CallRecorderAccessibilityService)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fallback initialization failed: ${e.message}", e)
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
                    Log.d(TAG, "✅ MediaRecorder stopped")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping recorder: ${e.message}")
                }
                try {
                    release()
                    Log.d(TAG, "✅ MediaRecorder released")
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing recorder: ${e.message}")
                }
            }
            
            // Restore audio mode
            try {
                audioManager?.mode = originalAudioMode
                Log.d(TAG, "🔊 Audio Mode restored to: NORMAL")
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring audio mode: ${e.message}")
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
            try {
                audioManager?.mode = originalAudioMode
            } catch (e2: Exception) {
                Log.e(TAG, "Error restoring audio mode in error handler: ${e2.message}")
            }
            CallRecordingForegroundService.stop(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for call recording
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

        try {
            audioManager?.mode = originalAudioMode
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring audio mode on destroy: ${e.message}")
        }

        Log.d(TAG, "❌ Service Destroyed")
    }
}
