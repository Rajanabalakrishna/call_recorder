package com.example.recorder.services

import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CallRecordingService : Service() {

    companion object {
        private const val TAG = "CallRecordingService"
        private const val CHANNEL_ID = "call_recorder_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START_RECORDING = "com.example.recorder.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.recorder.STOP_RECORDING"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_CALL_TYPE = "call_type"
    }

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentRecordingFile: File? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: "Unknown"
                val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "Call"
                startRecording(phoneNumber, callType)
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startRecording(phoneNumber: String, callType: String) {
        try {
            if (isRecording) {
                Log.w(TAG, "Already recording")
                return
            }

            // Create notification BEFORE starting foreground service
            val notification = createNotification(phoneNumber, "Recording...")
            startForeground(NOTIFICATION_ID, notification)

            // Create recording directory
            val recordingDir = File(getExternalFilesDir(null), "recordings")
            if (!recordingDir.exists()) {
                recordingDir.mkdirs()
            }

            // Create recording file
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            currentRecordingFile = File(recordingDir, "${phoneNumber}_${timestamp}.3gp")

            // Initialize MediaRecorder
            mediaRecorder = MediaRecorder().apply {
                // Set optimal audio source
                setAudioSource(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaRecorder.AudioSource.VOICE_RECOGNITION
                    } else {
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION
                    }
                )

                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(currentRecordingFile?.absolutePath)
                setAudioSamplingRate(8000)
                setAudioEncodingBitRate(12800)

                try {
                    prepare()
                    start()
                    isRecording = true
                    Log.d(TAG, "Recording started: ${currentRecordingFile?.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start recording: ${e.message}")
                    isRecording = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in startRecording: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        try {
            if (!isRecording) {
                Log.w(TAG, "Not recording")
                return
            }

            mediaRecorder?.apply {
                try {
                    stop()
                    release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping recorder: ${e.message}")
                }
            }
            mediaRecorder = null
            isRecording = false
            Log.d(TAG, "Recording stopped: ${currentRecordingFile?.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error in stopRecording: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Call Recorder",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Recording phone calls"
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(phoneNumber: String, status: String): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording Call")
            .setContentText("$phoneNumber - $status")
            .setSmallIcon(android.R.drawable.ic_dialog_info)  // Valid system drawable
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            stopRecording()
        }
        Log.d(TAG, "Service Destroyed")
    }
}
