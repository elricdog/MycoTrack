package com.mikotrack.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Thin wrapper around the platform [LocationManager].
 *
 * MycoTrack deliberately does not use the fused location provider so that the application
 * does not depend on Google Play Services.
 */
class LocationTracker(context: Context) {

    private val locationManager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /** True when at least one location provider is enabled on the device. */
    fun isLocationEnabled(): Boolean = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    /** True when the GPS provider is enabled. */
    fun isGpsEnabled(): Boolean = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    /** The last known fix of any provider, if the system has one cached. */
    fun lastKnownLocation(): Location? = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    ).mapNotNull { provider ->
        runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }

    /**
     * Emits every location update reported by the enabled providers.
     *
     * The flow is cold: it registers the listener when collected and removes it again when
     * the collector is cancelled, which happens when the recording service stops.
     */
    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMillis: Long): Flow<Location> = callbackFlow {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider ->
                runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
            }

        if (providers.isEmpty()) {
            close()
            return@callbackFlow
        }

        providers.forEach { provider ->
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    intervalMillis,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            }
        }

        awaitClose {
            runCatching { locationManager.removeUpdates(listener) }
        }
    }
}
