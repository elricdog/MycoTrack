package com.mikotrack.ui.map

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikotrack.R
import com.mikotrack.data.files.ImageStore
import com.mikotrack.data.prefs.DEFAULT_PROXIMITY_RADIUS
import com.mikotrack.data.prefs.DEFAULT_ZOOM_LEVEL
import com.mikotrack.data.prefs.SettingsStore
import com.mikotrack.data.repository.CategoryRepository
import com.mikotrack.data.repository.TripRepository
import com.mikotrack.data.repository.WaypointRepository
import com.mikotrack.domain.WaypointDistance
import com.mikotrack.domain.WaypointDistanceCalculator
import com.mikotrack.domain.model.ElevationProfile
import com.mikotrack.domain.model.TrackPoint
import com.mikotrack.domain.model.Trip
import com.mikotrack.domain.model.Waypoint
import com.mikotrack.domain.stats.TripStatsCalculator
import com.mikotrack.location.CurrentPosition
import com.mikotrack.location.GpsTrackSmoother
import com.mikotrack.location.LocationTracker
import com.mikotrack.location.ProximityAlertManager
import com.mikotrack.domain.ProximityHit
import com.mikotrack.location.RecordingController
import com.mikotrack.location.RecordingService
import com.mikotrack.location.toCurrentPosition
import com.mikotrack.location.toRawFix
import com.mikotrack.maps.GeoPoint
import com.mikotrack.maps.MapIconFactory
import com.mikotrack.maps.MapLayer
import com.mikotrack.maps.OfflineMapManager
import com.mikotrack.maps.OfflineSource
import com.mikotrack.maps.RouteOverlay
import com.mikotrack.maps.WaypointOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How the map screen presents the map and the elevation profile. */
enum class MapViewMode { MAP, ELEVATION, COMBINED }

/** The colours used to distinguish the overlaid routes. */
val OverlayPalette = listOf(
    0xFF2F7A3A.toInt(),
    0xFF42A5F5.toInt(),
    0xFFFFB300.toInt(),
    0xFFAB47BC.toInt(),
    0xFFE53935.toInt(),
    0xFF00ACC1.toInt(),
    0xFF8D6E63.toInt(),
    0xFF7E57C2.toInt(),
)

/**
 * Drives the main map screen: the recording, the base layer, the overlaid routes and the
 * geotagged photographs.
 */
class MapViewModel(
    private val tripRepository: TripRepository,
    private val waypointRepository: WaypointRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsStore: SettingsStore,
    private val offlineMapManager: OfflineMapManager,
    private val imageStore: ImageStore,
    private val locationTracker: LocationTracker,
    private val proximityAlertManager: ProximityAlertManager,
    private val gnssMonitor: com.mikotrack.location.GnssMonitor,
) : ViewModel() {

    /**
     * The satellites seen by the receiver while the map tracks the position. The callback
     * of the receiver only reports them while a location request is active, which is the
     * case whenever the map is on screen.
     */
    val gnssSnapshot: StateFlow<com.mikotrack.location.GnssSnapshot?> by lazy {
        _passiveTrackingEnabled
            .flatMapLatest { enabled -> if (enabled) gnssMonitor.status() else flowOf(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    /**
     * The quality of the GPS signal shown on the map. It is refreshed every few seconds,
     * so a fix that stops arriving turns into "searching".
     */
    val gpsQuality: StateFlow<com.mikotrack.domain.GpsQuality> by lazy {
        val ticker = kotlinx.coroutines.flow.flow {
            while (true) {
                emit(Unit)
                kotlinx.coroutines.delay(GPS_QUALITY_REFRESH_MILLIS)
            }
        }
        combine(currentPosition, gnssSnapshot, ticker) { position, gnss, _ ->
            com.mikotrack.domain.GpsQualityRating.rate(
                locationEnabled = locationTracker.isLocationEnabled(),
                accuracyMeters = position?.accuracy,
                fixAgeMillis = position?.let { System.currentTimeMillis() - it.timestamp },
                satellitesUsed = gnss?.takeIf { it.started && it.satellites.isNotEmpty() }?.usedCount,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.mikotrack.domain.GpsQuality.SEARCHING)
    }

    /** The live state of the recording. */
    val recordingState: StateFlow<com.mikotrack.location.RecordingState> = RecordingController.state

    /**
     * The position of the user while no trip is being recorded.
     *
     * The map has to show the position of the user from the moment the application is
     * opened, not only once the recording has started, so the fixes are tracked here as
     * well. The flow is only collected while the map is on screen, which keeps the GPS
     * from running in the background without a reason.
     */
    private val passivePosition = MutableStateFlow<CurrentPosition?>(null)

    /**
     * Whether the position of the user is tracked without recording a trip.
     *
     * The map screen turns this on while it is visible and off again when it is not, so
     * that the GPS is not kept running in the background for nothing.
     */
    private val _passiveTrackingEnabled = MutableStateFlow(false)

    /**
     * The position of the user, no matter whether a trip is being recorded or not.
     */
    val currentPosition: StateFlow<CurrentPosition?> = combine(
        recordingState,
        passivePosition,
    ) { recording, passive ->
        // A position left over by a finished recording is never used: only the live one.
        if (recording.isRecording) recording.position ?: passive else passive
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The base layer currently displayed on the map. */
    val selectedLayer: StateFlow<MapLayer> = settingsStore.selectedLayerId
        .map { MapLayer.fromId(it) ?: MapLayer.OSM_STREET }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapLayer.OSM_STREET)

    /** All recorded trips, used by the overlay picker. */
    val trips: StateFlow<List<Trip>> = tripRepository.observeTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The trips that can be overlaid: every trip except the one being recorded, which is
     * already drawn as the current route.
     */
    val overlayableTrips: StateFlow<List<Trip>> = combine(
        trips,
        RecordingController.state.map { it.tripId },
    ) { all, recordingId -> all.filter { it.id != recordingId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether the proximity alarm of the overlaid waypoints is enabled. */
    val proximityAlarmEnabled: StateFlow<Boolean> = settingsStore.proximityAlarmEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** The distance to a waypoint, in metres, at which the proximity alarm sounds. */
    val proximityRadius: StateFlow<Int> = settingsStore.proximityRadiusMeters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_PROXIMITY_RADIUS)

    /** The last waypoint of a previous trip that the user reached, shown as a banner. */
    private val _proximityHit = MutableStateFlow<ProximityHit?>(null)
    val proximityHit: StateFlow<ProximityHit?> = _proximityHit.asStateFlow()

    fun setProximityAlarmEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setProximityAlarmEnabled(enabled) }
    }

    fun setProximityRadius(meters: Int) {
        viewModelScope.launch { settingsStore.setProximityRadius(meters) }
    }

    /** Hides the banner of the last reached waypoint. */
    fun dismissProximityHit() {
        _proximityHit.value = null
    }

    /** Opens the details of the last reached waypoint, with its distance and direction. */
    fun showProximityHit() {
        val hit = _proximityHit.value ?: return
        _proximityHit.value = null
        selectWaypointById(hit.target.waypointId)
    }

    /** The identifiers of the trips that are overlaid on the map. */
    val overlayTripIds: StateFlow<List<Long>> = settingsStore.overlayTripIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The waypoint categories, built in and custom ones. */
    val categories = categoryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The GPS samples of the trip that is currently being recorded. */
    val activePoints: StateFlow<List<TrackPoint>> = recordingState
        .map { it.tripId }
        .flatMapLatest { tripId ->
            if (tripId == null) flowOf(emptyList()) else tripRepository.observePoints(tripId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The waypoints of the trip that is currently being recorded. */
    val activeWaypoints: StateFlow<List<Waypoint>> = recordingState
        .map { it.tripId }
        .flatMapLatest { tripId ->
            if (tripId == null) flowOf(emptyList()) else waypointRepository.observeForTrip(tripId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The elevation profile of the trip that is being recorded. */
    val elevationProfile: StateFlow<ElevationProfile> = activePoints
        .map { TripStatsCalculator.buildElevationProfile(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ElevationProfile(),
        )

    /** The historical routes that are drawn on top of the map. */
    private val _overlayRoutes = MutableStateFlow<List<RouteOverlay>>(emptyList())
    val overlayRoutes: StateFlow<List<RouteOverlay>> = _overlayRoutes.asStateFlow()

    /** A trip that was interrupted and can be recovered. */
    private val _recoverableTrip = MutableStateFlow<Trip?>(null)
    val recoverableTrip: StateFlow<Trip?> = _recoverableTrip.asStateFlow()

    /** The waypoint that is currently being inspected or edited. */
    private val _selectedWaypoint = MutableStateFlow<Waypoint?>(null)
    val selectedWaypoint: StateFlow<Waypoint?> = _selectedWaypoint.asStateFlow()

    /** The track of every overlaid route, used to measure the distance along a route. */
    private val _overlayTracks = MutableStateFlow<Map<Long, List<TrackPoint>>>(emptyMap())

    /** The name of the trip every overlaid route belongs to. */
    private val _overlayNames = MutableStateFlow<Map<Long, String>>(emptyMap())

    /**
     * How far the selected waypoint is from the position of the user.
     *
     * The route of the waypoint is used to report the distance along the track, which
     * tells how far the user walked when the photograph was taken. It works the same for
     * the trip that is being recorded and for the overlaid previous ones.
     */
    val selectedWaypointDistance: StateFlow<WaypointDistance?> = combine(
        selectedWaypoint,
        currentPosition,
        activePoints,
        _overlayTracks,
        recordingState.map { it.tripId },
    ) { waypoint, position, activeTrack, overlayTracks, recordingTripId ->
        if (waypoint == null || position == null) return@combine null
        val track = if (waypoint.tripId == recordingTripId) {
            activeTrack
        } else {
            overlayTracks[waypoint.tripId].orEmpty()
        }
        WaypointDistanceCalculator.compute(
            waypoint = waypoint,
            currentLatitude = position.latitude,
            currentLongitude = position.longitude,
            currentAltitude = position.altitude,
            trackPoints = track,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The name of the trip the selected waypoint belongs to, when it is a previous one. */
    val selectedWaypointTripName: StateFlow<String?> = combine(
        selectedWaypoint,
        _overlayNames,
        recordingState.map { it.tripId },
    ) { waypoint, names, recordingTripId ->
        val tripId = waypoint?.tripId ?: return@combine null
        if (tripId == recordingTripId) null else names[tripId]
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The distance along the track that the user selected in the elevation profile. */
    private val _selectedDistance = MutableStateFlow<Double?>(null)
    val selectedDistance: StateFlow<Double?> = _selectedDistance.asStateFlow()

    /** How the map screen is laid out. */
    private val _viewMode = MutableStateFlow(MapViewMode.MAP)
    val viewMode: StateFlow<MapViewMode> = _viewMode.asStateFlow()

    /** Whether the camera keeps following the user. */
    private val _followPosition = MutableStateFlow(true)
    val followPosition: StateFlow<Boolean> = _followPosition.asStateFlow()

    /** The offline areas that can be used as a base map. */
    val offlineAreas = offlineMapManager.observeAreas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether the screen should stay on while recording. */
    val keepScreenOn: StateFlow<Boolean> = settingsStore.keepScreenOn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** The zoom level the camera uses by default. */
    val defaultZoom: StateFlow<Float> = settingsStore.defaultZoom
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_ZOOM_LEVEL)

    /** Stores the zoom level the camera should use by default. */
    fun setDefaultZoom(zoom: Float) {
        viewModelScope.launch { settingsStore.setDefaultZoom(zoom) }
    }

    /** Whether the photographs are shown as bubbles on the map. */
    val showPhotoBubbles: StateFlow<Boolean> = settingsStore.showPhotoBubbles
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Shows or hides the photograph bubbles. */
    fun setShowPhotoBubbles(visible: Boolean) {
        viewModelScope.launch { settingsStore.setShowPhotoBubbles(visible) }
    }

    /** The optional buttons of the map screen that the user wants to see. */
    val visibleButtons: StateFlow<Set<MapButton>> = settingsStore.hiddenMapButtons
        .map { MapButton.visible(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MapButton.entries.toSet())

    /**
     * The timestamp of the downloaded rainfall lattice, which busts the tile URLs when
     * new data arrives; `0` while there is none.
     */
    private val rainVersion = MutableStateFlow(0L)

    /**
     * The habitat overlays (slope orientation, tree species, soil, rainfall and the
     * likely areas of a species) the user switched
     * on, with the classes picked in the legend. The URLs of the orientation and soil
     * tiles start the local tile server, so they are resolved off the main thread.
     */
    val habitatLayers: StateFlow<com.mikotrack.maps.HabitatLayers> = combine(
        combine(
            settingsStore.habitatAspectLayer,
            settingsStore.habitatTreesLayer,
            settingsStore.habitatSoilLayer,
        ) { aspect, trees, soil -> Triple(aspect, trees, soil) },
        combine(
            settingsStore.habitatAspectClasses,
            settingsStore.habitatHiddenTrees,
            settingsStore.habitatHiddenSoil,
        ) { classes, hiddenTrees, hiddenSoil -> Triple(classes, hiddenTrees, hiddenSoil) },
        combine(settingsStore.suitabilityLayer, settingsStore.suitabilitySearch) { enabled, search -> enabled to search },
        combine(settingsStore.habitatRainLayer, settingsStore.habitatRainWindow) { rain, window -> rain to window },
        rainVersion,
    ) { (aspect, trees, soil), (classes, hiddenTrees, hiddenSoil), (suitability, search), (rain, rainWindow), rainVersion ->
        val showSuitability = suitability && search != null
        // The likely areas are served next to the orientation tiles, by the same server.
        val aspectUrl = if (aspect || showSuitability) offlineMapManager.aspectTileUrl() else null
        com.mikotrack.maps.HabitatLayers(
            aspect = aspect,
            trees = trees,
            soil = soil,
            aspectClasses = com.mikotrack.maps.AspectHatch.decode(classes),
            hiddenTrees = hiddenTrees,
            hiddenSoil = hiddenSoil,
            aspectUrl = if (aspect) aspectUrl else null,
            soilUrl = if (soil) offlineMapManager.soilTileUrl() else null,
            suitability = suitability,
            suitabilitySearch = search,
            suitabilityUrl = if (showSuitability) {
                aspectUrl?.let(com.mikotrack.maps.LocalTileServer::suitabilityUrlFor)
            } else {
                null
            },
            rain = rain,
            rainWindow = rainWindow,
            rainUrl = if (rain) offlineMapManager.rainTileUrl() else null,
            rainVersion = rainVersion,
        )
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.mikotrack.maps.HabitatLayers())

    /** Shows or hides the rainfall habitat overlay, downloading the lattice on demand. */
    fun setHabitatRainLayer(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setHabitatRainLayer(enabled) }
    }

    /** The length of the rainfall accumulation window, in days (3, 7, 15 or 30). */
    fun setHabitatRainWindow(days: Int) {
        viewModelScope.launch { settingsStore.setHabitatRainWindow(days) }
    }

    /** Shows or hides the likely areas of the species chosen in the mushroom guide. */
    fun setSuitabilityLayer(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setSuitabilityLayer(enabled) }
    }

    /** Forgets the likely areas search and hides its layer. */
    fun clearSuitabilitySearch() {
        viewModelScope.launch { settingsStore.clearSuitabilitySearch() }
    }

    /** Shows or hides the orientation habitat overlay. */
    fun setHabitatAspectLayer(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setHabitatAspectLayer(enabled) }
    }

    /** Shows or hides the tree species habitat overlay. */
    fun setHabitatTreesLayer(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setHabitatTreesLayer(enabled) }
    }

    /** Shows or hides the soil habitat overlay. */
    fun setHabitatSoilLayer(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setHabitatSoilLayer(enabled) }
    }

    /** The orientation classes drawn by the habitat overlay. */
    fun setHabitatAspectClasses(classes: Set<com.mikotrack.maps.AspectClass>) {
        viewModelScope.launch { settingsStore.setHabitatAspectClasses(com.mikotrack.maps.AspectHatch.encode(classes)) }
    }

    /** The tree formations hidden in the habitat overlay. */
    fun setHabitatHiddenTrees(keys: Set<String>) {
        viewModelScope.launch { settingsStore.setHabitatHiddenTrees(keys) }
    }

    /** The lithologies hidden in the soil overlay. */
    fun setHabitatHiddenSoil(keys: Set<String>) {
        viewModelScope.launch { settingsStore.setHabitatHiddenSoil(keys) }
    }

    /**
     * Selects a waypoint tapped on the map. It can belong to the trip being recorded or
     * to one of the overlaid routes, so it is read from the database when needed.
     */
    fun selectWaypointById(id: Long) {
        activeWaypoints.value.firstOrNull { it.id == id }?.let {
            _selectedWaypoint.value = it
            return
        }
        viewModelScope.launch {
            _selectedWaypoint.value = waypointRepository.get(id)
        }
    }

    /**
     * The identifier of the offline area that is used as a base map.
     *
     * The value is mirrored in a state flow of its own because the settings are read
     * from a file: the combination with the list of areas must never wait for that
     * read to happen, otherwise the map would have no base map at all.
     */
    private val activeOfflineAreaId = MutableStateFlow<Long?>(null)

    /**
     * The offline area that is used as a base map, or `null` when no offline area is
     * available.
     */
    val offlineSource: StateFlow<OfflineSource?> = combine(
        offlineAreas,
        activeOfflineAreaId,
    ) { areas, activeId ->
        val area = areas.firstOrNull { it.id == activeId } ?: areas.firstOrNull()
        if (area == null) {
            Log.i(TAG, "No offline area is available")
            null
        } else {
            // Opening the MBTiles file and starting the local server must not happen
            // on the main thread: the packs are more than a hundred megabytes.
            val source = offlineMapManager.sourceFor(area)
            Log.i(TAG, "Offline area '${area.name}' is served as $source")
            source
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            // The offline area that the user chose in the settings is mirrored into a
            // state flow so that the map always has a base map, even before the
            // settings have been read.
            settingsStore.activeOfflineLayerId.collect { id ->
                activeOfflineAreaId.value = id
            }
        }
        viewModelScope.launch {
            // The overlays are also reloaded when the name or the colour of an overlaid
            // trip changes in the history.
            combine(
                settingsStore.overlayTripIds,
                RecordingController.state.map { it.tripId }.distinctUntilChanged(),
                tripRepository.observeTrips(),
            ) { ids, recordingId, allTrips ->
                val visible = ids.filter { it != recordingId }
                val byId = allTrips.associateBy { it.id }
                visible to visible.map { id -> byId[id]?.let { Triple(it.id, it.name, it.color) } }
            }
                .distinctUntilChanged()
                .collectLatest { (ids, _) -> loadOverlays(ids) }
        }
        viewModelScope.launch {
            proximityAlertManager.alerts.collect { hit -> _proximityHit.value = hit }
        }
        viewModelScope.launch {
            // A trip that was left open by a previous session can be recovered.
            val active = tripRepository.getActiveTrip()
            if (active != null && recordingState.value.tripId == null) {
                _recoverableTrip.value = active
            }
        }
        viewModelScope.launch {
            // The maps that ship with the application are copied to the private
            // storage and registered before anything else is displayed.
            offlineMapManager.seedBundledAreas()
            offlineMapManager.registerStoredAreas()
        }
        viewModelScope.launch {
            // The rainfall lattice is downloaded when the overlay is switched on, and
            // on the next start when it was left on. The version of the lattice is
            // published so that the tile URLs of the overlay change with new data.
            settingsStore.habitatRainLayer
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    if (enabled) {
                        runCatching { offlineMapManager.refreshRainData() }
                        rainVersion.value = runCatching { offlineMapManager.rainVersion() }.getOrDefault(0L)
                    }
                }
        }
        viewModelScope.launch {
            // The position of the user is tracked while the map is on screen, even when
            // no trip is being recorded, so that the map can always show where the user
            // is. The recording service takes over as soon as a trip starts. The fixes
            // go through the same smoothing as the recording, so the marker does not
            // jitter around while the user stands still.
            _passiveTrackingEnabled
                .flatMapLatest { enabled ->
                    if (!enabled) {
                        flowOf()
                    } else {
                        combine(
                            settingsStore.maxAccuracyMeters,
                            settingsStore.maxJumpMeters,
                        ) { accuracy, jump -> GpsTrackSmoother(accuracy.toDouble(), jump.toDouble()) }
                            .flatMapLatest { smoother ->
                                locationTracker
                                    .locationUpdates(GpsTrackSmoother.SAMPLING_INTERVAL_MILLIS)
                                    .mapNotNull { location ->
                                        val raw = location.toRawFix()
                                        smoother.process(raw)?.toCurrentPosition()
                                            ?: raw.toCurrentPosition().takeIf { !smoother.isInitialized }
                                    }
                            }
                    }
                }
                .collect { position ->
                    passivePosition.value = position
                    proximityAlertManager.onPassivePosition(position)
                }
        }
    }

    /**
     * Starts or stops tracking the position of the user without recording a trip.
     *
     * The map screen calls this when it becomes visible and when it is hidden.
     */
    fun setPassiveTracking(enabled: Boolean) {
        // A position from before the screen was left behind may be far away by now
        // (after a drive, for instance), so it is not shown again once it is stale.
        val last = passivePosition.value
        if (enabled && last != null && System.currentTimeMillis() - last.timestamp > STALE_POSITION_MILLIS) {
            passivePosition.value = null
        }
        _passiveTrackingEnabled.value = enabled
        // A position that is no longer refreshed must not trigger the proximity alarm.
        if (!enabled) proximityAlertManager.onPassivePosition(null)
    }

    override fun onCleared() {
        proximityAlertManager.onPassivePosition(null)
        super.onCleared()
    }

    private suspend fun loadOverlays(ids: List<Long>) {
        val tracks = mutableMapOf<Long, List<TrackPoint>>()
        val names = mutableMapOf<Long, String>()
        val overlays = ids.mapIndexedNotNull { index, id ->
            val trip = tripRepository.getTrip(id) ?: return@mapIndexedNotNull null
            val points = tripRepository.getPoints(id)
            tracks[id] = points
            names[id] = trip.name
            val waypoints = waypointRepository.getForTrip(id).map { waypoint ->
                WaypointOverlay(
                    waypointId = waypoint.id,
                    tripId = waypoint.tripId,
                    position = GeoPoint(waypoint.latitude, waypoint.longitude),
                    category = waypoint.category,
                    iconId = MapIconFactory.waypointIconId(waypoint.category),
                    isCurrentTrip = false,
                    imagePath = waypoint.imagePath,
                )
            }
            RouteOverlay(
                tripId = id,
                name = trip.name,
                // The colour chosen in the history wins over the automatic one.
                color = trip.color ?: OverlayPalette[index % OverlayPalette.size],
                points = points.map { GeoPoint(it.latitude, it.longitude) },
                waypoints = waypoints,
                dashed = true,
                showLabel = true,
            )
        }
        _overlayTracks.value = tracks
        _overlayNames.value = names
        _overlayRoutes.value = overlays
    }

    /** Starts a new trip and hands the recording over to the foreground service. */
    fun startTrip(context: Context) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val name = context.getString(
                R.string.trip_default_name,
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(now)),
            )
            val tripId = tripRepository.startTrip(name, now)
            RecordingService.start(context, tripId)
        }
    }

    /** Stops the recording and stores the final statistics. */
    fun stopTrip(context: Context) {
        val tripId = recordingState.value.tripId
        RecordingService.stop(context)
        if (tripId != null) {
            viewModelScope.launch {
                tripRepository.finishTrip(tripId, System.currentTimeMillis())
                tripRepository.refreshStatistics(tripId)
            }
        }
    }

    /** Resumes a trip that was interrupted by an unexpected shutdown. */
    fun resumeTrip(context: Context) {
        val trip = _recoverableTrip.value ?: return
        _recoverableTrip.value = null
        RecordingService.start(context, trip.id)
    }

    /** Abandons a trip that cannot be recovered. */
    fun discardRecoverableTrip() {
        val trip = _recoverableTrip.value ?: return
        _recoverableTrip.value = null
        viewModelScope.launch {
            tripRepository.finishTrip(trip.id, System.currentTimeMillis())
        }
    }

    /** Changes the base layer of the map. */
    fun selectLayer(layer: MapLayer) {
        viewModelScope.launch { settingsStore.setSelectedLayer(layer.id) }
    }

    /** Enables or disables the overlay of a previous trip. */
    fun toggleOverlay(tripId: Long) {
        viewModelScope.launch {
            val current = settingsStore.overlayTripIds.first()
            val updated = if (tripId in current) current - tripId else current + tripId
            settingsStore.setOverlayTripIds(updated)
        }
    }

    /** Hides every overlaid trip at once. */
    fun clearOverlays() {
        viewModelScope.launch { settingsStore.setOverlayTripIds(emptyList()) }
    }

    /**
     * Makes sure a previous trip is overlaid on the map. The trip being recorded is
     * already drawn as the current route, so it is never added.
     */
    fun ensureOverlay(tripId: Long) {
        if (tripId == recordingState.value.tripId) return
        viewModelScope.launch {
            val current = settingsStore.overlayTripIds.first()
            if (tripId !in current) settingsStore.setOverlayTripIds(current + tripId)
        }
    }

    /**
     * Adds a waypoint at the given coordinates (a point the user long-pressed on the map)
     * to the trip [tripId]. The altitude is unknown. A previous trip that is not overlaid
     * yet is overlaid, so the new waypoint can be seen.
     */
    fun addWaypointAt(
        tripId: Long,
        latitude: Double,
        longitude: Double,
        description: String,
        category: String,
    ) {
        viewModelScope.launch {
            waypointRepository.add(
                tripId = tripId,
                latitude = latitude,
                longitude = longitude,
                altitude = null,
                timestamp = System.currentTimeMillis(),
                description = description,
                category = category,
                imagePath = null,
            )
            updateCounters(tripId)
            if (tripId == recordingState.value.tripId) return@launch
            val overlaid = settingsStore.overlayTripIds.first()
            if (tripId in overlaid) {
                loadOverlays(overlaid.filter { it != recordingState.value.tripId })
            } else {
                settingsStore.setOverlayTripIds(overlaid + tripId)
            }
        }
    }

    /** Switches the layout of the map screen. */
    fun setViewMode(mode: MapViewMode) {
        _viewMode.value = mode
    }

    /** Enables or disables the "follow my position" behaviour. */
    fun setFollowPosition(enabled: Boolean) {
        _followPosition.value = enabled
    }

    /** Selects a point of the elevation profile. */
    fun selectDistance(distance: Double) {
        _selectedDistance.value = distance
    }

    /** Selects a waypoint so that its details can be shown. */
    fun selectWaypoint(waypoint: Waypoint?) {
        _selectedWaypoint.value = waypoint
    }

    /** Adds a waypoint at the current position. */
    fun addWaypointAtCurrentPosition(description: String, category: String) {
        val state = recordingState.value
        val tripId = state.tripId ?: return
        val position = state.position
        viewModelScope.launch {
            waypointRepository.add(
                tripId = tripId,
                latitude = position?.latitude ?: return@launch,
                longitude = position?.longitude ?: return@launch,
                altitude = position?.altitude,
                timestamp = System.currentTimeMillis(),
                description = description,
                category = category,
                imagePath = null,
            )
            updateCounters(tripId)
        }
    }

    /**
     * Attaches a photograph to the current position.
     *
     * The coordinates stored in the EXIF data of the photo take precedence over the current
     * position, because a photograph is usually taken a few metres away from the GPS fix.
     */
    fun addPhotoWaypoint(uri: Uri, description: String, category: String) {
        val state = recordingState.value
        val tripId = state.tripId ?: return
        viewModelScope.launch {
            val imported = imageStore.importFromUri(tripId, uri)
            val exif = imageStore.readExifLocation(imported?.absolutePath)
            val position = state.position
            val latitude = exif?.first ?: position?.latitude ?: return@launch
            val longitude = exif?.second ?: position?.longitude ?: return@launch

            waypointRepository.add(
                tripId = tripId,
                latitude = latitude,
                longitude = longitude,
                altitude = position?.altitude,
                timestamp = System.currentTimeMillis(),
                description = description,
                category = category,
                imagePath = imported?.absolutePath,
            )
            updateCounters(tripId)
        }
    }

    /** Creates the file that receives the next photograph of the current trip. */
    fun createPhotoFile(): File? {
        val tripId = recordingState.value.tripId ?: return null
        return imageStore.createImageFile(tripId)
    }

    /** Adds a photograph taken with the in-application camera as a waypoint. */
    fun addPhotoWaypoint(file: File, description: String, category: String) {
        val state = recordingState.value
        val tripId = state.tripId ?: return
        viewModelScope.launch {
            val position = state.position ?: return@launch
            waypointRepository.add(
                tripId = tripId,
                latitude = position.latitude,
                longitude = position.longitude,
                altitude = position.altitude,
                timestamp = System.currentTimeMillis(),
                description = description,
                category = category,
                imagePath = file.absolutePath,
            )
            updateCounters(tripId)
        }
    }

    /** Updates the editable metadata of a waypoint. */
    fun updateWaypoint(id: Long, description: String, category: String) {
        viewModelScope.launch {
            val tripId = waypointRepository.get(id)?.tripId
            waypointRepository.update(id, description, category)
            refreshAfterWaypointChange(tripId)
        }
    }

    /** Deletes a waypoint together with its photograph. */
    fun deleteWaypoint(id: Long) {
        viewModelScope.launch {
            val tripId = waypointRepository.get(id)?.tripId
            waypointRepository.delete(id)
            refreshAfterWaypointChange(tripId)
            if (_selectedWaypoint.value?.id == id) _selectedWaypoint.value = null
        }
    }

    /**
     * Keeps the counters of the trip and the overlaid routes in sync after a waypoint
     * changed. The waypoints of the trip being recorded are observed and refresh alone.
     */
    private suspend fun refreshAfterWaypointChange(tripId: Long?) {
        if (tripId == null) return
        updateCounters(tripId)
        val overlaid = overlayTripIds.value
        if (tripId in overlaid) loadOverlays(overlaid)
    }

    private suspend fun updateCounters(tripId: Long) {
        val photoCount = waypointRepository.countForTrip(tripId)
        tripRepository.updateCounters(tripId, photoCount, photoCount)
    }

    // region Back to the car

    /**
     * The waypoint where the car was parked: the one of the trip being recorded or, when
     * nothing is recorded, the one of the most recent trip if it ended less than 12 h ago.
     */
    val parkingWaypoint: StateFlow<Waypoint?> =
        com.mikotrack.data.repository.ParkingLocator(tripRepository, waypointRepository)
            .observe(recordingState.map { it.tripId })
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The straight-line distance and the direction from the user to the car. */
    val parkingDistance: StateFlow<ParkingDistance?> = combine(parkingWaypoint, currentPosition) { parking, position ->
        if (parking == null || position == null) return@combine null
        ParkingDistance(
            distanceMeters = com.mikotrack.core.GeoUtils.distanceMeters(
                position.latitude, position.longitude, parking.latitude, parking.longitude,
            ),
            bearingDegrees = com.mikotrack.core.GeoUtils.bearingDegrees(
                position.latitude, position.longitude, parking.latitude, parking.longitude,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Opens the waypoint sheet of the car, with its live distance and direction. */
    fun showParking() {
        parkingWaypoint.value?.let { selectWaypointById(it.id) }
    }

    // endregion

    private companion object {
        const val TAG = "MapViewModel"

        /** The age after which a position of the passive tracking is no longer shown. */
        const val STALE_POSITION_MILLIS = 60_000L

        /** How often the quality of the GPS signal shown on the map is recomputed. */
        const val GPS_QUALITY_REFRESH_MILLIS = 2_000L
    }
}
