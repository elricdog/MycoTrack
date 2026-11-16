package com.mikotrack.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikotrack.R
import com.mikotrack.core.Formatters
import com.mikotrack.domain.model.Trip
import com.mikotrack.ui.common.ConfirmationDialog
import com.mikotrack.ui.common.EmptyState
import com.mikotrack.ui.common.StatTile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The list of all recorded trips. */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenTrip: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trips by viewModel.visibleTrips.collectAsStateWithLifecycle()
    val allTrips by viewModel.trips.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()

    var showFilters by remember { mutableStateOf(false) }
    var tripToDelete by remember { mutableStateOf<Trip?>(null) }
    var tripToRename by remember { mutableStateOf<Trip?>(null) }
    var tripToColor by remember { mutableStateOf<Trip?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setSearchQuery,
                label = { Text(stringResource(R.string.search_trips)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showFilters = true }) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = stringResource(R.string.filters),
                    tint = if (filters.isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        if (trips.isEmpty()) {
            EmptyState(
                title = stringResource(if (allTrips.isEmpty()) R.string.history_empty else R.string.history_no_match),
                hint = if (allTrips.isEmpty()) {
                    stringResource(R.string.history_empty_hint)
                } else {
                    stringResource(R.string.history_no_match_hint)
                },
                icon = Icons.Filled.History,
                image = painterResource(R.drawable.brand_mascot),
            )
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(trips, key = { it.id }) { trip ->
                    TripCard(
                        trip = trip,
                        onClick = { onOpenTrip(trip.id) },
                        onRename = { tripToRename = trip },
                        onDelete = { tripToDelete = trip },
                        onChangeColor = { tripToColor = trip },
                    )
                }
            }
        }
    }

    if (showFilters) {
        FiltersDialog(
            filters = filters,
            onApply = {
                viewModel.setFilters(it)
                showFilters = false
            },
            onDismiss = { showFilters = false },
        )
    }

    tripToDelete?.let { trip ->
        ConfirmationDialog(
            title = stringResource(R.string.delete_trip_title),
            message = stringResource(R.string.history_delete_trip_message, trip.name),
            confirmLabel = stringResource(R.string.delete),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = {
                viewModel.delete(trip.id)
                tripToDelete = null
            },
            onDismiss = { tripToDelete = null },
        )
    }

    tripToColor?.let { trip ->
        TripColorDialog(
            current = trip.color,
            onSelect = { color ->
                viewModel.setColor(trip.id, color)
                tripToColor = null
            },
            onDismiss = { tripToColor = null },
        )
    }

    tripToRename?.let { trip ->
        RenameDialog(
            currentName = trip.name,
            onConfirm = { name ->
                viewModel.rename(trip.id, name)
                tripToRename = null
            },
            onDismiss = { tripToRename = null },
        )
    }
}

/** A card with the summary of a trip. */
@Composable
private fun TripCard(
    trip: Trip,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onChangeColor: () -> Unit,
) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = trip.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = SimpleDateFormat("dd MMM yyyy · HH:mm", locale)
                            .format(Date(trip.startTime)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rename))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = stringResource(R.string.stat_distance),
                    value = Formatters.distance(trip.distanceMeters),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.stat_duration),
                    value = Formatters.durationShort(trip.durationMillis),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.stat_gain),
                    value = Formatters.elevationDelta(trip.elevationGain),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The colour of the route on the map; a tap changes it.
                TripColorDot(
                    color = trip.color,
                    onClick = onChangeColor,
                    modifier = Modifier.padding(end = 2.dp),
                )
                Text(
                    text = pluralStringResource(R.plurals.history_photo_count, trip.photoCount, trip.photoCount) +
                        " · " +
                        pluralStringResource(R.plurals.history_waypoint_count, trip.waypointCount, trip.waypointCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The dialog that lets the user filter the list of trips. */
@Composable
private fun FiltersDialog(
    filters: TripFilters,
    onApply: (TripFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var distance by remember { mutableStateOf(filters.minimumDistanceMeters.toFloat()) }
    var duration by remember { mutableStateOf(filters.minimumDurationMillis.toFloat()) }
    var gain by remember { mutableStateOf(filters.minimumElevationGain.toFloat()) }
    var onlyPhotos by remember { mutableStateOf(filters.onlyWithPhotos) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filters)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterSlider(
                    label = stringResource(R.string.history_filter_min_distance, distance.toInt()),
                    value = distance,
                    range = 0f..50_000f,
                    onValueChange = { distance = it },
                )
                FilterSlider(
                    label = stringResource(R.string.history_filter_min_duration, (duration / 60000f).toInt()),
                    value = duration,
                    range = 0f..600_000f,
                    onValueChange = { duration = it },
                )
                FilterSlider(
                    label = stringResource(R.string.history_filter_min_gain, gain.toInt()),
                    value = gain,
                    range = 0f..2_000f,
                    onValueChange = { gain = it },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.history_filter_only_photos), modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = onlyPhotos, onCheckedChange = { onlyPhotos = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        TripFilters(
                            minimumDistanceMeters = distance.toDouble(),
                            minimumDurationMillis = duration.toLong(),
                            minimumElevationGain = gain.toDouble(),
                            onlyWithPhotos = onlyPhotos,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.history_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun FilterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}

/** The dialog that renames a trip. */
@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trip_name)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
