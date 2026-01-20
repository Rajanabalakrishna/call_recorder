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
        private const val CHANNEL_ID = "call_recorder_channel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d("ForegroundService", "Service started with action: ${intent?.action}")

        when (intent?.action) {
            "START_FOREGROUND" -> startForeground()
            "STOP_FOREGROUND" -> stopForeground()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForeground() {
        try {
            createNotificationChannel()
            val notification = buildNotification()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.app.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_RECORDING
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            Log.d("ForegroundService", "Foreground service started with notification")
        } catch (e: Exception) {
            Log.e("ForegroundService", "Error starting foreground service", e)
        }
    }

    private fun stopForeground() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            Log.d("ForegroundService", "Foreground service stopped")
        } catch (e: Exception) {
            Log.e("ForegroundService", "Error stopping foreground service", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Recorder",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Shows notification while recording calls"
            channel.setShowBadge(false)
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
            
            Log.d("ForegroundService", "Notification channel created")
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Recorder")
            .setContentText("Recording call...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ForegroundService", "Service destroyed")
    }
}
