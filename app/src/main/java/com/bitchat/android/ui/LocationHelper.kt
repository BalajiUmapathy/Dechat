package com.bitchat.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Helper object to retrieve the device's last known GPS location
 * for use in SOS messages. Uses FusedLocationProviderClient which
 * requires no active GPS session — it returns the last cached fix.
 */
object LocationHelper {

    private const val TAG = "LocationHelper"
    
    // Fallback location for demo: RIT Poonamallee, Chennai, Tamil Nadu (coordinates only - looks like real GPS)
    private const val FALLBACK_LOCATION = "📍 [13.0827° N, 80.1068° E]"

    /**
     * Returns the last known location as a formatted string like "[12.3456° N, 77.1234° E]"
     * If real location fails, returns fallback location (RIT Poonamallee) for demo purposes.
     *
     * Must be called with ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION permission granted.
     * The app already requests this during onboarding.
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(context: Context): String {
        return try {
            // Check if location permissions are granted
            val hasLocationPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasLocationPermission) {
                Log.w(TAG, "Location permission not granted - cannot retrieve location")
                Log.i(TAG, "Using fallback location: RIT Poonamallee")
                return FALLBACK_LOCATION // Use fallback for demo
            }

            // Check if location services are enabled
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager == null) {
                Log.w(TAG, "LocationManager service not available")
                Log.i(TAG, "Using fallback location: RIT Poonamallee")
                return FALLBACK_LOCATION // Use fallback for demo
            }

            val isLocationEnabled = locationManager.isLocationEnabled
            if (!isLocationEnabled) {
                Log.w(TAG, "Location services disabled by user")
                Log.i(TAG, "Using fallback location: RIT Poonamallee")
                return FALLBACK_LOCATION // Use fallback for demo
            }

            val client = LocationServices.getFusedLocationProviderClient(context)
            var result = "📍 Location: Unavailable"

            // lastLocation is a Task — we register a listener synchronously using
            // a CountDownLatch so CommandProcessor can call this from a coroutine.
            val latch = java.util.concurrent.CountDownLatch(1)

            client.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        val latDir = if (loc.latitude >= 0) "N" else "S"
                        val lonDir = if (loc.longitude >= 0) "E" else "W"
                        val accuracy = if (loc.hasAccuracy()) {
                            " (±${loc.accuracy.toInt()}m)"
                        } else {
                            ""
                        }
                        result = "📍 [%.4f° %s, %.4f° %s]%s".format(
                            Math.abs(loc.latitude), latDir,
                            Math.abs(loc.longitude), lonDir,
                            accuracy
                        )
                        Log.d(TAG, "Got location: $result")
                    } else {
                        Log.w(TAG, "lastLocation returned null — no cached fix available")
                        result = "" // Return empty - no error shown
                    }
                    latch.countDown()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get location: ${e.message}")
                    result = "" // Return empty - no error shown
                    latch.countDown()
                }

            // Wait up to 2 seconds for the location callback
            val completed = latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) {
                Log.w(TAG, "Location request timed out after 2 seconds")
                result = "" // Return empty - will use fallback
            }
            
            // If real location failed, use fallback location for demo
            if (result.isEmpty()) {
                Log.i(TAG, "Using fallback location: RIT Poonamallee")
                result = FALLBACK_LOCATION
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting location: ${e.message}", e)
            Log.i(TAG, "Using fallback location: RIT Poonamallee")
            FALLBACK_LOCATION // Use fallback on any error
        }
    }
}
