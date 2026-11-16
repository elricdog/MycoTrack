package com.mikotrack.core

import java.util.Locale
import java.util.concurrent.TimeUnit

/** Formatting helpers shared by the UI and the exporters. */
object Formatters {

    /** Human readable distance: `1.24 km`, `340 m`. */
    fun distance(meters: Double): String =
        if (meters >= 1000.0) {
            String.format(Locale.US, "%.2f km", meters / 1000.0)
        } else {
            String.format(Locale.US, "%.0f m", meters)
        }

    /** Human readable speed: `4.2 km/h`, `1.1 m/s`. */
    fun speed(metersPerSecond: Double): String =
        if (metersPerSecond >= 1.0) {
            String.format(Locale.US, "%.1f m/s", metersPerSecond)
        } else {
            String.format(Locale.US, "%.1f km/h", metersPerSecond * 3.6)
        }

    /** Human readable duration as `H:MM:SS` (or `M:SS` below one hour). */
    fun duration(millis: Long): String {
        val safe = millis.coerceAtLeast(0)
        val hours = TimeUnit.MILLISECONDS.toHours(safe)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(safe) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /** Compact duration used in lists: `2h 15m`, `45 min`. */
    fun durationShort(millis: Long): String {
        val safe = millis.coerceAtLeast(0)
        val hours = TimeUnit.MILLISECONDS.toHours(safe)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(safe) % 60
        return when {
            hours > 0 -> String.format(Locale.US, "%dh %02dm", hours, minutes)
            minutes > 0 -> String.format(Locale.US, "%d min", minutes)
            else -> String.format(Locale.US, "%d s", TimeUnit.MILLISECONDS.toSeconds(safe))
        }
    }

    /** Metres, rounded to whole units. */
    fun altitude(meters: Double?): String =
        if (meters == null) "--" else String.format(Locale.US, "%.0f m", meters)

    /** Signed elevation delta, e.g. `+120 m`. */
    fun elevationDelta(meters: Double): String =
        String.format(Locale.US, "%+.0f m", meters)

    /** Format a WGS-84 coordinate pair for display. */
    fun coordinates(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lon)

    /** The compass point of a bearing in degrees, e.g. `NE`. */
    fun compassPoint(bearingDegrees: Double): String {
        val points = listOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
        )
        return points[compassIndex(bearingDegrees)]
    }

    /**
     * The index (0 = north, clockwise in steps of 22.5°) of the compass point of a
     * bearing, used to look up the translated abbreviation shown on screen.
     */
    fun compassIndex(bearingDegrees: Double): Int {
        val normalized = ((bearingDegrees % 360.0) + 360.0) % 360.0
        return Math.round(normalized / 22.5).toInt() % 16
    }

    /** A bearing in degrees with the compass point, e.g. `NE · 45°`. */
    fun bearing(bearingDegrees: Double): String =
        String.format(Locale.US, "%s · %.0f°", compassPoint(bearingDegrees), bearingDegrees)
}
