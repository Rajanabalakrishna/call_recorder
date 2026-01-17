// File: android/app/src/main/kotlin/com/example/recorder/CallRecorderAccessibilityService.kt
package com.example.recorder

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class CallRecorderAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CallRecorderService"
        private var instance: CallRecorderAccessibilityService? = null

        fun getInstance(): CallRecorderAccessibilityService? = instance

        fun isServiceEnabled(): Boolean = instance != null
    }

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentFilePath: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service Connected - Ready for Call Recording")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Monitor window state changes for call detection
        event?.let {
            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    Log.d(TAG, "Window State Changed: ${it.packageName}")
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopRecording()
    }

    // CRITICAL FUNCTION: Start Recording with Android 15/16 Compatible AudioSource
    fun startRecording(filePath: String): Boolean {
        if (isRecording) {
            Log.w(TAG, "Recording already in progress")
            return false
        }

        return try {
            currentFilePath = filePath

            // Initialize MediaRecorder based on Android version
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                // AUDIO SOURCE SELECTION (Critical for 2026)
                // Based on Android version compatibility
                setAudioSource(getOptimalAudioSource())

                // Output Format: MPEG4 provides best compatibility
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

                // Audio Encoder: AAC provides good quality and compression
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

                // Optional: Set Audio Sampling Rate for better quality
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)

                // Set Output File
                setOutputFile(filePath)

                // Prepare and Start
                try {
                    prepare()
                    start()
                    isRecording = true
                    Log.d(TAG, "Recording Started: $filePath")
                    true
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to prepare MediaRecorder", e)
                    release()
                    false
                }
            }

            isRecording
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            false
        }
    }

    // STOP RECORDING
    fun stopRecording(): String? {
        if (!isRecording) {
            return null
        }

        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            val savedPath = currentFilePath
            currentFilePath = null
            Log.d(TAG, "Recording Stopped: $savedPath")
            savedPath
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            currentFilePath = null
            null
        }
    }

    // OPTIMAL AUDIO SOURCE SELECTION FOR 2026
    // Based on Android version and device capabilities
    private fun getOptimalAudioSource(): Int {
        return when {
            // Android 14+ (API 34+): Use VOICE_RECOGNITION
            // It bypasses most Echo Cancellation but still works reliably
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                Log.d(TAG, "Using VOICE_RECOGNITION for Android 14+")
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            }

            // Android 12-13 (API 31-33): Use VOICE_COMMUNICATION
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                Log.d(TAG, "Using VOICE_COMMUNICATION for Android 12-13")
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            }

            // Android 10-11 (API 29-30): Use MIC
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                Log.d(TAG, "Using MIC for Android 10-11")
                MediaRecorder.AudioSource.MIC
            }

            // Fallback for older versions
            else -> {
                Log.d(TAG, "Using DEFAULT for older Android")
                MediaRecorder.AudioSource.DEFAULT
            }
        }
    }

    fun isCurrentlyRecording(): Boolean = isRecording

    fun getCurrentFilePath(): String? = currentFilePath
}