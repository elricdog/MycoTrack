package com.mikotrack.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikotrack.data.prefs.SettingsStore
import com.mikotrack.location.LocationTracker
import com.mikotrack.maps.GeoPoint
import com.mikotrack.maps.HotspotRequest
import com.mikotrack.maps.HotspotRequests
import com.mikotrack.maps.HotspotSearches
import com.mikotrack.maps.SuitabilitySearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A pending request to frame the circle of a new likely areas search, posted by the
 * mushroom guide and consumed by the map screen once it is shown.
 */
object SuitabilityFocusRequests {
    private val _pending = MutableStateFlow<SuitabilitySearch?>(null)
    val pending: StateFlow<SuitabilitySearch?> = _pending.asStateFlow()

    fun post(search: SuitabilitySearch) {
        _pending.value = search
    }

    /** Clears the request, unless a newer one arrived meanwhile. */
    fun consume(search: SuitabilitySearch) {
        _pending.compareAndSet(search, null)
    }
}

/** Starts a search of the likely areas of a species around the user. */
class SuitabilitySearchViewModel(
    private val settingsStore: SettingsStore,
    private val locationTracker: LocationTracker,
) : ViewModel() {

    /**
     * Stores the search and enables its layer. It is centred on [mapCenter] (the centre of
     * the map) when [aroundMapCenter] is set; otherwise on the last known position of the
     * user or, without one, on [mapCenter].
     *
     * @param onDone called on the main thread with `true` once the search is stored and
     * the map can be shown, or `false` when there is no centre to search around.
     */
    fun start(
        speciesId: String,
        speciesName: String,
        radiusKm: Int,
        aroundMapCenter: Boolean,
        mapCenter: GeoPoint?,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val center = if (aroundMapCenter) {
                mapCenter
            } else {
                withContext(Dispatchers.IO) {
                    runCatching { locationTracker.lastKnownLocation() }.getOrNull()
                        ?.let { GeoPoint(it.latitude, it.longitude) }
                } ?: mapCenter
            }
            if (center == null) {
                onDone(false)
                return@launch
            }
            val search = SuitabilitySearch(
                speciesId = speciesId,
                speciesName = speciesName,
                latitude = center.latitude,
                longitude = center.longitude,
                radiusMeters = radiusKm * 1000,
            )
            settingsStore.startSuitabilitySearch(search)
            SuitabilityFocusRequests.post(search)
            HotspotSearches.clear()
            HotspotRequests.clear()
            onDone(true)
        }
    }

    /**
     * Stores a multi-species search of the likely areas of every selected species at
     * once and, when [findHotspots] is set, asks for the three places of the circle
     * where more species of the selection could be found. The centre is chosen like in
     * [start].
     *
     * @param onDone called on the main thread with `true` once the search is stored and
     * the map can be shown, or `false` when there is no centre to search around.
     */
    fun startMulti(
        species: List<Pair<String, String>>,
        radiusKm: Int,
        aroundMapCenter: Boolean,
        mapCenter: GeoPoint?,
        findHotspots: Boolean,
        language: String,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val center = if (aroundMapCenter) {
                mapCenter
            } else {
                withContext(Dispatchers.IO) {
                    runCatching { locationTracker.lastKnownLocation() }.getOrNull()
                        ?.let { GeoPoint(it.latitude, it.longitude) }
                } ?: mapCenter
            }
            if (center == null) {
                onDone(false)
                return@launch
            }
            val search = SuitabilitySearch(
                speciesId = species.joinToString(",") { it.first },
                speciesName = species.joinToString(", ") { it.second },
                latitude = center.latitude,
                longitude = center.longitude,
                radiusMeters = radiusKm * 1000,
            )
            settingsStore.startSuitabilitySearch(search)
            SuitabilityFocusRequests.post(search)
            HotspotSearches.clear()
            HotspotRequests.clear()
            if (findHotspots) HotspotRequests.post(HotspotRequest(search, language))
            onDone(true)
        }
    }
}
