package com.mikotrack.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikotrack.data.prefs.SettingsStore
import com.mikotrack.data.repository.TripRepository
import com.mikotrack.data.repository.WaypointRepository
import com.mikotrack.domain.model.Waypoint
import com.mikotrack.location.RecordingController
import com.mikotrack.ui.map.MapFocusRequest
import com.mikotrack.ui.map.MapFocusRequests
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Drives the gallery, the list of all waypoints with or without photograph. */
class GalleryViewModel(
    private val waypointRepository: WaypointRepository,
    private val tripRepository: TripRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    /** All waypoints of all trips. */
    private val allWaypoints: StateFlow<List<Waypoint>> = waypointRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    /** The waypoints that match the active category filter. */
    val waypoints: StateFlow<List<Waypoint>> = combine(allWaypoints, _categoryFilter) { waypoints, filter ->
        if (filter == null) waypoints else waypoints.filter { it.category == filter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The categories that are present in the gallery. */
    val categories: StateFlow<List<String>> = combine(allWaypoints, _categoryFilter) { waypoints, _ ->
        waypoints.map { it.category }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The names of the trips, indexed by identifier. */
    val tripNames: StateFlow<Map<Long, String>> = tripRepository.observeTrips()
        .let { flow ->
            combine(flow, allWaypoints) { trips, _ ->
                trips.associate { it.id to it.name }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Filters the gallery by category. */
    fun setCategoryFilter(category: String?) {
        _categoryFilter.value = category
    }

    /** Updates the description and the category of a waypoint. */
    fun update(id: Long, description: String, category: String) {
        viewModelScope.launch { waypointRepository.update(id, description, category) }
    }

    /** Deletes a waypoint and its photograph. */
    fun delete(id: Long) {
        deleteAll(setOf(id))
    }

    /**
     * Deletes several waypoints with their photographs and refreshes the photo and
     * waypoint counters of every trip involved.
     */
    fun deleteAll(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val tripIds = mutableSetOf<Long>()
            ids.forEach { id ->
                waypointRepository.get(id)?.let { tripIds += it.tripId }
                waypointRepository.delete(id)
            }
            tripIds.forEach { tripId ->
                val count = waypointRepository.countForTrip(tripId)
                tripRepository.updateCounters(tripId, count, count)
            }
        }
    }

    /**
     * Prepares the map to show [waypoint]: its trip is added to the overlaid routes
     * (unless it is the trip being recorded, which is always drawn) and a focus request
     * is posted. [onReady] runs once the request is in place, to navigate to the map.
     */
    fun showOnMap(waypoint: Waypoint, onReady: () -> Unit) {
        viewModelScope.launch {
            val tripId = waypoint.tripId
            if (tripId != RecordingController.state.value.tripId) {
                val overlaid = settingsStore.overlayTripIds.first()
                if (tripId !in overlaid) settingsStore.setOverlayTripIds(overlaid + tripId)
            }
            MapFocusRequests.request(
                MapFocusRequest(
                    tripId = tripId,
                    waypointId = waypoint.id,
                    latitude = waypoint.latitude,
                    longitude = waypoint.longitude,
                ),
            )
            onReady()
        }
    }
}