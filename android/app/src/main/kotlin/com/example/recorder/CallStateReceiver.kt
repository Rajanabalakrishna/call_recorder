package com.example.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log

/**
 * CallStateReceiver - Listens for phone state changes via TelephonyManager
 * 
 * This is the MASTER call detection mechanism:
 * - Receives all phone state events (RINGING, OFFHOOK, IDLE)
 * - Works independently of app lifecycle
 * - Triggers recording service start/stop
 * - Provides redundancy beyond accessibility service
 * 
 * Architecture: Native broadcast receiver (system-level events)
 * Triggered: By Android system when call state changes
 * Runs: Even when app is swiped away
 */
class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, "Null context or intent received")
            return
        }

        try {
            // Check both old and new action names for compatibility
            val action = intent.action
            if (action != TelephonyManager.ACTION_PHONE_STATE_CHANGED && 
                action != "android.intent.action.PHONE_STATE") {
                return
            }

            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = extractPhoneNumber(intent)
            
            Log.d(TAG, "Call State Changed: $state, Number: $incomingNumber")

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    handleCallRinging(context, incomingNumber)
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    handleCallOffhook(context, incomingNumber)
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    handleCallIdle(context)
                }
                else -> {
                    Log.d(TAG, "Unknown call state: $state")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing broadcast: ${e.message}", e)
        }
    }

    /**
     * Extract phone number from intent with multiple fallbacks
     */
    private fun extractPhoneNumber(intent: Intent): String {
        return try {
            // Primary: EXTRA_INCOMING_NUMBER (most common)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            if (!incomingNumber.isNullOrEmpty() && incomingNumber != "Unknown") {
                Log.d(TAG, "Phone number extracted: $incomingNumber")
                return incomingNumber
            }

            // Secondary: Try alternative extra key (some devices use different keys)
            val alternateNumber = intent.getStringExtra("incoming_number")
            if (!alternateNumber.isNullOrEmpty()) {
                Log.d(TAG, "Phone number extracted (alternate): $alternateNumber")
                return alternateNumber
            }

            // Tertiary: Check all extras for phone-like values
            val extras = intent.extras
            if (extras != null) {
                for (key in extras.keySet()) {
                    val value = extras.get(key)
                    if (value is String && isPhoneNumber(value)) {
                        Log.d(TAG, "Phone number found in extra '$key': $value")
                        return value
                    }
                }
            }

            Log.d(TAG, "No phone number found in intent extras")
            "Unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting phone number: ${e.message}")
            "Unknown"
        }
    }

    /**
     * Validate if string looks like a phone number
     */
    private fun isPhoneNumber(value: String): Boolean {
        if (value.isEmpty() || value.length < 7) return false
        // Check if string contains mostly digits
        val digitCount = value.count { it.isDigit() }
        return digitCount >= 7
    }

    /**
     * Call is ringing (incoming)
     */
    private fun handleCallRinging(context: Context, number: String) {
        Log.d(TAG, "Incoming call detected from: $number")
        // Optional: Can use this for notification or state update
        // Recording starts on OFFHOOK, not RINGING
    }

    /**
     * Call is active (off hook)
     * This is when we START recording
     */
    private fun handleCallOffhook(context: Context, number: String) {
        Log.d(TAG, "Call active (OFFHOOK): $number - Starting recording")
        
        if (!RecordingManager.isRecording()) {
            // Start the recording service
            val serviceIntent = Intent(context, CallRecordingService::class.java)
            serviceIntent.putExtra("action", "start_recording")
            serviceIntent.putExtra("phoneNumber", number)
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    @Suppress("DEPRECATION")
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "Recording service started for: $number")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording: ${e.message}", e)
            }
        }
    }

    /**
     * Call ended
     * This is when we STOP recording
     */
    private fun handleCallIdle(context: Context) {
        Log.d(TAG, "Call ended (IDLE) - Stopping recording")
        
        if (RecordingManager.isRecording()) {
            // Stop the recording service
            val serviceIntent = Intent(context, CallRecordingService::class.java)
            serviceIntent.putExtra("action", "stop_recording")
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    @Suppress("DEPRECATION")
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "Recording stop service started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop recording: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val TAG = "CallStateReceiver"
    }
}
