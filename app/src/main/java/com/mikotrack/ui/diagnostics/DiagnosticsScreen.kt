package com.mikotrack.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikotrack.BuildConfig
import com.mikotrack.R
import com.mikotrack.core.Formatters
import com.mikotrack.domain.GpsQuality
import com.mikotrack.location.Constellation
import com.mikotrack.location.SatelliteInfo
import com.mikotrack.location.SensorKind
import com.mikotrack.location.SensorReading
import com.mikotrack.location.SensorSnapshot
import com.mikotrack.location.SensorStaticInfo
import com.mikotrack.ui.common.ColorDot
import com.mikotrack.ui.common.SectionCard
import com.mikotrack.ui.common.StatTile
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** The GPS and sensors diagnostics screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPermissions: () -> Unit = {},
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.diag_back))
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.diag_title),
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                actions = {
                    IconButton(onClick = { copyReport(context, state) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.diag_copy_report))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SummaryCard(state, onOpenPermissions)
            LocationCard(state)
            SatellitesCard(state)
            CompassCard(state)
            SensorsCard(
                title = stringResource(R.string.diag_section_motion),
                kinds = MOTION_SENSORS,
                state = state,
            )
            SensorsCard(
                title = stringResource(R.string.diag_section_environment),
                kinds = ENVIRONMENT_SENSORS,
                state = state,
                footer = {
                    state.sensors.barometricAltitudeMeters?.let { altitude ->
                        InfoRow(stringResource(R.string.diag_baro_altitude), fmt("%.0f m", altitude))
                        HintText(stringResource(R.string.diag_baro_altitude_hint))
                    }
                },
            )
            DeviceCard(state)
        }
    }
}

private val MOTION_SENSORS = listOf(
    SensorKind.ACCELEROMETER,
    SensorKind.GRAVITY,
    SensorKind.LINEAR_ACCELERATION,
    SensorKind.GYROSCOPE,
)

private val ENVIRONMENT_SENSORS = listOf(
    SensorKind.PRESSURE,
    SensorKind.LIGHT,
    SensorKind.AMBIENT_TEMPERATURE,
    SensorKind.RELATIVE_HUMIDITY,
)

// ---------------------------------------------------------------------------------------
// Sections
// ---------------------------------------------------------------------------------------

@Composable
private fun SummaryCard(state: DiagnosticsUiState, onOpenPermissions: () -> Unit) {
    val context = LocalContext.current
    val quality = state.quality
    val color = gpsQualityColor(quality)
    SectionCard(title = stringResource(R.string.diag_section_summary)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(gpsQualityIcon(quality), contentDescription = null, tint = color, modifier = Modifier.size(36.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = gpsQualityLabel(quality),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                    SignalBars(filled = gpsQualityBars(quality), color = color, barWidth = 5, maxHeight = 20)
                }
                Text(
                    text = stringResource(qualityExplanation(quality)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile(
                label = stringResource(R.string.diag_stat_accuracy),
                value = state.primaryFix?.takeIf { it.hasAccuracy() }?.let { fmt("±%.0f m", it.accuracy) } ?: "--",
                modifier = Modifier.weight(1f),
                accent = color,
            )
            StatTile(
                label = stringResource(R.string.diag_stat_satellites),
                value = if (state.gnss.satellites.isEmpty()) "--" else "${state.gnss.usedCount}/${state.gnss.visibleCount}",
                modifier = Modifier.weight(1f),
                accent = color,
            )
            StatTile(
                label = stringResource(R.string.diag_stat_age),
                value = state.fixAgeMillis?.let { formatAge(it) } ?: "--",
                modifier = Modifier.weight(1f),
                accent = color,
            )
        }
        qualityTip(quality)?.let { HintText(stringResource(it)) }

        val location = state.location
        if (!location.finePermission) {
            WarningText(
                stringResource(
                    if (location.coarsePermission) R.string.diag_tip_approximate else R.string.diag_tip_no_permission,
                ),
            )
            OutlinedButton(onClick = onOpenPermissions, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.diag_action_permissions))
            }
        }
        if (!location.locationEnabled || !location.gpsEnabled) {
            if (location.locationEnabled) WarningText(stringResource(R.string.diag_tip_gps_provider_off))
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.diag_action_location_settings))
            }
        }
    }
}

@Composable
private fun LocationCard(state: DiagnosticsUiState) {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.diag_section_fix)) {
        RowsBlock(locationRows(context, state))
        if (state.primaryFix == null) HintText(stringResource(R.string.diag_no_fix))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SatellitesCard(state: DiagnosticsUiState) {
    val context = LocalContext.current
    val satellites = state.gnss.satellites
    SectionCard(title = stringResource(R.string.diag_section_satellites)) {
        RowsBlock(satelliteRows(context, state))
        if (satellites.isEmpty()) {
            HintText(stringResource(R.string.diag_no_satellites))
            return@SectionCard
        }

        SubTitle(stringResource(R.string.diag_sky_plot))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SkyPlot(
                satellites = satellites,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            satellites.map { it.constellation }.distinct().sorted().forEach { constellation ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ColorDot(constellationColor(constellation), size = 10)
                    Text(constellationName(constellation), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        HintText(stringResource(R.string.diag_legend_used))

        SubTitle(stringResource(R.string.diag_signal_chart))
        SignalChart(
            satellites = satellites,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        )
        HintText(stringResource(R.string.diag_cn0_hint))

        SatelliteTable(satellites)
    }
}

@Composable
private fun CompassCard(state: DiagnosticsUiState) {
    val context = LocalContext.current
    val sensors = state.sensors
    val points = stringArrayResource(R.array.format_compass_points)
    SectionCard(title = stringResource(R.string.diag_section_compass)) {
        val heading = sensors.headingDegrees
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CompassDial(heading = heading, northLabel = points[0], modifier = Modifier.size(120.dp))
                Text(
                    text = heading?.let { "${points[Formatters.compassIndex(it.toDouble())]} ${fmt("%.0f°", it)}" } ?: "--",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BubbleLevel(
                    sensors = sensors,
                    contentDescription = stringResource(R.string.diag_bubble_cd),
                    modifier = Modifier.size(120.dp),
                )
                Text(
                    text = sensors.pitchDegrees?.let { fmt("%+.1f° / %+.1f°", it, sensors.rollDegrees ?: 0f) } ?: "--",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        RowsBlock(compassRows(context, state))
        HintText(stringResource(R.string.diag_heading_hint))
        if (state.sensorInfo[SensorKind.MAGNETOMETER] == null && state.sensorInfo[SensorKind.ROTATION_VECTOR] == null) {
            WarningText(stringResource(R.string.diag_compass_unavailable))
        }
        if (sensors.magneticFieldOutOfRange) {
            WarningText(stringResource(R.string.diag_mag_out_of_range, fmt("%.0f µT", sensors.magneticFieldMicroTesla ?: 0f)))
        }
        if (sensors.compassNeedsCalibration) {
            WarningText(stringResource(R.string.diag_calibrate_hint))
        }
        listOf(SensorKind.MAGNETOMETER, SensorKind.ROTATION_VECTOR).forEach { kind ->
            SensorBlock(kind, state.sensorInfo[kind], sensors[kind])
        }
    }
}

@Composable
private fun SensorsCard(
    title: String,
    kinds: List<SensorKind>,
    state: DiagnosticsUiState,
    footer: @Composable () -> Unit = {},
) {
    SectionCard(title = title) {
        kinds.forEachIndexed { index, kind ->
            if (index > 0) HorizontalDivider()
            SensorBlock(kind, state.sensorInfo[kind], state.sensors[kind])
        }
        footer()
    }
}

@Composable
private fun DeviceCard(state: DiagnosticsUiState) {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.diag_section_device)) {
        RowsBlock(deviceRows(context, state))
        if (state.device.locationLimitedInBackground) {
            WarningText(stringResource(R.string.diag_lps_warning))
        }
        if (state.device.powerSaveMode) {
            WarningText(stringResource(R.string.diag_power_save_warning))
        }
    }
}

// ---------------------------------------------------------------------------------------
// Small building blocks
// ---------------------------------------------------------------------------------------

@Composable
private fun RowsBlock(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { (label, value) -> InfoRow(label, value) }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f),
        )
    }
}

@Composable
private fun SubTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WarningText(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = WARNING_COLOR, modifier = Modifier.size(18.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SensorBlock(kind: SensorKind, info: SensorStaticInfo?, reading: SensorReading?) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(sensorName(kind)), style = MaterialTheme.typography.titleSmall)
        if (info == null) {
            HintText(stringResource(R.string.diag_sensor_not_available))
            return@Column
        }
        Text(
            text = reading?.let { sensorValues(kind, it) } ?: stringResource(R.string.diag_sensor_waiting),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
        HintText("${info.name} · ${info.vendor}")
        HintText(sensorSpecs(context, kind, info))
    }
}

@Composable
private fun SatelliteTable(satellites: List<SatelliteInfo>) {
    val sorted = satellites.sortedWith(compareByDescending<SatelliteInfo> { it.usedInFix }.thenByDescending { it.cn0DbHz })
    val headers = listOf(
        stringResource(R.string.diag_col_system),
        stringResource(R.string.diag_col_id),
        stringResource(R.string.diag_col_cn0),
        stringResource(R.string.diag_col_elevation),
        stringResource(R.string.diag_col_azimuth),
        stringResource(R.string.diag_col_used),
    )
    val weights = listOf(1.6f, 0.8f, 1f, 1f, 1f, 0.8f)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            headers.forEachIndexed { i, header ->
                Text(
                    text = header,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.weight(weights[i]),
                )
            }
        }
        HorizontalDivider()
        sorted.forEach { sat ->
            val cells = listOf(
                constellationName(sat.constellation),
                sat.svid.toString(),
                fmt("%.1f", sat.cn0DbHz),
                fmt("%.0f°", sat.elevationDegrees),
                fmt("%.0f°", sat.azimuthDegrees),
                if (sat.usedInFix) "✓" else "·",
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                cells.forEachIndexed { i, cell ->
                    Text(
                        text = cell,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = when (i) {
                            0 -> constellationColor(sat.constellation)
                            2 -> cn0Color(sat.cn0DbHz)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.weight(weights[i]),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Drawings
// ---------------------------------------------------------------------------------------

@Composable
private fun SkyPlot(satellites: List<SatelliteInfo>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val points = stringArrayResource(R.array.format_compass_points)
    val cardinals = listOf(points[0], points[4], points[8], points[12])
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val description = stringResource(R.string.diag_sky_plot_cd, satellites.size)
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        val labelSpace = 16.dp.toPx()
        val radius = min(size.width, size.height) / 2f - labelSpace
        val center = Offset(size.width / 2f, size.height / 2f)
        // Rings at 0°, 30° and 60° of elevation; the centre is the zenith.
        listOf(1f, 2f / 3f, 1f / 3f).forEach { fraction ->
            drawCircle(lineColor, radius * fraction, center, style = Stroke(1.dp.toPx()))
        }
        drawLine(lineColor, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())
        drawLine(lineColor, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
        val labelStyle = TextStyle(color = labelColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        cardinals.forEachIndexed { i, label ->
            val angle = Math.toRadians(i * 90.0)
            val distance = radius + labelSpace / 2f
            drawCenteredText(
                measurer,
                label,
                Offset(center.x + distance * sin(angle).toFloat(), center.y - distance * cos(angle).toFloat()),
                labelStyle,
            )
        }
        val dot = 5.dp.toPx()
        satellites
            .filterNot { it.elevationDegrees == 0f && it.azimuthDegrees == 0f }
            .filter { it.elevationDegrees >= 0f }
            .sortedBy { it.usedInFix }
            .forEach { sat ->
                val r = radius * (90f - sat.elevationDegrees.coerceIn(0f, 90f)) / 90f
                val az = Math.toRadians(sat.azimuthDegrees.toDouble())
                val position = Offset(center.x + r * sin(az).toFloat(), center.y - r * cos(az).toFloat())
                val color = constellationColor(sat.constellation)
                if (sat.usedInFix) {
                    drawCircle(color, dot, position)
                } else {
                    drawCircle(color, dot, position, style = Stroke(1.5.dp.toPx()))
                }
            }
    }
}

@Composable
private fun SignalChart(satellites: List<SatelliteInfo>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val sorted = satellites.sortedByDescending { it.cn0DbHz }
    Canvas(modifier = modifier) {
        val axisWidth = 22.dp.toPx()
        val bottomSpace = 14.dp.toPx()
        val chartHeight = size.height - bottomSpace
        val chartWidth = size.width - axisWidth
        val maxCn0 = 50f
        val labelStyle = TextStyle(color = labelColor, fontSize = 9.sp)
        listOf(10f, 20f, 30f, 40f, 50f).forEach { level ->
            val y = chartHeight * (1f - level / maxCn0)
            drawLine(
                gridColor,
                Offset(axisWidth, y),
                Offset(size.width, y),
                1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            drawCenteredText(measurer, level.toInt().toString(), Offset(axisWidth / 2f, y), labelStyle)
        }
        if (sorted.isEmpty()) return@Canvas
        val slot = chartWidth / sorted.size
        val gap = (slot * 0.2f).coerceAtMost(4.dp.toPx())
        sorted.forEachIndexed { i, sat ->
            val h = chartHeight * (sat.cn0DbHz.coerceIn(0f, maxCn0) / maxCn0)
            val left = axisWidth + i * slot + gap / 2f
            drawRect(
                color = cn0Color(sat.cn0DbHz).copy(alpha = if (sat.usedInFix) 1f else 0.45f),
                topLeft = Offset(left, chartHeight - h),
                size = androidx.compose.ui.geometry.Size(slot - gap, h),
            )
            if (slot >= 14.dp.toPx()) {
                drawCenteredText(
                    measurer,
                    sat.svid.toString(),
                    Offset(left + (slot - gap) / 2f, chartHeight + bottomSpace / 2f),
                    labelStyle.copy(color = constellationColor(sat.constellation)),
                )
            }
        }
    }
}

@Composable
private fun CompassDial(heading: Float?, northLabel: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val ringColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val labelColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f - 2.dp.toPx()
        drawCircle(ringColor, radius, center, style = Stroke(2.dp.toPx()))
        // Fixed marker at the top: the direction the phone is pointing to.
        drawLine(labelColor, Offset(center.x, center.y - radius), Offset(center.x, center.y - radius + 10.dp.toPx()), 3.dp.toPx())
        if (heading == null) return@Canvas
        rotate(-heading, center) {
            val needle = radius * 0.7f
            val half = 7.dp.toPx()
            val north = Path().apply {
                moveTo(center.x, center.y - needle)
                lineTo(center.x - half, center.y)
                lineTo(center.x + half, center.y)
                close()
            }
            val south = Path().apply {
                moveTo(center.x, center.y + needle)
                lineTo(center.x - half, center.y)
                lineTo(center.x + half, center.y)
                close()
            }
            drawPath(north, Color(0xFFD32F2F))
            drawPath(south, ringColor)
            drawCenteredText(
                measurer,
                northLabel,
                Offset(center.x, center.y - radius + 20.dp.toPx()),
                TextStyle(color = Color(0xFFD32F2F), fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun BubbleLevel(sensors: SensorSnapshot, contentDescription: String, modifier: Modifier = Modifier) {
    val ringColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val offset = sensors.bubbleOffset
    val tilt = hypot(sensors.pitchDegrees ?: 0f, sensors.rollDegrees ?: 0f)
    val bubbleColor = if (tilt < 2f) Color(0xFF2E9D46) else WARNING_COLOR
    Canvas(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f - 2.dp.toPx()
        val bubble = radius * 0.18f
        drawCircle(ringColor, radius, center, style = Stroke(2.dp.toPx()))
        drawCircle(ringColor, radius * 0.3f, center, style = Stroke(1.dp.toPx()))
        drawLine(ringColor, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
        drawLine(ringColor, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())
        if (offset == null) return@Canvas
        // A tilt of about 15° takes the bubble to the edge of the level.
        var dx = offset.first / FULL_SCALE_TILT
        var dy = offset.second / FULL_SCALE_TILT
        val length = hypot(dx, dy)
        if (length > 1f) {
            dx /= length
            dy /= length
        }
        val travel = radius - bubble
        val position = Offset(center.x + dx * travel, center.y - dy * travel)
        drawCircle(bubbleColor.copy(alpha = 0.85f), bubble, position)
        drawCircle(Color.White.copy(alpha = 0.6f), bubble, position, style = Stroke(1.5.dp.toPx()))
    }
}

private fun DrawScope.drawCenteredText(measurer: TextMeasurer, text: String, center: Offset, style: TextStyle) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}

private val FULL_SCALE_TILT = sin(Math.toRadians(15.0)).toFloat()
private val WARNING_COLOR = Color(0xFFF2A100)

private fun constellationColor(constellation: Constellation): Color = when (constellation) {
    Constellation.GPS -> Color(0xFF1E88E5)
    Constellation.GLONASS -> Color(0xFFE53935)
    Constellation.GALILEO -> Color(0xFF8E24AA)
    Constellation.BEIDOU -> Color(0xFFFB8C00)
    Constellation.QZSS -> Color(0xFF00897B)
    Constellation.SBAS -> Color(0xFF757575)
    Constellation.IRNSS -> Color(0xFF6D4C41)
    Constellation.UNKNOWN -> Color(0xFF9E9E9E)
}

private fun cn0Color(cn0: Float): Color = when {
    cn0 < 20f -> Color(0xFFD32F2F)
    cn0 < 30f -> Color(0xFFF57C00)
    cn0 < 40f -> Color(0xFF9CCC65)
    else -> Color(0xFF2E9D46)
}

// ---------------------------------------------------------------------------------------
// Texts shared by the screen and the copied report
// ---------------------------------------------------------------------------------------

private fun fmt(pattern: String, vararg args: Any?): String = String.format(Locale.US, pattern, *args)

private fun formatAge(millis: Long): String = when {
    millis < 60_000L -> fmt("%d s", millis / 1_000L)
    else -> Formatters.durationShort(millis)
}

@Composable
private fun constellationName(constellation: Constellation): String =
    if (constellation == Constellation.UNKNOWN) stringResource(R.string.diag_unknown) else constellation.label

private fun constellationName(context: Context, constellation: Constellation): String =
    if (constellation == Constellation.UNKNOWN) context.getString(R.string.diag_unknown) else constellation.label

private fun qualityExplanation(quality: GpsQuality): Int = when (quality) {
    GpsQuality.DISABLED -> R.string.diag_quality_explain_disabled
    GpsQuality.SEARCHING -> R.string.diag_quality_explain_searching
    GpsQuality.POOR -> R.string.diag_quality_explain_poor
    GpsQuality.FAIR -> R.string.diag_quality_explain_fair
    GpsQuality.GOOD -> R.string.diag_quality_explain_good
    GpsQuality.EXCELLENT -> R.string.diag_quality_explain_excellent
}

private fun qualityTip(quality: GpsQuality): Int? = when (quality) {
    GpsQuality.DISABLED -> R.string.diag_tips_disabled
    GpsQuality.SEARCHING -> R.string.diag_tips_searching
    GpsQuality.POOR, GpsQuality.FAIR -> R.string.diag_tips_poor
    else -> null
}

private fun sensorName(kind: SensorKind): Int = when (kind) {
    SensorKind.ACCELEROMETER -> R.string.diag_sensor_accelerometer
    SensorKind.GRAVITY -> R.string.diag_sensor_gravity
    SensorKind.LINEAR_ACCELERATION -> R.string.diag_sensor_linear_acceleration
    SensorKind.GYROSCOPE -> R.string.diag_sensor_gyroscope
    SensorKind.MAGNETOMETER -> R.string.diag_sensor_magnetometer
    SensorKind.ROTATION_VECTOR -> R.string.diag_sensor_rotation_vector
    SensorKind.PRESSURE -> R.string.diag_sensor_pressure
    SensorKind.LIGHT -> R.string.diag_sensor_light
    SensorKind.AMBIENT_TEMPERATURE -> R.string.diag_sensor_temperature
    SensorKind.RELATIVE_HUMIDITY -> R.string.diag_sensor_humidity
}

private fun sensorUnit(kind: SensorKind): String = when (kind) {
    SensorKind.ACCELEROMETER, SensorKind.GRAVITY, SensorKind.LINEAR_ACCELERATION -> "m/s²"
    SensorKind.GYROSCOPE -> "rad/s"
    SensorKind.MAGNETOMETER -> "µT"
    SensorKind.ROTATION_VECTOR -> ""
    SensorKind.PRESSURE -> "hPa"
    SensorKind.LIGHT -> "lux"
    SensorKind.AMBIENT_TEMPERATURE -> "°C"
    SensorKind.RELATIVE_HUMIDITY -> "%"
}

private fun sensorValues(kind: SensorKind, reading: SensorReading): String = when (kind) {
    SensorKind.ACCELEROMETER, SensorKind.GRAVITY, SensorKind.LINEAR_ACCELERATION,
    SensorKind.GYROSCOPE, SensorKind.MAGNETOMETER,
    -> fmt(
        "x %+.2f  y %+.2f  z %+.2f\n|%.2f| %s",
        reading.x, reading.y, reading.z, reading.magnitude, sensorUnit(kind),
    )
    SensorKind.ROTATION_VECTOR -> reading.values.take(4).joinToString("  ") { fmt("%+.3f", it) }
    SensorKind.PRESSURE -> fmt("%.1f hPa", reading.x)
    SensorKind.LIGHT -> fmt("%.0f lux", reading.x)
    SensorKind.AMBIENT_TEMPERATURE -> fmt("%.1f °C", reading.x)
    SensorKind.RELATIVE_HUMIDITY -> fmt("%.0f %%", reading.x)
}

private fun sensorSpecs(context: Context, kind: SensorKind, info: SensorStaticInfo): String {
    val unit = sensorUnit(kind).let { if (it.isEmpty()) "" else " $it" }
    return context.getString(
        R.string.diag_sensor_specs,
        fmt("%.4g", info.maxRange) + unit,
        fmt("%.3g", info.resolution) + unit,
        fmt("%.3g", info.powerMilliAmps),
        info.minDelayMicros.toString(),
    )
}

private fun onOff(context: Context, value: Boolean): String =
    context.getString(if (value) R.string.diag_on else R.string.diag_off)

private fun locationRows(context: Context, state: DiagnosticsUiState): List<Pair<String, String>> {
    fun s(id: Int): String = context.getString(id)
    val location = state.location
    val rows = mutableListOf(
        s(R.string.diag_location_services) to onOff(context, location.locationEnabled),
        s(R.string.diag_gps_provider) to onOff(context, location.gpsEnabled),
        s(R.string.diag_network_provider) to onOff(context, location.networkEnabled),
        s(R.string.diag_permission) to s(
            when {
                location.finePermission -> R.string.diag_permission_precise
                location.coarsePermission -> R.string.diag_permission_approximate
                else -> R.string.diag_permission_none
            },
        ),
        s(R.string.diag_background_permission) to s(
            if (location.backgroundPermission) R.string.diag_background_granted else R.string.diag_background_denied,
        ),
        s(R.string.diag_gnss_hardware) to (state.gnssHardwareModel ?: s(R.string.diag_unknown)),
        s(R.string.diag_gnss_year) to (state.gnssHardwareYear?.toString() ?: s(R.string.diag_unknown)),
        s(R.string.diag_ttff) to (state.gnss.timeToFirstFixMillis?.let { fmt("%.1f s", it / 1000f) } ?: "--"),
    )
    val fix = state.primaryFix ?: return rows
    rows += s(R.string.diag_provider) to fix.provider.orEmpty()
    rows += s(R.string.diag_latitude) to fmt("%.6f°", fix.latitude)
    rows += s(R.string.diag_longitude) to fmt("%.6f°", fix.longitude)
    rows += s(R.string.diag_accuracy) to (if (fix.hasAccuracy()) fmt("±%.1f m", fix.accuracy) else "--")
    rows += s(R.string.diag_altitude) to altitudeText(fix)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        rows += s(R.string.diag_altitude_msl) to if (fix.hasMslAltitude()) {
            fmt("%.1f m", fix.mslAltitudeMeters) +
                if (fix.hasMslAltitudeAccuracy()) fmt(" ±%.1f m", fix.mslAltitudeAccuracyMeters) else ""
        } else {
            "--"
        }
    }
    rows += s(R.string.diag_speed) to if (fix.hasSpeed()) {
        fmt("%.1f m/s (%.1f km/h)", fix.speed, fix.speed * 3.6f) +
            if (fix.hasSpeedAccuracy()) fmt(" ±%.1f", fix.speedAccuracyMetersPerSecond) else ""
    } else {
        "--"
    }
    rows += s(R.string.diag_bearing) to if (fix.hasBearing()) {
        val points = context.resources.getStringArray(R.array.format_compass_points)
        fmt("%.0f° %s", fix.bearing, points[Formatters.compassIndex(fix.bearing.toDouble())]) +
            if (fix.hasBearingAccuracy()) fmt(" ±%.0f°", fix.bearingAccuracyDegrees) else ""
    } else {
        "--"
    }
    val time = DateFormat.getTimeFormat(context).format(Date(fix.time))
    rows += s(R.string.diag_fix_time) to
        context.getString(R.string.diag_fix_time_value, time, state.fixAgeMillis?.let(::formatAge) ?: "--")
    rows += s(R.string.diag_fixes_received) to state.providers.joinToString("\n") { "${it.provider} ${it.count}" }
    rows += s(R.string.diag_update_interval) to state.providers.joinToString("\n") { stats ->
        "${stats.provider} " + (stats.averageIntervalMillis?.let { fmt("%.1f s", it / 1000f) } ?: "--")
    }
    return rows
}

private fun altitudeText(fix: Location): String {
    if (!fix.hasAltitude()) return "--"
    return fmt("%.1f m", fix.altitude) +
        if (fix.hasVerticalAccuracy()) fmt(" ±%.1f m", fix.verticalAccuracyMeters) else ""
}

private fun satelliteRows(context: Context, state: DiagnosticsUiState): List<Pair<String, String>> {
    val gnss = state.gnss
    if (gnss.satellites.isEmpty()) return emptyList()
    val rows = mutableListOf(
        context.getString(R.string.diag_sats_visible_used) to "${gnss.visibleCount} / ${gnss.usedCount}",
        context.getString(R.string.diag_avg_cn0) to (gnss.averageTopCn0?.let { fmt("%.1f dB-Hz", it) } ?: "--"),
    )
    gnss.usedByConstellation.forEach { (constellation, counts) ->
        rows += context.getString(R.string.diag_constellation_row, constellationName(context, constellation)) to
            "${counts.first} / ${counts.second}"
    }
    return rows
}

private fun compassRows(context: Context, state: DiagnosticsUiState): List<Pair<String, String>> {
    val sensors = state.sensors
    val points = context.resources.getStringArray(R.array.format_compass_points)
    return listOf(
        context.getString(R.string.diag_heading) to (
            sensors.headingDegrees?.let { fmt("%.0f° %s", it, points[Formatters.compassIndex(it.toDouble())]) } ?: "--"
            ),
        context.getString(R.string.diag_pitch) to (sensors.pitchDegrees?.let { fmt("%+.1f°", it) } ?: "--"),
        context.getString(R.string.diag_roll) to (sensors.rollDegrees?.let { fmt("%+.1f°", it) } ?: "--"),
        context.getString(R.string.diag_magnetic_field) to
            (sensors.magneticFieldMicroTesla?.let { fmt("%.1f µT", it) } ?: "--"),
        context.getString(R.string.diag_mag_accuracy) to
            (sensors.magnetometerAccuracy?.let { context.getString(accuracyName(it)) } ?: "--"),
    )
}

private fun accuracyName(accuracy: Int): Int = when (accuracy) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> R.string.diag_accuracy_high
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> R.string.diag_accuracy_medium
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> R.string.diag_accuracy_low
    else -> R.string.diag_accuracy_unreliable
}

private fun locationPowerSaveName(mode: Int): Int = when (mode) {
    PowerManager.LOCATION_MODE_NO_CHANGE -> R.string.diag_lps_no_change
    PowerManager.LOCATION_MODE_GPS_DISABLED_WHEN_SCREEN_OFF -> R.string.diag_lps_gps_disabled
    PowerManager.LOCATION_MODE_ALL_DISABLED_WHEN_SCREEN_OFF -> R.string.diag_lps_all_disabled
    PowerManager.LOCATION_MODE_FOREGROUND_ONLY -> R.string.diag_lps_foreground_only
    PowerManager.LOCATION_MODE_THROTTLE_REQUESTS_WHEN_SCREEN_OFF -> R.string.diag_lps_throttle
    else -> R.string.diag_unknown
}

private fun deviceRows(context: Context, state: DiagnosticsUiState): List<Pair<String, String>> {
    val device = state.device
    fun s(id: Int): String = context.getString(id)
    val rows = mutableListOf(
        s(R.string.diag_android_version) to "${device.androidRelease} (API ${device.sdkInt})",
        s(R.string.diag_device_model) to "${device.manufacturer} ${device.model}",
        s(R.string.diag_battery) to (
            device.batteryPercent?.let {
                context.getString(
                    if (device.charging) R.string.diag_battery_value_charging else R.string.diag_battery_value,
                    it,
                )
            } ?: "--"
            ),
        s(R.string.diag_battery_temperature) to (device.batteryTemperatureCelsius?.let { fmt("%.1f °C", it) } ?: "--"),
        s(R.string.diag_power_save) to onOff(context, device.powerSaveMode),
    )
    device.locationPowerSaveMode?.let { rows += s(R.string.diag_location_power_save) to s(locationPowerSaveName(it)) }
    rows += s(R.string.diag_battery_optimization) to s(
        if (device.ignoringBatteryOptimizations) R.string.diag_battery_opt_unrestricted else R.string.diag_battery_opt_optimized,
    )
    return rows
}

/** Builds the plain-text report copied to the clipboard. */
internal fun buildReport(context: Context, state: DiagnosticsUiState): String = buildString {
    fun section(title: String, rows: List<Pair<String, String>>) {
        appendLine()
        appendLine("== $title ==")
        rows.forEach { (label, value) -> appendLine("$label: ${value.replace("\n", " · ")}") }
    }
    appendLine(context.getString(R.string.diag_report_header))
    appendLine(
        context.getString(
            R.string.diag_report_generated,
            DateFormat.getDateFormat(context).format(Date()) + " " + DateFormat.getTimeFormat(context).format(Date()),
        ),
    )
    appendLine("MycoTrack ${BuildConfig.VERSION_NAME}")
    section(
        context.getString(R.string.diag_section_summary),
        listOf(context.getString(R.string.diag_gps_quality) to qualityName(context, state.quality)),
    )
    section(context.getString(R.string.diag_section_fix), locationRows(context, state))
    section(context.getString(R.string.diag_section_satellites), satelliteRows(context, state))
    state.gnss.satellites
        .sortedWith(compareBy<SatelliteInfo> { it.constellation }.thenBy { it.svid })
        .forEach { sat ->
            appendLine(
                fmt(
                    "%-8s %3d  C/N0 %4.1f  el %3.0f°  az %3.0f°  %s",
                    constellationName(context, sat.constellation),
                    sat.svid,
                    sat.cn0DbHz,
                    sat.elevationDegrees,
                    sat.azimuthDegrees,
                    if (sat.usedInFix) "✓" else "",
                ),
            )
        }
    section(context.getString(R.string.diag_section_compass), compassRows(context, state))
    val sensorRows = SensorKind.entries.map { kind ->
        val info = state.sensorInfo[kind]
        val reading = state.sensors[kind]
        context.getString(sensorName(kind)) to when {
            info == null -> context.getString(R.string.diag_sensor_not_available)
            else -> (reading?.let { sensorValues(kind, it) } ?: "--") +
                " [${info.name}, ${info.vendor}; ${sensorSpecs(context, kind, info)}]"
        }
    }
    val altitude = state.sensors.barometricAltitudeMeters?.let {
        listOf(context.getString(R.string.diag_baro_altitude) to fmt("%.0f m", it))
    }.orEmpty()
    section(context.getString(R.string.diag_section_sensors), sensorRows + altitude)
    section(context.getString(R.string.diag_section_device), deviceRows(context, state))
}

private fun qualityName(context: Context, quality: GpsQuality): String = context.getString(
    when (quality) {
        GpsQuality.DISABLED -> R.string.diag_quality_disabled
        GpsQuality.SEARCHING -> R.string.diag_quality_searching
        GpsQuality.POOR -> R.string.diag_quality_poor
        GpsQuality.FAIR -> R.string.diag_quality_fair
        GpsQuality.GOOD -> R.string.diag_quality_good
        GpsQuality.EXCELLENT -> R.string.diag_quality_excellent
    },
)

private fun copyReport(context: Context, state: DiagnosticsUiState) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.diag_title), buildReport(context, state)))
    Toast.makeText(context, R.string.diag_report_copied, Toast.LENGTH_SHORT).show()
}
