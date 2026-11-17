package com.mikotrack.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikotrack.R
import com.mikotrack.maps.AspectClass
import com.mikotrack.maps.HabitatLayers
import com.mikotrack.maps.HabitatLegendEntry
import com.mikotrack.maps.HabitatSources
import com.mikotrack.maps.LegendPattern
import com.mikotrack.maps.RainRepository
import com.mikotrack.maps.RainScale
import com.mikotrack.maps.SoilGroup
import com.mikotrack.maps.SuitabilityScorer

/** The "Habitat layers" section of the layer sheet: one switch per overlay. */
@Composable
internal fun HabitatLayerSection(
    layers: HabitatLayers,
    onAspectChange: (Boolean) -> Unit,
    onTreesChange: (Boolean) -> Unit,
    onSoilChange: (Boolean) -> Unit = {},
    onSuitabilityChange: (Boolean) -> Unit = {},
    onRainChange: (Boolean) -> Unit = {},
    onRainWindowChange: (Int) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(
            text = stringResource(R.string.habitat_section_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            text = stringResource(R.string.habitat_section_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        HabitatSwitchRow(
            title = stringResource(R.string.habitat_aspect_title),
            description = stringResource(R.string.habitat_aspect_description),
            checked = layers.aspect,
            onCheckedChange = onAspectChange,
        )
        HabitatSwitchRow(
            title = stringResource(R.string.habitat_trees_title),
            description = stringResource(R.string.habitat_trees_description),
            checked = layers.trees,
            onCheckedChange = onTreesChange,
        )
        HabitatSwitchRow(
            title = stringResource(R.string.habitat_soil_title),
            description = stringResource(R.string.habitat_soil_description),
            checked = layers.soil,
            onCheckedChange = onSoilChange,
        )
        val search = layers.suitabilitySearch
        HabitatSwitchRow(
            title = stringResource(R.string.suitability_layer_title),
            description = if (search != null) {
                stringResource(
                    R.string.suitability_layer_description,
                    search.speciesName,
                    suitabilityRadiusText(search.radiusMeters),
                )
            } else {
                stringResource(R.string.suitability_layer_no_species)
            },
            checked = layers.suitabilityShown,
            onCheckedChange = onSuitabilityChange,
            enabled = search != null,
        )
        HabitatSwitchRow(
            title = stringResource(R.string.habitat_rain_title),
            description = stringResource(R.string.habitat_rain_description),
            checked = layers.rain,
            onCheckedChange = onRainChange,
        )
        if (layers.rain) {
            // The length of the accumulation window, in days.
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RainRepository.WINDOWS.forEach { days ->
                    FilterChip(
                        selected = layers.rainWindow == days,
                        onClick = { onRainWindowChange(days) },
                        label = { Text(stringResource(R.string.habitat_rain_window_days, days)) },
                    )
                }
            }
        }
    }
}

/** The radius of a likely areas search, in kilometres. */
@Composable
internal fun suitabilityRadiusText(radiusMeters: Int): String {
    val km = if (radiusMeters % 1000 == 0) {
        (radiusMeters / 1000).toString()
    } else {
        String.format(java.util.Locale.getDefault(), "%.1f", radiusMeters / 1000.0)
    }
    return stringResource(R.string.suitability_radius_value, km)
}

@Composable
private fun HabitatSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.alpha(if (enabled) 1f else 0.6f),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/**
 * The colour keys of the habitat overlays that are switched on, one collapsible section
 * per overlay, where every class can be shown or hidden. The whole card can be collapsed
 * to a small header so it does not hide the map.
 *
 * Inside a parent of bounded height (the map in portrait) the list takes the room it is
 * given and scrolls; inside a scrolling parent (the side panel in landscape) it is
 * limited to most of the screen height and scrolls on its own.
 */
@Composable
internal fun HabitatLegendCard(
    layers: HabitatLayers,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onAspectClassesChange: (Set<AspectClass>) -> Unit = {},
    onHiddenTreesChange: (Set<String>) -> Unit = {},
    onHiddenSoilChange: (Set<String>) -> Unit = {},
    onSuitabilityChange: (Boolean) -> Unit = {},
    onSuitabilityClear: () -> Unit = {},
    onRainChange: (Boolean) -> Unit = {},
) {
    if (!layers.anyVisible) return
    val unboundedMaxHeight = (LocalConfiguration.current.screenHeightDp * UNBOUNDED_HEIGHT_FRACTION).dp

    Surface(
        modifier = modifier.widthIn(max = 300.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        BoxWithConstraints {
            val bounded = constraints.hasBoundedHeight
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggle)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.habitat_legend_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(
                            if (expanded) R.string.habitat_legend_collapse else R.string.habitat_legend_expand,
                        ),
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (expanded) {
                    Column(
                        modifier = Modifier
                            .then(
                                if (bounded) Modifier.weight(1f, fill = false) else Modifier.heightIn(max = unboundedMaxHeight),
                            )
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (layers.suitabilityShown) SuitabilityLegend(layers, onSuitabilityChange, onSuitabilityClear)
                        if (layers.aspect) AspectLegend(layers, onAspectClassesChange)
                        if (layers.trees) TreeLegend(layers, onHiddenTreesChange)
                        if (layers.soil) SoilLegend(layers, onHiddenSoilChange)
                        if (layers.rain) RainLegend(layers, onRainChange)
                    }
                }
            }
        }
    }
}

/**
 * The likely areas of the species chosen in the mushroom guide: the species, the radius,
 * the colour ramp, a switch to hide them and an action to forget the search.
 */
@Composable
private fun SuitabilityLegend(layers: HabitatLayers, onChange: (Boolean) -> Unit, onClear: () -> Unit) {
    val search = layers.suitabilitySearch ?: return
    var open by rememberSaveable("suitability") { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { open = !open }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val title = stringResource(R.string.suitability_legend_title)
                Icon(
                    imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (open) R.string.habitat_legend_collapse_section else R.string.habitat_legend_expand_section,
                        title,
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ShortcutText(stringResource(R.string.suitability_legend_clear), onClear)
            val switchLabel = stringResource(R.string.suitability_legend_show)
            Switch(
                checked = layers.suitability,
                onCheckedChange = onChange,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .semantics { contentDescription = switchLabel },
            )
        }
        if (open) {
            Text(
                text = search.speciesName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.suitability_legend_radius, suitabilityRadiusText(search.radiusMeters)),
                style = MaterialTheme.typography.bodySmall,
            )
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(3.dp)),
            ) {
                val steps = 48
                val width = size.width / steps
                for (step in 0 until steps) {
                    val score = SuitabilityScorer.MIN_VISIBLE_SCORE +
                        (1f - SuitabilityScorer.MIN_VISIBLE_SCORE) * step / (steps - 1)
                    drawRect(
                        color = Color(SuitabilityScorer.color(score)),
                        topLeft = Offset(step * width, 0f),
                        size = androidx.compose.ui.geometry.Size(width + 1f, size.height),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.suitability_legend_low), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.suitability_legend_high), style = MaterialTheme.typography.labelSmall)
            }
            LegendNote(stringResource(R.string.suitability_legend_note))
        }
    }
}

/**
 * The rainfall overlay: the length of the accumulation window, the colour ramp from
 * dry to torrential, and a switch to hide it.
 */
@Composable
private fun RainLegend(layers: HabitatLayers, onChange: (Boolean) -> Unit) {
    var open by rememberSaveable("rain") { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { open = !open }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val title = stringResource(R.string.habitat_rain_legend_title)
                Icon(
                    imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (open) R.string.habitat_legend_collapse_section else R.string.habitat_legend_expand_section,
                        title,
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val switchLabel = stringResource(R.string.habitat_rain_legend_show)
            Switch(
                checked = layers.rain,
                onCheckedChange = onChange,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .semantics { contentDescription = switchLabel },
            )
        }
        if (open) {
            Text(
                text = stringResource(R.string.habitat_rain_legend_window, layers.rainWindow),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(3.dp)),
            ) {
                val intensities = RainScale.legendIntensities
                val width = size.width / intensities.size
                intensities.forEachIndexed { index, intensity ->
                    drawRect(
                        color = Color(RainScale.color(intensity)),
                        topLeft = Offset(index * width, 0f),
                        size = androidx.compose.ui.geometry.Size(width + 1f, size.height),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.habitat_rain_legend_low), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.habitat_rain_legend_high), style = MaterialTheme.typography.labelSmall)
            }
            LegendNote(stringResource(R.string.habitat_rain_legend_note))
        }
    }
}

@Composable
private fun AspectLegend(layers: HabitatLayers, onChange: (Set<AspectClass>) -> Unit) {
    val all = HabitatSources.aspectLegend.map(HabitatSources::aspectClassOf).toSet()
    LegendSection(
        key = "aspect",
        title = stringResource(R.string.habitat_legend_aspect),
        onAll = { onChange(all) },
        onNone = { onChange(emptySet()) },
    ) {
        HabitatSources.aspectLegend.forEach { entry ->
            val aspectClass = HabitatSources.aspectClassOf(entry)
            val checked = aspectClass in layers.aspectClasses
            LegendCheckRow(
                checked = checked,
                onCheckedChange = { onChange(if (it) layers.aspectClasses + aspectClass else layers.aspectClasses - aspectClass) },
                label = stringResource(entry.labelRes),
                swatch = { LegendSwatch(entry) },
            )
        }
    }
}

@Composable
private fun TreeLegend(layers: HabitatLayers, onChange: (Set<String>) -> Unit) {
    val keys = HabitatSources.treeLegend.map { it.key }.toSet()
    LegendSection(
        key = "trees",
        title = stringResource(R.string.habitat_legend_trees),
        onAll = { onChange(emptySet()) },
        onNone = { onChange(keys) },
    ) {
        HabitatSources.treeLegend.forEach { entry ->
            LegendCheckRow(
                checked = entry.key !in layers.hiddenTrees,
                onCheckedChange = { onChange(if (it) layers.hiddenTrees - entry.key else layers.hiddenTrees + entry.key) },
                label = stringResource(entry.labelRes),
                swatch = { LegendSwatch(entry) },
            )
        }
        LegendNote(stringResource(R.string.habitat_trees_source))
    }
}

@Composable
private fun SoilLegend(layers: HabitatLayers, onChange: (Set<String>) -> Unit) {
    val keys = HabitatSources.soilLegend.map { it.key }.toSet()
    LegendSection(
        key = "soil",
        title = stringResource(R.string.habitat_legend_soil),
        onAll = { onChange(emptySet()) },
        onNone = { onChange(keys) },
    ) {
        SoilGroup.entries.forEach { group ->
            val members = HabitatSources.soilLegend.filter { it.group == group }
            val groupKeys = members.map { it.key }.toSet()
            val shown = members.count { it.key !in layers.hiddenSoil }
            val state = when (shown) {
                members.size -> ToggleableState.On
                0 -> ToggleableState.Off
                else -> ToggleableState.Indeterminate
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .triStateToggleable(state = state, role = Role.Checkbox) {
                        onChange(if (state == ToggleableState.On) layers.hiddenSoil + groupKeys else layers.hiddenSoil - groupKeys)
                    }
                    .padding(top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TriStateCheckbox(state = state, onClick = null, modifier = Modifier.size(20.dp))
                Swatch(Color(group.color), LegendPattern.SOLID)
                Text(
                    text = stringResource(group.labelRes),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            members.forEach { lithology ->
                LegendCheckRow(
                    checked = lithology.key !in layers.hiddenSoil,
                    onCheckedChange = {
                        onChange(if (it) layers.hiddenSoil - lithology.key else layers.hiddenSoil + lithology.key)
                    },
                    label = stringResource(lithology.labelRes),
                    modifier = Modifier.padding(start = 20.dp),
                )
            }
        }
        LegendNote(stringResource(R.string.habitat_soil_mapping))
        LegendNote(stringResource(R.string.habitat_soil_source))
    }
}

/** A collapsible section of the legend with "All" and "None" shortcuts. */
@Composable
private fun LegendSection(
    key: String,
    title: String,
    onAll: () -> Unit,
    onNone: () -> Unit,
    content: @Composable () -> Unit,
) {
    var open by rememberSaveable(key) { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { open = !open }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (open) R.string.habitat_legend_collapse_section else R.string.habitat_legend_expand_section,
                        title,
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ShortcutText(stringResource(R.string.habitat_legend_all), onAll)
            ShortcutText(stringResource(R.string.habitat_legend_none), onNone)
        }
        if (open) content()
    }
}

@Composable
private fun ShortcutText(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
private fun LegendCheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    swatch: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Without a click handler the checkbox does not reserve a 48 dp touch target:
        // the whole row is the target.
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.size(20.dp))
        val dimmed = if (checked) 1f else 0.45f
        if (swatch != null) Row(Modifier.alpha(dimmed)) { swatch() }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.alpha(dimmed),
        )
    }
}

@Composable
private fun LegendNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun LegendSwatch(entry: HabitatLegendEntry) = Swatch(Color(entry.color), entry.pattern)

/** A small square that shows a colour or the texture of the orientation overlay. */
@Composable
private fun Swatch(color: Color, pattern: LegendPattern) {
    Surface(
        modifier = Modifier.size(16.dp),
        shape = RoundedCornerShape(3.dp),
        color = if (pattern == LegendPattern.SOLID) color else Color.White,
        border = BorderStroke(0.5.dp, Color(0x55000000)),
    ) {
        if (pattern != LegendPattern.SOLID) {
            Canvas(Modifier.size(16.dp)) {
                val step = size.width / 4f
                val stroke = 1.5.dp.toPx()
                when (pattern) {
                    LegendPattern.NORTH_LINES -> for (i in -4..8) {
                        drawLine(color, Offset(i * step, size.height), Offset(i * step + size.height, 0f), stroke)
                    }
                    LegendPattern.SOUTH_LINES -> for (i in -4..8) {
                        drawLine(color, Offset(i * step, 0f), Offset(i * step + size.height, size.height), stroke)
                    }
                    LegendPattern.DOTS -> for (row in 0..3) {
                        for (column in 0..3) {
                            drawCircle(color, radius = stroke * 0.6f, center = Offset((column + 0.5f) * step, (row + 0.5f) * step))
                        }
                    }
                    LegendPattern.SOLID -> Unit
                }
            }
        }
    }
}

/** The share of the screen height the legend may take inside a scrolling parent. */
private const val UNBOUNDED_HEIGHT_FRACTION = 0.7f
