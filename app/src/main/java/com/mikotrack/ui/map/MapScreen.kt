package com.mikotrack.ui.map

import com.mikotrack.ui.diagnostics.GpsStatusChip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import java.util.Date
import java.text.SimpleDateFormat
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.mikotrack.ui.common.categoryLabel
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import com.mikotrack.R
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mikotrack.core.Formatters
import com.mikotrack.core.PermissionRequirement
import com.mikotrack.data.repository.DefaultCategory
import com.mikotrack.domain.model.Trip
import com.mikotrack.domain.model.Waypoint
import com.mikotrack.maps.GeoPoint
import com.mikotrack.maps.MapLayer
import com.mikotrack.maps.RouteOverlay
import com.mikotrack.maps.WaypointOverlay
import com.mikotrack.ui.camera.CameraCaptureScreen
import com.mikotrack.ui.common.ColorDot
import com.mikotrack.ui.common.ConfirmationDialog
import com.mikotrack.ui.common.StatTile
import com.mikotrack.ui.elevation.ElevationProfileView
import com.mikotrack.ui.permissions.MissingPermissionsDialog
import com.mikotrack.ui.theme.Amber
import com.mikotrack.ui.theme.DangerRed
import com.mikotrack.ui.theme.ForestGreen
import java.io.File
import kotlin.math.roundToInt

/** The height of the elevation profile when it shares the screen with the map. */
private val COMBINED_PROFILE_HEIGHT = 250.dp

/** In landscape the profile of the combined view sits in the side panel. */
private val COMBINED_PROFILE_HEIGHT_LANDSCAPE = 200.dp

/** The share of the width that the side panel takes in landscape, and its limits. */
private const val SIDE_PANEL_FRACTION = 0.34f
private val SIDE_PANEL_MIN = 250.dp
private val SIDE_PANEL_MAX = 340.dp

/** The strip left free above the bottom panel for the logo and attribution of the map. */
private val ATTRIBUTION_STRIP = 32.dp

/** The width of a column of buttons plus its margins, kept free beside the banners. */
private val SIDE_COLUMN_CLEARANCE = 80.dp

/** The size of the zoom buttons, and the space kept free around them when framing a route. */
private val ZOOM_BUTTON_SIZE = 48.dp
private val FIT_SIDE_MARGIN = 72.dp
private val FIT_EDGE_GAP = 16.dp

/** The minimum zoom level used when another screen asks the map to show a place. */
private const val FOCUS_ZOOM = 17.0

/** The zoom level used to show a searched place that has no meaningful extent. */
private const val PLACE_ZOOM = 15.0

/** Below this extent (about 300 m) a searched place is centred instead of framed. */
private const val PLACE_MIN_SPAN_DEGREES = 0.003

/** Below this width the recording controls use smaller margins (landscape side panel). */
private val RECORDING_CONTROLS_COMPACT_WIDTH = 360.dp

/**
 * The main screen of the application: the live map, the recording controls, the live
 * statistics and the synchronised elevation profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    controller: MapController,
    modifier: Modifier = Modifier,
    onOpenPermissions: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
) {
    val context = LocalContext.current

    val recording by viewModel.recordingState.collectAsStateWithLifecycle()
    val layer by viewModel.selectedLayer.collectAsStateWithLifecycle()
    val overlays by viewModel.overlayRoutes.collectAsStateWithLifecycle()
    val overlayIds by viewModel.overlayTripIds.collectAsStateWithLifecycle()
    val overlayableTrips by viewModel.overlayableTrips.collectAsStateWithLifecycle()
    val proximityAlarmEnabled by viewModel.proximityAlarmEnabled.collectAsStateWithLifecycle()
    val proximityRadius by viewModel.proximityRadius.collectAsStateWithLifecycle()
    val proximityHit by viewModel.proximityHit.collectAsStateWithLifecycle()
    val activePoints by viewModel.activePoints.collectAsStateWithLifecycle()
    val activeWaypoints by viewModel.activeWaypoints.collectAsStateWithLifecycle()
    val profile by viewModel.elevationProfile.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val follow by viewModel.followPosition.collectAsStateWithLifecycle()
    val selectedWaypoint by viewModel.selectedWaypoint.collectAsStateWithLifecycle()
    val selectedWaypointDistance by viewModel.selectedWaypointDistance.collectAsStateWithLifecycle()
    val selectedWaypointTripName by viewModel.selectedWaypointTripName.collectAsStateWithLifecycle()
    val selectedDistance by viewModel.selectedDistance.collectAsStateWithLifecycle()
    val recoverableTrip by viewModel.recoverableTrip.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val defaultZoom by viewModel.defaultZoom.collectAsStateWithLifecycle()
    val showPhotoBubbles by viewModel.showPhotoBubbles.collectAsStateWithLifecycle()
    val visibleButtons by viewModel.visibleButtons.collectAsStateWithLifecycle()
    val offlineSource by viewModel.offlineSource.collectAsStateWithLifecycle()
    val habitatLayers by viewModel.habitatLayers.collectAsStateWithLifecycle()
    val gpsQuality by viewModel.gpsQuality.collectAsStateWithLifecycle()
    val gnssSnapshot by viewModel.gnssSnapshot.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val parkingWaypoint by viewModel.parkingWaypoint.collectAsStateWithLifecycle()
    val parkingDistance by viewModel.parkingDistance.collectAsStateWithLifecycle()
    val zoomLevel by controller.cameraZoom.collectAsStateWithLifecycle()
    val localView = LocalView.current

    var showLayerSheet by remember { mutableStateOf(false) }
    var showOverlaySheet by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(true) }
    var showPlaceSearch by remember { mutableStateOf(false) }
    val placeSearchScope = androidx.compose.runtime.rememberCoroutineScope()
    val placeSearchState = remember {
        PlaceSearchState(
            geocoder = com.mikotrack.data.geocoding.Geocoder(
                userAgent = "MycoTrack/${com.mikotrack.BuildConfig.VERSION_NAME} (Android)",
            ),
            scope = placeSearchScope,
        )
    }
    // The place found by the last search, marked until the camera follows the user again.
    var searchMarker by remember { mutableStateOf<GeoPoint?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var pendingDescription by remember { mutableStateOf("") }
    var pendingCategory by remember { mutableStateOf(DefaultCategory.MUSHROOM_FOUND.key) }

    // The permissions are never requested silently: the START button opens the
    // permission centre, which explains every permission before asking for it.
    var showPermissionDialog by remember { mutableStateOf(false) }

    // The state of the permissions is refreshed every time the screen comes back to
    // the foreground, because they may have been changed in the system settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRefresh by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> permissionRefresh++
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The position of the user is tracked while the map is on screen, so that the map
    // can show where the user is even before a trip has been started. The GPS is
    // released again as soon as the screen is left behind.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setPassiveTracking(true)
                Lifecycle.Event.ON_STOP -> viewModel.setPassiveTracking(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // The observer is registered after the screen has already been started in some
        // cases, so the current state has to be checked to avoid waiting for an event
        // that has already happened.
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.setPassiveTracking(true)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setPassiveTracking(false)
        }
    }

    val missingRequired = remember(permissionRefresh) {
        PermissionRequirement.missingForRecording(context)
    }
    val hasAllRequired = missingRequired.isEmpty()

    // Tapping a marker or a photograph bubble opens the details of the waypoint.
    // The binding is keyed by the controller, because the controller is restored
    // from its saved state when the screen is opened again.
    LaunchedEffect(controller, viewModel) {
        controller.onWaypointClicked = { id -> viewModel.selectWaypointById(id) }
    }

    // A long press marks the point and opens its menu (create a waypoint, directions...).
    var pressedPoint by remember { mutableStateOf<GeoPoint?>(null) }
    DisposableEffect(controller) {
        controller.onMapLongClicked = { latitude, longitude -> pressedPoint = GeoPoint(latitude, longitude) }
        onDispose {
            controller.onMapLongClicked = null
            controller.setPressedPoint(null)
        }
    }
    LaunchedEffect(pressedPoint) {
        controller.setPressedPoint(pressedPoint)
    }

    LaunchedEffect(showPhotoBubbles) {
        controller.setPhotoBubblesVisible(showPhotoBubbles)
    }

    // The habitat overlays are drawn above any base layer and survive its changes.
    LaunchedEffect(habitatLayers) {
        controller.setHabitatLayers(habitatLayers)
    }

    // The screen stays on while a trip is being recorded.
    LaunchedEffect(keepScreenOn, recording.isRecording) {
        localView.keepScreenOn = keepScreenOn && recording.isRecording
    }

    // The base layer is applied whenever the user picks another one.
    //
    // The parameters of the offline area are only relevant for the offline layer: for
    // the online layers they are kept at a fixed value, so that the style is not
    // reloaded when the offline areas finish registering. Reloading the style cancels
    // the tile requests that are in flight and the map would flash empty.
    val isOffline = layer == MapLayer.OFFLINE
    LaunchedEffect(layer, offlineSource) {
        controller.setLayer(
            layer = layer,
            offlineUrl = if (isOffline) offlineSource?.url else null,
            offlineTileSize = if (isOffline) offlineSource?.tileSize ?: 256 else 256,
            offlineMinZoom = if (isOffline) offlineSource?.minZoom ?: 0 else 0,
            offlineMaxZoom = if (isOffline) offlineSource?.maxZoom ?: 22 else 22,
        )
        // The camera is limited to the zoom levels that the source really contains.
        // An offline area only holds the tiles it was downloaded with, and zooming in
        // further would only stretch them, which makes the map look blurred.
        val limits = if (isOffline) {
            val source = offlineSource
            if (source != null) {
                source.minZoom.toDouble()..source.maxZoom.toDouble()
            } else {
                layer.minZoom.toDouble()..layer.maxZoom.toDouble()
            }
        } else {
            layer.minZoom.toDouble()..layer.maxZoom.toDouble()
        }
        controller.setZoomLimits(limits.start, limits.endInclusive)
    }

    // The category colours define the markers shown on the map.
    LaunchedEffect(categories) {
        val colors = categories.associate { it.name to it.color }
            .toMutableMap()
            .apply {
                DefaultCategory.entries.forEach { putIfAbsent(it.key, it.color) }
            }
        controller.setWaypointIcons(colors)
    }

    // Routes, waypoints and the current position are pushed to the map.
    LaunchedEffect(overlays, activeWaypoints, currentPosition, activePoints) {
        // Points are stored at the interval chosen in the settings, but the position is
        // estimated every second: the line is extended to the marker so that it never
        // lags behind the user.
        val livePosition = currentPosition?.takeIf { position ->
            recording.isRecording && activePoints.isNotEmpty() &&
                position.timestamp > activePoints.last().timestamp
        }
        val currentRoute = activePoints.map { GeoPoint(it.latitude, it.longitude) } +
            listOfNotNull(livePosition?.let { GeoPoint(it.latitude, it.longitude) })
        val waypointOverlays = buildList {
            addAll(overlays.flatMap { it.waypoints })
            addAll(
                activeWaypoints.map { waypoint ->
                    WaypointOverlay(
                        waypointId = waypoint.id,
                        tripId = waypoint.tripId,
                        position = GeoPoint(waypoint.latitude, waypoint.longitude),
                        category = waypoint.category,
                        iconId = com.mikotrack.maps.MapIconFactory.waypointIconId(waypoint.category),
                        isCurrentTrip = true,
                        imagePath = waypoint.imagePath,
                    )
                },
            )
        }
        controller.render(
            overlays = overlays + currentRouteOverlay(currentRoute),
            waypoints = waypointOverlays,
            position = currentPosition?.let { GeoPoint(it.latitude, it.longitude) },
            bearing = currentPosition?.bearing,
        )
    }

    // The camera follows the user while the "follow" mode is active. The first fix
    // also sets a useful zoom level, because the map starts at world scale.
    //
    // The flag lives in the controller, which is kept between visits to the screen,
    // so that the zoom level the user has chosen is not lost when they come back.
    LaunchedEffect(currentPosition, follow) {
        val position = currentPosition ?: return@LaunchedEffect
        if (follow) {
            controller.centerOn(
                latitude = position.latitude,
                longitude = position.longitude,
                zoom = if (controller.isCameraPlaced) null else controller.clampZoom(defaultZoom.toDouble()),
            )
            controller.markCameraPlaced()
        }
    }

    // The controls float over the content, so their measured size is used to keep the
    // map widgets, the framed route and the elevation profile clear of them.
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // In landscape the screen is too short for a bottom panel: the statistics and the
    // START button move to a panel on the right and the map keeps the full height.
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val layoutDirection = LocalLayoutDirection.current
    // The display cutout and, in landscape, the system navigation bar sit on the sides.
    val safeSides = WindowInsets.displayCutout.union(WindowInsets.navigationBars)
        .only(WindowInsetsSides.Horizontal)
        .asPaddingValues()
    val safeStart = safeSides.calculateStartPadding(layoutDirection)
    val safeEnd = safeSides.calculateEndPadding(layoutDirection)
    val sidePanelWidth = if (landscape) {
        (configuration.screenWidthDp.dp * SIDE_PANEL_FRACTION).coerceIn(SIDE_PANEL_MIN, SIDE_PANEL_MAX)
    } else {
        0.dp
    }
    // Everything that is not the side panel: the area of the map and its controls.
    val regionEnd = safeEnd + sidePanelWidth

    var topStackHeightPx by remember { mutableIntStateOf(0) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    var bottomRowHeightPx by remember { mutableIntStateOf(0) }
    val topStackHeight = with(density) { topStackHeightPx.toDp() }
    // In landscape nothing floats over the bottom of the screen: the panel is on the side.
    val bottomBarHeight = if (landscape) 0.dp else with(density) { bottomBarHeightPx.toDp() }
    val bottomBarHeightOnMap = if (landscape) 0 else bottomBarHeightPx
    var legendExpanded by rememberSaveable { mutableStateOf(false) }

    // The compass of the map is moved below the top controls, so that it never ends up
    // under the status bar, and left of the side panel in landscape.
    LaunchedEffect(topStackHeightPx, regionEnd) {
        with(density) {
            controller.setCompassTopInset(topStackHeightPx + 8.dp.roundToPx(), regionEnd.roundToPx())
        }
    }

    // The logo and the attribution of MapLibre are lifted above whatever covers the bottom
    // of the map: the bottom panel in portrait, or the zoom and action buttons when they
    // are laid out in a row (landscape and the view combined with the profile).
    LaunchedEffect(bottomBarHeightOnMap, bottomRowHeightPx, viewMode, landscape, safeStart) {
        val bottom = when {
            viewMode == MapViewMode.COMBINED -> bottomRowHeightPx
            landscape -> bottomRowHeightPx
            else -> bottomBarHeightOnMap
        }
        controller.setAttributionBottomInset(bottom, with(density) { safeStart.roundToPx() })
    }

    // Zoom actions. The zoom level of the camera is what sets how much of the
    // surroundings is visible.
    // Frames a set of points, keeping them clear of the controls that float over the map.
    val framePoints: (List<GeoPoint>) -> Unit = framePoints@{ points ->
        val bounds = com.mikotrack.maps.RouteGeometry.bounds(points) ?: return@framePoints
        if (bounds.isSinglePlace) {
            controller.centerOn(bounds.minLatitude, bounds.minLongitude, defaultZoom.toDouble())
        } else {
            with(density) {
                controller.fitBounds(
                    minLatitude = bounds.minLatitude,
                    minLongitude = bounds.minLongitude,
                    maxLatitude = bounds.maxLatitude,
                    maxLongitude = bounds.maxLongitude,
                    leftPx = (FIT_SIDE_MARGIN + safeStart).roundToPx(),
                    topPx = topStackHeightPx + FIT_EDGE_GAP.roundToPx(),
                    rightPx = (FIT_SIDE_MARGIN + regionEnd).roundToPx(),
                    // The controls cover the bottom of the map: the bottom panel in
                    // portrait, the row of buttons in landscape or beside the profile.
                    bottomPx = if (viewMode == MapViewMode.COMBINED || landscape) {
                        bottomRowHeightPx + FIT_EDGE_GAP.roundToPx()
                    } else {
                        bottomBarHeightPx + FIT_EDGE_GAP.roundToPx()
                    },
                )
            }
        }
    }
    // Another screen (e.g. the gallery or the trip detail) can ask the map to show a place
    // or a whole route: the trip is overlaid, the camera stops following the user and
    // moves there, and the waypoint opens. It works whether the screen was just created
    // or was already in memory.
    val focusRequest by MapFocusRequests.pending.collectAsStateWithLifecycle()
    LaunchedEffect(focusRequest) {
        val request = focusRequest ?: return@LaunchedEffect
        if (viewModel.viewMode.value == MapViewMode.ELEVATION) viewModel.setViewMode(MapViewMode.MAP)
        if (request.tripId != recording.tripId) viewModel.ensureOverlay(request.tripId)
        viewModel.setFollowPosition(false)
        val box = request.bounds
        if (box != null) {
            framePoints(
                listOf(
                    GeoPoint(box.minLatitude, box.minLongitude),
                    GeoPoint(box.maxLatitude, box.maxLongitude),
                ),
            )
        } else {
            val zoom = maxOf(controller.currentZoom() ?: FOCUS_ZOOM, FOCUS_ZOOM)
            controller.centerOn(request.latitude, request.longitude, controller.clampZoom(zoom))
        }
        controller.markCameraPlaced()
        request.waypointId?.let { viewModel.selectWaypointById(it) }
        MapFocusRequests.consume(request)
    }
    // A likely areas search started from the mushroom guide: frame its circle.
    val suitabilityRequest by SuitabilityFocusRequests.pending.collectAsStateWithLifecycle()
    LaunchedEffect(suitabilityRequest) {
        val search = suitabilityRequest ?: return@LaunchedEffect
        if (viewModel.viewMode.value == MapViewMode.ELEVATION) viewModel.setViewMode(MapViewMode.MAP)
        viewModel.setFollowPosition(false)
        val (southWest, northEast) = com.mikotrack.maps.SuitabilityScorer.circleBounds(
            search.latitude,
            search.longitude,
            search.radiusMeters.toDouble(),
        )
        framePoints(listOf(southWest, northEast))
        controller.markCameraPlaced()
        legendExpanded = true
        SuitabilityFocusRequests.consume(search)
    }
    // A place picked in the place search: the camera stops following the user, frames
    // the extent of the place (or centres it) and the place is marked.
    val goToPlace: (PlaceTarget) -> Unit = { target ->
        if (viewModel.viewMode.value == MapViewMode.ELEVATION) viewModel.setViewMode(MapViewMode.MAP)
        viewModel.setFollowPosition(false)
        val box = target.bounds
        val spansArea = box != null &&
            (box.maxLatitude - box.minLatitude > PLACE_MIN_SPAN_DEGREES ||
                box.maxLongitude - box.minLongitude > PLACE_MIN_SPAN_DEGREES)
        if (box != null && spansArea) {
            framePoints(
                listOf(
                    GeoPoint(box.minLatitude, box.minLongitude),
                    GeoPoint(box.maxLatitude, box.maxLongitude),
                ),
            )
        } else {
            controller.centerOn(target.latitude, target.longitude, controller.clampZoom(PLACE_ZOOM))
        }
        controller.markCameraPlaced()
        searchMarker = GeoPoint(target.latitude, target.longitude)
    }
    LaunchedEffect(searchMarker) { controller.setSearchMarker(searchMarker) }
    LaunchedEffect(follow) { if (follow) searchMarker = null }
    val fitRoute: () -> Unit = {
        val points = activePoints
        if (points.size < 2) {
            Toast.makeText(context, context.getString(R.string.map_no_route_yet), Toast.LENGTH_SHORT).show()
        } else {
            // The camera must stop following the user, otherwise it would jump back.
            viewModel.setFollowPosition(false)
            framePoints(points.map { GeoPoint(it.latitude, it.longitude) })
        }
    }

    // The "go to route" button: every tap frames the next overlaid route and, after the
    // last one, goes back to the position of the user.
    var focusedRouteId by remember { mutableStateOf<Long?>(null) }
    val focusNextRoute: () -> Unit = {
        val routes = overlays.filter { it.points.isNotEmpty() }
        val next = nextRouteFocus(focusedRouteId, routes.map { it.tripId })
        focusedRouteId = next
        val route = routes.firstOrNull { it.tripId == next }
        if (route != null) {
            viewModel.setFollowPosition(false)
            framePoints(route.points)
            Toast.makeText(context, route.name, Toast.LENGTH_SHORT).show()
        } else {
            viewModel.setFollowPosition(true)
            currentPosition?.let { controller.centerOn(it.latitude, it.longitude) }
            Toast.makeText(context, context.getString(R.string.maproute_focus_my_position), Toast.LENGTH_SHORT).show()
        }
    }
    val resetZoom: () -> Unit = { controller.zoomTo(defaultZoom.toDouble()) }
    val storeDefaultZoom: () -> Unit = {
        controller.currentZoom()?.let { zoom ->
            val level = zoom.roundToInt().toFloat()
            viewModel.setDefaultZoom(level)
            Toast.makeText(context, context.getString(R.string.map_zoom_default_set, level.toInt()), Toast.LENGTH_SHORT).show()
        }
    }

    // Camera and waypoint actions. They only make sense next to the map. Beside a short
    // map they are laid out in a row at the bottom so they stay clear of the top buttons.
    val cameraActions: @Composable (Modifier, Boolean) -> Unit = { actionsModifier, horizontal ->
        val buttons: @Composable () -> Unit = {
            if (MapButton.TAKE_PHOTO in visibleButtons) FloatingActionButton(
                onClick = {
                    if (recording.isRecording) {
                        val file = viewModel.createPhotoFile()
                        if (file != null) cameraFile = file
                    }
                },
                containerColor = if (recording.isRecording) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.take_photo))
            }
            if (MapButton.ADD_WAYPOINT in visibleButtons) FloatingActionButton(
                onClick = {
                    pendingDescription = ""
                    pendingCategory = DefaultCategory.MUSHROOM_FOUND.key
                    viewModel.addWaypointAtCurrentPosition(pendingDescription, pendingCategory)
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_waypoint))
            }
        }
        if (horizontal) {
            Row(
                modifier = actionsModifier,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) { buttons() }
        } else {
            Column(
                modifier = actionsModifier,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { buttons() }
        }
    }

    // The START/END button with the statistics toggle, shared by the bottom panel
    // (portrait) and the side panel (landscape).
    val recordingControls: @Composable () -> Unit = {
        RecordingControls(
            isRecording = recording.isRecording,
            showStats = showStats,
            showStatsToggle = MapButton.STATS in visibleButtons,
            onToggleStats = { showStats = !showStats },
            showPlaceSearch = MapButton.PLACE_SEARCH in visibleButtons,
            onPlaceSearch = { showPlaceSearch = true },
            onStart = {
                if (hasAllRequired) {
                    viewModel.startTrip(context)
                } else {
                    showPermissionDialog = true
                }
            },
            onStop = { viewModel.stopTrip(context) },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ---- Content: the map, the profile, or both ----
        when (viewMode) {
            MapViewMode.MAP -> MapCanvas(controller, Modifier.fillMaxSize())

            MapViewMode.ELEVATION -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
                ElevationPanel(
                    profile = profile,
                    selectedDistance = selectedDistance,
                    onDistanceSelected = viewModel::selectDistance,
                    compact = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 12.dp + safeStart,
                            end = 12.dp + regionEnd,
                            top = topStackHeight,
                            bottom = bottomBarHeight + 8.dp,
                        ),
                )
            }

            MapViewMode.COMBINED -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomBarHeight),
                ) {
                    MapCanvas(controller, Modifier.weight(1f).fillMaxWidth())
                    // In landscape the profile goes to the side panel: the screen is too
                    // short to stack it under the map.
                    if (!landscape) {
                        ElevationPanel(
                            profile = profile,
                            selectedDistance = selectedDistance,
                            onDistanceSelected = viewModel::selectDistance,
                            compact = true,
                            modifier = Modifier
                                .height(COMBINED_PROFILE_HEIGHT)
                                .padding(start = safeStart),
                        )
                    }
                }
            }
        }

        // ---- Controls of the map area. Every group has its own zone, so none of them
        // can cover another one in portrait or in landscape. ----
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = safeStart, end = regionEnd),
        ) {
            // Top: the row of buttons and, below it, the row of status chips.
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .onSizeChanged { topStackHeightPx = it.height }
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The buttons scroll sideways on narrow screens instead of overlapping.
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (MapButton.LAYERS in visibleButtons) {
                            MapControlButton(icon = MapButton.LAYERS.icon, label = stringResource(R.string.map_layers)) { showLayerSheet = true }
                        }
                        if (MapButton.ROUTES in visibleButtons) {
                            MapControlButton(
                                icon = MapButton.ROUTES.icon,
                                label = stringResource(R.string.map_control_routes),
                                active = overlays.isNotEmpty(),
                            ) { showOverlaySheet = true }
                        }
                        if (MapButton.ROUTE_FOCUS in visibleButtons && overlays.isNotEmpty() &&
                            viewMode != MapViewMode.ELEVATION
                        ) {
                            MapControlButton(
                                icon = MapButton.ROUTE_FOCUS.icon,
                                label = stringResource(R.string.maproute_focus_button),
                                active = focusedRouteId != null,
                                onClick = focusNextRoute,
                            )
                        }
                        if (MapButton.CENTRE in visibleButtons) {
                            MapControlButton(
                                icon = MapButton.CENTRE.icon,
                                label = stringResource(R.string.map_control_centre),
                                active = follow,
                            ) {
                                viewModel.setFollowPosition(!follow)
                                currentPosition?.let { controller.centerOn(it.latitude, it.longitude) }
                            }
                        }
                        if (MapButton.PHOTO_BUBBLES in visibleButtons && viewMode != MapViewMode.ELEVATION) {
                            MapControlButton(
                                icon = MapButton.PHOTO_BUBBLES.icon,
                                label = stringResource(
                                    if (showPhotoBubbles) R.string.map_hide_photo_bubbles else R.string.map_show_photo_bubbles,
                                ),
                                active = showPhotoBubbles,
                            ) {
                                viewModel.setShowPhotoBubbles(!showPhotoBubbles)
                            }
                        }
                        if (MapButton.CAR in visibleButtons && parkingWaypoint != null) {
                            MapControlButton(
                                icon = MapButton.CAR.icon,
                                label = stringResource(R.string.field_map_button_car),
                            ) { viewModel.showParking() }
                        }
                    }
                    if (MapButton.VIEW in visibleButtons) MapControlButton(
                        icon = when (viewMode) {
                            MapViewMode.MAP -> Icons.AutoMirrored.Filled.ShowChart
                            MapViewMode.ELEVATION -> Icons.Filled.Map
                            MapViewMode.COMBINED -> Icons.Filled.Map
                        },
                        label = stringResource(R.string.map_control_view),
                    ) {
                        viewModel.setViewMode(
                            when (viewMode) {
                                MapViewMode.MAP -> MapViewMode.COMBINED
                                MapViewMode.ELEVATION -> MapViewMode.MAP
                                MapViewMode.COMBINED -> MapViewMode.ELEVATION
                            },
                        )
                    }
                }

                // Status chips: GPS quality, distance to the car and the habitat legend.
                val carDistance = parkingDistance
                val showCarChip = recording.isRecording && MapButton.CAR in visibleButtons &&
                    carDistance != null && viewMode != MapViewMode.ELEVATION
                val showLegendChip = habitatLayers.anyVisible && viewMode != MapViewMode.ELEVATION &&
                    MapButton.HABITAT_LEGEND in visibleButtons
                val showGpsChip = MapButton.GPS_STATUS in visibleButtons
                // When the zoom buttons are laid out in a row, their level badge joins
                // the chips, because the row is often full.
                val zoomInRow = landscape || viewMode == MapViewMode.COMBINED
                val showZoomBadge = zoomInRow && viewMode != MapViewMode.ELEVATION &&
                    MapButton.ZOOM_LEVEL in visibleButtons && zoomLevel != null
                if (showCarChip || showLegendChip || showGpsChip || showZoomBadge) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (showZoomBadge) {
                            zoomLevel?.let { ZoomLevelBadge(it) }
                        }
                        if (showGpsChip) {
                            GpsStatusChip(
                                quality = gpsQuality,
                                accuracyMeters = currentPosition?.accuracy,
                                satellitesUsed = gnssSnapshot?.usedCount,
                                onClick = onOpenDiagnostics,
                            )
                        }
                        if (showCarChip && carDistance != null) {
                            CarChip(distance = carDistance, onClick = viewModel::showParking)
                        }
                        if (showLegendChip) {
                            LegendChip(
                                expanded = legendExpanded,
                                onClick = { legendExpanded = !legendExpanded },
                            )
                        }
                    }
                }
            }

            // Middle: the zone between the top stack and the bottom panel.
            val showMapControls = viewMode != MapViewMode.ELEVATION
            // Beside a short map (landscape, or above the profile) the zoom and action
            // buttons are laid out in rows along the bottom edge; otherwise in columns.
            val rowControls = landscape || viewMode == MapViewMode.COMBINED
            val middleBottom = when {
                viewMode == MapViewMode.COMBINED && !landscape -> bottomBarHeight + COMBINED_PROFILE_HEIGHT
                else -> bottomBarHeight
            }
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(top = topStackHeight, bottom = middleBottom),
            ) {
                val zoneHeight = maxHeight
                if (showMapControls && rowControls) {
                    // A row of zoom buttons on the left and the actions on the right,
                    // along the bottom of the map.
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .onSizeChanged { bottomRowHeightPx = it.height }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        // The zoom buttons take all the width the actions leave free,
                        // and only scroll when even that is not enough.
                        Box(Modifier.weight(1f)) {
                            MapZoomControls(
                                horizontal = true,
                                visibleButtons = visibleButtons,
                                zoomLevel = zoomLevel,
                                onZoomIn = controller::zoomIn,
                                onZoomOut = controller::zoomOut,
                                onResetZoom = resetZoom,
                                onStoreDefaultZoom = storeDefaultZoom,
                                onFitRoute = fitRoute,
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            )
                        }
                        cameraActions(Modifier, true)
                    }
                } else if (showMapControls) {
                    // A column of zoom buttons on the left and of actions on the right,
                    // which scroll when the zone is shorter than the buttons. A strip is
                    // left free at the bottom for the attribution of the map.
                    MapZoomControls(
                        horizontal = false,
                        visibleButtons = visibleButtons,
                        zoomLevel = zoomLevel,
                        onZoomIn = controller::zoomIn,
                        onZoomOut = controller::zoomOut,
                        onResetZoom = resetZoom,
                        onStoreDefaultZoom = storeDefaultZoom,
                        onFitRoute = fitRoute,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp, bottom = ATTRIBUTION_STRIP)
                            .heightIn(max = (zoneHeight - ATTRIBUTION_STRIP).coerceAtLeast(0.dp))
                            .verticalScroll(rememberScrollState()),
                    )
                    cameraActions(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp, bottom = ATTRIBUTION_STRIP)
                            .heightIn(max = (zoneHeight - ATTRIBUTION_STRIP).coerceAtLeast(0.dp))
                            .verticalScroll(rememberScrollState()),
                        false,
                    )
                }

                // The banner of a reached waypoint and the expanded legend sit in the
                // centre of the zone, between the columns of buttons (or above the rows).
                val sideClearance = if (showMapControls && !rowControls) SIDE_COLUMN_CLEARANCE else 12.dp
                val bottomClearance = if (showMapControls && rowControls) {
                    with(density) { bottomRowHeightPx.toDp() }
                } else {
                    ATTRIBUTION_STRIP
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(start = sideClearance, end = sideClearance, bottom = bottomClearance)
                        .heightIn(max = (zoneHeight - bottomClearance).coerceAtLeast(0.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    proximityHit?.let { hit ->
                        ProximityBanner(
                            hit = hit,
                            onShow = viewModel::showProximityHit,
                            onDismiss = viewModel::dismissProximityHit,
                        )
                    }
                    if (legendExpanded && !landscape && habitatLayers.anyVisible && viewMode != MapViewMode.ELEVATION &&
                        MapButton.HABITAT_LEGEND in visibleButtons
                    ) {
                        HabitatLegendCard(
                            layers = habitatLayers,
                            expanded = true,
                            onToggle = { legendExpanded = false },
                            modifier = Modifier.weight(1f, fill = false),
                            onAspectClassesChange = viewModel::setHabitatAspectClasses,
                            onHiddenTreesChange = viewModel::setHabitatHiddenTrees,
                            onHiddenSoilChange = viewModel::setHabitatHiddenSoil,
                            onSuitabilityChange = viewModel::setSuitabilityLayer,
                            onSuitabilityClear = viewModel::clearSuitabilitySearch,
                            onRainChange = viewModel::setHabitatRainLayer,
                        )
                    }
                }
            }

            // Bottom panel in portrait, with the statistics and the recording controls.
            // It sits right on top of the navigation bar of the application, which
            // already includes the system inset.
            if (!landscape) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { bottomBarHeightPx = it.height },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showStats) {
                        StatsPanel(
                            stats = recording.stats,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    recordingControls()
                }
            }
        }

        // Side panel in landscape: statistics on top (scrollable) and the recording
        // controls at the bottom, left of the system navigation bar.
        if (landscape) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = safeEnd)
                    .width(sidePanelWidth)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .statusBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // The map is too short in landscape for the legend, so it opens here.
                    if (legendExpanded && habitatLayers.anyVisible && viewMode != MapViewMode.ELEVATION &&
                        MapButton.HABITAT_LEGEND in visibleButtons
                    ) {
                        HabitatLegendCard(
                            layers = habitatLayers,
                            expanded = true,
                            onToggle = { legendExpanded = false },
                            modifier = Modifier.fillMaxWidth(),
                            onAspectClassesChange = viewModel::setHabitatAspectClasses,
                            onHiddenTreesChange = viewModel::setHabitatHiddenTrees,
                            onHiddenSoilChange = viewModel::setHabitatHiddenSoil,
                            onSuitabilityChange = viewModel::setSuitabilityLayer,
                            onSuitabilityClear = viewModel::clearSuitabilitySearch,
                            onRainChange = viewModel::setHabitatRainLayer,
                        )
                    }
                    // The profile next to the map, in the combined view.
                    if (viewMode == MapViewMode.COMBINED) {
                        ElevationPanel(
                            profile = profile,
                            selectedDistance = selectedDistance,
                            onDistanceSelected = viewModel::selectDistance,
                            compact = true,
                            modifier = Modifier.height(COMBINED_PROFILE_HEIGHT_LANDSCAPE),
                        )
                    }
                    if (showStats) {
                        StatsPanel(stats = recording.stats, columns = 2)
                    }
                }
                recordingControls()
            }
        }

        // The in-application camera.
        cameraFile?.let { file ->
            CameraCaptureScreen(
                outputFile = file,
                onCaptured = { captured ->
                    cameraFile = null
                    viewModel.addPhotoWaypoint(captured, pendingDescription, pendingCategory)
                },
                onCancel = { cameraFile = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // Bottom sheets.
    if (showLayerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLayerSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            LayerSheet(
                selected = layer,
                onSelect = {
                    viewModel.selectLayer(it)
                    showLayerSheet = false
                },
                habitatLayers = habitatLayers,
                onHabitatAspectChange = viewModel::setHabitatAspectLayer,
                onHabitatTreesChange = viewModel::setHabitatTreesLayer,
                onHabitatSoilChange = viewModel::setHabitatSoilLayer,
                onSuitabilityChange = viewModel::setSuitabilityLayer,
                onHabitatRainChange = viewModel::setHabitatRainLayer,
                onHabitatRainWindowChange = viewModel::setHabitatRainWindow,
            )
        }
    }

    if (showOverlaySheet) {
        ModalBottomSheet(
            onDismissRequest = { showOverlaySheet = false },
            // Fully expanded, so the alarm settings and the list of trips fit at once.
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            OverlaySheet(
                trips = overlayableTrips,
                selectedIds = overlayIds,
                colors = overlays.associate { it.tripId to it.color },
                onToggle = viewModel::toggleOverlay,
                onClearAll = viewModel::clearOverlays,
                alarmEnabled = proximityAlarmEnabled,
                alarmRadius = proximityRadius,
                onAlarmEnabledChange = viewModel::setProximityAlarmEnabled,
                onAlarmRadiusChange = viewModel::setProximityRadius,
            )
        }
    }

    selectedWaypoint?.let { waypoint ->
        WaypointSheet(
            waypoint = waypoint,
            categories = categories.map { it.name },
            distance = selectedWaypointDistance,
            tripName = selectedWaypointTripName,
            onDismiss = { viewModel.selectWaypoint(null) },
            onSave = { description, category ->
                viewModel.updateWaypoint(waypoint.id, description, category)
                viewModel.selectWaypoint(null)
            },
            onDelete = {
                viewModel.deleteWaypoint(waypoint.id)
            },
        )
    }

    // The place search: by name, by coordinates, or the address of the user.
    if (showPlaceSearch) {
        PlaceSearchSheet(
            state = placeSearchState,
            position = currentPosition,
            searchCenter = if (follow) {
                currentPosition?.let { GeoPoint(it.latitude, it.longitude) } ?: controller.cameraTarget()
            } else {
                controller.cameraTarget() ?: currentPosition?.let { GeoPoint(it.latitude, it.longitude) }
            },
            onDismiss = { showPlaceSearch = false },
            onGoTo = { target ->
                showPlaceSearch = false
                goToPlace(target)
            },
            onOpenPointMenu = { target ->
                showPlaceSearch = false
                goToPlace(target)
                pressedPoint = GeoPoint(target.latitude, target.longitude)
            },
        )
    }

    // The menu of a long-pressed point.
    val allTrips by viewModel.trips.collectAsStateWithLifecycle()
    pressedPoint?.let { point ->
        val recordingTrip = recording.tripId?.let { id -> allTrips.firstOrNull { it.id == id } }
        val candidates = remember(allTrips, overlayIds, recording.tripId) {
            waypointTripCandidates(allTrips.filter { it.id != recording.tripId }, overlayIds)
        }
        MapPointSheet(
            latitude = point.latitude,
            longitude = point.longitude,
            distanceMeters = currentPosition?.let {
                com.mikotrack.core.GeoUtils.distanceMeters(it.latitude, it.longitude, point.latitude, point.longitude)
            },
            recordingTrip = recordingTrip,
            candidates = candidates,
            overlaidIds = overlayIds.toSet(),
            onDismiss = { pressedPoint = null },
            onCreateWaypoint = { trip, category, description ->
                viewModel.addWaypointAt(trip.id, point.latitude, point.longitude, description, category)
                pressedPoint = null
                Toast.makeText(
                    context,
                    context.getString(R.string.maproute_waypoint_saved, trip.name),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onGoThere = {
                pressedPoint = null
                openWalkingDirections(context, point.latitude, point.longitude)
            },
            onCopyCoordinates = { copyCoordinates(context, point.latitude, point.longitude) },
            habitatLayers = habitatLayers,
        )
    }

    // The permissions that are missing to record a trip are explained before the user
    // is sent to the permission centre.
    if (showPermissionDialog) {
        MissingPermissionsDialog(
            missing = missingRequired,
            onGrant = {
                showPermissionDialog = false
                onOpenPermissions()
            },
            onDismiss = { showPermissionDialog = false },
        )
    }

    recoverableTrip?.let { trip ->
        ConfirmationDialog(
            title = stringResource(R.string.recording_ui_recover_title),
            message = stringResource(R.string.recording_ui_recover_message, trip.name),
            confirmLabel = stringResource(R.string.recording_ui_resume),
            dismissLabel = stringResource(R.string.recording_ui_discard),
            onConfirm = { viewModel.resumeTrip(context) },
            onDismiss = { viewModel.discardRecoverableTrip() },
            // Closing the trip must be a deliberate choice: a stray tap outside the
            // dialog would otherwise stop the recording.
            onDismissRequest = {},
        )
    }
}

/** The live statistics of the recording, laid out in rows of [columns] tiles. */
@Composable
private fun StatsPanel(
    stats: com.mikotrack.domain.model.TripStats,
    modifier: Modifier = Modifier,
    columns: Int = 3,
) {
    val tiles: List<Triple<String, String, Color?>> = listOf(
        Triple(stringResource(R.string.map_stat_time), Formatters.duration(stats.elapsedMillis), null),
        Triple(stringResource(R.string.stat_distance), Formatters.distance(stats.distanceMeters), null),
        Triple(stringResource(R.string.stat_speed), Formatters.speed(stats.currentSpeedMps), Amber),
        Triple(stringResource(R.string.stat_altitude), Formatters.altitude(stats.currentAltitude), null),
        Triple(stringResource(R.string.stat_gain), Formatters.elevationDelta(stats.elevationGain), null),
        Triple(stringResource(R.string.stat_loss), Formatters.elevationDelta(-stats.elevationLoss), null),
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tiles.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, value, accent) ->
                        if (accent != null) {
                            StatTile(label = label, value = value, modifier = Modifier.weight(1f), accent = accent)
                        } else {
                            StatTile(label = label, value = value, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** The big START and END buttons, between the statistics toggle and the place search. */
@Composable
private fun RecordingControls(
    isRecording: Boolean,
    showStats: Boolean,
    showStatsToggle: Boolean,
    onToggleStats: () -> Unit,
    showPlaceSearch: Boolean,
    onPlaceSearch: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // The side panel of landscape is narrow: the margins shrink so START stays wide.
        val compact = maxWidth < RECORDING_CONTROLS_COMPACT_WIDTH
        val gap = if (compact) 8.dp else 12.dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = if (compact) 8.dp else 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showStatsToggle) RecordingSideButton(
                icon = MapButton.STATS.icon,
                description = stringResource(
                    if (showStats) R.string.map_hide_statistics else R.string.map_show_statistics,
                ),
                onClick = onToggleStats,
            )

            if (isRecording) {
                HoldToStopButton(
                    onStop = onStop,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                )
            } else {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    contentPadding = if (compact) PaddingValues(horizontal = 8.dp) else ButtonDefaults.ContentPadding,
                ) {
                    if (!compact) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(
                        stringResource(R.string.start_recording),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }

            if (showPlaceSearch) RecordingSideButton(
                icon = MapButton.PLACE_SEARCH.icon,
                description = MapButton.PLACE_SEARCH.label,
                onClick = onPlaceSearch,
            )
        }
    }
}

/** The square buttons on both sides of START / END. */
@Composable
private fun RecordingSideButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(imageVector = icon, contentDescription = description)
    }
}

/** A round button used in the top control row. */
@Composable
private fun MapControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            },
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(12.dp).size(22.dp),
        )
    }
}

/**
 * The zoom controls of the map: zoom in, zoom out, back to the default zoom level and
 * frame the whole route. A long press on the default zoom button stores the current
 * zoom level as the new default.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MapZoomControls(
    horizontal: Boolean,
    visibleButtons: Set<MapButton>,
    zoomLevel: Double?,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onStoreDefaultZoom: () -> Unit,
    onFitRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val buttons: @Composable () -> Unit = {
        if (MapButton.ZOOM_IN in visibleButtons) {
            MapZoomButton(MapButton.ZOOM_IN.icon, stringResource(R.string.map_zoom_in), onClick = onZoomIn)
        }
        if (MapButton.ZOOM_OUT in visibleButtons) {
            MapZoomButton(MapButton.ZOOM_OUT.icon, stringResource(R.string.map_zoom_out), onClick = onZoomOut)
        }
        if (MapButton.ZOOM_DEFAULT in visibleButtons) MapZoomButton(
            icon = MapButton.ZOOM_DEFAULT.icon,
            label = stringResource(R.string.map_zoom_default_long_press),
            onClick = onResetZoom,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onStoreDefaultZoom()
            },
        )
        if (MapButton.ZOOM_FIT in visibleButtons) {
            MapZoomButton(MapButton.ZOOM_FIT.icon, stringResource(R.string.map_zoom_fit), onClick = onFitRoute)
        }
    }
    val level: @Composable () -> Unit = {
        if (MapButton.ZOOM_LEVEL in visibleButtons && zoomLevel != null) {
            ZoomLevelBadge(zoomLevel)
        }
    }
    if (horizontal) {
        // In a row the zoom level goes to the status chips at the top, where it has room.
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            buttons()
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            level()
            buttons()
        }
    }
}

/**
 * The zoom level of the camera, so the user can tell how close the map is zoomed in.
 */
@Composable
private fun ZoomLevelBadge(zoomLevel: Double) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ) {
        Text(
            text = "z${zoomLevel.roundToInt()}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MapZoomButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        modifier = Modifier
            .size(ZOOM_BUTTON_SIZE)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * The elevation profile with its statistics.
 *
 * In [compact] mode, used next to the map, the statistics are condensed into a single
 * line so the chart keeps most of the available height.
 */
@Composable
private fun ElevationPanel(
    profile: com.mikotrack.domain.model.ElevationProfile,
    selectedDistance: Double?,
    onDistanceSelected: (Double) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val selectedAltitude = selectedDistance?.let { profile.altitudeAtDistance(it) }
    val headerValue = if (selectedDistance != null && selectedAltitude != null) {
        stringResource(
            R.string.map_selected_altitude,
            Formatters.distance(selectedDistance),
            Formatters.altitude(selectedAltitude),
        )
    } else {
        "${Formatters.altitude(profile.minAltitude)} \u2013 ${Formatters.altitude(profile.maxAltitude)}"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        ),
        shape = if (compact) {
            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        } else {
            RoundedCornerShape(20.dp)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (compact) 12.dp else 16.dp,
                    bottom = 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.elevation_profile), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = headerValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (compact) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CompactStat(stringResource(R.string.stat_gain), Formatters.elevationDelta(profile.elevationGain))
                    CompactStat(stringResource(R.string.stat_loss), Formatters.elevationDelta(-profile.elevationLoss))
                    CompactStat(stringResource(R.string.stat_distance), Formatters.distance(profile.totalDistance))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        label = stringResource(R.string.stat_gain),
                        value = Formatters.elevationDelta(profile.elevationGain),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.stat_loss),
                        value = Formatters.elevationDelta(-profile.elevationLoss),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.stat_distance),
                        value = Formatters.distance(profile.totalDistance),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            ElevationProfileView(
                profile = profile,
                currentDistance = selectedDistance,
                onDistanceSelected = onDistanceSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 120.dp),
            )
        }
    }
}

/** A label and a value on one line, used by the compact elevation panel. */
@Composable
private fun CompactStat(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Lets the user pick the base map layer and switch the habitat overlays on or off. */
@Composable
private fun LayerSheet(
    selected: MapLayer,
    onSelect: (MapLayer) -> Unit,
    habitatLayers: com.mikotrack.maps.HabitatLayers = com.mikotrack.maps.HabitatLayers(),
    onHabitatAspectChange: (Boolean) -> Unit = {},
    onHabitatTreesChange: (Boolean) -> Unit = {},
    onHabitatSoilChange: (Boolean) -> Unit = {},
    onSuitabilityChange: (Boolean) -> Unit = {},
    onHabitatRainChange: (Boolean) -> Unit = {},
    onHabitatRainWindowChange: (Int) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // The list of layers is longer than a short screen, so it is scrollable to
            // keep every entry reachable.
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.map_layer),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        MapLayer.entries.forEach { layer ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(layer) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(stringResource(layer.labelRes), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(layer.descriptionRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (layer == selected) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.map_selected))
                }
            }
        }
        HabitatLayerSection(
            layers = habitatLayers,
            onAspectChange = onHabitatAspectChange,
            onTreesChange = onHabitatTreesChange,
            onSoilChange = onHabitatSoilChange,
            onSuitabilityChange = onSuitabilityChange,
            onRainChange = onHabitatRainChange,
            onRainWindowChange = onHabitatRainWindowChange,
        )
    }
}

/**
 * Lets the user choose the previous trips that are drawn on top of the map, and configure
 * the alarm that sounds when one of their waypoints is reached.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlaySheet(
    trips: List<Trip>,
    selectedIds: List<Long>,
    colors: Map<Long, Int>,
    onToggle: (Long) -> Unit,
    onClearAll: () -> Unit,
    alarmEnabled: Boolean,
    alarmRadius: Int,
    onAlarmEnabledChange: (Boolean) -> Unit,
    onAlarmRadiusChange: (Int) -> Unit,
) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val dateFormat = remember(locale) { SimpleDateFormat("dd MMM yyyy", locale) }
    val activeCount = trips.count { it.id in selectedIds }

    // A single list holds the settings and the trips, so every trip can be reached by
    // scrolling and the last one is never hidden behind the system navigation bar.
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues().let {
            PaddingValues(bottom = it.calculateBottomPadding() + 24.dp)
        },
    ) {
        item(key = "header") {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.overlay_routes),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.map_overlay_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (activeCount > 0) {
                        TextButton(onClick = onClearAll) { Text(stringResource(R.string.map_overlay_hide_all)) }
                    }
                }

                // The proximity alarm only makes sense for the trips shown on the map.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlarmEnabledChange(!alarmEnabled) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = if (alarmEnabled) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                        contentDescription = null,
                        tint = if (alarmEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.map_proximity_alarm), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = stringResource(R.string.map_proximity_alarm_hint, alarmRadius),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = alarmEnabled, onCheckedChange = onAlarmEnabledChange)
                }
                if (alarmEnabled) {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PROXIMITY_RADIUS_OPTIONS.forEach { radius ->
                            FilterChip(
                                selected = alarmRadius == radius,
                                onClick = { onAlarmRadiusChange(radius) },
                                label = { Text(Formatters.distance(radius.toDouble())) },
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
        }
        if (trips.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        items(trips, key = { it.id }) { trip ->
            val selected = trip.id in selectedIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(trip.id) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The dot has the colour of the route on the map, so each line can
                // be matched with its trip.
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            (colors[trip.id] ?: trip.color)?.let { Color(it) }
                                ?: MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = trip.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOf(
                            dateFormat.format(Date(trip.startTime)),
                            Formatters.distance(trip.distanceMeters),
                            pluralStringResource(R.plurals.map_overlay_waypoints, trip.waypointCount, trip.waypointCount),
                        ).joinToString(" \u00B7 "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = selected, onCheckedChange = { onToggle(trip.id) })
            }
        }
    }
}

/** The radius options of the proximity alarm, in metres. */
private val PROXIMITY_RADIUS_OPTIONS = listOf(15, 30, 50, 100)

/** A chip that shows or hides the colour keys of the habitat layers. */
@Composable
private fun LegendChip(expanded: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (expanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = if (expanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.habitat_legend_title), style = MaterialTheme.typography.labelLarge)
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (expanded) R.string.habitat_legend_collapse else R.string.habitat_legend_expand,
                ),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Tells the user that a waypoint of a previous trip is close. */
@Composable
private fun ProximityBanner(
    hit: com.mikotrack.domain.ProximityHit,
    onShow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The banner disappears on its own, the alarm has already been heard.
    LaunchedEffect(hit) {
        kotlinx.coroutines.delay(PROXIMITY_BANNER_MILLIS)
        onDismiss()
    }
    val label = hit.target.description.ifBlank { categoryLabel(hit.target.category) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        // The buttons go on their own row so that the text gets the whole width of the
        // card, which is kept free of the columns of map buttons.
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.proximity_notification_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.proximity_notification_text,
                            label,
                            Formatters.distance(hit.distanceMeters),
                            hit.target.tripName,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onShow) { Text(stringResource(R.string.map_proximity_show)) }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.map_close))
                }
            }
        }
    }
}

private const val PROXIMITY_BANNER_MILLIS = 30_000L

/**
 * How far the waypoint is from the position of the user: the straight line distance, the
 * direction to follow and, when it is known, the distance along the route.
 */
@Composable
private fun DistancePanel(distance: com.mikotrack.domain.WaypointDistance) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.stat_distance),
                value = Formatters.distance(distance.straightLineMeters),
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Timeline,
            )
            StatTile(
                label = stringResource(R.string.map_direction),
                value = stringArrayResource(R.array.format_compass_points)[
                    Formatters.compassIndex(distance.bearingDegrees)
                ],
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Explore,
            )
        }
        distance.alongTrackMeters?.let { along ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = stringResource(R.string.map_along_route),
                    value = Formatters.distance(along),
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Timeline,
                )
                distance.elevationDifferenceMeters?.let { delta ->
                    StatTile(
                        label = stringResource(R.string.map_altitude_difference),
                        value = Formatters.elevationDelta(delta),
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Terrain,
                    )
                }
            }
        }
    }
}

/** Shows and edits the details of a waypoint. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaypointSheet(
    waypoint: Waypoint,
    categories: List<String>,
    distance: com.mikotrack.domain.WaypointDistance?,
    tripName: String?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
) {
    var description by remember(waypoint.id) { mutableStateOf(waypoint.description) }
    var category by remember(waypoint.id) { mutableStateOf(waypoint.category) }
    var expanded by remember { mutableStateOf(false) }
    var showFullPhoto by remember(waypoint.id) { mutableStateOf(false) }
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val photoDescription = stringResource(R.string.waypoint_photo_description)
    val dateTimeFormat = remember(locale) {
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, locale)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            waypoint.imagePath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = waypoint.description.ifBlank { photoDescription },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showFullPhoto = true },
                )
            }
            Text(stringResource(R.string.waypoint_title), style = MaterialTheme.typography.titleLarge)
            tripName?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = Formatters.coordinates(waypoint.latitude, waypoint.longitude),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${Formatters.altitude(waypoint.altitude)} \u00B7 ${dateTimeFormat.format(java.util.Date(waypoint.timestamp))}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (distance != null) {
                DistancePanel(distance = distance)
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.description)) },
                trailingIcon = {
                    com.mikotrack.ui.common.DictationButton { dictated ->
                        description = com.mikotrack.ui.common.appendDictation(description, dictated)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
            )

            Text(stringResource(R.string.category), style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                categories.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { category = option }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ColorDot(Color(DefaultCategory.colorFor(option)))
                        Text(categoryLabel(option), style = MaterialTheme.typography.bodyLarge)
                        if (option == category) {
                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.map_selected))
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onSave(description, category) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.save)) }
                FilledTonalButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    }

    if (showFullPhoto) {
        waypoint.imagePath?.let { path ->
            FullScreenPhoto(
                path = path,
                description = waypoint.description,
                onDismiss = { showFullPhoto = false },
            )
        }
    }
}

/** Shows a photograph over the whole screen. It can be zoomed and moved with two fingers. */
@Composable
private fun FullScreenPhoto(
    path: String,
    description: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val photoDescription = stringResource(R.string.waypoint_photo_description)
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                },
        ) {
            AsyncImage(
                model = File(path),
                contentDescription = description.ifBlank { photoDescription },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.map_close), tint = Color.White)
            }
        }
    }
}

/** Builds the overlay that draws the route of the trip that is being recorded. */
private fun currentRouteOverlay(points: List<GeoPoint>): RouteOverlay =
    RouteOverlay(
        tripId = com.mikotrack.maps.MapOverlayManager.CURRENT_ROUTE_ID,
        name = "Current trip",
        color = 0xFF7CB342.toInt(),
        points = points,
    )

