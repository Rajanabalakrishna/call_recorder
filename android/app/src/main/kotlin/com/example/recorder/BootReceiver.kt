package com.example.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 📄 BootReceiver - Auto-starts Call Recorder service on device boot
 * 
 * Why this is important:
 * - Device boots up → BootReceiver catches BOOT_COMPLETED event
 * - Starts CallRecorderAccessibilityService automatically
 * - No user interaction needed
 * - Recording continues across device restarts
 * 
 * Like Cube ACR: User sets it up once, it works forever
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.d(TAG, "🖄 Device Boot Detected - Starting Call Recorder Background Service")
            
            try {
                // Start Accessibility Service
                val serviceIntent = Intent(context, CallRecorderAccessibilityService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    @Suppress("DEPRECATION")
                    context.startService(serviceIntent)
                }
                
                Log.d(TAG, "✅ Background Recording Services Started on Boot")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error starting service on boot: ${e.message}", e)
            }
        }
    }
}
