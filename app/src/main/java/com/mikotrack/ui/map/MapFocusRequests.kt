package com.mikotrack.ui.map

import com.mikotrack.maps.BoundingBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Asks the map screen to show a place: another screen (e.g. the gallery) posts a request
 * and navigates to the map, which loads the route of [tripId] as an overlay, centres the
 * camera on the coordinates and, when [waypointId] is set, opens that waypoint. When
 * [bounds] is set, the camera frames that box (the whole route) instead.
 */
data class MapFocusRequest(
    val tripId: Long,
    val waypointId: Long?,
    val latitude: Double,
    val longitude: Double,
    val bounds: BoundingBox? = null,
)

/** The pending [MapFocusRequest], shared by the screens of the single activity. */
object MapFocusRequests {

    private val _pending = MutableStateFlow<MapFocusRequest?>(null)

    /** The request that the map has not handled yet. */
    val pending: StateFlow<MapFocusRequest?> = _pending.asStateFlow()

    /** Posts a request; it replaces any request that was not handled yet. */
    fun request(request: MapFocusRequest) {
        _pending.value = request
    }

    /** Marks [request] as handled, unless a newer one has been posted meanwhile. */
    fun consume(request: MapFocusRequest) {
        _pending.compareAndSet(request, null)
    }
}
