package com.mikotrack.ui.elevation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import java.util.Locale
import com.mikotrack.R
import com.mikotrack.domain.model.ElevationProfile
import com.mikotrack.ui.theme.ForestGreenLight
import com.mikotrack.ui.theme.SkyBlue
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.core.cartesian.layer.CartesianLayerPadding
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round

/**
 * The elevation profile of a trip.
 *
 * Vico is the only element that draws the curve and the axes. The marker of the current
 * position and the touch handling live in an overlay that uses the exact plot area
 * reported by Vico, so the marker, the taps and the curve can never drift apart.
 */
@Composable
fun ElevationProfileView(
    profile: ElevationProfile,
    currentDistance: Double?,
    onDistanceSelected: (Double) -> Unit,
    modifier: Modifier = Modifier,
    lineColor: Color = ForestGreenLight,
    markerColor: Color = SkyBlue,
) {
    if (profile.points.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.elevation_not_enough_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    val producer = remember { CartesianChartModelProducer() }

    LaunchedEffect(profile.points) {
        // Whole metres are precise enough for the axis and keep Vico away from floating
        // point residues in the x values. Samples that collapse onto the same metre are
        // merged, because Vico expects strictly increasing x values.
        val samples = profile.points
            .map { round(it.distanceMeters) to it.altitude }
            .distinctBy { it.first }
        if (samples.size < 2) return@LaunchedEffect
        producer.runTransaction {
            lineSeries { series(samples.map { it.first }, samples.map { it.second }) }
        }
    }

    val yRange = remember(profile.minAltitude, profile.maxAltitude) {
        altitudeAxisRange(profile.minAltitude, profile.maxAltitude)
    }
    val rangeProvider = remember(yRange) {
        CartesianLayerRangeProvider.fixed(minY = yRange.first, maxY = yRange.second)
    }

    val line = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
        areaFill = LineCartesianLayer.AreaFill.single(fill(lineColor.copy(alpha = 0.22f))),
    )
    val lineProvider = remember(line) {
        LineCartesianLayer.LineProvider.series(listOf(line))
    }
    val lineLayer = rememberLineCartesianLayer(
        lineProvider = lineProvider,
        rangeProvider = rangeProvider,
    )

    val geometry = remember { PlotGeometry() }
    val geometryVersion = remember { mutableIntStateOf(0) }
    val geometryDecoration = remember(geometry) {
        GeometryDecoration(geometry) { geometryVersion.intValue++ }
    }

    val totalDistance = profile.totalDistance
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val distanceFormatter = remember(totalDistance, locale) {
        CartesianValueFormatter { _, value, _ ->
            if (totalDistance >= 2_000.0) String.format(locale, "%.1f km", value / 1000.0) else "${value.toInt()} m"
        }
    }
    val altitudeFormatter = remember {
        CartesianValueFormatter { _, value, _ -> "${value.toInt()} m" }
    }

    val chart = rememberCartesianChart(
        layers = arrayOf(lineLayer),
        startAxis = VerticalAxis.rememberStart(valueFormatter = altitudeFormatter),
        bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = distanceFormatter),
        // Vico derives the default step from the GCD of the x deltas, which throws for
        // irregular GPS distances ("The x values are too precise"). A fixed step avoids it.
        getXStep = { model -> profileXStep(model.width) },
        // The extra room at the end keeps the last label of the distance axis from being
        // clipped. The marker mapping subtracts the same room (see GeometryDecoration).
        layerPadding = { LAYER_PADDING },
        decorations = listOf(geometryDecoration),
    )

    Box(modifier = modifier) {
        CartesianChartHost(
            chart = chart,
            modelProducer = producer,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(
                zoomEnabled = false,
                initialZoom = Zoom.Content,
                minZoom = Zoom.Content,
                maxZoom = Zoom.Content,
            ),
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(totalDistance) {
                    detectTapGestures { offset ->
                        geometry.distanceAt(offset.x, totalDistance)?.let(onDistanceSelected)
                    }
                }
                .pointerInput(totalDistance) {
                    detectHorizontalDragGestures { change, _ ->
                        geometry.distanceAt(change.position.x, totalDistance)
                            ?.let(onDistanceSelected)
                    }
                },
        ) {
            // Reading the version subscribes the overlay to geometry changes.
            geometryVersion.intValue
            if (!geometry.isReady || currentDistance == null) return@Canvas

            val altitude = profile.altitudeAtDistance(currentDistance) ?: return@Canvas
            val x = geometry.xFor(currentDistance)
            val y = geometry.yFor(altitude)

            drawLine(
                color = markerColor.copy(alpha = 0.7f),
                start = Offset(x, geometry.top),
                end = Offset(x, geometry.bottom),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawCircle(color = markerColor, radius = 6.dp.toPx(), center = Offset(x, y))
            drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(x, y))
        }
    }
}

/** Fixed room, in dp, kept free at both ends of the plot. */
private val LAYER_PADDING = CartesianLayerPadding(
    scalableStartDp = 0f,
    scalableEndDp = 0f,
    unscalableStartDp = 4f,
    unscalableEndDp = 24f,
)

/**
 * The plot area that Vico used in its last draw, expressed in the coordinates of the
 * chart view, together with the data ranges that map onto it.
 */
private class PlotGeometry {
    var left = 0f
    var right = 0f
    var top = 0f
    var bottom = 0f
    var minX = 0.0
    var xLength = 0.0
    var minY = 0.0
    var maxY = 0.0

    val isReady: Boolean
        get() = right > left && bottom > top && xLength > 0.0 && maxY > minY

    /** Stores a new geometry and tells whether it differs from the previous one. */
    fun update(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        minX: Double,
        xLength: Double,
        minY: Double,
        maxY: Double,
    ): Boolean {
        val changed = this.left != left || this.top != top || this.right != right ||
            this.bottom != bottom || this.minX != minX || this.xLength != xLength ||
            this.minY != minY || this.maxY != maxY
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
        this.minX = minX
        this.xLength = xLength
        this.minY = minY
        this.maxY = maxY
        return changed
    }

    fun xFor(distance: Double): Float {
        val ratio = ((distance - minX) / xLength).coerceIn(0.0, 1.0)
        return left + ratio.toFloat() * (right - left)
    }

    fun yFor(altitude: Double): Float {
        val ratio = ((altitude - minY) / (maxY - minY)).coerceIn(0.0, 1.0)
        return bottom - ratio.toFloat() * (bottom - top)
    }

    /** The distance along the track under a horizontal touch position, if the plot is known. */
    fun distanceAt(touchX: Float, totalDistance: Double): Double? {
        if (!isReady) return null
        val ratio = ((touchX - left) / (right - left)).toDouble().coerceIn(0.0, 1.0)
        return (minX + ratio * xLength).coerceIn(0.0, totalDistance)
    }
}

/** Reports the plot area of the chart after every draw. */
private class GeometryDecoration(
    private val geometry: PlotGeometry,
    private val onChanged: () -> Unit,
) : Decoration {
    override fun drawUnderLayers(context: CartesianDrawingContext) = Unit

    override fun drawOverLayers(context: CartesianDrawingContext) {
        val bounds = context.layerBounds
        val dimensions = context.layerDimensions
        val y = context.ranges.getYRange(Axis.Position.Vertical.Start)
        val changed = geometry.update(
            left = bounds.left + dimensions.unscalableStartPadding,
            top = bounds.top,
            right = bounds.right - dimensions.unscalableEndPadding,
            bottom = bounds.bottom,
            minX = context.ranges.minX,
            xLength = context.ranges.xLength,
            minY = y.minY,
            maxY = y.maxY,
        )
        if (changed) onChanged()
    }
}

/**
 * The vertical range of the chart: the altitudes of the track plus some breathing room,
 * rounded to a multiple of 5 or 10 m. Without it Vico would start the axis at 0 m and
 * a trip on a plateau would look like a flat line.
 */
internal fun altitudeAxisRange(minAltitude: Double, maxAltitude: Double): Pair<Double, Double> {
    val span = max(maxAltitude - minAltitude, 0.0)
    val padding = max(span * 0.1, 5.0)
    val step = if (span + 2 * padding >= 40.0) 10.0 else 5.0
    val lower = floor((minAltitude - padding) / step) * step
    var upper = ceil((maxAltitude + padding) / step) * step
    if (upper - lower < 2 * step) upper = lower + 2 * step
    return lower to upper
}

/**
 * A round step (1, 2 or 5 × 10ⁿ metres, at least 1 m) that splits [width] into roughly
 * five intervals.
 */
internal fun profileXStep(width: Double): Double {
    if (!(width > 5.0)) return 1.0
    val raw = width / 5.0
    val magnitude = Math.pow(10.0, floor(kotlin.math.log10(raw)))
    val step = when {
        raw / magnitude < 2.0 -> magnitude
        raw / magnitude < 5.0 -> 2.0 * magnitude
        else -> 5.0 * magnitude
    }
    return step.coerceAtLeast(1.0)
}
