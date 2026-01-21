package com.example.recorder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootCompleteReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompleteReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed - App ready")
            // Accessibility service will be enabled by user
            // This receiver ensures the app is ready after boot
        }
    }
}
