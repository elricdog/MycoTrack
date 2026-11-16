package com.mikotrack.maps

/** A geographic point used by the map rendering layer. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

/** A previous trip drawn on top of the current one. */
data class RouteOverlay(
    val tripId: Long,
    val name: String,
    val color: Int,
    val points: List<GeoPoint>,
    val waypoints: List<WaypointOverlay> = emptyList(),
    /**
     * Previous trips are drawn with a dark casing and animated dashes ("marching ants")
     * on top of their colour, so they never look like the route being recorded.
     */
    val dashed: Boolean = false,
    /** Whether the name of the route is drawn on the map, next to the middle of the line. */
    val showLabel: Boolean = false,
)

/** A waypoint drawn on the map. */
data class WaypointOverlay(
    val waypointId: Long,
    val tripId: Long,
    val position: GeoPoint,
    val category: String,
    val iconId: String,
    val isCurrentTrip: Boolean,
    /** The photograph of the waypoint, shown as a bubble on the map when available. */
    val imagePath: String? = null,
)
