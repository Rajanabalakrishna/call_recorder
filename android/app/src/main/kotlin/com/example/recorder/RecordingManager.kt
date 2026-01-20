package com.example.recorder

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * RecordingManager - Centralized state management for recording
 * 
 * Manages:
 * - Recording state persistence (survives process death)
 * - Permission status tracking
 * - Accessibility service state
 * - Previous recording metadata
 * - Configuration settings
 * 
 * Architecture: Singleton pattern with SharedPreferences backing
 * Scope: Application-wide state that survives app lifecycle
 */
object RecordingManager {

    private const val TAG = "RecordingManager"
    private const val PREF_NAME = "call_recorder_state"
    private const val KEY_IS_RECORDING = "is_recording"
    private const val KEY_CURRENT_FILE = "current_file"
    private const val KEY_LAST_CALL_NUMBER = "last_call_number"
    private const val KEY_LAST_CALL_TIME = "last_call_time"
    private const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"
    private const val KEY_AUTO_START = "auto_start"
    private const val KEY_PERMISSIONS_GRANTED = "permissions_granted"

    private var isRecordingState = false
    private var currentRecordingFile: String? = null

    // Recording state flags
    private val recordingLock = Any()

    /**
     * Mark recording as started
     */
    fun markRecordingStarted(
        context: Context,
        filePath: String,
        phoneNumber: String? = null
    ) {
        synchronized(recordingLock) {
            isRecordingState = true
            currentRecordingFile = filePath

            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            with(prefs.edit()) {
                putBoolean(KEY_IS_RECORDING, true)
                putString(KEY_CURRENT_FILE, filePath)
                putString(KEY_LAST_CALL_NUMBER, phoneNumber ?: "")
                putLong(KEY_LAST_CALL_TIME, System.currentTimeMillis())
                apply()
            }

            Log.d(TAG, "Recording marked as started: $filePath")
        }
    }

    /**
     * Mark recording as stopped
     */
    fun markRecordingStopped(context: Context) {
        synchronized(recordingLock) {
            isRecordingState = false
            currentRecordingFile = null

            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            with(prefs.edit()) {
                putBoolean(KEY_IS_RECORDING, false)
                putString(KEY_CURRENT_FILE, "")
                apply()
            }

            Log.d(TAG, "Recording marked as stopped")
        }
    }

    /**
     * Check if currently recording (in-memory state)
     */
    fun isRecording(): Boolean {
        synchronized(recordingLock) {
            return isRecordingState
        }
    }

    /**
     * Get recording state from persistent storage
     * Used on app startup to restore state
     */
    fun getSavedRecordingState(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val wasRecording = prefs.getBoolean(KEY_IS_RECORDING, false)
        val filePath = prefs.getString(KEY_CURRENT_FILE, null)

        return if (wasRecording && filePath != null) {
            Log.d(TAG, "Found saved recording state: $filePath")
            filePath
        } else {
            null
        }
    }

    /**
     * Set accessibility service enabled flag
     */
    fun setAccessibilityServiceEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ACCESSIBILITY_ENABLED, enabled).apply()
        Log.d(TAG, "Accessibility service flag set: $enabled")
    }

    /**
     * Check if accessibility service is enabled (from preferences)
     * Note: This reflects our tracking, verify with actual service
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ACCESSIBILITY_ENABLED, false)
    }

    /**
     * Set auto-start preference
     */
    fun setAutoStart(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply()
        Log.d(TAG, "Auto-start preference set: $enabled")
    }

    /**
     * Get auto-start preference
     */
    fun isAutoStartEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_START, true)
    }

    /**
     * Set permissions granted flag
     */
    fun setPermissionsGranted(context: Context, granted: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PERMISSIONS_GRANTED, granted).apply()
        Log.d(TAG, "Permissions granted flag set: $granted")
    }

    /**
     * Check if all permissions were previously granted
     */
    fun arePermissionsGranted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PERMISSIONS_GRANTED, false)
    }

    /**
     * Get last call information for debugging
     */
    fun getLastCallInfo(context: Context): Pair<String?, Long>? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val number = prefs.getString(KEY_LAST_CALL_NUMBER, null)
        val time = prefs.getLong(KEY_LAST_CALL_TIME, 0)

        return if (number != null && time > 0) {
            Pair(number, time)
        } else {
            null
        }
    }

    /**
     * Clear all recorded state
     */
    fun clearAll(context: Context) {
        synchronized(recordingLock) {
            isRecordingState = false
            currentRecordingFile = null
        }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d(TAG, "All state cleared")
    }

    /**
     * Get detailed state for debugging
     */
    fun getDebugInfo(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return """|
            |RecordingManager Debug Info:
            |  In-Memory Recording: $isRecordingState
            |  Current File: $currentRecordingFile
            |  Saved Recording State: ${getSavedRecordingState(context)}
            |  Accessibility Service: ${isAccessibilityServiceEnabled(context)}
            |  Auto-Start Enabled: ${isAutoStartEnabled(context)}
            |  Permissions Granted: ${arePermissionsGranted(context)}
            |  Last Call Info: ${getLastCallInfo(context)}
        """.trimMargin()
    }
}
