package com.example.recorder

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * CallRecordingService - Enhanced foreground service for background call recording
 * 
 * Key improvements over original:
 * - Persists independently of app lifecycle
 * - Continues recording even when app is swiped away
 * - Uses MediaRecorder for audio capture
 * - Maintains foreground notification
 * - Handles service lifecycle and recovery
 * 
 * Architecture: Foreground Service with persistent notification
 * Lifecycle: Can survive app destruction but respects Android resource management
 * Memory: Minimized through efficient MediaRecorder usage
 */
class CallRecordingService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentRecordingPath: String? = null
    private var currentPhoneNumber: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action") ?: "unknown"
        val phoneNumber = intent?.getStringExtra("phoneNumber")

        Log.d(TAG, "onStartCommand: action=$action, phoneNumber=$phoneNumber")

        when (action) {
            "start_recording" -> {
                startRecordingInternal(phoneNumber)
            }
            "stop_recording" -> {
                stopRecordingInternal()
            }
            "initialize_on_boot" -> {
                Log.d(TAG, "Initializing after device boot")
                // Service is running, ready to receive call events
            }
        }

        // Show persistent notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Return START_STICKY to restart service if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopRecordingInternal()
    }

    /**
     * Start recording with proper audio source selection
     */
    private fun startRecordingInternal(phoneNumber: String? = null) {
        if (isRecording) {
            Log.w(TAG, "Recording already in progress, ignoring start request")
            return
        }

        try {
            currentPhoneNumber = phoneNumber

            // Generate file path
            val filePath = generateRecordingFilePath(phoneNumber)
            currentRecordingPath = filePath

            // Initialize MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                // Set optimal audio source based on Android version
                setAudioSource(getOptimalAudioSource())

                // Output format: MPEG-4 for best compatibility
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

                // Audio encoder: AAC for good quality/compression ratio
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

                // Audio settings
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)

                // Set output file path
                setOutputFile(filePath)

                // Prepare and start recording
                try {
                    prepare()
                    start()
                    isRecording = true
                    Log.d(TAG, "Recording started: $filePath (Phone: $phoneNumber)")

                    // Mark as recording in manager
                    RecordingManager.markRecordingStarted(this@CallRecordingService, filePath, phoneNumber)

                } catch (e: IOException) {
                    Log.e(TAG, "Failed to prepare MediaRecorder: ${e.message}", e)
                    release()
                    isRecording = false
                    currentRecordingPath = null
                    throw e
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            currentRecordingPath = null
        }
    }

    /**
     * Stop recording and save file
     */
    private fun stopRecordingInternal() {
        if (!isRecording) {
            Log.d(TAG, "No active recording to stop")
            return
        }

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: IllegalStateException) {
                    // Already stopped or not in recording state
                    Log.w(TAG, "stop() called in invalid state: ${e.message}")
                }
                release()
            }
            mediaRecorder = null
            isRecording = false

            // Verify file was created
            if (currentRecordingPath != null) {
                val file = File(currentRecordingPath!!)
                if (file.exists()) {
                    val sizeKB = file.length() / 1024
                    Log.d(TAG, "Recording saved: ${file.absolutePath} (${sizeKB}KB)")
                } else {
                    Log.w(TAG, "Recording file not found: $currentRecordingPath")
                }
            }

            // Update manager state
            RecordingManager.markRecordingStopped(this)

            currentRecordingPath = null
            currentPhoneNumber = null

            Log.d(TAG, "Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            RecordingManager.markRecordingStopped(this)
        }
    }

    /**
     * Select optimal audio source based on Android version
     */
    private fun getOptimalAudioSource(): Int {
        return when {
            // Android 14+ (API 34+): VOICE_RECOGNITION
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                Log.d(TAG, "Using VOICE_RECOGNITION (Android 14+)")
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            }

            // Android 12-13 (API 31-33): VOICE_COMMUNICATION
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                Log.d(TAG, "Using VOICE_COMMUNICATION (Android 12-13)")
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            }

            // Android 10-11 (API 29-30): MIC
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                Log.d(TAG, "Using MIC (Android 10-11)")
                MediaRecorder.AudioSource.MIC
            }

            // Fallback
            else -> {
                Log.d(TAG, "Using DEFAULT (older Android)")
                MediaRecorder.AudioSource.DEFAULT
            }
        }
    }

    /**
     * Generate unique recording file path with timestamp and phone number
     */
    private fun generateRecordingFilePath(phoneNumber: String? = null): String {
        val recordingsDir = File(getExternalFilesDir(null), "CallRecordings")
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        val dateString = dateFormat.format(Date(timestamp))
        
        val phoneTag = if (phoneNumber?.isNotEmpty() == true) {
            "_${phoneNumber.replace("+", "").takeLast(4)}"
        } else {
            ""
        }

        val fileName = "call_${dateString}${phoneTag}.m4a"
        return "${recordingsDir.absolutePath}/$fileName"
    }

    /**
     * Create notification channel (Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for active call recording"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create persistent notification for foreground service
     */
    private fun createNotification(): Notification {
        val statusText = if (isRecording) {
            "Recording call..."
        } else {
            "Service ready for call recording"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Recorder")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    companion object {
        private const val TAG = "CallRecordingService"
        private const val CHANNEL_ID = "call_recording_channel"
        private const val NOTIFICATION_ID = 1001

        /**
         * Start the foreground service
         */
        fun start(context: Context) {
            val intent = Intent(context, CallRecordingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the foreground service
         */
        fun stop(context: Context) {
            val intent = Intent(context, CallRecordingService::class.java)
            context.stopService(intent)
        }
    }
}
