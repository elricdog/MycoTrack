package com.mikotrack.ui.mushrooms

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mikotrack.maps.SuitabilitySearch
import com.mikotrack.R
import com.mikotrack.ui.common.EmptyState
import com.mikotrack.ui.common.SectionCard

/** The detail of a species of the edible mushroom guide. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushroomDetailScreen(
    viewModel: MushroomGuideViewModel,
    speciesId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the map, once the probability layer of a species has been set up. */
    onShowOnMap: () -> Unit = {},
    /**
     * Starts a search of the likely areas of a species (identifier, name, radius in km)
     * and reports whether it could be stored; `null` hides the button. The flag searches
     * around the centre of the map instead of the position of the user.
     */
    onSearchZones: ((String, String, Int, Boolean, (Boolean) -> Unit) -> Unit)? = null,
) {
    val language = guideLanguage()
    LaunchedEffect(language) { viewModel.load(language) }
    val all by viewModel.species.collectAsStateWithLifecycle()
    val mushroom = all?.firstOrNull { it.id == speciesId }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.mushroom_guide_back),
                        )
                    }
                },
                title = {
                    Text(
                        text = mushroom?.commonName ?: stringResource(R.string.mushroom_guide_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                all == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                mushroom == null -> EmptyState(
                    title = stringResource(R.string.mushroom_guide_not_found),
                    hint = "",
                    icon = Icons.Filled.Warning,
                )
                else -> MushroomDetailContent(
                    mushroom = mushroom,
                    currentMonth = viewModel.currentMonth,
                    onSearchZones = onSearchZones?.let { search ->
                        { radiusKm: Int, aroundMapCenter: Boolean, onDone: (Boolean) -> Unit ->
                            search(mushroom.id, mushroom.commonName, radiusKm, aroundMapCenter) { found ->
                                onDone(found)
                                if (found) onShowOnMap()
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MushroomDetailContent(
    mushroom: Mushroom,
    currentMonth: Int,
    onSearchZones: ((Int, Boolean, (Boolean) -> Unit) -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val credit = mushroom.data.imageCredit
    val language = guideLanguage()
    val speciesPhotos = remember(mushroom, language) { GuidePhoto.speciesPhotos(mushroom, language) }
    val lookAlikePhotos = remember(mushroom, language) { GuidePhoto.lookAlikePhotos(mushroom, language) }
    // The open viewer as (group, index): group 0 is the species, group 1 its look-alikes.
    var viewerGroup by rememberSaveable { mutableIntStateOf(-1) }
    var viewerIndex by rememberSaveable { mutableIntStateOf(0) }
    val viewerPhotos = when (viewerGroup) {
        VIEWER_SPECIES -> speciesPhotos
        VIEWER_LOOK_ALIKES -> lookAlikePhotos
        else -> emptyList()
    }
    if (viewerPhotos.isNotEmpty()) {
        GuidePhotoViewer(photos = viewerPhotos, initialIndex = viewerIndex, onDismiss = { viewerGroup = -1 })
    }
    fun openViewer(group: Int, index: Int) {
        viewerIndex = index
        viewerGroup = group
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AsyncImage(
                model = mushroom.imageUri,
                contentDescription = mushroom.commonName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        onClickLabel = stringResource(R.string.guide_photos_open),
                        role = Role.Image,
                    ) { openViewer(VIEWER_SPECIES, 0) },
            )
            Text(
                text = stringResource(R.string.mushroom_guide_photo_credit, credit.author, credit.licence),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(
                    onClickLabel = stringResource(R.string.mushroom_guide_photo_credit_hint),
                    role = Role.Button,
                ) { runCatching { uriHandler.openUri(credit.sourceUrl) } },
            )
        }

        Column {
            Text(
                text = mushroom.commonName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = mushroom.scientificName,
                style = MaterialTheme.typography.titleMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.mushroom_guide_edibility),
                style = MaterialTheme.typography.labelLarge,
            )
            EdibilityStars(rating = mushroom.edibility, starSize = 22.dp)
        }

        SeasonBar(mushroom = mushroom, currentMonth = currentMonth, showInitials = true)

        if (onSearchZones != null) {
            SuitabilitySearchCard(mushroom = mushroom, currentMonth = currentMonth, onSearch = onSearchZones)
        }

        TextSection(stringResource(R.string.mushroom_guide_section_culinary), mushroom.texts.culinary)
        TextSection(stringResource(R.string.mushroom_guide_section_habitat), mushroom.texts.habitat)
        HabitatChartCard(mushroom.data.habitat)
        TextSection(stringResource(R.string.mushroom_guide_section_season), mushroom.texts.season)
        // The main photograph is the first of the group, so the gallery starts at index 1.
        GuideGallerySection(speciesPhotos.drop(1), onOpen = { openViewer(VIEWER_SPECIES, it + 1) })
        LookAlikesCard(mushroom.texts.lookAlikes, lookAlikePhotos, onOpen = { openViewer(VIEWER_LOOK_ALIKES, it) })
        MushroomDisclaimerCard()
    }
}

@Composable
private fun TextSection(title: String, text: String) {
    SectionCard(title = title) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The look-alikes, highlighted because a confusion can be dangerous. */
@Composable
private fun LookAlikesCard(text: String, photos: List<GuidePhoto>, onOpen: (Int) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    text = stringResource(R.string.mushroom_guide_section_look_alikes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
            LookAlikePhotoRow(photos = photos, onOpen = onOpen)
        }
    }
}

private const val VIEWER_SPECIES = 0
private const val VIEWER_LOOK_ALIKES = 1

/** The button that looks for the likely areas of the species around the user. */
@Composable
private fun SuitabilitySearchCard(
    mushroom: Mushroom,
    currentMonth: Int,
    onSearch: (Int, Boolean, (Boolean) -> Unit) -> Unit,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.TravelExplore, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.suitability_find_button))
        }
        Text(
            text = stringResource(R.string.suitability_find_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (showDialog) {
        SuitabilitySearchDialog(
            mushroom = mushroom,
            inSeason = mushroom.isInSeason(currentMonth),
            onDismiss = { showDialog = false },
            onSearch = onSearch,
        )
    }
}

/** Lets the user choose the radius of the search, warning when the species is out of season. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuitabilitySearchDialog(
    mushroom: Mushroom,
    inSeason: Boolean,
    onDismiss: () -> Unit,
    onSearch: (Int, Boolean, (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var radiusKm by rememberSaveable { mutableIntStateOf(SuitabilitySearch.DEFAULT_RADIUS_KM) }
    var aroundMapCenter by rememberSaveable { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.TravelExplore, contentDescription = null) },
        title = { Text(stringResource(R.string.suitability_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.suitability_dialog_text, mushroom.commonName))
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
                if (!inSeason) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = stringResource(R.string.suitability_out_of_season, mushroom.commonName),
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
                    onSearch(radiusKm, aroundMapCenter) { found ->
                        searching = false
                        if (found) {
                            onDismiss()
                        } else {
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
