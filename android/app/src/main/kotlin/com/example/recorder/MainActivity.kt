package com.example.recorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.Locale

class MainActivity : FlutterActivity() {
    companion object {
        private const val CHANNEL = "com.example.recorder/native"
        private const val TAG = "MainActivity"
        private const val NOTIFICATION_CHANNEL_ID = "call_recorder_channel"
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        createNotificationChannel()
        requestBatteryOptimizationExclusion()

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
                    "isAccessibilityServiceEnabled" -> {
                        result.success(isAccessibilityServiceEnabled())
                    }
                    "openAccessibilitySettings" -> {
                        openAccessibilitySettings()
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }

        startBackgroundRecording()
    }

    /**
     * 🔥 CRITICAL: Request battery optimization exclusion
     * Xiaomi/MIUI devices kill accessibility services when app removed from recents
     * This prevents that behavior
     */
    private fun requestBatteryOptimizationExclusion() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                val packageName = packageName

                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    Log.w(TAG, "⚠️ Battery optimization enabled - Requesting exclusion")
                    
                    val intent = Intent().apply {
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                    
                    Log.d(TAG, "✅ Battery optimization exclusion requested")
                } else {
                    Log.d(TAG, "✅ Battery optimization already excluded")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting battery optimization exclusion", e)
        }

        // 🔥 XIAOMI/MIUI SPECIFIC: Request autostart permission
        requestXiaomiAutostart()
    }

    /**
     * 🔥 XIAOMI SPECIFIC: Request autostart permission
     * MIUI kills background services aggressively
     * This opens the autostart permission screen
     */
    private fun requestXiaomiAutostart() {
        try {
            val manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault())
            if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")) {
                Log.d(TAG, "📱 Xiaomi device detected - Opening autostart settings")
                
                val intent = Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
                startActivity(intent)
                
                Log.d(TAG, "✅ Autostart permission screen opened")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not open Xiaomi autostart settings (device might not be Xiaomi): ${e.message}")
        }
    }

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

    private fun startBackgroundRecording() {
        Log.d(TAG, "▶️ Starting Background Recording")

        if (!isAccessibilityServiceEnabled()) {
            Log.w(TAG, "⚠️ Accessibility Service not enabled - Opening Settings")
            openAccessibilitySettings()
            return
        }

        val intent = Intent(this, CallRecorderAccessibilityService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            @Suppress("DEPRECATION")
            startService(intent)
        }

        Log.d(TAG, "✅ Background Recording Enabled")
    }

    private fun stopBackgroundRecording() {
        Log.d(TAG, "⏹️ Stopping Background Recording")
        val intent = Intent(this, CallRecorderAccessibilityService::class.java)
        stopService(intent)
        Log.d(TAG, "✅ Service Stopped")
    }

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

    private fun openAccessibilitySettings() {
        Log.d(TAG, "🔓 Opening Accessibility Settings")
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Log.e(TAG, "Error opening accessibility settings", e)
        }
    }
}
