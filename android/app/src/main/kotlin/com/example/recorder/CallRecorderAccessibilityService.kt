// File: android/app/src/main/kotlin/com/example/recorder/CallRecorderAccessibilityService.kt
package com.example.recorder

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * CRITICAL FIX: AccessibilityService is now ONLY used to:
 * 1. Keep system priority high
 * 2. Auto-restart foreground service
 * 3. Prevent aggressive killing
 *
 * NO CALL RECORDING LOGIC HERE - All moved to ForegroundService
 */
class CallRecorderAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CallRecorderA11y"

        /**
         * FIXED: Check if service is enabled using Settings, NOT instance
         */
        fun isServiceEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = enabledServices.split(":")
            val componentName = "${context.packageName}/${CallRecorderAccessibilityService::class.java.name}"

            return colonSplitter.any { it.equals(componentName, ignoreCase = true) }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "✅ Accessibility Service Connected")

        // ONLY job: Start and maintain foreground service
        startAndMaintainForegroundService()
    }

    /**
     * Start foreground service and ensure it stays alive
     */
    private fun startAndMaintainForegroundService() {
        try {
            CallRecordingForegroundService.start(this)
            Log.d(TAG, "🛡️ Foreground service started from accessibility")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start foreground service", e)
        }
    }

    // android/app/src/main/kotlin/com/example/recorder/CallRecorderAccessibilityService.kt
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This is the "Heartbeat". Every time the user interacts with the phone,
        // we ensure the recording service is still alive.
        if (!CallRecordingForegroundService.isServiceRunning) {
            startAndMaintainForegroundService()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "⚠️ Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "❌ Accessibility Service destroyed")

        // Don't stop foreground service here - let it run independently
    }

    /**
     * CRITICAL: Override to restart service automatically
     */
    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "🔄 Service unbound - requesting rebind")
        return true // Request rebind
    }
}