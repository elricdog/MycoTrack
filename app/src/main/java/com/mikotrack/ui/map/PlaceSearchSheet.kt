package com.mikotrack.ui.map

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.mikotrack.R
import com.mikotrack.core.CoordinateParser
import com.mikotrack.core.Formatters
import com.mikotrack.core.GeoUtils
import com.mikotrack.data.geocoding.Geocoder
import com.mikotrack.data.geocoding.GeocodingException
import com.mikotrack.data.geocoding.PlaceAddress
import com.mikotrack.data.geocoding.PlaceBounds
import com.mikotrack.data.geocoding.PlaceResult
import com.mikotrack.location.CurrentPosition
import com.mikotrack.maps.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

enum class PlaceSearchTab { NAME, COORDINATES, WHERE_AM_I }

/** A place the map has to show: framed by [bounds] when known, otherwise centred. */
data class PlaceTarget(val latitude: Double, val longitude: Double, val bounds: PlaceBounds? = null)

/**
 * The state of the place search sheet. It lives as long as the map screen, so the last
 * search is still there when the sheet is opened again.
 */
class PlaceSearchState(private val geocoder: Geocoder, private val scope: CoroutineScope) {
    var tab by mutableStateOf(PlaceSearchTab.NAME)

    var query by mutableStateOf("")
    var results by mutableStateOf<List<PlaceResult>?>(null)
        private set
    var searching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<GeocodingException.Kind?>(null)
        private set

    var coordinates by mutableStateOf("")

    var address by mutableStateOf<PlaceAddress?>(null)
        private set
    var addressPosition by mutableStateOf<CurrentPosition?>(null)
        private set
    var locating by mutableStateOf(false)
        private set
    var addressError by mutableStateOf<GeocodingException.Kind?>(null)
        private set

    private var searchJob: Job? = null
    private var locateJob: Job? = null

    fun search(language: String, center: GeoPoint?) {
        val text = query.trim()
        if (text.isEmpty()) return
        searchJob?.cancel()
        searchJob = scope.launch {
            searching = true
            searchError = null
            try {
                val viewBox = center?.let { Geocoder.viewBoxAround(it.latitude, it.longitude) }
                results = geocoder.search(text, language, viewBox)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                results = null
                searchError = (e as? GeocodingException)?.kind ?: GeocodingException.Kind.SERVER
            } finally {
                searching = false
            }
        }
    }

    fun locate(position: CurrentPosition, language: String) {
        locateJob?.cancel()
        locateJob = scope.launch {
            locating = true
            addressError = null
            addressPosition = position
            try {
                address = geocoder.reverse(position.latitude, position.longitude, language)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                address = null
                addressError = (e as? GeocodingException)?.kind ?: GeocodingException.Kind.SERVER
            } finally {
                locating = false
            }
        }
    }
}

/**
 * The sheet to find a place by its name, by its coordinates, or to tell where the user
 * is. [onGoTo] moves the map there; [onOpenPointMenu] also opens the menu of the point
 * (create a waypoint, directions...).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSearchSheet(
    state: PlaceSearchState,
    position: CurrentPosition?,
    searchCenter: GeoPoint?,
    onDismiss: () -> Unit,
    onGoTo: (PlaceTarget) -> Unit,
    onOpenPointMenu: (PlaceTarget) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val language = remember(configuration) {
        ConfigurationCompat.getLocales(configuration)[0]?.language ?: Locale.getDefault().language
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.placesearch_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            val tabs = listOf(
                PlaceSearchTab.NAME to R.string.placesearch_tab_name,
                PlaceSearchTab.COORDINATES to R.string.placesearch_tab_coordinates,
                PlaceSearchTab.WHERE_AM_I to R.string.placesearch_tab_where,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, (tab, label) ->
                    SegmentedButton(
                        selected = state.tab == tab,
                        onClick = { state.tab = tab },
                        shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                        icon = {},
                    ) {
                        Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            when (state.tab) {
                PlaceSearchTab.NAME -> NameSearchTab(state, language, position, searchCenter, onGoTo, onOpenPointMenu)
                PlaceSearchTab.COORDINATES -> CoordinatesTab(state, onGoTo, onOpenPointMenu)
                PlaceSearchTab.WHERE_AM_I -> WhereAmITab(state, language, position)
            }
            Text(
                text = stringResource(R.string.placesearch_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NameSearchTab(
    state: PlaceSearchState,
    language: String,
    position: CurrentPosition?,
    searchCenter: GeoPoint?,
    onGoTo: (PlaceTarget) -> Unit,
    onOpenPointMenu: (PlaceTarget) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        state.search(language, searchCenter)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { state.query = it },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.placesearch_query_label)) },
            placeholder = { Text(stringResource(R.string.placesearch_query_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = submit, enabled = state.query.isNotBlank() && !state.searching) {
            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.placesearch_search))
        }
    }
    val results = state.results
    when {
        state.searching -> ProgressLine(stringResource(R.string.placesearch_searching))
        state.searchError != null -> Message(errorText(state.searchError!!), error = true)
        results != null && results.isEmpty() -> Message(stringResource(R.string.placesearch_no_results))
        results != null -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(results) { place ->
                val target = PlaceTarget(place.latitude, place.longitude, place.bounds)
                PlaceRow(
                    place = place,
                    distance = position?.let {
                        GeoUtils.distanceMeters(it.latitude, it.longitude, place.latitude, place.longitude)
                    },
                    onClick = { onGoTo(target) },
                    onOpenMenu = { onOpenPointMenu(target) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PlaceRow(place: PlaceResult, distance: Double?, onClick: () -> Unit, onOpenMenu: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                place.type?.let { type ->
                    placeTypeLabel(type)?.let { stringResource(it) }
                        ?: type.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                },
                place.locality,
            ).joinToString(" \u00b7 ")
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (distance != null) {
            Text(
                text = Formatters.distance(distance),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        IconButton(onClick = onOpenMenu) {
            Icon(Icons.Filled.AddLocationAlt, contentDescription = stringResource(R.string.placesearch_open_menu))
        }
    }
}

@Composable
private fun CoordinatesTab(
    state: PlaceSearchState,
    onGoTo: (PlaceTarget) -> Unit,
    onOpenPointMenu: (PlaceTarget) -> Unit,
) {
    var showError by remember { mutableStateOf(false) }
    val parsed = remember(state.coordinates) { CoordinateParser.parse(state.coordinates) }
    val success = parsed as? CoordinateParser.Result.Success
    val target = success?.let { PlaceTarget(it.latitude, it.longitude) }
    val go: () -> Unit = {
        if (target != null) {
            onGoTo(target)
        } else {
            showError = true
        }
    }
    OutlinedTextField(
        value = state.coordinates,
        onValueChange = {
            state.coordinates = it
            showError = false
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.placesearch_coordinates_label)) },
        singleLine = true,
        isError = showError && success == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { go() }),
        supportingText = {
            val failure = parsed as? CoordinateParser.Result.Failure
            Text(
                when {
                    success != null -> stringResource(
                        R.string.placesearch_coordinates_parsed,
                        String.format(Locale.US, "%.6f", success.latitude),
                        String.format(Locale.US, "%.6f", success.longitude),
                    )
                    showError && failure != null -> coordinateErrorText(failure.error)
                    else -> stringResource(R.string.placesearch_coordinates_help)
                },
            )
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = go, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.placesearch_go))
        }
        OutlinedButton(onClick = { if (target != null) { onOpenPointMenu(target) } else { showError = true } }) {
            Icon(Icons.Filled.AddLocationAlt, contentDescription = stringResource(R.string.placesearch_open_menu))
        }
    }
}

@Composable
private fun WhereAmITab(state: PlaceSearchState, language: String, position: CurrentPosition?) {
    val context = LocalContext.current
    // The address is looked up once when the tab opens; "Refresh" asks again.
    LaunchedEffect(position == null) {
        if (position != null && state.addressPosition == null && !state.locating) {
            state.locate(position, language)
        }
    }
    if (position == null && state.addressPosition == null) {
        Message(stringResource(R.string.placesearch_no_position), error = true)
        return
    }
    val shown = state.addressPosition ?: position!!
    val fields = addressFields(state.address, shown)
    val shareText = fields.joinToString("\n") { (label, value) -> "$label: $value" } +
        "\n" + String.format(
            Locale.US,
            "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=17/%.6f/%.6f",
            shown.latitude, shown.longitude, shown.latitude, shown.longitude,
        )
    Column(
        modifier = Modifier
            .heightIn(max = 380.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            state.locating -> ProgressLine(stringResource(R.string.placesearch_locating))
            state.addressError != null -> Message(errorText(state.addressError!!), error = true)
            state.address == null -> Message(stringResource(R.string.placesearch_no_address))
        }
        fields.forEach { (label, value) ->
            Row {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(120.dp),
                )
                Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
    val shareTitle = stringResource(R.string.placesearch_share_title)
    val copied = stringResource(R.string.placesearch_copied)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { position?.let { state.locate(it, language) } },
            enabled = position != null && !state.locating,
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.placesearch_refresh))
        }
        OutlinedButton(onClick = { copyText(context, shareText, copied) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.placesearch_copy), maxLines = 1)
        }
        Button(onClick = { shareText(context, shareTitle, shareText) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.placesearch_share), maxLines = 1)
        }
    }
}

/** The labelled lines of an address, followed by the coordinates and the altitude. */
@Composable
private fun addressFields(address: PlaceAddress?, position: CurrentPosition): List<Pair<String, String>> {
    val fields = mutableListOf<Pair<String, String>>()
    fun add(label: String, value: String?) {
        if (!value.isNullOrBlank()) fields += label to value
    }
    if (address != null) {
        add(
            stringResource(R.string.placesearch_field_road),
            listOfNotNull(address.road, address.houseNumber).joinToString(" ").ifBlank { null },
        )
        add(stringResource(R.string.placesearch_field_nearby), address.nearbyFeature)
        add(stringResource(R.string.placesearch_field_locality), address.locality)
        add(stringResource(R.string.placesearch_field_municipality), address.municipality)
        add(stringResource(R.string.placesearch_field_county), address.county)
        add(stringResource(R.string.placesearch_field_province), address.province)
        add(stringResource(R.string.placesearch_field_state), address.state?.takeIf { it != address.province })
        add(stringResource(R.string.placesearch_field_postcode), address.postcode)
        add(stringResource(R.string.placesearch_field_country), address.country)
    }
    add(stringResource(R.string.placesearch_field_coordinates), Formatters.coordinates(position.latitude, position.longitude))
    position.altitude?.let { add(stringResource(R.string.placesearch_field_altitude), Formatters.altitude(it)) }
    return fields
}

@Composable
private fun ProgressLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Message(text: String, error: Boolean = false) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun errorText(kind: GeocodingException.Kind): String = stringResource(
    when (kind) {
        GeocodingException.Kind.NETWORK -> R.string.placesearch_error_network
        GeocodingException.Kind.SERVER -> R.string.placesearch_error_server
    },
)

@Composable
private fun coordinateErrorText(error: CoordinateParser.Error): String = stringResource(
    when (error) {
        CoordinateParser.Error.EMPTY -> R.string.placesearch_error_empty
        CoordinateParser.Error.FORMAT -> R.string.placesearch_error_format
        CoordinateParser.Error.LATITUDE_RANGE -> R.string.placesearch_error_latitude
        CoordinateParser.Error.LONGITUDE_RANGE -> R.string.placesearch_error_longitude
        CoordinateParser.Error.MINUTES_SECONDS_RANGE -> R.string.placesearch_error_minutes
    },
)

private fun copyText(context: Context, text: String, confirmation: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("MycoTrack", text))
    Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, title)) }
}

/** The translated name of the most common Nominatim place types, or `null` if unknown. */
private fun placeTypeLabel(type: String): Int? = when (type.lowercase(Locale.ROOT)) {
    "city" -> R.string.placesearch_type_city
    "town" -> R.string.placesearch_type_town
    "village" -> R.string.placesearch_type_village
    "hamlet", "isolated dwelling", "farm" -> R.string.placesearch_type_hamlet
    "suburb", "neighbourhood", "quarter", "city district" -> R.string.placesearch_type_suburb
    "municipality" -> R.string.placesearch_type_municipality
    "county" -> R.string.placesearch_type_county
    "state", "province", "region" -> R.string.placesearch_type_region
    "country" -> R.string.placesearch_type_country
    "peak", "hill", "volcano" -> R.string.placesearch_type_peak
    "saddle", "mountain pass" -> R.string.placesearch_type_pass
    "ridge", "arete", "cliff" -> R.string.placesearch_type_ridge
    "valley" -> R.string.placesearch_type_valley
    "wood", "forest" -> R.string.placesearch_type_forest
    "spring" -> R.string.placesearch_type_spring
    "river", "stream", "water", "waterway" -> R.string.placesearch_type_water
    "road", "residential", "track", "path", "footway", "highway", "tertiary", "secondary", "primary",
    "unclassified", "service" -> R.string.placesearch_type_road
    "parking" -> R.string.placesearch_type_parking
    "place of worship", "church", "chapel" -> R.string.placesearch_type_church
    "locality", "isolated locality" -> R.string.placesearch_type_locality
    else -> null
}
