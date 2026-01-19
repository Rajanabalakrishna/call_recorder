// File: android/app/src/main/kotlin/com/example/recorder/BootReceiver.kt
package com.example.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * FIXED: Actually start foreground service at boot
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "📱 Device booted - Starting call recording service")

                try {
                    // CRITICAL FIX: Actually start the foreground service
                    CallRecordingForegroundService.start(context)
                    Log.d(TAG, "✅ Foreground service started at boot")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to start service at boot", e)
                }
            }
        }
    }
}