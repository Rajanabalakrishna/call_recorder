package com.example.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class CallRecordingForegroundService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "call_recording_channel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            "START_FOREGROUND" -> startForegroundRecording()
            "STOP_FOREGROUND" -> stopForegroundRecording()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundRecording() {
        try {
            // Create notification channel
            createNotificationChannel()

            // Build notification
            val notification = buildNotification()

            // Start foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                @Suppress("MissingPermission")
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.app.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+
                @Suppress("MissingPermission")
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.app.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                // For older versions
                @Suppress("MissingPermission")
                startForeground(NOTIFICATION_ID, notification)
            }

            // Start actual recording service
            val recordingIntent = Intent(this, CallRecorderService::class.java)
            recordingIntent.action = "START_RECORDING"
            startService(recordingIntent)

            Log.d("CallRecordingForegroundService", "Foreground recording started")

        } catch (e: Exception) {
            Log.e("CallRecordingForegroundService", "Error starting foreground recording", e)
            stopSelf()
        }
    }

    private fun stopForegroundRecording() {
        try {
            // Stop the recording service
            val recordingIntent = Intent(this, CallRecorderService::class.java)
            recordingIntent.action = "STOP_RECORDING"
            startService(recordingIntent)

            // Stop foreground notification
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()

            Log.d("CallRecordingForegroundService", "Foreground recording stopped")

        } catch (e: Exception) {
            Log.e("CallRecordingForegroundService", "Error stopping foreground recording", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Call Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Shows when a call is being recorded"
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Call Recorder")
            .setContentText("Recording call in progress...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("CallRecordingForegroundService", "Service destroyed")
    }
}
