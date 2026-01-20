package com.example.recorder

import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioSource
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class CallRecorderService : Service() {
    private var audioRecord: AudioRecord? = null
    private var mediaCodec: MediaCodec? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var fileOutputStream: FileOutputStream? = null

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BIT_RATE = 128000
        private const val CODEC_MIME = "audio/mp4a-latm"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d("CallRecorderService", "Service started with action: ${intent?.action}")

        when (intent?.action) {
            "START_RECORDING" -> startRecordingProcess()
            "STOP_RECORDING" -> stopRecordingProcess()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRecordingProcess() {
        if (isRecording) {
            Log.w("CallRecorderService", "Recording already in progress")
            return
        }

        try {
            isRecording = true

            // Create recording directory
            val recordingDir = File(filesDir, "CallRecordings")
            if (!recordingDir.exists()) {
                recordingDir.mkdirs()
            }

            // Generate recording file
            val timeStamp = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault()).format(Date())
            val recordingFile = File(recordingDir, "call_$timeStamp.m4a")

            // Initialize audio recording
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            audioRecord = AudioRecord(
                AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            // Initialize encoder
            mediaCodec = MediaCodec.createEncoderByType(CODEC_MIME)
            val format = MediaFormat.createAudioFormat(CODEC_MIME, SAMPLE_RATE, 1)
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

            mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mediaCodec?.start()

            // Open output file
            fileOutputStream = FileOutputStream(recordingFile)

            // Start recording thread
            recordingThread = thread {
                recordAudio(bufferSize)
            }

            Log.d("CallRecorderService", "Recording started: ${recordingFile.absolutePath}")

        } catch (e: Exception) {
            Log.e("CallRecorderService", "Error starting recording", e)
            isRecording = false
            cleanup()
        }
    }

    private fun recordAudio(bufferSize: Int) {
        try {
            audioRecord?.startRecording()
            val buffer = ByteArray(bufferSize)
            var presentationTimeUs = 0L
            val TIMEOUT_US = 10000L

            while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (readBytes > 0) {
                    // Feed to encoder
                    val inputIndex = mediaCodec?.dequeueInputBuffer(TIMEOUT_US) ?: -1
                    if (inputIndex >= 0) {
                        val inputBuffer = mediaCodec?.getInputBuffer(inputIndex)
                        inputBuffer?.clear()
                        inputBuffer?.put(buffer, 0, readBytes)
                        mediaCodec?.queueInputBuffer(inputIndex, 0, readBytes, presentationTimeUs, 0)
                        presentationTimeUs += (readBytes * 1000000L) / (SAMPLE_RATE * 2)
                    }

                    // Get encoded output
                    val bufferInfo = MediaCodec.BufferInfo()
                    var outputIndex = mediaCodec?.dequeueOutputBuffer(bufferInfo, TIMEOUT_US) ?: -1

                    while (outputIndex >= 0) {
                        val outputBuffer = mediaCodec?.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            val data = ByteArray(bufferInfo.size)
                            outputBuffer.get(data)
                            fileOutputStream?.write(data)
                        }
                        mediaCodec?.releaseOutputBuffer(outputIndex, false)
                        outputIndex = mediaCodec?.dequeueOutputBuffer(bufferInfo, TIMEOUT_US) ?: -1
                    }
                }
            }

            // Drain remaining output
            if (!isRecording) {
                mediaCodec?.signalEndOfInputStream()
                val bufferInfo = MediaCodec.BufferInfo()
                var outputIndex = mediaCodec?.dequeueOutputBuffer(bufferInfo, TIMEOUT_US) ?: -1

                while (outputIndex >= 0) {
                    val outputBuffer = mediaCodec?.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val data = ByteArray(bufferInfo.size)
                        outputBuffer.get(data)
                        fileOutputStream?.write(data)
                    }
                    mediaCodec?.releaseOutputBuffer(outputIndex, false)
                    outputIndex = mediaCodec?.dequeueOutputBuffer(bufferInfo, TIMEOUT_US) ?: -1
                }
            }

            Log.d("CallRecorderService", "Recording completed")

        } catch (e: Exception) {
            Log.e("CallRecorderService", "Error during recording", e)
        }
    }

    private fun stopRecordingProcess() {
        if (!isRecording) {
            Log.w("CallRecorderService", "No recording in progress")
            return
        }

        try {
            isRecording = false

            // Stop audio recording
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }

            // Wait for recording thread
            recordingThread?.join(5000) // Wait max 5 seconds

            cleanup()
            Log.d("CallRecorderService", "Recording stopped")

        } catch (e: Exception) {
            Log.e("CallRecorderService", "Error stopping recording", e)
        }
    }

    private fun cleanup() {
        try {
            // Release audio record
            audioRecord?.release()
            audioRecord = null

            // Stop and release media codec
            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null

            // Close file stream
            fileOutputStream?.close()
            fileOutputStream = null

            Log.d("CallRecorderService", "Cleanup completed")
        } catch (e: Exception) {
            Log.e("CallRecorderService", "Error during cleanup", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        cleanup()
        Log.d("CallRecorderService", "Service destroyed")
    }
}
