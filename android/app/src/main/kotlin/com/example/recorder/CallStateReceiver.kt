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
            return
        }

        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Unknown"
            
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
            }
        }
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
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "Recording service started")
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
