package com.mikotrack.maps

/** A geographic bounding box in WGS-84 coordinates. */
data class BoundingBox(
    val minLatitude: Double,
    val minLongitude: Double,
    val maxLatitude: Double,
    val maxLongitude: Double,
) {
    /** The latitude span of the box. */
    val latitudeSpan: Double get() = maxLatitude - minLatitude

    /** The longitude span of the box. */
    val longitudeSpan: Double get() = maxLongitude - minLongitude

    /** True when the box has no area at all. */
    val isEmpty: Boolean get() = latitudeSpan <= 0.0 || longitudeSpan <= 0.0

    companion object {
        /** Builds a box around a single point with the given margin in degrees. */
        fun around(latitude: Double, longitude: Double, marginDegrees: Double = 0.01): BoundingBox =
            BoundingBox(
                minLatitude = latitude - marginDegrees,
                minLongitude = longitude - marginDegrees,
                maxLatitude = latitude + marginDegrees,
                maxLongitude = longitude + marginDegrees,
            )
    }
}
