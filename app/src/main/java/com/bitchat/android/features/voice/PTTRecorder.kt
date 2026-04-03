package com.bitchat.android.features.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

/**
 * Improvement 5 — Push-to-Talk (Walkie Talkie)
 *
 * Wraps the existing VoiceRecorder for PTT use.
 * Hold → startPTT(), Release → stopPTT() returns the recorded file path.
 *
 * Audio spec: 16 kHz mono AAC @ 20 kbps (~2.5 KB/sec)
 * 10-second max → ~25 KB — well within BLE FILE_TRANSFER limits.
 */
class PTTRecorder(private val context: Context) {

    companion object {
        private const val TAG = "PTTRecorder"
        const val MAX_DURATION_MS = 10_000L   // 10 second safety cap
    }

    private val recorder = VoiceRecorder(context)
    private var timeoutJob: Job? = null
    private var isRecording = false
    private var onMaxDurationReached: (() -> Unit)? = null

    /**
     * Start recording.
     * @param onMaxDuration called if the 10-second cap is hit (so UI can auto-stop).
     * @return true if recording started successfully.
     */
    fun startPTT(
        scope: CoroutineScope,
        onMaxDuration: () -> Unit
    ): Boolean {
        if (isRecording) {
            Log.w(TAG, "startPTT called while already recording — ignoring")
            return false
        }
        val file = recorder.start()
        if (file == null) {
            Log.e(TAG, "VoiceRecorder.start() returned null — mic unavailable?")
            return false
        }
        isRecording = true
        onMaxDurationReached = onMaxDuration
        Log.w(TAG, "🎙️ PTT started: ${file.absolutePath}")

        // Safety timeout — auto-stop after 10 seconds
        timeoutJob = scope.launch {
            delay(MAX_DURATION_MS)
            if (isRecording) {
                Log.w(TAG, "⏱️ PTT max duration reached — auto-stopping")
                onMaxDurationReached?.invoke()
            }
        }
        return true
    }

    /**
     * Stop recording.
     * @return absolute path to the M4A file, or null on error.
     */
    fun stopPTT(): String? {
        if (!isRecording) return null
        timeoutJob?.cancel()
        timeoutJob = null
        isRecording = false
        val file = recorder.stop()
        return if (file != null && file.exists() && file.length() > 0) {
            Log.w(TAG, "🎙️ PTT stopped — file: ${file.absolutePath} (${file.length()} bytes)")
            file.absolutePath
        } else {
            Log.w(TAG, "PTT recording was too short or empty — discarding")
            file?.delete()
            null
        }
    }

    /** Poll live amplitude (0–32768) for the animated recording indicator. */
    fun pollAmplitude(): Int = recorder.pollAmplitude()

    val isActive get() = isRecording
}
