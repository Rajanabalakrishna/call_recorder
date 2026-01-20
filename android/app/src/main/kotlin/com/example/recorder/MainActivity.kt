package com.example.recorder

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.example.recorder/call_recorder"

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        setupMethodChannel(flutterEngine)
    }

    private fun setupMethodChannel(flutterEngine: FlutterEngine) {
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "getRecordings" -> {
                    try {
                        val recordings = getRecordingsList()
                        result.success(recordings)
                    } catch (e: Exception) {
                        result.error("ERROR", e.message, null)
                    }
                }
                "deleteRecording" -> {
                    try {
                        val path = call.argument<String>("path") ?: ""
                        val success = deleteRecordingFile(path)
                        result.success(success)
                    } catch (e: Exception) {
                        result.error("ERROR", e.message, null)
                    }
                }
                "getRecordingPath" -> {
                    try {
                        val path = getRecordingDirectory()
                        result.success(path)
                    } catch (e: Exception) {
                        result.error("ERROR", e.message, null)
                    }
                }
                "startAccessibilityService" -> {
                    try {
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("ERROR", e.message, null)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun getRecordingsList(): List<Map<String, Any>> {
        val recordingDir = getRecordingDirectory()
        val dir = File(recordingDir)
        val recordings = mutableListOf<Map<String, Any>>()

        if (dir.exists() && dir.isDirectory) {
            val files = dir.listFiles { file ->
                file.isFile && file.name.endsWith(".m4a")
            } ?: return recordings

            // Sort by modified date (newest first)
            files.sortByDescending { it.lastModified() }

            for (file in files) {
                val duration = calculateDuration(file.length())
                recordings.add(
                    mapOf(
                        "name" to file.name,
                        "path" to file.absolutePath,
                        "size" to file.length(),
                        "duration" to duration,
                        "date" to file.lastModified(),
                        "formattedDate" to formatDate(file.lastModified())
                    )
                )
            }
        }

        return recordings
    }

    private fun deleteRecordingFile(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun getRecordingDirectory(): String {
        val filesDir = this.filesDir
        val recordingDir = File(filesDir, "CallRecordings")
        if (!recordingDir.exists()) {
            recordingDir.mkdirs()
        }
        return recordingDir.absolutePath
    }

    private fun calculateDuration(fileSize: Long): Int {
        // Approximate duration based on file size
        // M4A at 128 kbps = ~16000 bytes per second
        // Adjust based on actual bitrate
        return (fileSize / 16000).toInt()
    }

    private fun formatDate(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return format.format(date)
    }
}
