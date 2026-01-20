package com.example.recorder

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * 🔔 CallRecordingForegroundService - Keeps recording alive in background
 * 
 * Why Foreground Service?
 * - Android kills background processes to save RAM
 * - Foreground service has HIGH priority (protected from killing)
 * - Displays persistent notification to user
 * - Allows MediaRecorder to continue working
 * - Like Cube ACR: Recording continues even with low memory
 */
class CallRecordingForegroundService : Service() {
    companion object {
        private const val TAG = "ForegroundService"
        private const val NOTIFICATION_CHANNEL_ID = "call_recorder_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, CallRecordingForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                @Suppress("DEPRECATION")
                context.startService(intent)
            }
            Log.d(TAG, "🎙️ Foreground Service Started")
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallRecordingForegroundService::class.java))
            Log.d(TAG, "🛑 Foreground Service Stopped")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        
        // Create persistent notification
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("🎙️ Recording Call")
            .setContentText("Call recording in progress...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)  // Ongoing notification - can't be swiped away
            .setAutoCancel(false)
            .build()

        // Start foreground with notification
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+: Use ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10-13: Use legacy constant
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    0x00000040 // FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION constant value
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8-9
                startForeground(NOTIFICATION_ID, notification)
            } else {
                // Android 7 and below
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, notification)
            }
            
            Log.d(TAG, "💯 Notification Posted - Recording Protected from Killing")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting foreground: ${e.message}", e)
        }

        // Service will be restarted if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            // Remove foreground notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing foreground", e)
        }
        Log.d(TAG, "✅ Service Destroyed")
    }
}
