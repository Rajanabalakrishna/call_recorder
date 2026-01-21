package com.example.recorder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.recorder.services.CallRecordingService

class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallStateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Broadcast received: ${intent.action}")

        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Unknown"

            Log.d(TAG, "Phone state: $state, Number: $phoneNumber")

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    Log.d(TAG, "Call incoming: $phoneNumber")
                    startRecording(context, phoneNumber, "Incoming")
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    Log.d(TAG, "Call connected: $phoneNumber")
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    Log.d(TAG, "Call ended")
                    stopRecording(context)
                }
            }
        }
    }

    private fun startRecording(context: Context, phoneNumber: String, callType: String) {
        try {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = CallRecordingService.ACTION_START_RECORDING
                putExtra(CallRecordingService.EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(CallRecordingService.EXTRA_CALL_TYPE, callType)
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }

            Log.d(TAG, "Recording service started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}")
        }
    }

    private fun stopRecording(context: Context) {
        try {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = CallRecordingService.ACTION_STOP_RECORDING
            }
            context.startService(intent)
            Log.d(TAG, "Stop recording requested")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}")
        }
    }
}
