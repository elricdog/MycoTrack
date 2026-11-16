package com.mikotrack.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.mikotrack.core.Permissions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** The satellite systems reported by [GnssStatus]. */
enum class Constellation(val label: String) {
    GPS("GPS"),
    GLONASS("GLONASS"),
    GALILEO("Galileo"),
    BEIDOU("BeiDou"),
    QZSS("QZSS"),
    SBAS("SBAS"),
    IRNSS("NavIC"),
    UNKNOWN("?"),
    ;

    companion object {
        fun fromGnssType(type: Int): Constellation = when (type) {
            GnssStatus.CONSTELLATION_GPS -> GPS
            GnssStatus.CONSTELLATION_GLONASS -> GLONASS
            GnssStatus.CONSTELLATION_GALILEO -> GALILEO
            GnssStatus.CONSTELLATION_BEIDOU -> BEIDOU
            GnssStatus.CONSTELLATION_QZSS -> QZSS
            GnssStatus.CONSTELLATION_SBAS -> SBAS
            CONSTELLATION_IRNSS -> IRNSS
            else -> UNKNOWN
        }

        // GnssStatus.CONSTELLATION_IRNSS only exists from API 29.
        private const val CONSTELLATION_IRNSS = 7
    }
}

/** One satellite as reported by the GNSS receiver. */
data class SatelliteInfo(
    val svid: Int,
    val constellation: Constellation,
    val cn0DbHz: Float,
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val usedInFix: Boolean,
    val hasAlmanac: Boolean,
    val hasEphemeris: Boolean,
    val carrierFrequencyHz: Float?,
)

/** The state of the GNSS receiver at one moment. */
data class GnssSnapshot(
    val satellites: List<SatelliteInfo> = emptyList(),
    val timeToFirstFixMillis: Int? = null,
    val started: Boolean = false,
) {
    val visibleCount: Int get() = satellites.size

    val usedCount: Int get() = satellites.count { it.usedInFix }

    /** Used and visible satellites of every constellation present: `used to visible`. */
    val usedByConstellation: Map<Constellation, Pair<Int, Int>>
        get() = satellites.groupBy { it.constellation }
            .toSortedMap()
            .mapValues { (_, list) -> list.count { it.usedInFix } to list.size }

    /** The mean C/N0 of the four strongest satellites used in the fix, or null when none is used. */
    val averageTopCn0: Float?
        get() = satellites.filter { it.usedInFix && it.cn0DbHz > 0f }
            .map { it.cn0DbHz }
            .sortedDescending()
            .take(4)
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()
}

/**
 * Reports the satellites seen by the GNSS receiver.
 *
 * The platform only delivers these callbacks while some location request on the GPS
 * provider is active, so callers must request location updates at the same time.
 */
class GnssMonitor(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /** Name of the GNSS chipset (API 28+), when the device reports it. */
    fun hardwareModelName(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { locationManager.gnssHardwareModelName }.getOrNull()
        } else {
            null
        }

    /** Year of the GNSS hardware (API 28+), or null when unknown. */
    fun hardwareYear(): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { locationManager.gnssYearOfHardware }.getOrNull()?.takeIf { it > 0 }
        } else {
            null
        }

    /**
     * Emits a snapshot every time the receiver reports its satellites. Without the precise
     * location permission a single empty snapshot is emitted and the flow completes.
     */
    @SuppressLint("MissingPermission")
    fun status(): Flow<GnssSnapshot> = callbackFlow {
        if (!Permissions.isGranted(appContext, Manifest.permission.ACCESS_FINE_LOCATION)) {
            trySend(GnssSnapshot())
            close()
            return@callbackFlow
        }

        var current = GnssSnapshot()
        val callback = object : GnssStatus.Callback() {
            override fun onStarted() {
                current = current.copy(started = true)
                trySend(current)
            }

            override fun onStopped() {
                current = current.copy(started = false)
                trySend(current)
            }

            override fun onFirstFix(ttffMillis: Int) {
                current = current.copy(timeToFirstFixMillis = ttffMillis)
                trySend(current)
            }

            override fun onSatelliteStatusChanged(status: GnssStatus) {
                current = current.copy(satellites = status.toSatellites(), started = true)
                trySend(current)
            }
        }

        val registered = try {
            locationManager.registerGnssStatusCallback(callback, Handler(Looper.getMainLooper()))
        } catch (_: SecurityException) {
            false
        }
        if (!registered) {
            trySend(GnssSnapshot())
            close()
            return@callbackFlow
        }

        awaitClose {
            runCatching { locationManager.unregisterGnssStatusCallback(callback) }
        }
    }

    private fun GnssStatus.toSatellites(): List<SatelliteInfo> = (0 until satelliteCount).map { i ->
        SatelliteInfo(
            svid = getSvid(i),
            constellation = Constellation.fromGnssType(getConstellationType(i)),
            cn0DbHz = getCn0DbHz(i),
            elevationDegrees = getElevationDegrees(i),
            azimuthDegrees = getAzimuthDegrees(i),
            usedInFix = usedInFix(i),
            hasAlmanac = hasAlmanacData(i),
            hasEphemeris = hasEphemerisData(i),
            carrierFrequencyHz = if (hasCarrierFrequencyHz(i)) getCarrierFrequencyHz(i) else null,
        )
    }
}
