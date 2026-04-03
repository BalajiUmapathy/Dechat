package com.bitchat.android.features

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import kotlinx.coroutines.*

/**
 * Controls flashlight-based Morse code SOS signalling.
 *
 * Flashes the international Morse SOS pattern:  · · · — — — · · ·
 * simultaneously with haptic vibration for dual-channel alerting.
 *
 * NO camera permission is needed — CameraManager.setTorchMode() is
 * exempt from CAMERA permission on all Android versions.
 *
 * Usage:
 *   val controller = MorseSOSController(context)
 *   controller.startSOS()   // starts looping
 *   controller.stopSOS()    // stops and turns flash off
 */
class MorseSOSController(private val context: Context) {

    private val TAG = "MorseSOSController"

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    @Suppress("DEPRECATION")
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private var flashJob: Job? = null
    private var cameraId: String? = null

    init {
        // Get the first back-facing (or any available) camera ID
        cameraId = try {
            cameraManager.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Could not get camera ID: ${e.message}")
            null
        }
    }

    companion object {
        const val DOT_MS  = 200L   // Short flash (dit)
        const val DASH_MS = 600L   // Long flash (dah)
        const val GAP_MS  = 200L   // Gap between symbols in same letter
        const val LETTER_GAP = 600L // Gap between letters (S and O)
        const val CYCLE_GAP = 1400L // Pause before repeating whole pattern

        // SOS = ... --- ...
        val SOS_PATTERN = listOf(
            DOT_MS,  DOT_MS,  DOT_MS,   // S: three dots
            DASH_MS, DASH_MS, DASH_MS,  // O: three dashes
            DOT_MS,  DOT_MS,  DOT_MS    // S: three dots
        )
    }

    /** @return true if this device has a flashlight */
    fun hasFlashlight(): Boolean = cameraId != null

    fun startSOS() {
        if (cameraId == null) {
            Log.w(TAG, "No flashlight available on this device")
            return
        }
        flashJob?.cancel()
        flashJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Morse SOS started")
            while (isActive) {
                SOS_PATTERN.forEachIndexed { index, duration ->
                    if (!isActive) return@forEachIndexed

                    // Flash ON + vibrate
                    setFlash(true)
                    vibrateFlash(duration)
                    delay(duration)

                    // Flash OFF
                    setFlash(false)

                    // Compute gap: letter gap after S (index 2) and O (index 5)
                    val gap = when (index) {
                        2 -> LETTER_GAP  // After first 'S' → before 'O'
                        5 -> LETTER_GAP  // After 'O' → before second 'S'
                        else -> GAP_MS
                    }
                    delay(gap)
                }
                // Pause before repeating the full SOS sequence
                delay(CYCLE_GAP)
            }
        }
    }

    fun stopSOS() {
        flashJob?.cancel()
        flashJob = null
        setFlash(false) // Always ensure flash turns OFF
        Log.d(TAG, "Morse SOS stopped")
    }

    private fun setFlash(on: Boolean) {
        try {
            cameraId?.let { cameraManager.setTorchMode(it, on) }
        } catch (e: Exception) {
            // Device may not have flash, or another app is using the camera
            Log.w(TAG, "setTorchMode failed: ${e.message}")
        }
    }

    private fun vibrateFlash(duration: Long) {
        try {
            vibrator.vibrate(
                VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Vibrate failed: ${e.message}")
        }
    }
}
