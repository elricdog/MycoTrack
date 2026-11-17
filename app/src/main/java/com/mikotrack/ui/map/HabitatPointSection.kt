package com.mikotrack.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikotrack.R
import com.mikotrack.core.Formatters
import com.mikotrack.maps.AspectClass
import com.mikotrack.maps.HabitatLayers
import com.mikotrack.maps.HabitatPointInfo
import com.mikotrack.maps.HabitatPointInspector
import com.mikotrack.maps.PointValue
import com.mikotrack.maps.RainAtPoint
import com.mikotrack.maps.RainScale
import com.mikotrack.maps.SoilAtPoint
import com.mikotrack.maps.SuitabilityAtPoint
import com.mikotrack.maps.TerrainAtPoint
import com.mikotrack.maps.TreesAtPoint
import kotlin.math.roundToInt

/**
 * The values of the visible habitat layers at a long-pressed point: orientation and
 * slope, forest formation, substrate and the score of the likely areas. Nothing is
 * shown when no habitat layer is visible. The values are fetched while the section is
 * shown and the requests are abandoned when the point or the layers change.
 */
@Composable
fun HabitatPointSection(latitude: Double, longitude: Double, layers: HabitatLayers) {
    if (!layers.anyVisible) return
    val context = LocalContext.current
    val inspector = remember { HabitatPointInspector.get(context) }
    // Only what changes the values restarts the requests (not the hidden legend classes).
    val request = listOf(
        layers.aspect,
        layers.trees,
        layers.soil,
        layers.suitabilitySearch?.takeIf { layers.suitabilityShown },
        layers.rainWindow.takeIf { layers.rain },
    )
    var info by remember(latitude, longitude, request) { mutableStateOf(HabitatPointInfo.loading(layers)) }
    LaunchedEffect(latitude, longitude, request) {
        inspector.inspect(latitude, longitude, layers).collect { info = it }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider()
        Text(
            text = stringResource(R.string.habitatpoint_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        info.aspect?.let { value ->
            ValueRow(stringResource(R.string.habitatpoint_aspect_label), value) { TerrainValue(it) }
        }
        info.trees?.let { value ->
            ValueRow(stringResource(R.string.habitatpoint_trees_label), value) { TreesValue(it) }
        }
        info.soil?.let { value ->
            ValueRow(stringResource(R.string.habitatpoint_soil_label), value) { SoilValue(it) }
        }
        info.suitability?.let { value ->
            val name = layers.suitabilitySearch?.speciesName.orEmpty()
            ValueRow(stringResource(R.string.habitatpoint_suitability_label, name), value) { SuitabilityValue(it) }
        }
        info.rain?.let { value ->
            ValueRow(stringResource(R.string.habitatpoint_rain_label), value) { RainValue(it, layers.rainWindow) }
        }
    }
}

@Composable
private fun <T> ValueRow(label: String, value: PointValue<T>, content: @Composable (T) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (value) {
            PointValue.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    stringResource(R.string.habitatpoint_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PointValue.Failed -> Text(
                stringResource(R.string.habitatpoint_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            is PointValue.Ready -> content(value.value)
        }
    }
}

@Composable
private fun ValueText(title: String, detail: String?) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    if (!detail.isNullOrBlank()) {
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TerrainValue(terrain: TerrainAtPoint) {
    val title = stringResource(
        when (terrain.aspectClass) {
            AspectClass.NORTH -> R.string.habitatpoint_aspect_north
            AspectClass.SOUTH -> R.string.habitatpoint_aspect_south
            AspectClass.NEUTRAL -> R.string.habitatpoint_aspect_neutral
            AspectClass.FLAT -> R.string.habitatpoint_aspect_flat
        },
    )
    val percent = terrain.slopePercent.roundToInt()
    val degrees = terrain.slopeDegrees.roundToInt()
    val altitude = Formatters.altitude(terrain.elevationMeters)
    val detail = if (terrain.aspectClass == AspectClass.FLAT) {
        stringResource(R.string.habitatpoint_flat_detail, percent, degrees, altitude)
    } else {
        val compass = stringArrayResource(R.array.format_compass_points)[Formatters.compassIndex(terrain.aspectDegrees)]
        stringResource(
            R.string.habitatpoint_aspect_detail,
            compass,
            terrain.aspectDegrees.roundToInt() % 360,
            percent,
            degrees,
            altitude,
        )
    }
    ValueText(title, detail)
}

@Composable
private fun TreesValue(trees: TreesAtPoint) {
    when (trees) {
        TreesAtPoint.NoForest -> ValueText(stringResource(R.string.habitatpoint_no_forest), null)
        is TreesAtPoint.Forest -> {
            val title = trees.legend?.let { stringResource(it.labelRes) }
                ?: trees.formationName
                ?: stringResource(R.string.habitatpoint_unknown_formation)
            val parts = trees.species.map { stringResource(R.string.habitatpoint_species_share, it.name, it.percent) } +
                listOfNotNull(trees.canopyCoverPercent?.let { stringResource(R.string.habitatpoint_canopy, it) })
            ValueText(title, parts.joinToString(" \u00B7 "))
        }
    }
}

@Composable
private fun SoilValue(soil: SoilAtPoint) {
    when (soil) {
        SoilAtPoint.OutsideMap -> ValueText(stringResource(R.string.habitatpoint_outside_map), null)
        is SoilAtPoint.Lithology -> {
            val lithology = soil.lithology
            if (lithology == null) {
                ValueText(soil.name, null)
            } else {
                ValueText(stringResource(lithology.labelRes), stringResource(lithology.group.labelRes))
            }
        }
    }
}

@Composable
private fun SuitabilityValue(suitability: SuitabilityAtPoint) {
    val level = stringResource(
        when (suitability.level) {
            SuitabilityAtPoint.Level.LOW -> R.string.habitatpoint_level_low
            SuitabilityAtPoint.Level.MEDIUM -> R.string.habitatpoint_level_medium
            SuitabilityAtPoint.Level.HIGH -> R.string.habitatpoint_level_high
        },
    )
    ValueText(stringResource(R.string.habitatpoint_suitability_value, (suitability.score * 100).roundToInt(), level), null)
}

@Composable
private fun RainValue(rain: RainAtPoint, currentWindow: Int) {
    val level = stringResource(
        when (RainScale.intensityOf(rain.millimeters(currentWindow) / currentWindow)) {
            RainScale.Level.DRY -> R.string.habitatpoint_rain_dry
            RainScale.Level.DAMP -> R.string.habitatpoint_rain_damp
            RainScale.Level.WET -> R.string.habitatpoint_rain_wet
            RainScale.Level.VERY_WET -> R.string.habitatpoint_rain_very_wet
        },
    )
    val title = stringResource(
        R.string.habitatpoint_rain_value,
        formatMillimeters(rain.millimeters(currentWindow)),
        currentWindow,
        level,
    )
    val detail = buildList {
        com.mikotrack.maps.RainRepository.WINDOWS.forEach { days ->
            add(stringResource(R.string.habitatpoint_rain_window, days, formatMillimeters(rain.millimeters(days))))
        }
    }.joinToString(" \u00B7 ")
    ValueText(title, detail)
}

/** Accumulated rain, with one decimal and the locale's decimal separator. */
private fun formatMillimeters(millimeters: Float): String =
    String.format(java.util.Locale.getDefault(), "%.1f", millimeters)
