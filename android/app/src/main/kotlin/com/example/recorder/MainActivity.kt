package com.example.recorder

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * MainActivity - Flutter entry point with native method channel bridge
 * 
 * Responsibilities:
 * - Establish MethodChannel communication with Dart
 * - Delegate to native services for recording control
 * - Handle UI interactions (buttons, settings)
 * - Verify permissions and accessibility service
 * 
 * Note: Main recording logic is in CallRecordingService (native layer)
 * MainActivity is only responsible for UI communication
 */
class MainActivity : FlutterActivity() {

    private val CHANNEL = "com.example.recorder/native"
    private var methodChannel: MethodChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Establish method channel for Dart ↔ Kotlin communication
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel?.setMethodCallHandler { call, result ->
            Log.d(TAG, "Method called from Flutter: ${call.method}")
            
            when (call.method) {
                // RECORDING CONTROL
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

                // ACCESSIBILITY SERVICE
                "isAccessibilityServiceEnabled" -> {
                    val enabled = isAccessibilityServiceEnabled()
                    result.success(enabled)
                }

                "openAccessibilitySettings" -> {
                    openAccessibilitySettings()
                    result.success(true)
                }

                // PERMISSIONS
                "requestPermissions" -> {
                    result.success(true) // Handled by permission_handler plugin
                }

                // DEBUGGING
                "getDebugInfo" -> {
                    val debugInfo = getDebugInfo()
                    result.success(debugInfo)
                }

                else -> {
                    result.notImplemented()
                }
            }
        }

        // Initialize recording service on app start
        initializeRecordingService()
    }

    /**
     * Initialize the recording service on app startup
     * This ensures the native layer is ready to receive calls
     */
    private fun initializeRecordingService() {
        try {
            Log.d(TAG, "Initializing recording service")
            CallRecordingService.start(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize recording service: ${e.message}", e)
        }
    }

    /**
     * Start manual recording (user-initiated from UI)
     */
    private fun startRecording(filePath: String): Boolean {
        return try {
            Log.d(TAG, "Starting recording: $filePath")
            
            // Start the recording service
            val intent = Intent(this, CallRecordingService::class.java)
            intent.putExtra("action", "start_recording")
            intent.putExtra("phoneNumber", "manual")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}", e)
            false
        }
    }

    /**
     * Stop manual recording (user-initiated from UI)
     */
    private fun stopRecording(): String? {
        return try {
            Log.d(TAG, "Stopping recording")
            
            val intent = Intent(this, CallRecordingService::class.java)
            intent.putExtra("action", "stop_recording")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            
            // Return path from manager
            RecordingManager.getSavedRecordingState(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}", e)
            null
        }
    }

    /**
     * Check if currently recording
     */
    private fun isRecording(): Boolean {
        return RecordingManager.isRecording()
    }

    /**
     * Check if accessibility service is enabled
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        return CallRecorderAccessibilityService.isServiceEnabled()
    }

    /**
     * Open system accessibility settings
     * User must manually enable the service
     */
    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            Log.d(TAG, "Opened accessibility settings")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening accessibility settings: ${e.message}", e)
        }
    }

    /**
     * Get debug information for troubleshooting
     */
    private fun getDebugInfo(): String {
        val info = StringBuilder()
        info.append("=== Call Recorder Debug Info ===\n")
        info.append("Android Version: ${android.os.Build.VERSION.SDK_INT}\n")
        info.append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
        info.append("App Version: 1.0.0\n")
        info.append("\n${RecordingManager.getDebugInfo(this)}\n")
        info.append("Accessibility Service Enabled: ${isAccessibilityServiceEnabled()}\n")
        info.append("Currently Recording: ${isRecording()}\n")
        
        return info.toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        methodChannel?.setMethodCallHandler(null)
        Log.d(TAG, "MainActivity destroyed")
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
