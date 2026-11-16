package com.mikotrack.domain

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise and sunset times computed with the NOAA solar calculator algorithm.
 *
 * Everything is computed in UTC; the caller converts the instant to the time zone of the
 * device. The accuracy is about one minute between the polar circles, which is plenty to
 * warn a forager that night is coming.
 */
object SunCalculator {

    /** The zenith of the sun at sunrise and sunset: refraction and the solar disc included. */
    private const val SUNRISE_ZENITH_DEGREES = 90.833

    /**
     * The instant of the sunset of [date] at the given position, in UTC milliseconds, or
     * `null` when the sun does not set or does not rise that day (polar day or night).
     */
    fun sunsetUtcMillis(date: LocalDate, latitude: Double, longitude: Double): Long? =
        eventUtcMillis(date, latitude, longitude, rise = false)

    /** The instant of the sunrise of [date], in UTC milliseconds, or `null` near the poles. */
    fun sunriseUtcMillis(date: LocalDate, latitude: Double, longitude: Double): Long? =
        eventUtcMillis(date, latitude, longitude, rise = true)

    private fun eventUtcMillis(date: LocalDate, latitude: Double, longitude: Double, rise: Boolean): Long? {
        val julianDay = date.toEpochDay() + 2_440_587.5
        // A first estimate around noon, then refined once at the estimated time of the event.
        val first = eventUtcMinutes(julianDay + 0.5, latitude, longitude, rise) ?: return null
        val refined = eventUtcMinutes(julianDay + first / 1_440.0, latitude, longitude, rise) ?: return null
        val midnight = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return midnight + Math.round(refined * 60_000.0)
    }

    /** Minutes after 00:00 UTC of the requested day at which the event happens. */
    private fun eventUtcMinutes(
        evaluationDay: Double,
        latitude: Double,
        longitude: Double,
        rise: Boolean,
    ): Double? {
        val t = julianCentury(evaluationDay)
        val equationOfTime = equationOfTimeMinutes(t)
        val declination = sunDeclinationDegrees(t)
        val hourAngle = hourAngleDegrees(latitude, declination) ?: return null
        val signed = if (rise) hourAngle else -hourAngle
        return 720.0 - 4.0 * (longitude + signed) - equationOfTime
    }

    private fun julianCentury(julianDay: Double): Double = (julianDay - 2_451_545.0) / 36_525.0

    private fun geometricMeanLongitudeSun(t: Double): Double {
        val l0 = 280.46646 + t * (36_000.76983 + 0.0003032 * t)
        return ((l0 % 360.0) + 360.0) % 360.0
    }

    private fun geometricMeanAnomalySun(t: Double): Double =
        357.52911 + t * (35_999.05029 - 0.0001537 * t)

    private fun eccentricityEarthOrbit(t: Double): Double =
        0.016708634 - t * (0.000042037 + 0.0000001267 * t)

    private fun sunEquationOfCenter(t: Double): Double {
        val m = Math.toRadians(geometricMeanAnomalySun(t))
        return sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * m) * (0.019993 - 0.000101 * t) +
            sin(3 * m) * 0.000289
    }

    private fun sunApparentLongitude(t: Double): Double {
        val trueLongitude = geometricMeanLongitudeSun(t) + sunEquationOfCenter(t)
        val omega = 125.04 - 1_934.136 * t
        return trueLongitude - 0.00569 - 0.00478 * sin(Math.toRadians(omega))
    }

    private fun meanObliquityOfEcliptic(t: Double): Double {
        val seconds = 21.448 - t * (46.8150 + t * (0.00059 - t * 0.001813))
        return 23.0 + (26.0 + seconds / 60.0) / 60.0
    }

    private fun obliquityCorrection(t: Double): Double {
        val omega = 125.04 - 1_934.136 * t
        return meanObliquityOfEcliptic(t) + 0.00256 * cos(Math.toRadians(omega))
    }

    private fun sunDeclinationDegrees(t: Double): Double {
        val epsilon = Math.toRadians(obliquityCorrection(t))
        val lambda = Math.toRadians(sunApparentLongitude(t))
        return Math.toDegrees(asin(sin(epsilon) * sin(lambda)))
    }

    private fun equationOfTimeMinutes(t: Double): Double {
        val epsilon = Math.toRadians(obliquityCorrection(t))
        val l0 = Math.toRadians(geometricMeanLongitudeSun(t))
        val e = eccentricityEarthOrbit(t)
        val m = Math.toRadians(geometricMeanAnomalySun(t))
        val y = tan(epsilon / 2.0).let { it * it }
        val value = y * sin(2.0 * l0) -
            2.0 * e * sin(m) +
            4.0 * e * y * sin(m) * cos(2.0 * l0) -
            0.5 * y * y * sin(4.0 * l0) -
            1.25 * e * e * sin(2.0 * m)
        return Math.toDegrees(value) * 4.0
    }

    /** The hour angle of the sun at sunrise, or `null` when it never crosses the horizon. */
    private fun hourAngleDegrees(latitude: Double, declination: Double): Double? {
        val phi = Math.toRadians(latitude)
        val delta = Math.toRadians(declination)
        val cosHourAngle = cos(Math.toRadians(SUNRISE_ZENITH_DEGREES)) / (cos(phi) * cos(delta)) -
            tan(phi) * tan(delta)
        if (cosHourAngle < -1.0 || cosHourAngle > 1.0) return null
        return Math.toDegrees(acos(cosHourAngle))
    }
}
