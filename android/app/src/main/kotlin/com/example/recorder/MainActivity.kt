// File: android/app/src/main/kotlin/com/example/recorder/MainActivity.kt
package com.example.recorder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File

class MainActivity: FlutterActivity() {

    private val CHANNEL = "com.example.recorder/native"
    private var methodChannel: MethodChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "getRecordingsDirectory" -> {
                    // Logic to get the recordings directory
                    val recordingsDir = File(dataDir.absolutePath + "/app_flutter/CallRecordings")
                    if (!recordingsDir.exists()) {
                        recordingsDir.mkdirs()
                    }
                    result.success(recordingsDir.absolutePath)
                }

                "openAccessibilitySettings" -> {
                    openAccessibilitySettings()
                    result.success(true)
                }

                "isAccessibilityServiceEnabled" -> {
                    val enabled = CallRecorderAccessibilityService.isServiceEnabled(this)
                    result.success(enabled)
                }

                "startForegroundService" -> {
                    CallRecordingForegroundService.start(this)
                    result.success(true)
                }

                "stopForegroundService" -> {
                    CallRecordingForegroundService.stop(this)
                    result.success(true)
                }

                "requestBatteryOptimization" -> {
                    requestBatteryOptimizationDisable()
                    result.success(true)
                }

                "isBatteryOptimizationDisabled" -> {
                    val disabled = isBatteryOptimizationDisabled()
                    result.success(disabled)
                }

                "configureS3" -> {
                    val s3Url = call.argument<String>("s3Url")
                    val authToken = call.argument<String>("authToken")

                    if (s3Url != null && authToken != null) {
                        S3ConfigManager.saveConfig(this, s3Url, authToken)
                        result.success(true)
                    } else {
                        result.error("INVALID_ARGUMENT", "S3 URL and token required", null)
                    }
                }

                "getS3Config" -> {
                    val config = mapOf(
                        "s3Url" to S3ConfigManager.getS3Url(this),
                        "authToken" to S3ConfigManager.getAuthToken(this),
                        "isConfigured" to S3ConfigManager.isConfigured(this)
                    )
                    result.success(config)
                }

                "uploadAllToS3" -> {
                    Thread {
                        val count = S3UploadManager.uploadAllRecordings(this)
                        runOnUiThread {
                            result.success(count)
                        }
                    }.start()
                }

                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun requestBatteryOptimizationDisable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun isBatteryOptimizationDisabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(packageName)
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        methodChannel?.setMethodCallHandler(null)
    }
}
