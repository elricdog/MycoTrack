package com.mikotrack.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikotrack.R
import com.mikotrack.core.Formatters
import com.mikotrack.data.repository.DefaultCategory
import com.mikotrack.domain.model.Trip
import com.mikotrack.maps.HabitatLayers
import com.mikotrack.ui.common.ColorDot
import com.mikotrack.ui.common.categoryLabel
import java.text.DateFormat
import java.util.Date

/** The steps of the sheet of a long-pressed point. */
private enum class PointSheetStep { MENU, TRIP, DETAILS }

/**
 * The menu of a point the user long-pressed on the map: its coordinates and distance,
 * and the actions to create a waypoint there, get walking directions or copy the
 * coordinates, followed by the values of the visible habitat layers at the point.
 *
 * @param recordingTrip the trip being recorded, which receives the new waypoint.
 * @param candidates the trips the waypoint can belong to when nothing is recorded.
 * @param overlaidIds the overlaid trips, marked in the list of candidates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPointSheet(
    latitude: Double,
    longitude: Double,
    distanceMeters: Double?,
    recordingTrip: Trip?,
    candidates: List<Trip>,
    overlaidIds: Set<Long>,
    onDismiss: () -> Unit,
    onCreateWaypoint: (trip: Trip, category: String, description: String) -> Unit,
    onGoThere: () -> Unit,
    onCopyCoordinates: () -> Unit,
    habitatLayers: HabitatLayers = HabitatLayers(),
) {
    var step by rememberSaveable { mutableStateOf(PointSheetStep.MENU) }
    var tripId by rememberSaveable { mutableStateOf<Long?>(null) }
    var category by rememberSaveable { mutableStateOf(DefaultCategory.MUSHROOM_FOUND.key) }
    var description by rememberSaveable { mutableStateOf("") }
    val selectedTrip = recordingTrip ?: candidates.firstOrNull { it.id == tripId }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step != PointSheetStep.MENU) {
                    IconButton(
                        onClick = {
                            step = if (step == PointSheetStep.DETAILS && recordingTrip == null) {
                                PointSheetStep.TRIP
                            } else {
                                PointSheetStep.MENU
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.maproute_back))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            when (step) {
                                PointSheetStep.MENU -> R.string.maproute_point_title
                                PointSheetStep.TRIP -> R.string.maproute_pick_trip
                                PointSheetStep.DETAILS -> R.string.maproute_create_waypoint
                            },
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = buildString {
                            append(Formatters.coordinates(latitude, longitude))
                            if (distanceMeters != null) append(" \u00B7 ")
                        } + (distanceMeters?.let {
                            stringResource(R.string.maproute_distance_from_you, Formatters.distance(it))
                        } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (step) {
                PointSheetStep.MENU -> {
                    ActionRow(
                        icon = { Icon(Icons.Filled.AddLocationAlt, contentDescription = null) },
                        title = stringResource(R.string.maproute_create_waypoint),
                        subtitle = recordingTrip?.let { stringResource(R.string.maproute_recording_trip) },
                        onClick = {
                            step = if (recordingTrip != null) PointSheetStep.DETAILS else PointSheetStep.TRIP
                        },
                    )
                    ActionRow(
                        icon = { Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null) },
                        title = stringResource(R.string.maproute_go_there),
                        subtitle = stringResource(R.string.maproute_go_there_hint),
                        onClick = onGoThere,
                    )
                    ActionRow(
                        icon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                        title = stringResource(R.string.maproute_copy_coordinates),
                        subtitle = MapPointLinks.coordinates(latitude, longitude),
                        onClick = onCopyCoordinates,
                    )
                    HabitatPointSection(latitude, longitude, habitatLayers)
                }

                PointSheetStep.TRIP -> {
                    Text(
                        text = stringResource(R.string.maproute_pick_trip_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (candidates.isEmpty()) {
                        Text(
                            text = stringResource(R.string.maproute_no_trips),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                    Column {
                        candidates.forEach { trip ->
                            val onMap = trip.id in overlaidIds
                            ActionRow(
                                icon = {
                                    Icon(
                                        Icons.Filled.Map,
                                        contentDescription = null,
                                        tint = if (onMap) {
                                            trip.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                    )
                                },
                                title = trip.name,
                                subtitle = buildString {
                                    append(dateFormat.format(Date(trip.startTime)))
                                    append(" \u00B7 ")
                                    append(Formatters.distance(trip.distanceMeters))
                                } + if (onMap) " \u00B7 " + stringResource(R.string.maproute_on_map) else "",
                                onClick = {
                                    tripId = trip.id
                                    step = PointSheetStep.DETAILS
                                },
                            )
                        }
                    }
                }

                PointSheetStep.DETAILS -> {
                    selectedTrip?.let { trip ->
                        Text(
                            text = stringResource(R.string.maproute_trip_label, trip.name),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(stringResource(R.string.category), style = MaterialTheme.typography.titleMedium)
                    DefaultCategory.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { category = option.key }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ColorDot(Color(option.color))
                            Text(
                                text = categoryLabel(option.key),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (option.key == category) {
                                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.map_selected))
                            }
                        }
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
                    )
                    Button(
                        onClick = { selectedTrip?.let { onCreateWaypoint(it, category, description.trim()) } },
                        enabled = selectedTrip != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.maproute_save_waypoint)) }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = subtitle?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        leadingContent = icon,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
