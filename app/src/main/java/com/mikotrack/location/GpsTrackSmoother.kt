package com.mikotrack.location

import android.location.Location
import android.location.LocationManager
import android.os.Build
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * One raw fix of a location provider.
 *
 * It mirrors the fields of [Location] that the smoothing needs, so that the filter can be
 * exercised by plain unit tests.
 */
data class RawFix(
    val latitude: Double,
    val longitude: Double,
    /** Altitude above the ellipsoid (WGS84), as reported by every Android version. */
    val ellipsoidAltitude: Double? = null,
    /** Altitude above the mean sea level, reported by Android 14 and later. */
    val mslAltitude: Double? = null,
    val verticalAccuracy: Float? = null,
    val accuracy: Float? = null,
    val speed: Float? = null,
    val speedAccuracy: Float? = null,
    val bearing: Float? = null,
    /** Wall clock time of the fix, stored with the track. */
    val timestamp: Long,
    /** Monotonic time of the fix, used for the time steps of the filter. */
    val elapsedMillis: Long,
    val isGps: Boolean = true,
)

/** A position estimated by [GpsTrackSmoother]. */
data class SmoothedFix(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    /** The accuracy reported by the receiver for the fix that produced the estimate. */
    val accuracy: Float?,
    val speed: Float,
    val bearing: Float?,
    val timestamp: Long,
    /** True when the user is standing still. */
    val isStationary: Boolean,
    /** True when the filter restarted from this fix, for example after a signal loss. */
    val wasReset: Boolean,
)

/**
 * Smooths the GPS positions with a Kalman filter.
 *
 * The receiver is sampled much faster than the track is stored and every fix is fused
 * into a constant velocity model (position and velocity on the east and north axes):
 *  - the horizontal accuracy of each fix weights how much it moves the estimate, so a
 *    poor fix under the trees barely shifts the position,
 *  - the Doppler speed and bearing of the receiver, which are far more precise than
 *    the positions, keep the estimate on the path without lagging behind the user; if
 *    the positions keep contradicting the reported speed (a receiver that reports 0
 *    while the user walks), the speed is ignored for a while and the velocity is
 *    estimated from the positions alone,
 *  - a fix that is statistically impossible (a jump) is rejected, but a sequence of
 *    them, or a long silence, restarts the filter: after a signal loss the track
 *    continues from the new position instead of rejecting it forever,
 *  - network fixes are ignored while the GPS is delivering, because their error of a
 *    hundred metres would pull the track around.
 *
 * The altitude is smoothed by a separate filter. The altitude above the mean sea level
 * is preferred when the device provides it, because it matches the maps; the reference
 * that is chosen with the first fix is kept for the whole track, so that the two
 * references, which differ by about 50 m in Catalonia, are never mixed.
 */
class GpsTrackSmoother(
    private val maxAccuracyMeters: Double = 30.0,
    private val maxJumpMeters: Double = 100.0,
    /** Standard deviation of the acceleration of a walker, in m/s². */
    private val accelerationNoise: Double = 0.8,
) {

    /** The state of one horizontal axis: position, velocity and their covariance. */
    private class Axis {
        var position = 0.0
        var velocity = 0.0
        var varPosition = 0.0
        var covariance = 0.0
        var varVelocity = 0.0

        fun init(position: Double, variance: Double, velocity: Double = 0.0) {
            this.position = position
            this.velocity = velocity
            varPosition = variance
            covariance = 0.0
            varVelocity = INITIAL_VELOCITY_VARIANCE
        }

        fun predict(dt: Double, q: Double) {
            position += velocity * dt
            val dt2 = dt * dt
            varPosition += 2 * dt * covariance + dt2 * varVelocity + q * dt2 * dt2 / 4
            covariance += dt * varVelocity + q * dt2 * dt / 2
            varVelocity += q * dt2
        }

        fun updatePosition(measured: Double, variance: Double) {
            val s = varPosition + variance
            val kPosition = varPosition / s
            val kVelocity = covariance / s
            val innovation = measured - position
            position += kPosition * innovation
            velocity += kVelocity * innovation
            val oldVarPosition = varPosition
            val oldCovariance = covariance
            varPosition = (1 - kPosition) * oldVarPosition
            covariance = (1 - kPosition) * oldCovariance
            varVelocity -= kVelocity * oldCovariance
        }

        fun updateVelocity(measured: Double, variance: Double) {
            val s = varVelocity + variance
            val kPosition = covariance / s
            val kVelocity = varVelocity / s
            val innovation = measured - velocity
            position += kPosition * innovation
            velocity += kVelocity * innovation
            val oldCovariance = covariance
            val oldVarVelocity = varVelocity
            varPosition -= kPosition * oldCovariance
            covariance = (1 - kVelocity) * oldCovariance
            varVelocity = (1 - kVelocity) * oldVarVelocity
        }
    }

    private enum class AltitudeReference { MSL, ELLIPSOID }

    private val east = Axis()
    private val north = Axis()
    private var originLatitude = 0.0
    private var originLongitude = 0.0
    private var metersPerDegreeLongitude = METERS_PER_DEGREE_LATITUDE

    private var lastElapsedMillis: Long? = null
    private var lastGpsElapsedMillis: Long? = null
    private var consecutiveRejections = 0

    // Running mean of the position innovations. Noise averages out to almost zero; a
    // persistent offset means that the estimated velocity is wrong, which happens when
    // the receiver reports a Doppler speed that contradicts its own positions.
    private var meanInnovationEast = 0.0
    private var meanInnovationNorth = 0.0
    private var dopplerDistrustedUntil = Long.MIN_VALUE

    private var altitudeReference: AltitudeReference? = null
    private var altitude: Double? = null
    private var altitudeVariance = 0.0
    private var lastAltitudeElapsedMillis: Long? = null

    /** True once the filter holds a position. */
    var isInitialized: Boolean = false
        private set

    /** Forgets the current estimate; the next acceptable fix starts a new one. */
    fun reset() {
        isInitialized = false
        lastElapsedMillis = null
        lastGpsElapsedMillis = null
        consecutiveRejections = 0
        altitudeReference = null
        altitude = null
        lastAltitudeElapsedMillis = null
    }

    /**
     * Fuses a new fix into the estimate.
     *
     * Returns the new estimate, or `null` when the fix was discarded.
     */
    fun process(fix: RawFix): SmoothedFix? {
        if (fix.latitude !in -90.0..90.0 || fix.longitude !in -180.0..180.0) return null
        val accuracy = (fix.accuracy?.toDouble() ?: DEFAULT_ACCURACY_METERS)
            .coerceAtLeast(MIN_ACCURACY_METERS)
        if (accuracy > maxAccuracyMeters) return null

        val lastGps = lastGpsElapsedMillis
        if (!fix.isGps && lastGps != null && fix.elapsedMillis - lastGps < NETWORK_SUPPRESSION_MILLIS) {
            return null
        }

        val last = lastElapsedMillis
        if (!isInitialized || last == null) return start(fix, accuracy)

        val dt = (fix.elapsedMillis - last) / 1000.0
        // Both providers can report the same instant; the older fix is already fused.
        if (dt <= 0.0) return null
        if (dt > MAX_GAP_SECONDS) return start(fix, accuracy)

        val q = accelerationNoise * accelerationNoise
        east.predict(dt, q)
        north.predict(dt, q)
        lastElapsedMillis = fix.elapsedMillis

        val measuredEast = eastOf(fix.longitude)
        val measuredNorth = northOf(fix.latitude)
        val variance = accuracy * accuracy
        val innovationEast = measuredEast - east.position
        val innovationNorth = measuredNorth - north.position
        val jump = hypot(innovationEast, innovationNorth)
        val normalised = innovationEast * innovationEast / (east.varPosition + variance) +
            innovationNorth * innovationNorth / (north.varPosition + variance)

        val isJump = jump > maxJumpMeters ||
            (jump > MIN_GATE_METERS && normalised > GATE_SIGMAS * GATE_SIGMAS)
        if (isJump) {
            consecutiveRejections++
            // Several "impossible" fixes in a row mean that the estimate is wrong, not
            // the receiver: the filter starts again from the new position.
            return if (consecutiveRejections >= RESET_AFTER_REJECTIONS) start(fix, accuracy) else null
        }
        consecutiveRejections = 0

        meanInnovationEast += INNOVATION_SMOOTHING * (innovationEast - meanInnovationEast)
        meanInnovationNorth += INNOVATION_SMOOTHING * (innovationNorth - meanInnovationNorth)
        if (hypot(meanInnovationEast, meanInnovationNorth) > max(MIN_BIAS_METERS, accuracy)) {
            dopplerDistrustedUntil = fix.elapsedMillis + DOPPLER_DISTRUST_MILLIS
        }

        east.updatePosition(measuredEast, variance)
        north.updatePosition(measuredNorth, variance)
        val reportedSpeed = fix.speed
        if (fix.isGps && reportedSpeed != null && fix.elapsedMillis < dopplerDistrustedUntil) {
            // The reported speed is trusted again only once it agrees with the speed
            // that the positions show.
            val estimatedSpeed = hypot(east.velocity, north.velocity)
            if (abs(estimatedSpeed - reportedSpeed) > SPEED_AGREEMENT) {
                dopplerDistrustedUntil = fix.elapsedMillis + DOPPLER_DISTRUST_MILLIS
            }
        }
        fuseVelocity(fix)
        if (fix.isGps) lastGpsElapsedMillis = fix.elapsedMillis
        fuseAltitude(fix, accuracy)

        return estimate(fix, wasReset = false)
    }

    private fun start(fix: RawFix, accuracy: Double): SmoothedFix {
        originLatitude = fix.latitude
        originLongitude = fix.longitude
        metersPerDegreeLongitude =
            (METERS_PER_DEGREE_LATITUDE * cos(fix.latitude * PI / 180.0)).coerceAtLeast(1.0)
        val variance = accuracy * accuracy
        east.init(0.0, variance)
        north.init(0.0, variance)
        meanInnovationEast = 0.0
        meanInnovationNorth = 0.0
        dopplerDistrustedUntil = Long.MIN_VALUE
        fuseVelocity(fix)
        isInitialized = true
        consecutiveRejections = 0
        lastElapsedMillis = fix.elapsedMillis
        if (fix.isGps) lastGpsElapsedMillis = fix.elapsedMillis
        fuseAltitude(fix, accuracy)
        return estimate(fix, wasReset = true)
    }

    /** Fuses the Doppler velocity of the receiver, when it reports one. */
    private fun fuseVelocity(fix: RawFix) {
        if (!fix.isGps) return
        // The positions contradicted the reported speed recently: the velocity is then
        // estimated from the positions alone.
        if (fix.elapsedMillis < dopplerDistrustedUntil) return
        val speed = fix.speed?.toDouble() ?: return
        if (speed < ZERO_VELOCITY_SPEED) {
            // The user stands still: anchoring the velocity at zero stops the estimate
            // from wandering with the noise of the positions.
            val variance = ZERO_VELOCITY_SIGMA * ZERO_VELOCITY_SIGMA
            east.updateVelocity(0.0, variance)
            north.updateVelocity(0.0, variance)
            return
        }
        // The bearing is meaningless at very low speeds.
        val bearing = fix.bearing?.toDouble() ?: return
        if (speed < MIN_BEARING_SPEED) return
        val sigma = max(fix.speedAccuracy?.toDouble() ?: DEFAULT_SPEED_SIGMA, MIN_SPEED_SIGMA)
        val radians = bearing * PI / 180.0
        east.updateVelocity(speed * sin(radians), sigma * sigma)
        north.updateVelocity(speed * cos(radians), sigma * sigma)
    }

    private fun fuseAltitude(fix: RawFix, horizontalAccuracy: Double) {
        val reference = altitudeReference ?: when {
            fix.mslAltitude != null -> AltitudeReference.MSL
            fix.ellipsoidAltitude != null -> AltitudeReference.ELLIPSOID
            else -> return
        }.also { altitudeReference = it }

        val measured = when (reference) {
            AltitudeReference.MSL -> fix.mslAltitude
            AltitudeReference.ELLIPSOID -> fix.ellipsoidAltitude
        } ?: return

        // The vertical error of a GPS is usually about 1.5 times the horizontal one.
        val sigma = fix.verticalAccuracy?.toDouble()?.takeIf { it > 0.0 }
            ?: (horizontalAccuracy * 1.5)
        val variance = max(sigma, 1.0).let { it * it }

        val current = altitude
        val lastTime = lastAltitudeElapsedMillis
        lastAltitudeElapsedMillis = fix.elapsedMillis
        if (current == null || lastTime == null) {
            altitude = measured
            altitudeVariance = variance
            return
        }
        val dt = ((fix.elapsedMillis - lastTime) / 1000.0).coerceAtLeast(0.0)
        altitudeVariance += ALTITUDE_PROCESS_NOISE * dt
        val gain = altitudeVariance / (altitudeVariance + variance)
        altitude = current + gain * (measured - current)
        altitudeVariance *= (1 - gain)
    }

    private fun estimate(fix: RawFix, wasReset: Boolean): SmoothedFix {
        val speed = hypot(east.velocity, north.velocity)
        val bearing = if (speed >= MIN_BEARING_SPEED) {
            ((atan2(east.velocity, north.velocity) * 180.0 / PI + 360.0) % 360.0).toFloat()
        } else {
            fix.bearing
        }
        return SmoothedFix(
            latitude = originLatitude + north.position / METERS_PER_DEGREE_LATITUDE,
            longitude = originLongitude + east.position / metersPerDegreeLongitude,
            altitude = altitude,
            accuracy = fix.accuracy,
            speed = speed.toFloat(),
            bearing = bearing,
            timestamp = fix.timestamp,
            isStationary = speed < STATIONARY_SPEED,
            wasReset = wasReset,
        )
    }

    private fun eastOf(longitude: Double) = (longitude - originLongitude) * metersPerDegreeLongitude

    private fun northOf(latitude: Double) = (latitude - originLatitude) * METERS_PER_DEGREE_LATITUDE

    companion object {
        /** The rate at which the receiver is sampled, independent of the stored interval. */
        const val SAMPLING_INTERVAL_MILLIS = 1_000L

        private const val METERS_PER_DEGREE_LATITUDE = 111_320.0
        private const val DEFAULT_ACCURACY_METERS = 25.0
        private const val MIN_ACCURACY_METERS = 2.0
        private const val INITIAL_VELOCITY_VARIANCE = 4.0
        private const val NETWORK_SUPPRESSION_MILLIS = 15_000L
        private const val MAX_GAP_SECONDS = 20.0
        private const val MIN_GATE_METERS = 10.0
        private const val GATE_SIGMAS = 4.0
        private const val RESET_AFTER_REJECTIONS = 4
        private const val ZERO_VELOCITY_SPEED = 0.3
        private const val ZERO_VELOCITY_SIGMA = 0.15
        private const val MIN_BEARING_SPEED = 0.6
        private const val DEFAULT_SPEED_SIGMA = 0.5
        private const val MIN_SPEED_SIGMA = 0.2
        private const val STATIONARY_SPEED = 0.35
        private const val INNOVATION_SMOOTHING = 0.2
        private const val MIN_BIAS_METERS = 4.0
        private const val DOPPLER_DISTRUST_MILLIS = 15_000L
        private const val SPEED_AGREEMENT = 0.5

        /** Variance growth of the altitude per second, in m²/s. */
        private const val ALTITUDE_PROCESS_NOISE = 1.0
    }
}

/** Converts a fix of the platform into the input of [GpsTrackSmoother]. */
fun Location.toRawFix(): RawFix = RawFix(
    latitude = latitude,
    longitude = longitude,
    ellipsoidAltitude = if (hasAltitude()) altitude else null,
    mslAltitude = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitude()) {
        mslAltitudeMeters
    } else {
        null
    },
    verticalAccuracy = if (hasVerticalAccuracy()) verticalAccuracyMeters else null,
    accuracy = if (hasAccuracy()) accuracy else null,
    speed = if (hasSpeed()) speed else null,
    speedAccuracy = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
    bearing = if (hasBearing()) bearing else null,
    timestamp = time.takeIf { it > 0 } ?: System.currentTimeMillis(),
    elapsedMillis = elapsedRealtimeNanos / 1_000_000,
    isGps = provider == LocationManager.GPS_PROVIDER,
)

/** The position shown on the map for a smoothed estimate. */
fun SmoothedFix.toCurrentPosition(): CurrentPosition = CurrentPosition(
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    accuracy = accuracy,
    speed = speed,
    bearing = bearing,
    timestamp = timestamp,
)

/** The position shown on the map for a raw fix, used until the filter has an estimate. */
fun RawFix.toCurrentPosition(): CurrentPosition = CurrentPosition(
    latitude = latitude,
    longitude = longitude,
    altitude = mslAltitude ?: ellipsoidAltitude,
    accuracy = accuracy,
    speed = speed,
    bearing = bearing,
    timestamp = timestamp,
)
