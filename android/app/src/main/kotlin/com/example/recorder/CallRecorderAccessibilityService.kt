package com.example.recorder

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
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

    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null

    private var lastEventTime = 0L
    private val EVENT_DEBOUNCE_MS = 1000L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        handlerThread = HandlerThread("CallRecorderThread", android.os.Process.THREAD_PRIORITY_BACKGROUND).apply {
            start()
            backgroundHandler = Handler(looper)
        }

        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        registerPhoneStateListener()

        Log.d(TAG, "✅ TelephonyCallback registered (Android 12+)")
        Log.d(TAG, "✅ Service Connected with PhoneStateListener")
        Log.d(TAG, "📱 App can be closed - Recording will continue")
    }

    @Suppress("DEPRECATION")
    private fun registerPhoneStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallStateChange(state)
                }
            }
            try {
                telephonyManager?.registerTelephonyCallback(
                    { it.run() },
                    telephonyCallback!!
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to register TelephonyCallback", e)
            }
        } else {
            phoneStateListener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallStateChange(state)
                }
            }
            try {
                telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to register PhoneStateListener", e)
            }
        }
    }

    private fun handleCallStateChange(state: Int) {
        backgroundHandler?.post {
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    if (isInCall.compareAndSet(false, true) && !isRecording.get()) {
                        Log.d(TAG, "📄 [PhoneState] CALL STARTED")
                        
                        // 🔥 CRITICAL: Minimize Flutter app to prevent ANR
                        minimizeApp()
                        
                        startCallRecording()
                    }
                }
                TelephonyManager.CALL_STATE_RINGING -> {
                    isInCall.set(true)
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    if (isInCall.compareAndSet(true, false)) {
                        Log.d(TAG, "📄 [PhoneState] CALL ENDED")
                        if (isRecording.get()) {
                            stopCallRecording()
                        }
                    }
                }
            }
        }
    }

    /**
     * 🔥 CRITICAL FIX: Minimize the Flutter app when call starts
     * This prevents the main thread from being blocked by UI rendering
     */
    private fun minimizeApp() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            Log.d(TAG, "🏠 App minimized to prevent ANR")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to minimize app", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        if (!isCallRelatedPackage(packageName)) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEventTime < EVENT_DEBOUNCE_MS) return
        lastEventTime = currentTime

        backgroundHandler?.post {
            Log.d(TAG, "📄 [Accessibility Backup] $packageName")
            if (!isInCall.get() && !isRecording.get()) {
                checkCallState()
            }
        }
    }

    private fun checkCallState() {
        try {
            val callState = telephonyManager?.callState ?: TelephonyManager.CALL_STATE_IDLE
            handleCallStateChange(callState)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking call state", e)
        }
    }

    private fun isCallRelatedPackage(packageName: String): Boolean {
        return packageName.contains("dialer") ||
               packageName.contains("phone") ||
               packageName.contains("telecom") ||
               packageName.contains("incallui")
    }

    private fun startCallRecording() {
        if (isRecording.get()) return

        try {
            CallRecordingForegroundService.start(this)
            val filePath = generateRecordingFilePath()
            currentFilePath = filePath

            if (startRecording(filePath)) {
                Log.d(TAG, "✅ Recording started: $filePath")
            } else {
                Log.e(TAG, "❌ Failed to start recording")
                currentFilePath = null
                CallRecordingForegroundService.stop(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in startCallRecording", e)
        }
    }

    private fun stopCallRecording() {
        if (!isRecording.get()) return

        try {
            val savedPath = stopRecording()
            if (savedPath != null) {
                val file = File(savedPath)
                if (file.exists()) {
                    val sizeMB = file.length() / (1024.0 * 1024.0)
                    Log.d(TAG, "✅ Saved: ${file.name} (${String.format("%.2f", sizeMB)} MB)")
                }
            }
            CallRecordingForegroundService.stop(this)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in stopCallRecording", e)
        }
    }

    private fun generateRecordingFilePath(): String {
        val recordingsDir = File(filesDir, "CallRecordings")
        if (!recordingsDir.exists()) recordingsDir.mkdirs()
        
        val timestamp = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val dateStr = dateFormat.format(Date(timestamp))
        val fileName = "call_${dateStr}_$timestamp.m4a"
        
        return File(recordingsDir, fileName).absolutePath
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
        } else {
            phoneStateListener?.let { telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE) }
        }
        
        stopRecording()
        CallRecordingForegroundService.stop(this)
        
        handlerThread?.quitSafely()
        handlerThread = null
        backgroundHandler = null
        
        Log.d(TAG, "❌ Service Destroyed")
    }

    fun startRecording(filePath: String): Boolean {
        if (isRecording.get()) return false

        return try {
            currentFilePath = filePath

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(filePath)

                try {
                    prepare()
                    start()
                    isRecording.set(true)
                    true
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to prepare", e)
                    release()
                    false
                }
            }

            isRecording.get()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start", e)
            mediaRecorder?.release()
            mediaRecorder = null
            false
        }
    }

    fun stopRecording(): String? {
        if (!isRecording.get()) return null

        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording.set(false)

            val savedPath = currentFilePath
            currentFilePath = null
            savedPath
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording.set(false)
            currentFilePath = null
            null
        }
    }

    fun isCurrentlyRecording(): Boolean = isRecording.get()
    fun getCurrentFilePath(): String? = currentFilePath
}
