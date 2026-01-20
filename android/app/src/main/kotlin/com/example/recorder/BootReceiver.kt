package com.example.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("BootReceiver", "Boot completed, received action: ${intent?.action}")

        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            try {
                // Re-enable accessibility service after boot
                // Note: Accessibility service needs to be manually enabled by user
                // This just logs that boot was completed
                Log.d("BootReceiver", "Device boot completed - Accessibility service will resume")
                
                // Optional: You can send a broadcast or notification here
                // to wake up the accessibility service
                val accessibilityIntent = Intent(context, CallRecorderAccessibilityService::class.java)
                context?.startService(accessibilityIntent)
                
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error in BootReceiver", e)
            }
        }
    }
}
