package com.mikotrack.ui.history

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import com.mikotrack.R
import com.mikotrack.core.Formatters
import com.mikotrack.data.repository.DefaultCategory
import com.mikotrack.domain.stats.FindsAnalysis
import com.mikotrack.domain.stats.TripAnalysis
import com.mikotrack.domain.stats.TripRanking
import com.mikotrack.ui.common.ColorDot
import com.mikotrack.ui.common.categoryLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** The tab with the detailed analysis of a mushroom outing. */
@Composable
fun TripAnalysisTab(
    analysis: TripAnalysis,
    ranking: TripRanking?,
    onShowOnMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val time = SimpleDateFormat("HH:mm", locale)
    val hasData = analysis.pointCount > 0 || analysis.waypointCount > 0

    val cards: List<@Composable () -> Unit> = buildList {
        add { FindsCard(analysis.finds, time) }
        if (analysis.pointCount >= 2) add { MovementCard(analysis) }
        if (analysis.pointCount >= 2) add { TerrainCard(analysis) }
        if (analysis.pointCount >= 2) add { RouteCard(analysis) }
        if (analysis.daylight != null) add { DaylightCard(analysis, time) }
        add { RecordsCard(ranking) }
        add { SummaryCard(analysis) }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.tripanalysis_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (hasData) {
                    FilledTonalButton(onClick = onShowOnMap) {
                        Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.tripanalysis_load_on_map),
                            modifier = Modifier.padding(start = 6.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
            if (!hasData) {
                Text(
                    text = stringResource(R.string.tripanalysis_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (wide) {
                // In landscape and on tablets the cards are laid out in two columns.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(0, 1).forEach { column ->
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            cards.filterIndexed { index, _ -> index % 2 == column }.forEach { it() }
                        }
                    }
                }
            } else {
                cards.forEach { it() }
            }
        }
    }
}

@Composable
private fun AnalysisCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

/** A row with a small icon, a label and its value aligned to the end. */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    icon: ImageVector? = null,
    highlight: Boolean = false,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    // A long value wraps within 55 % of the row instead of squeezing the label.
    val maxValueWidth = maxWidth * 0.55f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.widthIn(max = maxValueWidth),
        )
    }
    }
}

@Composable
private fun FindsCard(finds: FindsAnalysis, time: SimpleDateFormat) {
    AnalysisCard(stringResource(R.string.tripanalysis_finds_title), Icons.Filled.EmojiEvents) {
        if (!finds.hasFinds) {
            Text(
                text = stringResource(R.string.tripanalysis_no_finds),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(
                    R.string.tripanalysis_no_finds_hint,
                    stringResource(R.string.category_mushroom_found),
                    stringResource(R.string.category_interesting_mushroom),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = finds.findCount.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            finds.findsPerHour?.let {
                InfoRow(stringResource(R.string.tripanalysis_finds_per_hour), decimal(it), Icons.Filled.Timer)
            }
            finds.findsPerKm?.let {
                InfoRow(stringResource(R.string.tripanalysis_finds_per_km), decimal(it), Icons.Filled.Route)
            }
            finds.timeToFirstFindMillis?.let {
                InfoRow(stringResource(R.string.tripanalysis_time_to_first), Formatters.durationShort(it), Icons.Filled.AccessTime)
            }
            finds.firstFindMillis?.let {
                InfoRow(stringResource(R.string.tripanalysis_first_find), time.format(Date(it)))
            }
            finds.lastFindMillis?.let {
                InfoRow(stringResource(R.string.tripanalysis_last_find), time.format(Date(it)))
            }
            val minAltitude = finds.minAltitude
            val maxAltitude = finds.maxAltitude
            if (minAltitude != null && maxAltitude != null) {
                InfoRow(
                    stringResource(R.string.tripanalysis_find_altitude),
                    stringResource(
                        R.string.tripanalysis_find_altitude_value,
                        Formatters.altitude(minAltitude),
                        Formatters.altitude(maxAltitude),
                        Formatters.altitude(finds.averageAltitude),
                    ),
                    Icons.Filled.Height,
                )
            }
            finds.averageDistanceBetweenFindsMeters?.let {
                InfoRow(stringResource(R.string.tripanalysis_distance_between), Formatters.distance(it), Icons.AutoMirrored.Filled.DirectionsWalk)
            }
            finds.bestTimeSlot?.let { slot ->
                InfoRow(
                    stringResource(R.string.tripanalysis_best_slot),
                    stringResource(
                        R.string.tripanalysis_best_slot_value,
                        time.format(Date(slot.startMillis)),
                        time.format(Date(slot.endMillis)),
                        slot.finds,
                    ),
                    highlight = true,
                )
            }
            finds.bestSegment?.let { segment ->
                InfoRow(
                    stringResource(R.string.tripanalysis_best_segment),
                    stringResource(
                        R.string.tripanalysis_best_segment_value,
                        decimal(segment.startMeters / 1000.0),
                        decimal(segment.endMeters / 1000.0),
                        segment.finds,
                    ),
                    highlight = true,
                )
            }

            if (finds.timeSlots.size >= 2) {
                ChartTitle(stringResource(R.string.tripanalysis_chart_time))
                BarChart(
                    values = finds.timeSlots.map { it.finds },
                    labels = finds.timeSlots.map { time.format(Date(it.startMillis)) },
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (finds.altitudeBands.size >= 2) {
                ChartTitle(stringResource(R.string.tripanalysis_chart_altitude))
                BarChart(
                    values = finds.altitudeBands.map { it.finds },
                    labels = finds.altitudeBands.map { "${it.minAltitude.roundToInt()}" },
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        if (finds.countsByCategory.isNotEmpty()) {
            ChartTitle(stringResource(R.string.tripanalysis_by_category))
            finds.countsByCategory.forEach { (category, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ColorDot(Color(DefaultCategory.colorFor(category)))
                    Text(
                        text = categoryLabel(category),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(count.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ChartTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** A simple bar chart with the value over every bar and a few labels under them. */
@Composable
private fun BarChart(values: List<Int>, labels: List<String>, color: Color) {
    val measurer = rememberTextMeasurer()
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.surfaceVariant
    val maxValue = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    // Only some labels fit under narrow bars: about one of every few.
    val labelStep = ((values.size + 5) / 6).coerceAtLeast(1)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
    ) {
        val valueStyle = TextStyle(fontSize = 11.sp, color = textColor, fontWeight = FontWeight.SemiBold)
        val labelStyle = TextStyle(fontSize = 10.sp, color = textColor)
        val labelHeight = 16.dp.toPx()
        val valueHeight = 14.dp.toPx()
        val chartHeight = size.height - labelHeight - valueHeight
        val slot = size.width / values.size
        val barWidth = slot * 0.7f
        values.forEachIndexed { index, value ->
            val left = index * slot + (slot - barWidth) / 2f
            drawRoundRect(
                color = track,
                topLeft = Offset(left, valueHeight),
                size = Size(barWidth, chartHeight),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            if (value > 0) {
                val barHeight = chartHeight * value / maxValue
                val top = valueHeight + chartHeight - barHeight
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
                val text = measurer.measure(value.toString(), valueStyle)
                drawText(
                    text,
                    topLeft = Offset(
                        left + (barWidth - text.size.width) / 2f,
                        (top - text.size.height).coerceAtLeast(0f),
                    ),
                )
            }
            if (index % labelStep == 0) {
                val text = measurer.measure(labels[index], labelStyle)
                val x = (left + (barWidth - text.size.width) / 2f).coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f))
                drawText(text, topLeft = Offset(x, size.height - labelHeight + 2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun MovementCard(analysis: TripAnalysis) {
    val movement = analysis.movement
    AnalysisCard(stringResource(R.string.tripanalysis_movement_title), Icons.AutoMirrored.Filled.DirectionsWalk) {
        val moving = MaterialTheme.colorScheme.primary
        val stopped = MaterialTheme.colorScheme.outlineVariant
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .background(stopped, RoundedCornerShape(6.dp)),
        ) {
            val fraction = movement.movingFraction.toFloat().coerceIn(0f, 1f)
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(12.dp)
                        .background(moving, RoundedCornerShape(6.dp)),
                )
            }
        }
        Text(
            text = stringResource(R.string.tripanalysis_moving_share, (movement.movingFraction * 100).roundToInt()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(moving)
            Text(
                text = stringResource(R.string.tripanalysis_moving) + "  " + Formatters.durationShort(movement.movingTimeMillis),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 6.dp),
            )
            Box(Modifier.width(16.dp))
            ColorDot(stopped)
            Text(
                text = stringResource(R.string.tripanalysis_stopped) + "  " + Formatters.durationShort(movement.stoppedTimeMillis),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        InfoRow(stringResource(R.string.tripanalysis_avg_moving_speed), kmh(movement.averageMovingSpeedMps), Icons.Filled.Speed)
        InfoRow(stringResource(R.string.tripanalysis_max_speed), kmh(movement.maxSpeedMps), Icons.AutoMirrored.Filled.TrendingUp)
        movement.paceMillisPerKm?.let {
            InfoRow(
                stringResource(R.string.tripanalysis_pace),
                stringResource(R.string.tripanalysis_pace_value, Formatters.duration(it)),
                Icons.Filled.Timer,
            )
        }
        if (movement.longestStopMillis > 0) {
            InfoRow(stringResource(R.string.tripanalysis_longest_stop), Formatters.durationShort(movement.longestStopMillis), Icons.Filled.AccessTime)
        }
        if (movement.discardedSpikes > 0) {
            Text(
                text = stringResource(R.string.tripanalysis_spikes, movement.discardedSpikes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TerrainCard(analysis: TripAnalysis) {
    val terrain = analysis.terrain
    AnalysisCard(stringResource(R.string.tripanalysis_terrain_title), Icons.Filled.Landscape) {
        if (terrain.minAltitude == null) {
            Text(
                text = stringResource(R.string.tripanalysis_no_altitude),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TerrainRows(analysis)
        }
    }
}

@Composable
private fun TerrainRows(analysis: TripAnalysis) {
    val terrain = analysis.terrain
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoRow(stringResource(R.string.tripanalysis_altitude_min), Formatters.altitude(terrain.minAltitude), Icons.Filled.Height)
        InfoRow(stringResource(R.string.tripanalysis_altitude_max), Formatters.altitude(terrain.maxAltitude), Icons.Filled.Height)
        InfoRow(stringResource(R.string.tripanalysis_altitude_avg), Formatters.altitude(terrain.averageAltitude), Icons.Filled.Height)
        InfoRow(stringResource(R.string.tripanalysis_ascent), Formatters.elevationDelta(terrain.ascentMeters), Icons.AutoMirrored.Filled.TrendingUp)
        InfoRow(stringResource(R.string.tripanalysis_descent), Formatters.elevationDelta(-terrain.descentMeters))
        terrain.steepestClimb?.let { climb ->
            InfoRow(
                stringResource(R.string.tripanalysis_steepest),
                stringResource(
                    R.string.tripanalysis_steepest_value,
                    "${climb.gradePercent.roundToInt()}%",
                    Formatters.elevationDelta(climb.gainMeters),
                    Formatters.distance(climb.lengthMeters),
                ),
                Icons.Filled.Landscape,
                highlight = true,
            )
        }
    }
}

@Composable
private fun RouteCard(analysis: TripAnalysis) {
    val route = analysis.route
    AnalysisCard(stringResource(R.string.tripanalysis_route_title), Icons.Filled.Route) {
        InfoRow(stringResource(R.string.tripanalysis_max_from_start), Formatters.distance(route.maxDistanceFromStartMeters), Icons.Filled.Explore)
        route.maxDistanceFromParkingMeters?.let {
            InfoRow(stringResource(R.string.tripanalysis_max_from_parking), Formatters.distance(it), Icons.Filled.DirectionsCar)
        }
        route.endToStartMeters?.let {
            InfoRow(
                stringResource(R.string.tripanalysis_route_type),
                stringResource(
                    if (route.isLoop) R.string.tripanalysis_loop else R.string.tripanalysis_linear,
                    Formatters.distance(it),
                ),
                Icons.Filled.Place,
            )
        }
    }
}

@Composable
private fun DaylightCard(analysis: TripAnalysis, time: SimpleDateFormat) {
    val daylight = analysis.daylight ?: return
    AnalysisCard(stringResource(R.string.tripanalysis_daylight_title), Icons.Filled.WbSunny) {
        val sunrise = daylight.sunriseMillis
        val sunset = daylight.sunsetMillis
        if (sunrise == null || sunset == null) {
            Text(
                text = stringResource(R.string.tripanalysis_no_sun),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            InfoRow(stringResource(R.string.tripanalysis_sunrise), time.format(Date(sunrise)), Icons.Filled.WbTwilight)
            InfoRow(stringResource(R.string.tripanalysis_sunset), time.format(Date(sunset)), Icons.Filled.WbTwilight)
        }
        InfoRow(
            stringResource(R.string.tripanalysis_start_end),
            time.format(Date(daylight.startMillis)) + " – " + time.format(Date(daylight.endMillis)),
            Icons.Filled.AccessTime,
        )
        if (daylight.startedBeforeSunrise) {
            Text(
                text = stringResource(R.string.tripanalysis_before_sunrise),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        daylight.daylightLeftAtEndMillis?.let { left ->
            if (left >= 0) {
                InfoRow(stringResource(R.string.tripanalysis_daylight_left), Formatters.durationShort(left), highlight = true)
            } else {
                Text(
                    text = stringResource(R.string.tripanalysis_after_sunset, Formatters.durationShort(abs(left))),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RecordsCard(ranking: TripRanking?) {
    AnalysisCard(stringResource(R.string.tripanalysis_records_title), Icons.Filled.EmojiEvents) {
        if (ranking == null || !ranking.isMeaningful) {
            Text(
                text = stringResource(R.string.tripanalysis_records_single),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            RankRow(stringResource(R.string.tripanalysis_finds_count), ranking.findsRank, ranking.totalTrips)
            RankRow(stringResource(R.string.stat_distance), ranking.distanceRank, ranking.totalTrips)
            RankRow(stringResource(R.string.stat_duration), ranking.durationRank, ranking.totalTrips)
        }
    }
}

@Composable
private fun RankRow(label: String, rank: Int, total: Int) {
    val isRecord = rank == 1
    InfoRow(
        label = label,
        value = stringResource(R.string.tripanalysis_rank, rank, total) +
            if (isRecord) " · " + stringResource(R.string.tripanalysis_record) else "",
        icon = if (isRecord) Icons.Filled.EmojiEvents else null,
        highlight = isRecord,
    )
}

@Composable
private fun SummaryCard(analysis: TripAnalysis) {
    AnalysisCard(stringResource(R.string.tripanalysis_summary_title), Icons.Filled.Insights) {
        InfoRow(stringResource(R.string.stat_photos), analysis.photoCount.toString(), Icons.Filled.PhotoCamera)
        InfoRow(stringResource(R.string.stat_waypoints), analysis.waypointCount.toString(), Icons.Filled.Place)
        InfoRow(stringResource(R.string.trip_detail_points), analysis.pointCount.toString(), Icons.Filled.Route)
    }
}

private fun decimal(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

/** Walking speeds read better in km/h, always in the same unit. */
private fun kmh(metersPerSecond: Double): String = decimal(metersPerSecond * 3.6) + " km/h"
