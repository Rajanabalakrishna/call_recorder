package com.example.recorder

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class CallRecorderAccessibilityService : AccessibilityService() {
    private var isRecording = false
    private var currentCallState = TelephonyManager.CALL_STATE_IDLE

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("CallRecorder", "Accessibility Service Connected")
        configureAccessibilityService()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handlePhoneStateChange(event)
            }
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                handleNotificationChange(event)
            }
        }
    }

    override fun onInterrupt() {
        Log.d("CallRecorder", "Accessibility Service Interrupted")
    }

    private fun handlePhoneStateChange(event: AccessibilityEvent) {
        val eventText = event.source?.contentDescription?.toString() ?: ""
        Log.d("CallRecorder", "Window changed: $eventText")

        // Detect call state from accessibility event
        val newCallState = detectCallState(eventText)

        if (newCallState != currentCallState) {
            currentCallState = newCallState
            handleCallStateChange(newCallState)
        }
    }

    private fun handleNotificationChange(event: AccessibilityEvent) {
        val eventText = event.text.joinToString(" ")
        Log.d("CallRecorder", "Notification: $eventText")

        // Handle call notifications
        if (eventText.contains("calling", ignoreCase = true) ||
            eventText.contains("ringing", ignoreCase = true) ||
            eventText.contains("incoming", ignoreCase = true)) {
            handleCallStateChange(TelephonyManager.CALL_STATE_RINGING)
        }
    }

    private fun detectCallState(eventText: String): Int {
        return when {
            eventText.contains("ringing", ignoreCase = true) ||
            eventText.contains("incoming", ignoreCase = true) ->
                TelephonyManager.CALL_STATE_RINGING

            eventText.contains("onCall", ignoreCase = true) ||
            eventText.contains("call in progress", ignoreCase = true) ->
                TelephonyManager.CALL_STATE_OFFHOOK

            else -> TelephonyManager.CALL_STATE_IDLE
        }
    }

    private fun handleCallStateChange(callState: Int) {
        Log.d("CallRecorder", "Call state changed to: $callState")

        when (callState) {
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d("CallRecorder", "Incoming call detected")
                startRecording()
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (!isRecording) {
                    Log.d("CallRecorder", "Call started (offhook)")
                    startRecording()
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (isRecording) {
                    Log.d("CallRecorder", "Call ended")
                    stopRecording()
                }
            }
        }
    }

    private fun startRecording() {
        if (isRecording) return
        isRecording = true

        try {
            // Start recording service
            val recordingIntent = Intent(this, CallRecorderService::class.java)
            recordingIntent.action = "START_RECORDING"
            startService(recordingIntent)

            // Start foreground service to keep process alive
            val foregroundIntent = Intent(this, CallRecordingForegroundService::class.java)
            foregroundIntent.action = "START_FOREGROUND"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(foregroundIntent)
            } else {
                startService(foregroundIntent)
            }

            Log.d("CallRecorder", "Recording started")
        } catch (e: Exception) {
            Log.e("CallRecorder", "Error starting recording", e)
            isRecording = false
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false

        try {
            // Stop recording service
            val recordingIntent = Intent(this, CallRecorderService::class.java)
            recordingIntent.action = "STOP_RECORDING"
            startService(recordingIntent)

            // Stop foreground service
            val foregroundIntent = Intent(this, CallRecordingForegroundService::class.java)
            foregroundIntent.action = "STOP_FOREGROUND"
            stopService(foregroundIntent)

            Log.d("CallRecorder", "Recording stopped")
        } catch (e: Exception) {
            Log.e("CallRecorder", "Error stopping recording", e)
        }
    }

    private fun configureAccessibilityService() {
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_NOTIFICATION_STATE or
                AccessibilityServiceInfo.DEFAULT
        serviceInfo = info

        Log.d("CallRecorder", "Accessibility Service configured")
    }
}
