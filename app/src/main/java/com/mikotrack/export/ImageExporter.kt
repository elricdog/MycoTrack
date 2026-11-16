package com.mikotrack.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.core.os.ConfigurationCompat
import com.mikotrack.R
import com.mikotrack.domain.model.ElevationProfile
import com.mikotrack.domain.model.Trip
import com.mikotrack.domain.model.TripStats
import com.mikotrack.domain.model.Waypoint
import com.mikotrack.maps.MapLayer
import com.mikotrack.maps.MapStyleFactory
import com.mikotrack.maps.OfflineSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan
import kotlin.math.PI

/**
 * Renders the visual exports of a trip: the map, the elevation profile and the complete
 * visual report.
 */
class ImageExporter(private val context: Context) {

    /** The mascot of the application, decoded once and reused by every report. */
    private var mascot: Bitmap? = null
    private var mascotDecoded = false

    /** Options that control how the map image is rendered. */
    data class MapOptions(
        val width: Int = 1600,
        val height: Int = 1200,
        val pixelRatio: Float = 1f,
        val routeColor: Int = DEFAULT_ROUTE_COLOR,
        val showWaypoints: Boolean = true,
        val offlineSource: OfflineSource? = null,
    )

    /**
     * Renders the map of a trip into a bitmap.
     *
     * The rendering is completely independent from the map shown on screen, which makes it
     * possible to export images with a much higher resolution than the display.
     */
    suspend fun renderMapImage(
        layer: MapLayer,
        points: List<com.mikotrack.domain.model.TrackPoint>,
        waypoints: List<Waypoint>,
        options: MapOptions = MapOptions(),
    ): Bitmap? {
        if (points.size < 2) return null
        // Every MapLibre object (sources, layers and the snapshotter) must be created
        // and used on the main thread, or the native side rejects it.
        return withContext(Dispatchers.Main) { snapshot(layer, points, waypoints, options) }
    }

    private suspend fun snapshot(
        layer: MapLayer,
        points: List<com.mikotrack.domain.model.TrackPoint>,
        waypoints: List<Waypoint>,
        options: MapOptions,
    ): Bitmap? {
        val styleJson = buildExportStyle(layer, points, waypoints, options)

        val bounds = boundsOf(points)
        val optionsBuilder = MapSnapshotter.Options(options.width, options.height)
            .withStyleJson(styleJson)
            .withPixelRatio(options.pixelRatio)
            .withLogo(false)
            .withAttribution(true)

        // The zoom level that frames the whole route is limited to the one the source
        // really contains: an offline area only holds the tiles it was downloaded
        // with, and stretching them beyond that level would blur the picture.
        val maxZoom = if (layer == MapLayer.OFFLINE) {
            options.offlineSource?.maxZoom ?: layer.maxZoom
        } else {
            layer.maxZoom
        }
        val framingZoom = zoomForBounds(bounds, options.width, options.height)
        if (framingZoom > maxZoom) {
            optionsBuilder.withCameraPosition(
                CameraPosition.Builder()
                    .target(bounds.center)
                    .zoom(maxZoom.toDouble())
                    .build(),
            )
        } else {
            optionsBuilder.withRegion(bounds)
        }

        val snapshotter = MapSnapshotter(context, optionsBuilder)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { snapshotter.cancel() }
            snapshotter.start(
                { snapshot: MapSnapshot ->
                    if (continuation.isActive) continuation.resume(snapshot.bitmap)
                },
                { error: String ->
                    if (continuation.isActive) {
                        continuation.resumeWith(
                            Result.failure<Bitmap?>(IllegalStateException(context.getString(R.string.export_msg_map_render_error, error))),
                        )
                    }
                },
            )
        }
    }

    /**
     * The zoom level that frames the given bounds in a picture of the given size.
     *
     * The world is 256 pixels wide at the zoom level zero, and the latitude is
     * measured in the Mercator projection, which is the one the tiles use.
     */
    private fun zoomForBounds(bounds: LatLngBounds, width: Int, height: Int): Double {
        val longitudeSpan = bounds.longitudeSpan.coerceAtLeast(0.000001)
        val mercatorSpan = (mercatorY(bounds.getLatNorth()) - mercatorY(bounds.getLatSouth()))
            .coerceAtLeast(0.000001)
        val zoomX = log2(width * 360.0 / (256.0 * longitudeSpan))
        val zoomY = log2(height * 2.0 * PI / (256.0 * mercatorSpan))
        return min(zoomX, zoomY)
    }

    /** The vertical coordinate of a latitude in the Mercator projection. */
    private fun mercatorY(latitude: Double): Double =
        ln(tan(PI / 4.0 + Math.toRadians(latitude) / 2.0))

    /** Builds the style document used for the map exports. */
    private fun buildExportStyle(
        layer: MapLayer,
        points: List<com.mikotrack.domain.model.TrackPoint>,
        waypoints: List<Waypoint>,
        options: MapOptions,
    ): String {
        val sources = linkedMapOf(
            EXPORT_ROUTE_SOURCE to JSONObject()
                .put("type", "geojson")
                .put("data", JSONObject(buildLineString(points))),
        )
        val layers = mutableListOf(
            JSONObject()
                .put("id", EXPORT_ROUTE_LAYER)
                .put("type", "line")
                .put("source", EXPORT_ROUTE_SOURCE)
                .put("layout", JSONObject().put("line-cap", "round").put("line-join", "round"))
                .put(
                    "paint",
                    JSONObject()
                        .put("line-color", MapStyleFactory.cssColor(options.routeColor))
                        .put("line-width", 5),
                ),
        )

        if (options.showWaypoints && waypoints.isNotEmpty()) {
            sources[EXPORT_WAYPOINT_SOURCE] = JSONObject()
                .put("type", "geojson")
                .put("data", JSONObject(buildWaypoints(waypoints)))
            layers += JSONObject()
                .put("id", EXPORT_WAYPOINT_LAYER)
                .put("type", "circle")
                .put("source", EXPORT_WAYPOINT_SOURCE)
                .put(
                    "paint",
                    JSONObject()
                        .put("circle-radius", 7)
                        .put("circle-color", MapStyleFactory.cssColor(Color.WHITE))
                        .put("circle-stroke-width", 3)
                        .put("circle-stroke-color", MapStyleFactory.cssColor(Color.rgb(47, 122, 58))),
                )
        }

        return MapStyleFactory.buildJson(
            layer = layer,
            offlineUrl = options.offlineSource?.url,
            offlineTileSize = options.offlineSource?.tileSize ?: 256,
            offlineMinZoom = options.offlineSource?.minZoom ?: 0,
            offlineMaxZoom = options.offlineSource?.maxZoom ?: 22,
            extraSources = sources,
            extraLayers = layers,
        )
    }

    /** Renders the elevation profile of a trip into a bitmap. */
    fun renderElevationImage(
        profile: ElevationProfile,
        width: Int = 1600,
        height: Int = 700,
        background: Int = Color.rgb(28, 32, 28),
        lineColor: Int = DEFAULT_ROUTE_COLOR,
        showAxes: Boolean = true,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val padding = 48f
        val graphHeight = height - padding * 2
        val graphWidth = width - padding * 2

        if (profile.points.isEmpty()) {
            paint.color = Color.LTGRAY
            paint.textSize = 28f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.report_no_elevation_data), width / 2f, height / 2f, paint)
            return bitmap
        }

        val minAltitude = profile.minAltitude
        val maxAltitude = profile.maxAltitude
        val altitudeRange = max(maxAltitude - minAltitude, 1.0)
        val totalDistance = max(profile.totalDistance, 1.0)

        fun xFor(distance: Double) = padding + (distance / totalDistance).toFloat() * graphWidth
        fun yFor(altitude: Double) =
            padding + (1.0 - ((altitude - minAltitude) / altitudeRange)).toFloat() * graphHeight

        // Filled area under the curve.
        val fill = Path().apply {
            moveTo(xFor(0.0), yFor(profile.points.first().altitude))
            profile.points.forEach { lineTo(xFor(it.distanceMeters), yFor(it.altitude)) }
            lineTo(xFor(profile.points.last().distanceMeters), padding + graphHeight)
            lineTo(xFor(0.0), padding + graphHeight)
            close()
        }
        paint.color = Color.argb(70, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor))
        canvas.drawPath(fill, paint)

        // The curve itself.
        val line = Path().apply {
            moveTo(xFor(0.0), yFor(profile.points.first().altitude))
            profile.points.forEach { lineTo(xFor(it.distanceMeters), yFor(it.altitude)) }
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = lineColor
        canvas.drawPath(line, paint)
        paint.style = Paint.Style.FILL

        if (showAxes) {
            paint.color = Color.argb(160, 255, 255, 255)
            paint.textSize = 24f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(context.getString(R.string.report_min_value, minAltitude.toInt()), padding, padding - 12f, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(context.getString(R.string.report_max_value, maxAltitude.toInt()), width - padding, padding - 12f, paint)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                String.format(Locale.US, "%.2f km", totalDistance / 1000.0),
                width / 2f,
                height - 12f,
                paint,
            )
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(
                String.format(Locale.US, "+%.0f m / -%.0f m", profile.elevationGain, profile.elevationLoss),
                padding,
                height - 12f,
                paint,
            )
        }

        return bitmap
    }

    /** Renders the complete visual trip report: map, statistics and elevation profile. */
    fun renderTripReport(
        mapImage: Bitmap?,
        profile: ElevationProfile,
        trip: Trip,
        stats: TripStats,
        width: Int = 1600,
    ): Bitmap {
        val padding = 40
        val mapHeight = if (mapImage != null) (width * mapImage.height / mapImage.width.toFloat()).toInt() else 0
        val elevationHeight = 420
        // Two rows of statistics end at about 250 px, so the header keeps a margin below them.
        val headerHeight = 280
        val height = headerHeight + mapHeight + elevationHeight + padding * 3

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(28, 32, 28))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 46f
        // A long name is shortened so that it never runs under the mascot.
        val maxTitleWidth = (width - padding * 3 - 150).toFloat()
        var title = trip.name
        if (paint.measureText(title) > maxTitleWidth) {
            while (title.isNotEmpty() && paint.measureText("$title…") > maxTitleWidth) {
                title = title.dropLast(1)
            }
            title = "$title…"
        }
        canvas.drawText(title, padding.toFloat(), 60f, paint)

        // The mascot of the application is shown in the corner of the header.
        brandMascot()?.let { mascot ->
            val target = 150
            val scaled = Bitmap.createScaledBitmap(mascot, target, target, true)
            canvas.drawBitmap(
                scaled,
                (width - padding - target).toFloat(),
                20f,
                paint,
            )
            if (scaled !== mascot) scaled.recycle()
        }

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 28f
        paint.color = Color.LTGRAY
        canvas.drawText(
            java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, contextLocale())
                .format(java.util.Date(trip.startTime)),
            padding.toFloat(),
            100f,
            paint,
        )

        val entries = listOf(
            context.getString(R.string.report_distance) to String.format(Locale.US, "%.2f km", trip.distanceMeters / 1000.0),
            context.getString(R.string.report_duration) to formatDuration(trip.durationMillis),
            context.getString(R.string.report_elevation_gain) to String.format(Locale.US, "+%.0f m", trip.elevationGain),
            context.getString(R.string.report_elevation_loss) to String.format(Locale.US, "-%.0f m", trip.elevationLoss),
            context.getString(R.string.report_max_altitude) to String.format(Locale.US, "%.0f m", trip.maxAltitude ?: 0.0),
            context.getString(R.string.report_min_altitude) to String.format(Locale.US, "%.0f m", trip.minAltitude ?: 0.0),
            context.getString(R.string.report_average_speed) to String.format(Locale.US, "%.2f km/h", stats.averageSpeedMps * 3.6),
            context.getString(R.string.report_photos) to trip.photoCount.toString(),
        )

        var column = 0
        var row = 0
        entries.forEach { (label, value) ->
            val x = padding + column * (width / 4)
            val y = 150f + row * 66f
            paint.color = Color.LTGRAY
            paint.textSize = 24f
            canvas.drawText(label, x.toFloat(), y, paint)
            paint.color = Color.WHITE
            paint.textSize = 28f
            canvas.drawText(value, x.toFloat(), y + 28f, paint)
            column++
            if (column == 4) {
                column = 0
                row++
            }
        }

        var y = headerHeight
        if (mapImage != null) {
            canvas.drawBitmap(mapImage, null, android.graphics.Rect(padding, y, width - padding, y + mapHeight), paint)
            y += mapHeight + padding
        }

        val elevation = renderElevationImage(profile, width - padding * 2, elevationHeight)
        canvas.drawBitmap(elevation, padding.toFloat(), y.toFloat(), paint)
        elevation.recycle()

        return bitmap
    }

    /**
     * The mascot of the application, decoded from the packaged drawable.
     *
     * It is used as the header of the visual trip report. The decoding is done once
     * and cached, because the report may be exported several times.
     */
    private fun brandMascot(): Bitmap? {
        if (!mascotDecoded) {
            mascotDecoded = true
            mascot = runCatching {
                BitmapFactory.decodeResource(context.resources, R.drawable.brand_mascot)
            }.getOrNull()
        }
        return mascot
    }

    /** The locale of the configuration of the context, which follows the language of the app. */
    private fun contextLocale(): Locale =
        ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return if (hours > 0) {
            String.format(Locale.US, "%dh %02dm", hours, minutes)
        } else {
            String.format(Locale.US, "%d min", minutes)
        }
    }

    private fun boundsOf(points: List<com.mikotrack.domain.model.TrackPoint>): LatLngBounds {
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        points.forEach { point ->
            minLat = min(minLat, point.latitude)
            maxLat = max(maxLat, point.latitude)
            minLon = min(minLon, point.longitude)
            maxLon = max(maxLon, point.longitude)
        }
        // A small margin keeps the route away from the edge of the image.
        val latPadding = max((maxLat - minLat) * 0.15, 0.002)
        val lonPadding = max((maxLon - minLon) * 0.15, 0.002)
        // MapLibre expects the order north, east, south, west.
        return LatLngBounds.from(
            maxLat + latPadding,
            maxLon + lonPadding,
            minLat - latPadding,
            minLon - lonPadding,
        )
    }

    private fun buildLineString(points: List<com.mikotrack.domain.model.TrackPoint>): String = buildString {
        append("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},")
        append("\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
        points.forEachIndexed { index, point ->
            if (index > 0) append(',')
            append(String.format(Locale.US, "[%.7f,%.7f]", point.longitude, point.latitude))
        }
        append("]}}]}")
    }

    private fun buildWaypoints(waypoints: List<Waypoint>): String = buildString {
        append("{\"type\":\"FeatureCollection\",\"features\":[")
        waypoints.forEachIndexed { index, waypoint ->
            if (index > 0) append(',')
            append("{\"type\":\"Feature\",\"properties\":{\"id\":").append(waypoint.id)
            append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":")
            append(String.format(Locale.US, "[%.7f,%.7f]", waypoint.longitude, waypoint.latitude))
            append("}}")
        }
        append("]}")
    }

    companion object {
        /** Default colour of the exported route: forest green. */
        const val DEFAULT_ROUTE_COLOR = 0xFF2F7A3A.toInt()

        private const val EXPORT_ROUTE_SOURCE = "export-route"
        private const val EXPORT_ROUTE_LAYER = "export-route-layer"
        private const val EXPORT_WAYPOINT_SOURCE = "export-waypoints"
        private const val EXPORT_WAYPOINT_LAYER = "export-waypoints-layer"
    }
}
