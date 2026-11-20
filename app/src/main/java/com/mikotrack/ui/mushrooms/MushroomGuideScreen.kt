package com.mikotrack.ui.mushrooms

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mikotrack.R
import com.mikotrack.maps.SuitabilitySearch
import com.mikotrack.ui.common.EmptyState

/** The list of species of the edible mushroom guide. */
@Composable
fun MushroomGuideScreen(
    viewModel: MushroomGuideViewModel,
    onOpenSpecies: (String) -> Unit,
    onFindSpecies: (List<Pair<String, String>>, Int, Boolean, Boolean, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = guideLanguage()
    LaunchedEffect(language) { viewModel.load(language) }

    val all by viewModel.species.collectAsStateWithLifecycle()
    val visible by viewModel.visibleSpecies.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val onlyInSeason by viewModel.onlyInSeason.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    var showFindDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            label = { Text(stringResource(R.string.mushroom_guide_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = null)
                    }
                }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = onlyInSeason,
                onClick = { viewModel.setOnlyInSeason(!onlyInSeason) },
                label = { Text(stringResource(R.string.mushroom_guide_in_season)) },
                leadingIcon = if (onlyInSeason) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
            OutlinedButton(
                onClick = { if (selected.isNotEmpty()) showFindDialog = true },
                enabled = selected.isNotEmpty(),
            ) {
                Icon(Icons.Filled.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.mushroom_guide_find_selected, selected.size),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (selected.isNotEmpty()) {
                IconButton(onClick = { viewModel.clearSelected() }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.mushroom_guide_clear_selection))
                }
            }
        }
        if (selected.isNotEmpty()) {
            Text(
                text = stringResource(R.string.mushroom_guide_selection_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (all == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "disclaimer") { MushroomDisclaimerCard() }
            if (visible.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.mushroom_guide_no_match),
                        hint = stringResource(R.string.mushroom_guide_no_match_hint),
                        icon = Icons.Filled.Spa,
                    )
                }
            }
            items(visible, key = { it.id }) { mushroom ->
                MushroomCard(
                    mushroom = mushroom,
                    currentMonth = viewModel.currentMonth,
                    selected = mushroom.id in selected,
                    onClick = {
                        // With an ongoing selection a tap adds or removes; otherwise it opens.
                        if (selected.isNotEmpty()) viewModel.toggleSelected(mushroom.id) else onOpenSpecies(mushroom.id)
                    },
                    onLongClick = { viewModel.toggleSelected(mushroom.id) },
                )
            }
        }
    }

    if (showFindDialog) {
        val picked = all.orEmpty().filter { it.id in selected }
        if (picked.isEmpty()) {
            showFindDialog = false
        } else {
            MultiFindDialog(
                mushrooms = picked,
                currentMonth = viewModel.currentMonth,
                onDismiss = { showFindDialog = false },
                onFind = { radiusKm, aroundMapCenter, findHotspots, onDone ->
                    onFindSpecies(picked.map { it.id to it.commonName }, radiusKm, aroundMapCenter, findHotspots) { ok ->
                        onDone(ok)
                        if (ok) {
                            showFindDialog = false
                            viewModel.clearSelected()
                        }
                    }
                },
            )
        }
    }
}

/** A card with the photo, the names, the season and the edibility of a species. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MushroomCard(
    mushroom: Mushroom,
    currentMonth: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = stringResource(R.string.mushroom_guide_toggle_selection),
            ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                AsyncImage(
                    model = mushroom.imageUri,
                    contentDescription = mushroom.commonName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(22.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = mushroom.commonName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = mushroom.scientificName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SeasonBar(mushroom = mushroom, currentMonth = currentMonth)
                EdibilityStars(rating = mushroom.edibility)
            }
        }
    }
}

/**
 * The dialog of the multi-species search: like the search of the likely areas of one
 * species, but for the whole selection, with the option to also mark on the map the
 * three places of the circle where most of the selected species could be found.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiFindDialog(
    mushrooms: List<Mushroom>,
    currentMonth: Int,
    onDismiss: () -> Unit,
    onFind: (Int, Boolean, Boolean, (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var radiusKm by rememberSaveable { mutableIntStateOf(SuitabilitySearch.DEFAULT_RADIUS_KM) }
    var aroundMapCenter by rememberSaveable { mutableStateOf(false) }
    var findHotspots by rememberSaveable { mutableStateOf(true) }
    var searching by remember { mutableStateOf(false) }
    val names = mushrooms.joinToString(", ") { it.commonName }
    val noneInSeason = mushrooms.none { it.isInSeason(currentMonth) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.TravelExplore, contentDescription = null) },
        title = { Text(stringResource(R.string.suitability_multi_dialog_title, mushrooms.size)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.suitability_multi_dialog_text, names))
                Text(
                    text = stringResource(R.string.suitability_dialog_center),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !aroundMapCenter,
                        onClick = { aroundMapCenter = false },
                        label = { Text(stringResource(R.string.suitability_center_position)) },
                        leadingIcon = { Icon(Icons.Filled.MyLocation, contentDescription = null) },
                    )
                    FilterChip(
                        selected = aroundMapCenter,
                        onClick = { aroundMapCenter = true },
                        label = { Text(stringResource(R.string.suitability_center_map)) },
                        leadingIcon = { Icon(Icons.Filled.CenterFocusStrong, contentDescription = null) },
                    )
                }
                if (aroundMapCenter) {
                    Text(
                        text = stringResource(R.string.suitability_center_map_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.suitability_dialog_radius),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuitabilitySearch.RADIUS_OPTIONS_KM.forEach { option ->
                        FilterChip(
                            selected = option == radiusKm,
                            onClick = { radiusKm = option },
                            label = { Text(stringResource(R.string.suitability_radius_value, option.toString())) },
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Switch(checked = findHotspots, onCheckedChange = { findHotspots = it })
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.suitability_hotspots_option))
                        Text(
                            text = stringResource(R.string.suitability_hotspots_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (noneInSeason) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = stringResource(R.string.suitability_multi_out_of_season),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.suitability_dialog_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !searching,
                onClick = {
                    searching = true
                    onFind(radiusKm, aroundMapCenter, findHotspots) { found ->
                        searching = false
                        if (!found) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    if (aroundMapCenter) R.string.suitability_no_map_center else R.string.suitability_no_position,
                                ),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
            ) { Text(stringResource(R.string.suitability_dialog_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
