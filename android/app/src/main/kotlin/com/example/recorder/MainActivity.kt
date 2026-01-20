package com.example.recorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    companion object {
        private const val CHANNEL = "com.example.recorder/background"
        private const val TAG = "MainActivity"
        private const val NOTIFICATION_CHANNEL_ID = "call_recorder_channel"
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Create notification channel (required for Android 8+)
        createNotificationChannel()

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startBackgroundRecording" -> {
                        startBackgroundRecording()
                        result.success(true)
                    }
                    "stopBackgroundRecording" -> {
                        stopBackgroundRecording()
                        result.success(true)
                    }
                    "isAccessibilityEnabled" -> {
                        result.success(isAccessibilityServiceEnabled())
                    }
                    "openAccessibilitySettings" -> {
                        openAccessibilitySettings()
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }

        // Auto-start background recording on app launch
        startBackgroundRecording()
    }

    /// Create notification channel for Android 8.0+
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
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
            
            Log.d(TAG, "✅ Notification channel created: $NOTIFICATION_CHANNEL_ID")
        }
    }

    /// Start background recording via Accessibility Service
    private fun startBackgroundRecording() {
        Log.d(TAG, "▶️ Starting Background Recording")

        // Check if accessibility service is enabled
        if (!isAccessibilityServiceEnabled()) {
            Log.w(TAG, "⚠️ Accessibility Service not enabled - Opening Settings")
            openAccessibilitySettings()
            return
        }

        // Start the accessibility service
        val intent = Intent(this, CallRecorderAccessibilityService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            @Suppress("DEPRECATION")
            startService(intent)
        }

        Log.d(TAG, "✅ Background Recording Enabled")
    }

    /// Stop background recording
    private fun stopBackgroundRecording() {
        Log.d(TAG, "⏹️ Stopping Background Recording")
        val intent = Intent(this, CallRecorderAccessibilityService::class.java)
        stopService(intent)
        Log.d(TAG, "✅ Service Stopped")
    }

    /// Check if accessibility service is enabled
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (accessibilityManager == null) {
            Log.w(TAG, "⚠️ AccessibilityManager is null")
            return false
        }

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        val serviceName = "${packageName}/${CallRecorderAccessibilityService::class.java.name}"
        val isEnabled = enabledServices.contains(serviceName)
        
        Log.d(TAG, "Accessibility Service Enabled: $isEnabled")
        return isEnabled
    }

    /// Open accessibility settings for user to enable service
    private fun openAccessibilitySettings() {
        Log.d(TAG, "🔓 Opening Accessibility Settings")
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Log.e(TAG, "Error opening accessibility settings", e)
        }
    }
}
