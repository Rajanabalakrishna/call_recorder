package com.example.recorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class CallRecordingForegroundService : Service() {
    companion object {
        private const val TAG = "ForegroundService"
        private const val CHANNEL_ID = "call_recorder_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            try {
                val intent = Intent(context, CallRecordingForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    @Suppress("DEPRECATION")
                    context.startService(intent)
                }
                Log.d(TAG, "✅ Foreground Service Started")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting foreground service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, CallRecordingForegroundService::class.java))
                Log.d(TAG, "✅ Foreground Service Stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping service: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🎙️ Call Recording")
                .setContentText("Recording call in progress...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setAutoCancel(false)
                .build()

            // Android 11+ supports foreground service types
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    // Use reflection to access ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    val serviceInfoClass = Class.forName("android.app.ServiceInfo")
                    val foregroundServiceType = serviceInfoClass.getField("FOREGROUND_SERVICE_TYPE_MICROPHONE").getInt(null)
                    startForeground(NOTIFICATION_ID, notification, foregroundServiceType)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not use ServiceInfo type, falling back: ${e.message}")
                    @Suppress("DEPRECATION")
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, notification)
            }

            Log.d(TAG, "📢 Notification Posted")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand: ${e.message}")
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Recording Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notification for active call recording"
                setShowBadge(true)
                enableLights(true)
                enableVibration(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "✅ Notification Channel Created")
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            // Android 13+ requires specific stop flags
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    // Use reflection to access STOP_FOREGROUND_REMOVE_NOTIFICATION
                    val serviceCompatClass = ServiceCompat::class.java
                    val stopForegroundFlagField = serviceCompatClass.getField("STOP_FOREGROUND_REMOVE_NOTIFICATION")
                    val flag = stopForegroundFlagField.getInt(null)
                    ServiceCompat.stopForeground(this, flag)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not use STOP_FOREGROUND_REMOVE_NOTIFICATION, falling back: ${e.message}")
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground: ${e.message}")
        }
        Log.d(TAG, "Service Destroyed")
    }
}
