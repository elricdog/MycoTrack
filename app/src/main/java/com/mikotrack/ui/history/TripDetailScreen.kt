package com.mikotrack.ui.history

import com.mikotrack.ui.common.categoryLabel
import androidx.compose.ui.res.stringResource
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.os.ConfigurationCompat
import com.mikotrack.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikotrack.core.Formatters
import com.mikotrack.domain.model.Waypoint
import com.mikotrack.export.ExportManager
import com.mikotrack.maps.GeoPoint
import com.mikotrack.maps.MapIconFactory
import com.mikotrack.maps.MapLayer
import com.mikotrack.maps.RouteOverlay
import com.mikotrack.maps.WaypointOverlay
import com.mikotrack.ui.common.ColorDot
import com.mikotrack.ui.common.ConfirmationDialog
import com.mikotrack.ui.common.StatTile
import com.mikotrack.ui.elevation.ElevationProfileView
import com.mikotrack.ui.map.MapCanvas
import com.mikotrack.ui.map.MapController
import com.mikotrack.data.repository.DefaultCategory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** The review screen of a recorded trip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    viewModel: TripDetailViewModel,
    tripId: Long,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the map, once the trip has been overlaid and a focus request posted. */
    onShowOnMap: () -> Unit = {},
) {
    val trip by viewModel.trip.collectAsStateWithLifecycle()
    val points by viewModel.points.collectAsStateWithLifecycle()
    val waypoints by viewModel.waypoints.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val exportLayer by viewModel.exportLayer.collectAsStateWithLifecycle()
    val exportOfflineSource by viewModel.exportOfflineSource.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val ranking by viewModel.ranking.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val showOnMainMap: () -> Unit = {
        viewModel.showOnMainMap(
            onReady = onShowOnMap,
            onEmpty = {
                android.widget.Toast.makeText(
                    context,
                    R.string.tripanalysis_no_location,
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }
    // The camera of the map is saved, so that the review of the trip keeps the view
    // the user chose when the screen is recreated.
    val controller = rememberSaveable(saver = MapController.Saver) { MapController() }
    val exportManager = remember { ExportManager(context) }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var notes by remember(trip?.id) { mutableStateOf(trip?.notes ?: "") }

    LaunchedEffect(tripId) { viewModel.load(tripId) }

    // The map of the review uses the base layer that the user has chosen, and the
    // camera is limited to the zoom levels that the source really contains.
    LaunchedEffect(exportLayer, exportOfflineSource) {
        val isOffline = exportLayer == MapLayer.OFFLINE
        val source = exportOfflineSource
        controller.setLayer(
            layer = exportLayer,
            offlineUrl = if (isOffline) source?.url else null,
            offlineTileSize = if (isOffline) source?.tileSize ?: 256 else 256,
            offlineMinZoom = if (isOffline) source?.minZoom ?: 0 else 0,
            offlineMaxZoom = if (isOffline) source?.maxZoom ?: 22 else 22,
        )
        val limits = if (isOffline && source != null) {
            source.minZoom.toDouble()..source.maxZoom.toDouble()
        } else {
            exportLayer.minZoom.toDouble()..exportLayer.maxZoom.toDouble()
        }
        controller.setZoomLimits(limits.start, limits.endInclusive)
    }

    // The whole route is framed as soon as it is loaded, so that it is visible at
    // once instead of at world scale.
    var routeFramed by remember(tripId) { mutableStateOf(false) }
    LaunchedEffect(points, routeFramed) {
        if (routeFramed || points.size < 2) return@LaunchedEffect
        routeFramed = true
        controller.fitBounds(
            minLatitude = points.minOf { it.latitude },
            minLongitude = points.minOf { it.longitude },
            maxLatitude = points.maxOf { it.latitude },
            maxLongitude = points.maxOf { it.longitude },
        )
    }

    // The whole route is drawn in blue and the part already played back in red on top
    // of it, so the red line grows over the blue one while the playback advances.
    LaunchedEffect(points, waypoints, playback.index) {
        val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
        val waypointOverlays = waypoints.map { waypoint ->
            WaypointOverlay(
                waypointId = waypoint.id,
                tripId = waypoint.tripId,
                position = GeoPoint(waypoint.latitude, waypoint.longitude),
                category = waypoint.category,
                iconId = MapIconFactory.waypointIconId(waypoint.category),
                isCurrentTrip = true,
                imagePath = waypoint.imagePath,
            )
        }
        val fullRoute = RouteOverlay(
            tripId = FULL_ROUTE_ID,
            name = trip?.name.orEmpty(),
            color = PENDING_ROUTE_COLOR,
            points = geoPoints,
        )
        val playedRoute = RouteOverlay(
            tripId = PLAYED_ROUTE_ID,
            name = trip?.name.orEmpty(),
            color = PLAYED_ROUTE_COLOR,
            points = if (playback.index > 0) geoPoints.take(playback.index + 1) else emptyList(),
        )
        val current = points.getOrNull(playback.index)
        controller.render(
            overlays = listOf(fullRoute, playedRoute),
            waypoints = waypointOverlays,
            position = current?.let { GeoPoint(it.latitude, it.longitude) },
            bearing = current?.bearing,
        )
    }

    Scaffold(
        modifier = modifier,
        // The top bar applies the status bar inset on its own, so the content of the
        // tabs does not need any additional padding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.trip_detail_back),
                        )
                    }
                },
                title = {
                    Text(
                        text = trip?.name ?: stringResource(R.string.trip_detail_default_title),
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                actions = {
                    IconButton(onClick = showOnMainMap) {
                        Icon(Icons.Filled.Map, contentDescription = stringResource(R.string.tripanalysis_show_on_map))
                    }
                    IconButton(
                        onClick = { showExportDialog = true },
                        enabled = !isExporting,
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.export))
                    }
                    TripColorDot(
                        color = trip?.color,
                        size = 22.dp,
                        onClick = { showColorDialog = true },
                    )
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rename))
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // The tabs scroll instead of wrapping their labels when the font is large.
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                listOf(
                    R.string.trip_detail_tab_map,
                    R.string.trip_detail_tab_profile,
                    R.string.trip_detail_tab_statistics,
                    R.string.tripanalysis_tab,
                    R.string.trip_detail_tab_timeline,
                ).forEachIndexed { index, titleRes ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(stringResource(titleRes), maxLines = 1, softWrap = false) },
                    )
                }
            }

            when (selectedTab) {
                0 -> MapTab(
                    controller = controller,
                    points = points,
                    playback = playback,
                    layer = exportLayer,
                    onSelectLayer = viewModel::setLayer,
                    onPlayToggle = viewModel::togglePlayback,
                    onSeek = viewModel::seekToIndex,
                    onSpeedChange = viewModel::setPlaybackSpeed,
                    onShowOnMainMap = showOnMainMap,
                )

                1 -> ProfileTab(
                    profile = profile,
                    selectedDistance = profile.points.getOrNull(playback.index)?.distanceMeters,
                    onDistanceSelected = viewModel::seekToDistance,
                    trip = trip,
                )

                2 -> StatisticsTab(stats = stats, trip = trip)

                3 -> TripAnalysisTab(
                    analysis = analysis,
                    ranking = ranking,
                    onShowOnMap = showOnMainMap,
                )

                else -> TimelineTab(
                    waypoints = waypoints,
                    notes = notes,
                    onEditNotes = {
                        notes = it
                        viewModel.updateNotes(it)
                    },
                )
            }
        }
    }

    if (showEditDialog) {
        var name by remember { mutableStateOf(trip?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.trip_name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showColorDialog = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.tripcolor_title),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TripColorDot(color = trip?.color, size = 24.dp, onClick = { showColorDialog = true })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(name)
                    showEditDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showColorDialog) {
        TripColorDialog(
            current = trip?.color,
            onSelect = { color ->
                viewModel.setColor(color)
                showColorDialog = false
            },
            onDismiss = { showColorDialog = false },
        )
    }

    if (showNotesDialog) {
        AlertDialog(
            onDismissRequest = { showNotesDialog = false },
            title = { Text(stringResource(R.string.trip_notes)) },
            text = {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    minLines = 4,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateNotes(notes)
                    showNotesDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNotesDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            onExport = { format ->
                showExportDialog = false
                val currentTrip = trip ?: return@ExportDialog
                scope.launch {
                    isExporting = true
                    exportError = null
                    try {
                        val file = exportManager.export(
                            format = format,
                            trip = currentTrip,
                            points = points,
                            waypoints = waypoints,
                            stats = stats,
                            profile = profile,
                            layer = exportLayer,
                            offlineSource = exportOfflineSource,
                        )
                        context.startActivity(
                            Intent.createChooser(exportManager.shareIntent(file), context.getString(R.string.trip_detail_share_chooser)),
                        )
                    } catch (cancellation: kotlinx.coroutines.CancellationException) {
                        throw cancellation
                    } catch (error: Throwable) {
                        android.util.Log.e("MikoTrack", "The export of the trip failed", error)
                        exportError = error.message
                            ?: context.getString(R.string.export_msg_generic_error, error.javaClass.simpleName)
                    } finally {
                        isExporting = false
                    }
                }
            },
        )
    }

    if (isExporting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.export_msg_exporting)) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text(stringResource(R.string.trip_detail_preparing_file))
                }
            },
            confirmButton = {},
        )
    }

    if (exportError != null) {
        AlertDialog(
            onDismissRequest = { exportError = null },
            title = { Text(stringResource(R.string.export_failed)) },
            text = { Text(exportError.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { exportError = null }) { Text(stringResource(R.string.export_msg_close)) }
            },
        )
    }

    if (showDeleteDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.delete_trip_title),
            message = stringResource(R.string.trip_detail_delete_message),
            confirmLabel = stringResource(R.string.delete),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

/** The map with the playback controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapTab(
    controller: MapController,
    points: List<com.mikotrack.domain.model.TrackPoint>,
    playback: PlaybackState,
    layer: MapLayer,
    onSelectLayer: (MapLayer) -> Unit,
    onPlayToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onShowOnMainMap: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            MapCanvas(controller, Modifier.fillMaxSize())

            // MapLibre hangs the compass from the top-right corner, right over the layer
            // button, so it is pushed below it.
            val density = LocalDensity.current
            LaunchedEffect(Unit) {
                with(density) { controller.setCompassTopInset(68.dp.roundToPx()) }
            }

            // Loads the route on the main map, among the overlaid routes.
            FilledTonalButton(
                onClick = onShowOnMainMap,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            ) {
                Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.tripanalysis_load_on_map),
                    modifier = Modifier.padding(start = 6.dp),
                    maxLines = 1,
                )
            }

            // The base layer of the review can be changed without leaving the trip.
            LayerMenu(
                layer = layer,
                onSelect = onSelectLayer,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val lastIndex = (points.size - 1).coerceAtLeast(0)
                val elapsed = if (points.isNotEmpty()) {
                    points[playback.index.coerceIn(0, lastIndex)].timestamp - points.first().timestamp
                } else {
                    0L
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledIconButton(
                        onClick = onPlayToggle,
                        enabled = points.size >= 2,
                    ) {
                        Icon(
                            imageVector = if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                if (playback.isPlaying) R.string.playback_pause else R.string.playback_play,
                            ),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = Formatters.duration(elapsed),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(
                                R.string.playback_position,
                                if (points.isEmpty()) 0 else playback.index + 1,
                                points.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }

                if (points.size >= 2) {
                    Slider(
                        value = playback.index.coerceIn(0, lastIndex).toFloat(),
                        onValueChange = { onSeek(it.roundToInt()) },
                        valueRange = 0f..lastIndex.toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // The speeds share the whole width, so the four options always fit,
                // even on narrow screens or with a large font size.
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PLAYBACK_SPEEDS.forEachIndexed { index, speed ->
                        SegmentedButton(
                            selected = playback.speed == speed,
                            onClick = { onSpeedChange(speed) },
                            shape = SegmentedButtonDefaults.itemShape(index, PLAYBACK_SPEEDS.size),
                            icon = {},
                            label = {
                                Text(
                                    text = "${speed.toInt()}×",
                                    maxLines = 1,
                                    softWrap = false,
                                    fontWeight = if (playback.speed == speed) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The button that opens the list of the base layers of the review map. */
@Composable
private fun LayerMenu(
    layer: MapLayer,
    onSelect: (MapLayer) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Layers, contentDescription = stringResource(R.string.map_layer))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MapLayer.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(option.labelRes))
                            Text(
                                text = stringResource(option.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    trailingIcon = {
                        if (option == layer) {
                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.trip_detail_selected))
                        }
                    },
                )
            }
        }
    }
}

/** The elevation profile of the trip. */
@Composable
private fun ProfileTab(
    profile: com.mikotrack.domain.model.ElevationProfile,
    selectedDistance: Double?,
    onDistanceSelected: (Double) -> Unit,
    trip: com.mikotrack.domain.model.Trip?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.trip_detail_min),
                value = Formatters.altitude(profile.minAltitude),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.trip_detail_max),
                value = Formatters.altitude(profile.maxAltitude),
                modifier = Modifier.weight(1f),
            )
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
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
        ) {
            ElevationProfileView(
                profile = profile,
                currentDistance = selectedDistance,
                onDistanceSelected = onDistanceSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(8.dp),
            )
        }
    }
}

/** The detailed statistics of the trip. */
@Composable
private fun StatisticsTab(
    stats: com.mikotrack.domain.model.TripStats,
    trip: com.mikotrack.domain.model.Trip?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.stat_duration),
                value = Formatters.duration(trip?.durationMillis ?: 0L),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.stat_distance),
                value = Formatters.distance(trip?.distanceMeters ?: 0.0),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.trip_detail_average_speed),
                value = Formatters.speed(stats.averageSpeedMps),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.stat_max_speed),
                value = Formatters.speed(stats.maxSpeedMps),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.stat_moving_time),
                value = Formatters.durationShort(stats.movingTimeMillis),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.stat_stationary_time),
                value = Formatters.durationShort(stats.stationaryTimeMillis),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.stat_photos),
                value = (trip?.photoCount ?: 0).toString(),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.stat_waypoints),
                value = (trip?.waypointCount ?: 0).toString(),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.trip_detail_points),
                value = stats.pointCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The timeline of the waypoints and the personal notes. */
@Composable
private fun TimelineTab(
    waypoints: List<Waypoint>,
    notes: String,
    onEditNotes: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.trip_notes), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { editing = !editing }) {
                        Text(stringResource(if (editing) R.string.save else R.string.edit))
                    }
                }
                if (editing) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = onEditNotes,
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = notes.ifBlank { stringResource(R.string.trip_detail_no_notes) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (waypoints.isEmpty()) {
            Text(
                text = stringResource(R.string.trip_detail_no_waypoints),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(waypoints, key = { it.id }) { waypoint ->
                    WaypointTimelineItem(waypoint)
                }
            }
        }
    }
}

@Composable
private fun WaypointTimelineItem(waypoint: Waypoint) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ColorDot(androidx.compose.ui.graphics.Color(DefaultCategory.colorFor(waypoint.category)))
            Column(Modifier.weight(1f)) {
                Text(
                    text = waypoint.description.ifBlank { categoryLabel(waypoint.category) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = SimpleDateFormat("HH:mm:ss", locale)
                        .format(Date(waypoint.timestamp)) +
                        " · " + Formatters.coordinates(waypoint.latitude, waypoint.longitude),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The identifiers of the two lines of the review map: the whole route and the played part. */
private const val FULL_ROUTE_ID = -10L
private const val PLAYED_ROUTE_ID = -11L

/** The whole route is blue and the part already played back is red. */
private const val PENDING_ROUTE_COLOR = 0xFF1E88E5.toInt()
private const val PLAYED_ROUTE_COLOR = 0xFFE53935.toInt()
