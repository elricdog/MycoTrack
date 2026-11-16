package com.mikotrack.location

import com.mikotrack.core.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Tests of the GPS smoothing and of the selection of the stored track points. */
class GpsTrackSmootherTest {

    private val latitude = 41.6
    private val longitude = 2.29
    private val metersPerDegreeLatitude = 111_320.0
    private val metersPerDegreeLongitude = metersPerDegreeLatitude * Math.cos(Math.toRadians(latitude))

    private fun fix(
        second: Int,
        northMeters: Double = 0.0,
        eastMeters: Double = 0.0,
        accuracy: Float = 8f,
        speed: Float? = null,
        bearing: Float? = null,
        mslAltitude: Double? = null,
        ellipsoidAltitude: Double? = null,
        isGps: Boolean = true,
    ) = RawFix(
        latitude = latitude + northMeters / metersPerDegreeLatitude,
        longitude = longitude + eastMeters / metersPerDegreeLongitude,
        mslAltitude = mslAltitude,
        ellipsoidAltitude = ellipsoidAltitude,
        accuracy = accuracy,
        speed = speed,
        bearing = bearing,
        timestamp = 1_700_000_000_000L + second * 1_000L,
        elapsedMillis = second * 1_000L,
        isGps = isGps,
    )

    private fun distanceFromTruth(lat: Double, lon: Double, northMeters: Double = 0.0) =
        GeoUtils.distanceMeters(latitude + northMeters / metersPerDegreeLatitude, longitude, lat, lon)

    @Test
    fun standingStill_noiseIsSuppressed() {
        val smoother = GpsTrackSmoother()
        val random = Random(42)
        var worstRaw = 0.0
        var worstSmoothed = 0.0
        for (second in 0 until 120) {
            val raw = fix(second, random.nextGaussian() * 6, random.nextGaussian() * 6, speed = 0.1f)
            val estimate = smoother.process(raw) ?: continue
            if (second >= 20) {
                worstRaw = maxOf(worstRaw, distanceFromTruth(raw.latitude, raw.longitude))
                worstSmoothed = maxOf(worstSmoothed, distanceFromTruth(estimate.latitude, estimate.longitude))
                assertTrue(estimate.isStationary)
            }
        }
        assertTrue("smoothed $worstSmoothed m, raw $worstRaw m", worstSmoothed < 3.0)
        assertTrue(worstRaw > 10.0)
    }

    @Test
    fun walking_estimateFollowsThePathWithoutLagging() {
        val smoother = GpsTrackSmoother()
        val random = Random(7)
        val speed = 1.3
        var rawError = 0.0
        var smoothedError = 0.0
        var last: SmoothedFix? = null
        for (second in 0 until 120) {
            val truth = speed * second
            val raw = fix(
                second,
                northMeters = truth + random.nextGaussian() * 6,
                eastMeters = random.nextGaussian() * 6,
                speed = (speed + random.nextGaussian() * 0.2).toFloat(),
                bearing = ((random.nextGaussian() * 5 + 360) % 360).toFloat(),
            )
            val estimate = smoother.process(raw) ?: continue
            if (second >= 20) {
                rawError += distanceFromTruth(raw.latitude, raw.longitude, truth)
                smoothedError += distanceFromTruth(estimate.latitude, estimate.longitude, truth)
            }
            last = estimate
        }
        assertTrue("smoothed $smoothedError, raw $rawError", smoothedError < rawError * 0.5)
        assertNotNull(last)
        assertEquals(speed, last!!.speed.toDouble(), 0.3)
        assertTrue(!last.isStationary)
    }

    @Test
    fun singleJumpIsRejected_butARealRelocationIsFollowed() {
        val smoother = GpsTrackSmoother(maxJumpMeters = 100.0)
        for (second in 0 until 10) smoother.process(fix(second, speed = 0f))

        assertNull(smoother.process(fix(10, northMeters = 60.0, speed = 0f)))
        assertNotNull(smoother.process(fix(11, speed = 0f)))

        // After a signal loss the receiver reappears 500 m away and stays there: the
        // filter must not reject the new position forever.
        var estimate: SmoothedFix? = null
        for (second in 12 until 20) {
            estimate = smoother.process(fix(second, northMeters = 500.0, speed = 0f)) ?: estimate
        }
        assertNotNull(estimate)
        assertTrue(distanceFromTruth(estimate!!.latitude, estimate.longitude, 500.0) < 10.0)
    }

    @Test
    fun aSpeedThatContradictsThePositionsIsIgnored() {
        // Emulators, and some receivers under the trees, report a speed of 0 while the
        // user walks: the positions must win.
        val smoother = GpsTrackSmoother()
        val random = Random(3)
        val speed = 1.3
        var last: SmoothedFix? = null
        for (second in 0 until 90) {
            val raw = fix(
                second,
                northMeters = speed * second + random.nextGaussian() * 3,
                eastMeters = random.nextGaussian() * 3,
                accuracy = 5f,
                speed = 0f,
            )
            last = smoother.process(raw) ?: last
        }
        assertNotNull(last)
        assertTrue("speed ${last!!.speed}", last.speed > 0.8f)
        assertTrue(!last.isStationary)
        assertTrue(distanceFromTruth(last.latitude, last.longitude, speed * 89) < 8.0)
    }

    @Test
    fun networkFixesAreIgnoredWhileTheGpsDelivers() {
        val smoother = GpsTrackSmoother(maxAccuracyMeters = 200.0)
        smoother.process(fix(0, speed = 0f))
        assertNull(smoother.process(fix(1, northMeters = 40.0, accuracy = 30f, isGps = false)))
    }

    @Test
    fun inaccurateFixesAreDiscarded() {
        val smoother = GpsTrackSmoother(maxAccuracyMeters = 30.0)
        assertNull(smoother.process(fix(0, accuracy = 80f)))
        assertTrue(!smoother.isInitialized)
    }

    @Test
    fun altitudeReferencesAreNeverMixed() {
        val smoother = GpsTrackSmoother()
        var estimate: SmoothedFix? = null
        for (second in 0 until 10) {
            estimate = smoother.process(fix(second, speed = 0f, mslAltitude = 170.0, ellipsoidAltitude = 220.0))
        }
        // A fix without the sea level altitude must not pull the estimate 50 m up.
        estimate = smoother.process(fix(10, speed = 0f, ellipsoidAltitude = 220.0)) ?: estimate
        assertEquals(170.0, estimate!!.altitude!!, 0.5)
    }

    @Test
    fun missingAltitudeStaysMissing() {
        val smoother = GpsTrackSmoother()
        assertNull(smoother.process(fix(0))!!.altitude)
    }

    private fun smoothed(second: Int, northMeters: Double, stationary: Boolean) = SmoothedFix(
        latitude = latitude + northMeters / metersPerDegreeLatitude,
        longitude = longitude,
        altitude = 100.0,
        accuracy = 5f,
        speed = if (stationary) 0f else 1.2f,
        bearing = 0f,
        timestamp = second * 1_000L,
        isStationary = stationary,
        wasReset = false,
    )

    @Test
    fun sampler_storesAtTheConfiguredIntervalWhileMoving() {
        val sampler = TrackSampler(intervalMillis = 5_000)
        val stored = (0..20).mapNotNull { second -> sampler.sample(smoothed(second, second * 1.2, false)) }
        assertEquals(listOf(0L, 5_000L, 10_000L, 15_000L, 20_000L), stored.map { it.timestamp })
    }

    @Test
    fun sampler_anchorsStationaryPointsOnTheLastStoredPosition() {
        val sampler = TrackSampler(intervalMillis = 2_000, stationaryIntervalMillis = 10_000)
        val first = sampler.sample(smoothed(0, 0.0, true))!!
        val stored = (1..30).mapNotNull { second ->
            sampler.sample(smoothed(second, if (second % 2 == 0) 1.5 else -1.5, true))
        }
        assertEquals(listOf(10_000L, 20_000L, 30_000L), stored.map { it.timestamp })
        stored.forEach {
            assertEquals(first.latitude, it.latitude, 0.0)
            assertEquals(first.longitude, it.longitude, 0.0)
        }
    }
}
