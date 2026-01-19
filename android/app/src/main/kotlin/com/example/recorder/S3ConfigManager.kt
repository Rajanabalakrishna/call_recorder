// File: android/app/src/main/kotlin/com/example/recorder/S3ConfigManager.kt
package com.example.recorder

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages S3 configuration using SharedPreferences
 * Persists even when app is killed
 */
object S3ConfigManager {
    private const val PREFS_NAME = "s3_config"
    private const val KEY_S3_URL = "s3_url"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_IS_CONFIGURED = "is_configured"

    // Default values
    private const val DEFAULT_S3_URL = "https://demand.bharatintelligence.ai/chat/api/upload_image_to_s3/"
    private const val DEFAULT_AUTH_TOKEN = "e8fa8310c9af344ca22ec6bd23960d609b09c704"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Save S3 configuration
     */
    fun saveConfig(context: Context, s3Url: String, authToken: String) {
        getPrefs(context).edit()
            .putString(KEY_S3_URL, s3Url)
            .putString(KEY_AUTH_TOKEN, authToken)
            .putBoolean(KEY_IS_CONFIGURED, true)
            .apply()
    }

    /**
     * Get S3 URL
     */
    fun getS3Url(context: Context): String {
        return getPrefs(context).getString(KEY_S3_URL, DEFAULT_S3_URL) ?: DEFAULT_S3_URL
    }

    /**
     * Get Auth Token
     */
    fun getAuthToken(context: Context): String {
        return getPrefs(context).getString(KEY_AUTH_TOKEN, DEFAULT_AUTH_TOKEN) ?: DEFAULT_AUTH_TOKEN
    }

    /**
     * Check if S3 is configured
     */
    fun isConfigured(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_CONFIGURED, false)
    }

    /**
     * Set default configuration on first run
     */
    fun setDefaultConfig(context: Context) {
        if (!isConfigured(context)) {
            saveConfig(context, DEFAULT_S3_URL, DEFAULT_AUTH_TOKEN)
        }
    }

    /**
     * Clear configuration
     */
    fun clearConfig(context: Context) {
        getPrefs(context).edit()
            .clear()
            .apply()
    }
}