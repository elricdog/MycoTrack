package com.mikotrack.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikotrack.data.prefs.SettingsStore
import com.mikotrack.data.repository.TripRepository
import com.mikotrack.data.repository.WaypointRepository
import com.mikotrack.domain.model.ElevationProfile
import com.mikotrack.domain.model.TrackPoint
import com.mikotrack.domain.model.Trip
import com.mikotrack.domain.model.TripStats
import com.mikotrack.domain.model.Waypoint
import com.mikotrack.domain.stats.TripAnalysis
import com.mikotrack.domain.stats.TripAnalyzer
import com.mikotrack.domain.stats.TripRanking
import com.mikotrack.domain.stats.TripStatsCalculator
import com.mikotrack.domain.stats.TripSummary
import com.mikotrack.location.RecordingController
import com.mikotrack.maps.BoundingBox
import com.mikotrack.maps.MapLayer
import com.mikotrack.maps.OfflineMapManager
import com.mikotrack.maps.OfflineSource
import com.mikotrack.ui.map.MapFocusRequest
import com.mikotrack.ui.map.MapFocusRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The state of the route playback. */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val index: Int = 0,
    val speed: Float = 8f,
)

/** The playback speeds the user can choose from. */
val PLAYBACK_SPEEDS = listOf(1f, 8f, 30f, 120f)

/**
 * Drives the review screen of a recorded trip, including the animated playback of the
 * route.
 */
class TripDetailViewModel(
    private val tripRepository: TripRepository,
    private val waypointRepository: WaypointRepository,
    private val settingsStore: SettingsStore,
    private val offlineMapManager: OfflineMapManager,
) : ViewModel() {

    /** The base layer used for the exported map images. */
    val exportLayer: StateFlow<MapLayer> = settingsStore.selectedLayerId
        .map { MapLayer.fromId(it) ?: MapLayer.OSM_STREET }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapLayer.OSM_STREET)

    /** Changes the base layer of the review map and of the exported map images. */
    fun setLayer(layer: MapLayer) {
        viewModelScope.launch { settingsStore.setSelectedLayer(layer.id) }
    }

    /**
     * The offline area that backs the exported map, when the offline layer is used.
     *
     * The exported image is rendered by MapLibre in a snapshotter, which needs the very
     * same URL template that the map on the screen uses. Resolving it opens the
     * MBTiles file, so it is done off the main thread.
     */
    val exportOfflineSource: StateFlow<OfflineSource?> = combine(
        offlineMapManager.observeAreas(),
        settingsStore.activeOfflineLayerId,
    ) { areas, activeId ->
        val area = areas.firstOrNull { it.id == activeId } ?: areas.firstOrNull()
        area?.let { offlineMapManager.sourceFor(it) }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _tripId = MutableStateFlow(0L)

    /** The trip that is being reviewed. */
    val trip: StateFlow<Trip?> = _tripId
        .flatMapLatest { id -> tripRepository.observeTrip(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The GPS samples of the trip. */
    val points: StateFlow<List<TrackPoint>> = _tripId
        .flatMapLatest { id -> tripRepository.observePoints(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The waypoints of the trip. */
    val waypoints: StateFlow<List<Waypoint>> = _tripId
        .flatMapLatest { id -> waypointRepository.observeForTrip(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The elevation profile of the trip. */
    val profile: StateFlow<ElevationProfile> = points
        .map { TripStatsCalculator.buildElevationProfile(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ElevationProfile())

    /** The statistics of the trip. */
    val stats: StateFlow<TripStats> = combine(points, trip) { points, trip ->
        TripStatsCalculator.compute(
            points = points,
            startTime = trip?.startTime ?: 0L,
            endTime = trip?.endTime,
            photoCount = trip?.photoCount ?: 0,
            waypointCount = trip?.waypointCount ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripStats())

    /** The detailed analysis of the outing: movement, terrain, finds and daylight. */
    val analysis: StateFlow<TripAnalysis> = combine(points, waypoints, trip) { points, waypoints, trip ->
        TripAnalyzer.analyze(
            points = points,
            waypoints = waypoints,
            startTime = trip?.startTime,
            endTime = trip?.endTime,
            photoCount = trip?.photoCount,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripAnalysis())

    /** The position of the trip among all the trips of the user. */
    val ranking: StateFlow<TripRanking?> = combine(
        _tripId,
        tripRepository.observeTrips(),
        waypointRepository.observeAll(),
    ) { tripId, trips, allWaypoints ->
        val findsByTrip = allWaypoints.filter(TripAnalyzer::isFind).groupingBy { it.tripId }.eachCount()
        val summaries = trips.map { trip ->
            TripSummary(
                tripId = trip.id,
                distanceMeters = trip.distanceMeters,
                durationMillis = trip.durationMillis,
                finds = findsByTrip[trip.id] ?: 0,
            )
        }
        TripAnalyzer.rank(tripId, summaries)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _playback = MutableStateFlow(PlaybackState())
    val playback: StateFlow<PlaybackState> = _playback.asStateFlow()

    private var playbackJob: Job? = null

    /** Loads a trip into the screen. */
    fun load(tripId: Long) {
        if (_tripId.value == tripId) return
        _tripId.value = tripId
        _playback.value = PlaybackState()
        playbackJob?.cancel()
    }

    /** Starts or pauses the playback. */
    fun togglePlayback() {
        if (_playback.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    /** Pauses the playback. */
    fun pause() {
        playbackJob?.cancel()
        playbackJob = null
        _playback.value = _playback.value.copy(isPlaying = false)
    }

    /** Starts the playback from the current position. */
    fun play() {
        val points = points.value
        if (points.size < 2) return
        playbackJob?.cancel()
        // A finished playback starts again from the beginning of the route.
        val start = _playback.value.index.let { if (it >= points.lastIndex) 0 else it }
        _playback.value = _playback.value.copy(isPlaying = true, index = start)
        playbackJob = viewModelScope.launch {
            var index = _playback.value.index.coerceIn(0, points.lastIndex)
            while (index < points.lastIndex) {
                val current = points[index]
                val next = points[index + 1]
                val deltaMillis = (next.timestamp - current.timestamp).coerceAtLeast(1L)
                val delayMillis = (deltaMillis / _playback.value.speed).toLong().coerceIn(16L, 2_000L)
                delay(delayMillis)
                index += 1
                _playback.value = _playback.value.copy(index = index)
            }
            _playback.value = _playback.value.copy(isPlaying = false)
        }
    }

    /** Changes the playback speed. */
    fun setPlaybackSpeed(speed: Float) {
        _playback.value = _playback.value.copy(speed = speed)
    }

    /** Moves the playback to the given GPS sample, keeping it running when it was playing. */
    fun seekToIndex(index: Int) {
        val lastIndex = points.value.lastIndex
        if (lastIndex < 0) return
        val wasPlaying = _playback.value.isPlaying
        if (wasPlaying) pause()
        _playback.value = _playback.value.copy(index = index.coerceIn(0, lastIndex))
        if (wasPlaying) play()
    }

    /** Moves the playback to the given distance along the track. */
    fun seekToDistance(distance: Double) {
        val profile = profile.value
        val index = profile.indexAtDistance(distance)
        if (index >= 0) {
            _playback.value = _playback.value.copy(index = index)
        }
    }

    /** Updates the personal notes of the trip. */
    fun updateNotes(notes: String) {
        val current = trip.value ?: return
        viewModelScope.launch {
            tripRepository.updateTripDetails(current.id, current.name, notes)
        }
    }

    /** Renames the trip. */
    fun rename(name: String) {
        val current = trip.value ?: return
        viewModelScope.launch {
            tripRepository.updateTripDetails(current.id, name, current.notes)
        }
    }

    /** Sets the colour of the route on the map; `null` goes back to the automatic colour. */
    fun setColor(color: Int?) {
        val current = trip.value ?: return
        viewModelScope.launch { tripRepository.setColor(current.id, color) }
    }

    /**
     * Loads the trip on the main map: overlays its route (unless it is the trip being
     * recorded, which the map already draws), asks the map to frame it and then calls
     * [onReady]. [onEmpty] is called instead when the trip has nothing to show.
     */
    fun showOnMainMap(onReady: () -> Unit, onEmpty: () -> Unit) {
        val tripId = _tripId.value
        if (tripId == 0L) return
        viewModelScope.launch {
            val points = points.value.ifEmpty { tripRepository.getPoints(tripId) }
            val waypoints = waypoints.value.ifEmpty { waypointRepository.getForTrip(tripId) }
            val bounds = tripBounds(
                points.map { it.latitude to it.longitude },
                waypoints.map { it.latitude to it.longitude },
            )
            if (bounds == null) {
                onEmpty()
                return@launch
            }
            if (tripId != RecordingController.state.value.tripId) {
                val overlaid = settingsStore.overlayTripIds.first()
                if (tripId !in overlaid) settingsStore.setOverlayTripIds(overlaid + tripId)
            }
            MapFocusRequests.request(
                MapFocusRequest(
                    tripId = tripId,
                    waypointId = null,
                    latitude = (bounds.minLatitude + bounds.maxLatitude) / 2.0,
                    longitude = (bounds.minLongitude + bounds.maxLongitude) / 2.0,
                    bounds = bounds,
                ),
            )
            onReady()
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
    }
}

/**
 * The box that frames a trip: its track, or its waypoints when it has no track. A single
 * position gets a small box around it; `null` when there is nothing at all.
 */
internal fun tripBounds(
    track: List<Pair<Double, Double>>,
    waypoints: List<Pair<Double, Double>>,
): BoundingBox? {
    val positions = track.ifEmpty { waypoints }
    if (positions.isEmpty()) return null
    val box = BoundingBox(
        minLatitude = positions.minOf { it.first },
        minLongitude = positions.minOf { it.second },
        maxLatitude = positions.maxOf { it.first },
        maxLongitude = positions.maxOf { it.second },
    )
    // A single position or a perfectly straight north-south/east-west line has no area.
    val latitudePad = if (box.latitudeSpan <= 0.0) 0.002 else 0.0
    val longitudePad = if (box.longitudeSpan <= 0.0) 0.002 else 0.0
    return BoundingBox(
        minLatitude = box.minLatitude - latitudePad,
        minLongitude = box.minLongitude - longitudePad,
        maxLatitude = box.maxLatitude + latitudePad,
        maxLongitude = box.maxLongitude + longitudePad,
    )
}
