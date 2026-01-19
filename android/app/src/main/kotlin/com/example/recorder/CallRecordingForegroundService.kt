// File: android/app/src/main/kotlin/com/example/recorder/CallRecordingForegroundService.kt
package com.example.recorder

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import android.os.PowerManager

import java.util.*

/**
 * FIXED ARCHITECTURE: All call recording logic is HERE
 * This service MUST stay alive to record calls
 */
class CallRecordingForegroundService : Service() {

    companion object {
        private const val TAG = "CallRecordingFG"
        private const val CHANNEL_ID = "call_recording_channel"
        private const val NOTIFICATION_ID = 1001

         var isServiceRunning = false

        fun start(context: Context) {
            if (isServiceRunning) {
                Log.d(TAG, "Service already running")
                return
            }

            val intent = Intent(context, CallRecordingForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CallRecordingForegroundService::class.java)
            context.stopService(intent)
            isServiceRunning = false
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentFilePath: String? = null

    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 Service created")
        createNotificationChannel()
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "📱 Service started")

        // Start as foreground with explicit types for Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification("Ready to record calls"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification("Ready to record calls"))
        }

        registerPhoneStateListener()
        return START_STICKY
    }

    private fun registerPhoneStateListener() {
        try {
            telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                registerTelephonyCallback()
            } else {
                registerPhoneStateListenerLegacy()
            }

            Log.d(TAG, "📞 Phone state listener registered in foreground service")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error registering listener", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerTelephonyCallback() {
        telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                handleCallStateChange(state)
            }
        }
        telephonyManager?.registerTelephonyCallback(mainExecutor, telephonyCallback!!)
    }

    @Suppress("DEPRECATION")
    private fun registerPhoneStateListenerLegacy() {
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                handleCallStateChange(state)
            }
        }
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun handleCallStateChange(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (!isRecording) {
                    Log.d(TAG, "📞 CALL STARTED - Starting recording")
                    updateNotification("Recording call...")
                    startRecording()
                }
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                if (isRecording) {
                    Log.d(TAG, "📞 CALL ENDED - Stopping recording")
                    stopRecording()
                    updateNotification("Ready to record calls")
                }
            }

            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d(TAG, "📞 Phone ringing...")
            }
        }
    }

    private fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "⚠️ Already recording")
            return
        }

        try {
            val filePath = generateRecordingFilePath()
            currentFilePath = filePath

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "❌ MediaRecorder error: what=$what, extra=$extra")
                }

                // Try multiple audio sources with fallback
                val audioSource = getOptimalAudioSource()
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(filePath)

                try {
                    prepare()
                    start()
                    isRecording = true
                    Log.d(TAG, "✅ Recording started: $filePath")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to start recording", e)
                    release()
                    tryFallbackAudioSource(filePath)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Recording error", e)
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    /**
     * CRITICAL: Try fallback audio sources (like Cube ACR does)
     */
    private fun tryFallbackAudioSource(filePath: String) {
        val fallbackSources = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )

        for (source in fallbackSources) {
            try {
                Log.d(TAG, "🔄 Trying fallback audio source: $source")

                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(this)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }.apply {
                    setAudioSource(source)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(44100)
                    setAudioEncodingBitRate(128000)
                    setOutputFile(filePath)

                    prepare()
                    start()
                    isRecording = true
                    Log.d(TAG, "✅ Fallback recording started with source: $source")
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Fallback source $source failed", e)
                mediaRecorder?.release()
                mediaRecorder = null
            }
        }

        Log.e(TAG, "❌ All audio sources failed")
    }

    private fun stopRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            val savedPath = currentFilePath
            currentFilePath = null

            if (savedPath != null) {
                Log.d(TAG, "✅ Recording saved: $savedPath")

                val file = File(savedPath)
                if (file.exists()) {
                    val sizeMB = file.length() / (1024.0 * 1024.0)
                    Log.d(TAG, "📁 File size: ${String.format("%.2f", sizeMB)} MB")

                    // Upload to S3
                    uploadToS3(savedPath)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
        }
    }

    private fun uploadToS3(filePath: String) {
        Thread {
            try {
                Log.d(TAG, "📤 Starting S3 upload: $filePath")
                val success = S3UploadManager.uploadFile(this, filePath)
                if (success) {
                    Log.d(TAG, "✅ S3 upload successful")
                } else {
                    Log.e(TAG, "❌ S3 upload failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ S3 upload error", e)
            }
        }.start()
    }

    // Inside CallRecordingForegroundService.kt
    private fun getOptimalAudioSource(): Int {
        // MIC is the most reliable for starting in the background
        return MediaRecorder.AudioSource.MIC
    }

    private fun generateRecordingFilePath(): String {
        // CHANGE: Point to the standard Flutter documents directory
        val recordingsDir = File(applicationContext.dataDir.absolutePath + "/app_flutter/CallRecordings")
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val dateStr = dateFormat.format(Date(timestamp))
        val fileName = "call_${dateStr}_$timestamp.m4a"

        return File(recordingsDir, fileName).absolutePath
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps call recording service active"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Recorder Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()

        // Unregister listeners
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let {
                    telephonyManager?.unregisterTelephonyCallback(it)
                }
            } else {
                @Suppress("DEPRECATION")
                phoneStateListener?.let {
                    telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering listener", e)
        }

        stopRecording()
        isServiceRunning = false
        Log.d(TAG, "❌ Service destroyed")
    }

    /**
     * CRITICAL: Handle task removal (app swipe)
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "⚠️ Task removed - restarting service")

        // Restart service immediately
        val restartIntent = Intent(applicationContext, CallRecordingForegroundService::class.java)
        val pendingIntent = PendingIntent.getService(
            this,
            1,
            restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 1000,
            pendingIntent
        )
    }
}