package com.mikotrack.ui.map

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.mikotrack.maps.GeoPoint
import com.mikotrack.maps.HabitatLayerManager
import com.mikotrack.maps.HabitatLayers
import com.mikotrack.maps.Hotspot
import com.mikotrack.maps.MapIconFactory
import com.mikotrack.maps.MapLayer
import com.mikotrack.maps.MapOverlayManager
import com.mikotrack.maps.MapStyleFactory
import com.mikotrack.maps.PhotoBubbleFactory
import com.mikotrack.maps.RouteOverlay
import com.mikotrack.maps.WaypointOverlay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.roundToInt
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The distance, in density independent pixels, between the right edge of the screen
 * and the compass widget of the map.
 */
private const val COMPASS_END_MARGIN_DP = 12f

/** Duration of the camera animation used when framing the whole route. */
private const val FIT_ANIMATION_MS = 600

/** The default margin of the MapLibre logo and attribution, in density independent pixels. */
private const val ATTRIBUTION_MARGIN_DP = 4f

/** The attribution button sits next to the logo, so it starts further from the edge. */
private const val ATTRIBUTION_START_MARGIN_DP = 92f

/** How far from a waypoint marker a tap still selects it, in density independent pixels. */
private const val WAYPOINT_TAP_TOLERANCE_DP = 14f

/** The frame colour of a bubble whose category has no colour. */
private const val DEFAULT_BUBBLE_COLOR = 0xFFFFFFFFL

/** The keys used to save the camera of the map. */
private const val KEY_LATITUDE = "camera_latitude"
private const val KEY_LONGITUDE = "camera_longitude"
private const val KEY_ZOOM = "camera_zoom"
private const val KEY_BEARING = "camera_bearing"
private const val KEY_PLACED = "camera_placed"

/** The highest zoom level the camera is ever allowed to reach. */
private const val MAX_ZOOM_LIMIT = 22.0

/** The time between two steps of the animated dashes of the overlaid routes (~12 fps). */
private const val ANIMATION_FRAME_MS = 80L

/** The marker of the place found by the place search. */
private const val SEARCH_MARKER_SOURCE = "mycotrack-search-marker"
private const val SEARCH_MARKER_LAYER = "mycotrack-search-marker-layer"
private const val SEARCH_MARKER_HALO_LAYER = "mycotrack-search-marker-halo-layer"
private const val SEARCH_MARKER_COLOR = "#7B1FA2"

/** Renders the photograph bubbles off the main thread, one at a time. */
private val bubbleExecutor = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "mycotrack-photo-bubbles").apply { isDaemon = true }
}

/**
 * Owns the MapLibre [MapView] and exposes the imperative operations the user interface
 * needs: changing the layer, updating the overlays and moving the camera.
 */
class MapController {

    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private val overlayManager = MapOverlayManager()

    /** Draws the habitat overlays (tree species and slope orientation). */
    private val habitatManager = HabitatLayerManager()

    /** The habitat overlays that are requested, applied again whenever the style reloads. */
    private var habitatLayers = HabitatLayers()

    /**
     * The space, in pixels, that the compass has to leave free at the top of the map so
     * it does not sit under the status bar or the control buttons.
     */
    private var compassTopInsetPx = 0
    private var compassEndInsetPx = 0

    /** The space, in pixels, that the logo and attribution leave free at the bottom. */
    private var attributionBottomInsetPx = 0
    private var attributionStartInsetPx = 0

    /** Invoked whenever the style finished loading. */
    var onStyleReady: ((Style) -> Unit)? = null

    /** Invoked when the user taps a waypoint marker or a photograph bubble. */
    var onWaypointClicked: ((Long) -> Unit)? = null

    /** Invoked when the user taps one of the hotspot marks of a multi-species search. */
    var onHotspotClicked: ((Int) -> Unit)? = null

    /** The hotspot marks of a multi-species search, drawn while its search is active. */
    private var lastHotspots: List<Hotspot> = emptyList()

    /** Invoked with the latitude and longitude of a long press on the map. */
    var onMapLongClicked: ((Double, Double) -> Unit)? = null

    /** The point the user long-pressed, marked on the map while its menu is open. */
    private var pressedPoint: GeoPoint? = null

    /** The place found by the place search, marked until another one replaces it. */
    private var searchMarker: GeoPoint? = null

    /** Whether the host of the map is resumed, so the route animation may run. */
    private var hostResumed = true

    /** Whether the route animation ticker is scheduled. */
    private var animationRunning = false

    /** Moves the dashes of the overlaid routes one step and schedules the next one. */
    private val animationTick = object : Runnable {
        override fun run() {
            val style = map?.style
            if (style == null || !style.isFullyLoaded || !shouldAnimate()) {
                animationRunning = false
                return
            }
            runCatching { overlayManager.stepAnimation(style) }
            mainHandler.postDelayed(this, ANIMATION_FRAME_MS)
        }
    }

    private fun shouldAnimate(): Boolean =
        hostResumed && mapView != null && map != null && overlayManager.hasAnimatedRoutes

    /** Starts or stops the route animation to match the state of the map. */
    private fun updateAnimation() {
        if (shouldAnimate()) {
            if (!animationRunning) {
                animationRunning = true
                mainHandler.postDelayed(animationTick, ANIMATION_FRAME_MS)
            }
        } else if (animationRunning) {
            animationRunning = false
            mainHandler.removeCallbacks(animationTick)
        }
    }

    /**
     * Tells the controller whether the screen that hosts the map is resumed: the
     * animation of the overlaid routes only runs while the map can be seen.
     */
    fun setHostResumed(resumed: Boolean) {
        hostResumed = resumed
        updateAnimation()
    }

    /** Marks the point the user long-pressed, or removes the mark when it is `null`. */
    fun setPressedPoint(point: GeoPoint?) {
        if (pressedPoint == point) return
        pressedPoint = point
        val style = map?.style ?: return
        if (!style.isFullyLoaded) return
        runCatching { overlayManager.applyMarker(style, point) }
    }

    /**
     * The zoom level of the camera, published so the user interface can show it.
     *
     * It is null while the map is not ready.
     */
    private val _cameraZoom = MutableStateFlow<Double?>(null)

    /** The live zoom level of the camera. */
    val cameraZoom: StateFlow<Double?> = _cameraZoom.asStateFlow()

    /** Publishes the zoom level of the camera whenever it changes. */
    private val cameraMoveListener = MapLibreMap.OnCameraMoveListener {
        publishCameraZoom()
    }

    /** Publishes the final zoom level when the camera settles. */
    private val cameraIdleListener = MapLibreMap.OnCameraIdleListener {
        publishCameraZoom()
        lastCamera = map?.cameraPosition
    }

    /** Whether the photographs are shown as bubbles on top of their waypoints. */
    private var photoBubblesVisible = true

    /** The bubble images that are ready, by waypoint, with the photograph they show. */
    private val bubbleImages = HashMap<Long, Pair<String, Bitmap>>()

    /** The waypoints whose bubble is being rendered in the background. */
    private val bubblesLoading = HashSet<Long>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var pendingStyle: Style.Builder? = null
    private var waypointIcons: Map<String, Long> = emptyMap()
    private var lastOverlays: List<RouteOverlay> = emptyList()
    private var lastWaypoints: List<WaypointOverlay> = emptyList()
    private var lastPosition: GeoPoint? = null
    private var lastBearing: Float? = null

    /**
     * The camera position that was requested before the map finished loading.
     *
     * The first fix often arrives before the map is ready, and the camera move would
     * be lost, leaving the map at world scale.
     */
    private var pendingCamera: Pair<LatLng, Double?>? = null

    /**
     * A camera action that has to wait for the map to be ready.
     *
     * The route of a trip is often loaded before the map view has finished loading,
     * and the request would be lost otherwise.
     */
    private var pendingCameraAction: (() -> Unit)? = null

    /** The camera of the map view that was released last, restored on the next one. */
    private var savedCamera: CameraPosition? = null

    /**
     * The camera of the map once it has settled.
     *
     * It is kept separately from [savedCamera], because the state of the controller
     * may be saved before the map view is released.
     */
    private var lastCamera: CameraPosition? = null

    /**
     * Whether the camera has already been placed on the map.
     *
     * A map that has just been created starts at world scale, so the first position
     * that arrives has to be centred with the default zoom level. The positions that
     * arrive later keep the zoom level the user has chosen.
     *
     * The flag belongs to the controller and not to the screen, so that the map is not
     * zoomed out again when the user comes back to it after visiting another section
     * of the application.
     */
    var isCameraPlaced: Boolean = false
        private set

    /**
     * Marks the camera as placed, so that the zoom level the user has chosen is kept.
     */
    fun markCameraPlaced() {
        isCameraPlaced = true
    }

    /** The lowest zoom level the camera is allowed to reach. */
    private var minZoomLimit: Double = 0.0

    /** The highest zoom level the camera is allowed to reach. */
    private var maxZoomLimit: Double = MAX_ZOOM_LIMIT

    /**
     * Restricts the zoom levels the camera can reach.
     *
     * A source that only publishes tiles up to a given zoom level must not be zoomed
     * beyond it: the tiles would be scaled up and the map would look blurred. The
     * limits are also published to the renderer, so that it does not ask for tiles
     * that the source does not have.
     */
    fun setZoomLimits(minZoom: Double, maxZoom: Double) {
        val low = minZoom.coerceIn(0.0, MAX_ZOOM_LIMIT)
        val high = maxZoom.coerceIn(low, MAX_ZOOM_LIMIT)
        if (minZoomLimit == low && maxZoomLimit == high) return
        minZoomLimit = low
        maxZoomLimit = high
        applyZoomLimits()
        clampCamera()
    }

    /** Keeps a zoom level inside the limits of the current source. */
    fun clampZoom(zoom: Double): Double = zoom.coerceIn(minZoomLimit, maxZoomLimit)

    /** Publishes the zoom limits to the renderer. */
    private fun applyZoomLimits() {
        val map = this.map ?: return
        runCatching { map.setMinZoomPreference(minZoomLimit) }
        runCatching { map.setMaxZoomPreference(maxZoomLimit) }
    }

    /** Brings the camera back inside the zoom limits, if it is outside of them. */
    private fun clampCamera() {
        val map = this.map ?: return
        val position = map.cameraPosition
        val zoom = position.zoom.coerceIn(minZoomLimit, maxZoomLimit)
        val target = position.target ?: return
        if (zoom != position.zoom) {
            runCatching { map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, zoom)) }
        }
    }

    /**
     * The state of the camera, so that the map can be restored when the screen is
     * recreated, for example after a change of orientation.
     */
    fun saveState(): Bundle {
        val bundle = Bundle()
        // The live camera is the most accurate one, but it is not available when the
        // map view has already been released.
        val camera = map?.cameraPosition ?: lastCamera ?: savedCamera
        val target = camera?.target
        if (camera != null && target != null) {
            bundle.putDouble(KEY_LATITUDE, target.latitude)
            bundle.putDouble(KEY_LONGITUDE, target.longitude)
            bundle.putDouble(KEY_ZOOM, camera.zoom)
            bundle.putDouble(KEY_BEARING, camera.bearing)
        }
        bundle.putBoolean(KEY_PLACED, isCameraPlaced)
        return bundle
    }

    /**
     * The centre of the camera (live, or the last one if the map view was released), used
     * as the centre of a likely areas search when the position of the user is unknown.
     */
    fun cameraTarget(): GeoPoint? {
        // Before it is placed the camera shows the whole world, which is no place to search.
        if (!isCameraPlaced) return null
        val camera = runCatching { map?.cameraPosition }.getOrNull() ?: lastCamera ?: savedCamera
        val target = camera?.target ?: return null
        return GeoPoint(target.latitude, target.longitude)
    }

    /**
     * Restores a camera that was saved before, which is applied as soon as the map
     * is ready.
     */
    fun restoreState(bundle: Bundle) {
        isCameraPlaced = bundle.getBoolean(KEY_PLACED, false)
        if (!bundle.containsKey(KEY_ZOOM)) return
        savedCamera = CameraPosition.Builder()
            .target(LatLng(bundle.getDouble(KEY_LATITUDE), bundle.getDouble(KEY_LONGITUDE)))
            .zoom(bundle.getDouble(KEY_ZOOM))
            .bearing(bundle.getDouble(KEY_BEARING))
            .build()
    }

    companion object {
        /**
         * Saves the camera of the map, so that it survives the recreation of the
         * screen the map belongs to.
         */
        val Saver: Saver<MapController, Bundle> = Saver(
            save = { it.saveState() },
            restore = { MapController().apply { restoreState(it) } },
        )
    }
    /**
     * The style that is currently applied, used to avoid applying it twice.
     *
     * Reloading the style cancels the tile requests that are in flight, which makes the
     * map flash empty. The style is therefore only reloaded when something that really
     * changes has happened.
     */
    private var appliedStyle: StyleKey? = null

    /** The parameters that describe the style of the map. */
    private data class StyleKey(
        val layer: MapLayer,
        val offlineUrl: String?,
        val offlineTileSize: Int,
        val offlineMinZoom: Int,
        val offlineMaxZoom: Int,
    )

    /** Attaches the controller to a freshly created map view. */
    fun attach(view: MapView) {
        mapView = view
        view.getMapAsync { mapLibreMap ->
            // The map may have been released while it was loading, in which case
            // this callback belongs to a map view that no longer exists.
            if (mapView !== view) return@getMapAsync
            map = mapLibreMap
            applyCompassPosition()
            applyAttributionPosition()
            applyZoomLimits()
            mapLibreMap.addOnCameraMoveListener(cameraMoveListener)
            mapLibreMap.addOnCameraIdleListener(cameraIdleListener)
            mapLibreMap.addOnMapClickListener { latLng ->
                val rank = hotspotAt(mapLibreMap, latLng)
                if (rank != null) {
                    onHotspotClicked?.invoke(rank)
                    return@addOnMapClickListener true
                }
                val waypointId = waypointAt(mapLibreMap, latLng) ?: return@addOnMapClickListener false
                onWaypointClicked?.invoke(waypointId)
                true
            }
            mapLibreMap.addOnMapLongClickListener { latLng ->
                val callback = onMapLongClicked ?: return@addOnMapLongClickListener false
                callback(latLng.latitude, latLng.longitude)
                true
            }
            // The style is applied as soon as the map is ready: the map view can be
            // recreated when the user switches between the map and the profile.
            applyStyle()
            // The camera the user left the map with is restored before anything else,
            // so that the map does not come back at world scale.
            savedCamera?.let { camera ->
                savedCamera = null
                isCameraPlaced = true
                mapLibreMap.setCameraPosition(
                    CameraPosition.Builder(camera)
                        .zoom(camera.zoom.coerceIn(minZoomLimit, maxZoomLimit))
                        .build(),
                )
            }
            pendingCamera?.let { (target, zoom) ->
                pendingCamera = null
                applyCamera(target, zoom)
            }
            pendingCameraAction?.let { action ->
                pendingCameraAction = null
                action()
            }
        }
    }

    /**
     * The waypoint drawn under a tap, if any. Bubbles are checked first because they are
     * drawn on top of the markers.
     */
    private fun waypointAt(map: MapLibreMap, latLng: LatLng): Long? {
        val point = map.projection.toScreenLocation(latLng)
        val density = mapView?.resources?.displayMetrics?.density ?: 1f
        val tolerance = WAYPOINT_TAP_TOLERANCE_DP * density

        if (photoBubblesVisible) {
            val bubble = runCatching {
                map.queryRenderedFeatures(point, MapOverlayManager.PHOTO_BUBBLE_LAYER)
            }.getOrNull()?.firstOrNull()
            bubble?.getNumberProperty("id")?.toLong()?.let { return it }
        }

        val area = RectF(point.x - tolerance, point.y - tolerance, point.x + tolerance, point.y + tolerance)
        val marker = runCatching {
            map.queryRenderedFeatures(area, MapOverlayManager.WAYPOINT_LAYER)
        }.getOrNull()?.firstOrNull()
        return marker?.getNumberProperty("id")?.toLong()
    }

    /** The rank of the hotspot mark tapped, if the tap fell on one. */
    private fun hotspotAt(map: MapLibreMap, latLng: LatLng): Int? {
        if (lastHotspots.isEmpty()) return null
        val point = map.projection.toScreenLocation(latLng)
        val density = mapView?.resources?.displayMetrics?.density ?: 1f
        val tolerance = WAYPOINT_TAP_TOLERANCE_DP * density
        val area = RectF(point.x - tolerance, point.y - tolerance, point.x + tolerance, point.y + tolerance)
        val feature = runCatching {
            map.queryRenderedFeatures(area, MapOverlayManager.HOTSPOT_LAYER)
        }.getOrNull()?.firstOrNull()
        return feature?.getNumberProperty("rank")?.toInt()
    }

    /** Draws the hotspot marks of a multi-species search, or removes them when empty. */
    fun setHotspots(hotspots: List<Hotspot>) {
        lastHotspots = hotspots
        val style = map?.style ?: return
        if (!style.isFullyLoaded) return
        registerHotspotIcons(style)
        runCatching { overlayManager.applyHotspots(style, hotspots) }
    }

    private fun registerHotspotIcons(style: Style) {
        lastHotspots.forEach { hotspot ->
            val id = MapIconFactory.hotspotIconId(hotspot.rank)
            if (style.getImage(id) == null) {
                runCatching { style.addImage(id, MapIconFactory.hotspotIcon(hotspot.rank)) }
            }
        }
    }

    /** Shows or hides the photograph bubbles. */
    fun setPhotoBubblesVisible(visible: Boolean) {
        if (photoBubblesVisible == visible) return
        photoBubblesVisible = visible
        render(lastOverlays, lastWaypoints, lastPosition, lastBearing)
    }

    /**
     * Renders the bubbles of the photographs that are not ready yet. The work happens on
     * a background thread; the map is refreshed when each bubble is done.
     */
    private fun requestBubbles(waypoints: List<WaypointOverlay>) {
        val density = mapView?.resources?.displayMetrics?.density ?: return
        waypoints.forEach { waypoint ->
            val path = waypoint.imagePath ?: return@forEach
            val id = waypoint.waypointId
            if (bubbleImages[id]?.first == path || id in bubblesLoading) return@forEach
            bubblesLoading += id
            val frameColor = ((waypointIcons[waypoint.category] ?: DEFAULT_BUBBLE_COLOR) and 0xFFFFFFFFL).toInt()
            bubbleExecutor.execute {
                val bitmap = PhotoBubbleFactory.create(path, frameColor, density)
                mainHandler.post {
                    bubblesLoading -= id
                    if (bitmap == null) return@post
                    bubbleImages[id] = path to bitmap
                    val style = map?.style ?: return@post
                    if (!style.isFullyLoaded) return@post
                    runCatching { style.addImage(PhotoBubbleFactory.imageId(id), bitmap) }
                    render(lastOverlays, lastWaypoints, lastPosition, lastBearing)
                }
            }
        }
    }

    /**
     * Moves the compass below the top controls of the screen, and away from the panel or
     * the system bar that may cover the right side of the map in landscape.
     */
    fun setCompassTopInset(topInsetPx: Int, endInsetPx: Int = 0) {
        compassTopInsetPx = topInsetPx
        compassEndInsetPx = endInsetPx
        applyCompassPosition()
    }

    private fun applyCompassPosition() {
        val view = mapView ?: return
        val settings = map?.uiSettings ?: return
        val endMargin = (COMPASS_END_MARGIN_DP * view.resources.displayMetrics.density).roundToInt()
        settings.compassGravity = Gravity.TOP or Gravity.END
        settings.setCompassMargins(0, compassTopInsetPx, endMargin + compassEndInsetPx, 0)
    }

    /** Switches the base layer of the map. */
    fun setLayer(
        layer: MapLayer,
        offlineUrl: String? = null,
        offlineTileSize: Int = 256,
        offlineMinZoom: Int = 0,
        offlineMaxZoom: Int = 22,
    ) {
        val key = StyleKey(layer, offlineUrl, offlineTileSize, offlineMinZoom, offlineMaxZoom)
        if (appliedStyle == key && map != null) {
            // The style is already the one that is on the screen: reloading it would
            // cancel the tile requests that are in flight and make the map flash empty.
            return
        }
        appliedStyle = key
        applyStyle()
    }

    /** Builds and applies the style that is currently selected. */
    private fun applyStyle() {
        val key = appliedStyle ?: return
        val builder = MapStyleFactory.build(
            layer = key.layer,
            offlineUrl = key.offlineUrl,
            offlineTileSize = key.offlineTileSize,
            offlineMinZoom = key.offlineMinZoom,
            offlineMaxZoom = key.offlineMaxZoom,
        )
        pendingStyle = builder
        map?.setStyle(builder) { style -> handleStyleLoaded(style) }
    }

    /**
     * Shows or hides the habitat overlays on top of the base map.
     *
     * @param requested the overlays to draw, with the classes and the URL templates of
     * the tiles; a new URL replaces the source, so its tiles are loaded again.
     */
    fun setHabitatLayers(requested: HabitatLayers) {
        if (requested == habitatLayers) return
        habitatLayers = requested
        val style = map?.style ?: return
        runCatching { habitatManager.apply(style, requested) }
    }

    /** Registers the colour used for every waypoint category. */
    fun setWaypointIcons(categoryColors: Map<String, Long>) {
        waypointIcons = categoryColors
    }

    /**
     * Re-registers the waypoint images of the current style (the parking badge may have
     * changed its colour or icon in the settings) and draws the markers again.
     */
    fun refreshWaypointIcons() {
        val style = map?.style ?: return
        if (!style.isFullyLoaded) return
        registerWaypointIcons(style)
        runCatching { render(lastOverlays, lastWaypoints, lastPosition, lastBearing) }
    }

    private fun registerWaypointIcons(style: Style) {
        val context = mapView?.context ?: return
        waypointIcons.forEach { (category, color) ->
            val glyph = category.takeIf { it == com.mikotrack.data.repository.DefaultCategory.PARKING.key }
                ?.let { MapIconFactory.parkingIconStyle }
            runCatching { style.addImage(MapIconFactory.waypointIconId(category), MapIconFactory.waypointIcon(context, color, glyph)) }
        }
    }

    /** Updates the drawn routes, waypoints and the current position. */
    fun render(
        overlays: List<RouteOverlay>,
        waypoints: List<WaypointOverlay>,
        position: GeoPoint?,
        bearing: Float?,
    ) {
        lastOverlays = overlays
        lastWaypoints = waypoints
        lastPosition = position
        lastBearing = bearing

        val style = map?.style ?: return
        overlayManager.syncRoutes(style, overlays)
        val density = mapView?.resources?.displayMetrics?.density ?: 1f
        runCatching { overlayManager.applyRouteLabels(style, overlays, density) }
        updateAnimation()

        // A waypoint with a visible bubble is marked by the bubble alone: drawing the
        // marker underneath would hide the tip of the bubble.
        val withPhoto = if (photoBubblesVisible) waypoints.filter { it.imagePath != null } else emptyList()
        if (withPhoto.isNotEmpty()) requestBubbles(withPhoto)
        val bubbles = withPhoto.filter { bubbleImages[it.waypointId]?.first == it.imagePath }
        val bubbleIds = bubbles.mapTo(HashSet()) { it.waypointId }

        overlayManager.applyWaypoints(style, waypoints.filterNot { it.waypointId in bubbleIds }) { category ->
            MapIconFactory.waypointIconId(category)
        }
        overlayManager.applyPhotoBubbles(style, bubbles)
        registerHotspotIcons(style)
        runCatching { overlayManager.applyHotspots(style, lastHotspots) }
        runCatching { overlayManager.applyMarker(style, pressedPoint) }
        runCatching { applySearchMarker(style) }
        overlayManager.applyPosition(style, position, bearing)
    }

    /** Marks the place found by a search, or removes the mark when it is `null`. */
    fun setSearchMarker(point: GeoPoint?) {
        if (searchMarker == point) return
        searchMarker = point
        val style = map?.style ?: return
        if (!style.isFullyLoaded) return
        runCatching { applySearchMarker(style) }
    }

    private fun applySearchMarker(style: Style) {
        val point = searchMarker
        val geoJson = if (point == null) {
            """{"type":"FeatureCollection","features":[]}"""
        } else {
            String.format(
                java.util.Locale.US,
                """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[%.7f,%.7f]}}""",
                point.longitude,
                point.latitude,
            )
        }
        val source = style.getSourceAs<GeoJsonSource>(SEARCH_MARKER_SOURCE)
        if (source != null) {
            source.setGeoJson(geoJson)
            return
        }
        style.addSource(GeoJsonSource(SEARCH_MARKER_SOURCE, geoJson))
        val halo = org.maplibre.android.style.layers.CircleLayer(SEARCH_MARKER_HALO_LAYER, SEARCH_MARKER_SOURCE)
            .withProperties(
                PropertyFactory.circleRadius(18f),
                PropertyFactory.circleColor(SEARCH_MARKER_COLOR),
                PropertyFactory.circleOpacity(0.25f),
            )
        val dot = org.maplibre.android.style.layers.CircleLayer(SEARCH_MARKER_LAYER, SEARCH_MARKER_SOURCE)
            .withProperties(
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleColor(SEARCH_MARKER_COLOR),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
            )
        // Below the position of the user, which must always stay visible.
        if (style.getLayer(MapOverlayManager.POSITION_HALO_LAYER) != null) {
            style.addLayerBelow(halo, MapOverlayManager.POSITION_HALO_LAYER)
            style.addLayerBelow(dot, MapOverlayManager.POSITION_HALO_LAYER)
        } else {
            style.addLayer(halo)
            style.addLayer(dot)
        }
    }

    /** Moves the camera to the given coordinates. */
    fun centerOn(latitude: Double, longitude: Double, zoom: Double? = null) {
        val target = LatLng(latitude, longitude)
        val map = this.map
        if (map == null) {
            // The map is not ready yet: remember the request and apply it as soon as
            // the map is available, otherwise the camera would stay at world scale.
            pendingCamera = target to zoom
            return
        }
        applyCamera(target, zoom)
    }

    private fun applyCamera(target: LatLng, zoom: Double?) {
        val update = if (zoom != null) {
            CameraUpdateFactory.newLatLngZoom(target, clampZoom(zoom))
        } else {
            CameraUpdateFactory.newLatLng(target)
        }
        map?.animateCamera(update)
    }

    /** Frames the given bounds on the screen, keeping the given margins (in pixels) free. */
    fun fitBounds(
        minLatitude: Double,
        minLongitude: Double,
        maxLatitude: Double,
        maxLongitude: Double,
        leftPx: Int = 64,
        topPx: Int = 64,
        rightPx: Int = 64,
        bottomPx: Int = 64,
    ) {
        // The route is often loaded before the map is ready, and the framing would
        // be lost otherwise.
        val frame: () -> Unit = {
            val bounds = org.maplibre.android.geometry.LatLngBounds.from(
                maxLatitude,
                maxLongitude,
                minLatitude,
                minLongitude,
            )
            val view = mapView
            // Margins larger than the view would leave no room for the route.
            val horizontal = if (view != null) (view.width * 0.4).toInt() else Int.MAX_VALUE
            val vertical = if (view != null) (view.height * 0.4).toInt() else Int.MAX_VALUE
            val padding = intArrayOf(
                leftPx.coerceAtMost(horizontal),
                topPx.coerceAtMost(vertical),
                rightPx.coerceAtMost(horizontal),
                bottomPx.coerceAtMost(vertical),
            )
            val map = this.map
            if (map != null) {
                // The camera is computed before the animation starts, so that the zoom
                // level can be kept inside the range the source really contains: a
                // route that is too long for the maximum zoom level would be framed
                // with tiles that do not exist.
                val position = runCatching { map.getCameraForLatLngBounds(bounds, padding) }.getOrNull()
                if (position != null) {
                    val limited = CameraPosition.Builder(position)
                        .zoom(clampZoom(position.zoom))
                        .build()
                    map.animateCamera(CameraUpdateFactory.newCameraPosition(limited), FIT_ANIMATION_MS)
                } else {
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(
                            bounds,
                            padding[0],
                            padding[1],
                            padding[2],
                            padding[3],
                        ),
                        FIT_ANIMATION_MS,
                    )
                }
            }
            Unit
        }
        if (map == null) {
            pendingCameraAction = frame
            return
        }
        frame()
    }

    /** Zooms in one level around the centre of the map. */
    fun zoomIn() {
        markCameraPlaced()
        map?.animateCamera(CameraUpdateFactory.zoomIn())
    }

    /** Zooms out one level around the centre of the map. */
    fun zoomOut() {
        markCameraPlaced()
        map?.animateCamera(CameraUpdateFactory.zoomOut())
    }

    /** Moves the camera to the given zoom level, keeping its centre. */
    fun zoomTo(zoom: Double) {
        markCameraPlaced()
        map?.animateCamera(CameraUpdateFactory.zoomTo(clampZoom(zoom)))
    }

    /** The zoom level of the camera, if the map is ready. */
    fun currentZoom(): Double? = map?.cameraPosition?.zoom

    /**
     * Publishes the current zoom level of the camera.
     *
     * The camera reports the level it is heading to while it animates, so the value
     * settles on the exact level once the camera becomes idle.
     */
    private fun publishCameraZoom() {
        _cameraZoom.value = map?.cameraPosition?.zoom
    }

    /**
     * Moves the logo and the attribution of the map up by [bottomInsetPx], so they are not
     * covered by the controls that float over the bottom of the map, and right by
     * [startInsetPx], away from a display cutout.
     */
    fun setAttributionBottomInset(bottomInsetPx: Int, startInsetPx: Int = 0) {
        attributionBottomInsetPx = bottomInsetPx
        attributionStartInsetPx = startInsetPx
        applyAttributionPosition()
    }

    private fun applyAttributionPosition() {
        val view = mapView ?: return
        val settings = map?.uiSettings ?: return
        val density = view.resources.displayMetrics.density
        val margin = (ATTRIBUTION_MARGIN_DP * density).roundToInt()
        val attributionStart = (ATTRIBUTION_START_MARGIN_DP * density).roundToInt()
        settings.setLogoMargins(margin + attributionStartInsetPx, margin, margin, margin + attributionBottomInsetPx)
        settings.setAttributionMargins(
            attributionStart + attributionStartInsetPx,
            margin,
            margin,
            margin + attributionBottomInsetPx,
        )
    }

    private fun handleStyleLoaded(style: Style) {
        // The images and layers of the previous style are gone.
        overlayManager.onStyleChanged()
        // Register the marker images before the waypoint layer is created.
        registerWaypointIcons(style)
        runCatching { style.addImage(MapIconFactory.BEARING_ICON, MapIconFactory.bearingIcon()) }
        // A new style has no images, so the bubbles that are ready are registered again.
        bubbleImages.forEach { (id, image) ->
            runCatching { style.addImage(PhotoBubbleFactory.imageId(id), image.second) }
        }

        // The habitat overlays go in first: they sit right above the base map, below
        // the routes and markers that render() adds next.
        runCatching { habitatManager.apply(style, habitatLayers) }

        render(lastOverlays, lastWaypoints, lastPosition, lastBearing)
        onStyleReady?.invoke(style)
    }

    /**
     * Releases the resources held by the controller.
     *
     * The selected style is kept on purpose: the map view is recreated every time the
     * user switches between the map and the elevation profile, and the style has to be
     * applied again to the new map.
     */
    fun release() {
        // The camera is remembered, so that the new map view opens where the previous one
        // was instead of at world scale.
        map?.let { runCatching { savedCamera = it.cameraPosition } }
        map?.let {
            runCatching { it.removeOnCameraMoveListener(cameraMoveListener) }
            runCatching { it.removeOnCameraIdleListener(cameraIdleListener) }
        }
        mapView = null
        map = null
        pendingStyle = null
        pendingCamera = null
        updateAnimation()
    }
}

/** Embeds the MapLibre map inside the Compose hierarchy. */
@Composable
fun MapCanvas(
    controller: MapController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember(context) { MapView(context) }

    DisposableEffect(mapView) {
        controller.attach(mapView)
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        onDispose {
            // The controller reads the camera before the map is destroyed.
            controller.release()
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // The animation of the overlaid routes only runs while the screen is resumed.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> controller.setHostResumed(true)
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> controller.setHostResumed(false)
                else -> Unit
            }
        }
        controller.setHostResumed(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.setHostResumed(false)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
}
