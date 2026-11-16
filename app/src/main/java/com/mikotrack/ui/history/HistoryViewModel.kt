package com.mikotrack.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikotrack.data.repository.TripRepository
import com.mikotrack.domain.model.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The filters that can be applied to the list of trips. */
data class TripFilters(
    val minimumDistanceMeters: Double = 0.0,
    val minimumDurationMillis: Long = 0L,
    val minimumElevationGain: Double = 0.0,
    val onlyWithPhotos: Boolean = false,
    val fromTimestamp: Long? = null,
    val toTimestamp: Long? = null,
) {
    val isActive: Boolean
        get() = minimumDistanceMeters > 0 ||
            minimumDurationMillis > 0 ||
            minimumElevationGain > 0 ||
            onlyWithPhotos ||
            fromTimestamp != null ||
            toTimestamp != null
}

/** Drives the trip history list. */
class HistoryViewModel(
    private val tripRepository: TripRepository,
) : ViewModel() {

    /** All recorded trips. */
    val trips: StateFlow<List<Trip>> = tripRepository.observeTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filters = MutableStateFlow(TripFilters())
    val filters: StateFlow<TripFilters> = _filters.asStateFlow()

    /** The trips that match the current search and filters. */
    val visibleTrips: StateFlow<List<Trip>> = combine(
        trips,
        _searchQuery,
        _filters,
    ) { trips, query, filters ->
        trips.filter { trip -> matches(trip, query, filters) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Updates the search term. */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** Updates the active filters. */
    fun setFilters(filters: TripFilters) {
        _filters.value = filters
    }

    /** Renames a trip. */
    fun rename(tripId: Long, name: String) {
        viewModelScope.launch {
            val trip = tripRepository.getTrip(tripId) ?: return@launch
            tripRepository.updateTripDetails(tripId, name, trip.notes)
        }
    }

    /** Updates the personal notes of a trip. */
    fun updateNotes(tripId: Long, notes: String) {
        viewModelScope.launch {
            val trip = tripRepository.getTrip(tripId) ?: return@launch
            tripRepository.updateTripDetails(tripId, trip.name, notes)
        }
    }

    /** Sets the colour of the route on the map; `null` goes back to the automatic colour. */
    fun setColor(tripId: Long, color: Int?) {
        viewModelScope.launch { tripRepository.setColor(tripId, color) }
    }

    /** Deletes a trip together with its photographs. */
    fun delete(tripId: Long) {
        viewModelScope.launch { tripRepository.deleteTrip(tripId) }
    }

    private fun matches(trip: Trip, query: String, filters: TripFilters): Boolean {
        if (query.isNotBlank()) {
            val needle = query.trim().lowercase()
            val inName = trip.name.lowercase().contains(needle)
            val inNotes = trip.notes.lowercase().contains(needle)
            if (!inName && !inNotes) return false
        }
        if (trip.distanceMeters < filters.minimumDistanceMeters) return false
        if (trip.durationMillis < filters.minimumDurationMillis) return false
        if (trip.elevationGain < filters.minimumElevationGain) return false
        if (filters.onlyWithPhotos && trip.photoCount == 0) return false
        filters.fromTimestamp?.let { if (trip.startTime < it) return false }
        filters.toTimestamp?.let { if (trip.startTime > it) return false }
        return true
    }
}
