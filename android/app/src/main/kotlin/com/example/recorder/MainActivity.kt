package com.example.recorder

import android.content.Intent
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {

    private val CHANNEL = "com.example.recorder/native"
    private var methodChannel: MethodChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "startRecording" -> {
                    val filePath = call.argument<String>("filePath")
                    if (filePath != null) {
                        val success = startRecording(filePath)
                        result.success(success)
                    } else {
                        result.error("INVALID_ARGUMENT", "File path is required", null)
                    }
                }

                "stopRecording" -> {
                    val savedPath = stopRecording()
                    result.success(savedPath)
                }

                "isRecording" -> {
                    val recording = isRecording()
                    result.success(recording)
                }

                "openAccessibilitySettings" -> {
                    openAccessibilitySettings()
                    result.success(true)
                }

                "isAccessibilityServiceEnabled" -> {
                    val enabled = CallRecorderAccessibilityService.isServiceEnabled()
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

                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun startRecording(filePath: String): Boolean {
        val service = CallRecorderAccessibilityService.getInstance()
        return if (service != null) {
            // Start foreground service to keep app alive
            CallRecordingForegroundService.start(this)
            service.startRecording(filePath)
        } else {
            false
        }
    }

    private fun stopRecording(): String? {
        val service = CallRecorderAccessibilityService.getInstance()
        val result = service?.stopRecording()
        // Stop foreground service after recording
        CallRecordingForegroundService.stop(this)
        return result
    }

    private fun isRecording(): Boolean {
        val service = CallRecorderAccessibilityService.getInstance()
        return service?.isCurrentlyRecording() ?: false
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        methodChannel?.setMethodCallHandler(null)
    }
}
