// File: android/app/src/main/kotlin/com/example/recorder/BootReceiver.kt
package com.example.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Broadcast receiver to handle device boot completion
 * Ensures the accessibility service is available after phone restart
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "📱 Device booted - Accessibility Service will auto-start if enabled")

                // Note: The accessibility service will automatically restart
                // if it was enabled before reboot. No manual start needed.

                // Optional: Show a notification that the service is ready
                // You can add notification code here if desired
            }
        }
    }
}