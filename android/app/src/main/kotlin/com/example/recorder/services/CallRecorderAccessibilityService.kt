package com.example.recorder.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.telecom.TelecomManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

class CallRecorderAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CallRecorderA11yService"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) {
            Log.w(TAG, "Null accessibility event")
            return
        }

        Log.d(TAG, "Event Type: ${event.eventType}")

        // TYPE_CALL_STATE_CHANGED doesn't exist in AccessibilityEvent
        // Instead, handle window state changes for call detection
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleWindowContentChanged(event)
            }
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        Log.d(TAG, "Window changed: $packageName")
        checkForCallAndRecord(packageName)
    }

    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        Log.d(TAG, "Window content changed: $packageName")
        checkForCallAndRecord(packageName)
    }

    private fun checkForCallAndRecord(packageName: String) {
        // Detect phone app
        if (packageName == "com.android.phone" || packageName == "com.android.dialer") {
            Log.d(TAG, "Phone app detected: $packageName")
            startRecording("Phone Call")
            return
        }

        // Detect VoIP apps
        val voipApps = listOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.viber.voip",
            "com.skype.raider",
            "com.google.android.apps.hangouts",
            "com.google.duo",
            "com.facebook.orca"
        )

        if (voipApps.contains(packageName)) {
            Log.d(TAG, "VoIP app detected: $packageName")
            startRecording(packageName)
        }
    }

    private fun startRecording(source: String) {
        try {
            val intent = Intent(this, CallRecordingService::class.java).apply {
                action = CallRecordingService.ACTION_START_RECORDING
                putExtra(CallRecordingService.EXTRA_PHONE_NUMBER, source)
                putExtra(CallRecordingService.EXTRA_CALL_TYPE, "Accessibility")
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }

            Log.d(TAG, "Recording service started from: $source")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Accessibility Service Destroyed")
    }
}
