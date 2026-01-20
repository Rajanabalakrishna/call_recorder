package com.example.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BootReceiver - Triggered on device startup
 * 
 * Critical for persistent call recording functionality:
 * - Restarts the recording service after device reboot
 * - Re-enables accessibility service monitoring
 * - Restores previous recording state
 * - Ensures background monitoring even after cold boot
 * 
 * Architecture: Native layer (survives app lifecycle)
 * Triggered: After device boot completion
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device boot completed - Initializing call recorder")
            
            if (context != null) {
                // Start the persistent recording service
                startRecordingService(context)
                
                // Verify accessibility service is still enabled
                verifyAccessibilityService(context)
                
                // Restore previous recording state if needed
                restoreRecordingState(context)
            }
        }
    }

    private fun startRecordingService(context: Context) {
        val serviceIntent = Intent(context, CallRecordingService::class.java)
        serviceIntent.putExtra("action", "initialize_on_boot")
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "Recording service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording service: ${e.message}", e)
        }
    }

    private fun verifyAccessibilityService(context: Context) {
        val isEnabled = RecordingManager.isAccessibilityServiceEnabled(context)
        Log.d(TAG, "Accessibility Service status: $isEnabled")
        
        if (!isEnabled) {
            Log.w(TAG, "Accessibility service not enabled - user must enable in settings")
            // Note: Cannot enable programmatically, user must do manually
        }
    }

    private fun restoreRecordingState(context: Context) {
        val savedState = RecordingManager.getSavedRecordingState(context)
        if (savedState != null) {
            Log.d(TAG, "Restoring previous recording state: $savedState")
            // Restore state will be handled by the recording service
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
