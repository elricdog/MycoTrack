package com.mikotrack.domain

/** How good the current GPS position is, from worst to best. */
enum class GpsQuality { DISABLED, SEARCHING, POOR, FAIR, GOOD, EXCELLENT }

/** Rates the quality of the GPS position from the last fix and the satellites in use. */
object GpsQualityRating {

    /** A fix older than this is considered lost: the receiver is searching again. */
    const val MAX_FIX_AGE_MILLIS = 15_000L

    const val EXCELLENT_ACCURACY_METERS = 5f
    const val GOOD_ACCURACY_METERS = 10f
    const val FAIR_ACCURACY_METERS = 25f

    /** An excellent rating also needs this many satellites, when the count is known. */
    const val EXCELLENT_MIN_SATELLITES = 8

    fun rate(
        locationEnabled: Boolean,
        accuracyMeters: Float?,
        fixAgeMillis: Long?,
        satellitesUsed: Int?,
    ): GpsQuality {
        if (!locationEnabled) return GpsQuality.DISABLED
        if (fixAgeMillis == null || fixAgeMillis > MAX_FIX_AGE_MILLIS) return GpsQuality.SEARCHING
        // A fix without an accuracy estimate cannot be trusted.
        if (accuracyMeters == null || accuracyMeters.isNaN()) return GpsQuality.POOR
        return when {
            accuracyMeters <= EXCELLENT_ACCURACY_METERS ->
                if (satellitesUsed == null || satellitesUsed >= EXCELLENT_MIN_SATELLITES) {
                    GpsQuality.EXCELLENT
                } else {
                    GpsQuality.GOOD
                }
            accuracyMeters <= GOOD_ACCURACY_METERS -> GpsQuality.GOOD
            accuracyMeters <= FAIR_ACCURACY_METERS -> GpsQuality.FAIR
            else -> GpsQuality.POOR
        }
    }
}
