package com.mikotrack.maps

import android.graphics.Color
import com.mikotrack.data.repository.DefaultCategory
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale

internal fun waypointIconSizeExpression(): Expression = Expression.match(
    Expression.string(Expression.get("category")),
    Expression.literal(0.7f),
    Expression.stop(DefaultCategory.PARKING.key, 1.15f),
)

/**
 * Draws the recorded route, the overlaid historical routes, the waypoints and the current
 * position onto a MapLibre style.
 *
 * Every route gets its own source and layer, which guarantees that a previous trip can
 * never interfere with the route that is currently being recorded.
 */
class MapOverlayManager {

    private val appliedRoutes = mutableSetOf<Long>()

    /** The routes whose dashes are animated ("marching ants"). */
    private val animatedRoutes = LinkedHashSet<Long>()

    /** The step of [ANT_DASH_SEQUENCE] that is currently drawn. */
    private var dashStep = 0

    /** The label image registered in the current style, by trip. */
    private val labelImages = HashMap<Long, String>()

    /** The anchor of every label, cached with the points it was computed from. */
    private val labelAnchors = HashMap<Long, Pair<List<GeoPoint>, GeoPoint?>>()

    /** The labels that are on the map, to skip updating the source when nothing changed. */
    private var lastLabelGeoJson: String? = null

    /** Whether any route is drawn with animated dashes. */
    val hasAnimatedRoutes: Boolean get() = animatedRoutes.isNotEmpty()

    /**
     * Forgets the images registered in the previous style: a new style starts without
     * images, so the labels are registered again on the next render.
     */
    fun onStyleChanged() {
        labelImages.clear()
        lastLabelGeoJson = null
        animatedRoutes.clear()
    }

    /** Removes every overlay from the style, used when the style is rebuilt. */
    fun clear(style: Style) {
        appliedRoutes.toList().forEach { removeRoute(style, it) }
        removeIfPresent(style, WAYPOINT_SOURCE)
        removeIfPresent(style, WAYPOINT_LAYER)
        removeIfPresent(style, PHOTO_BUBBLE_LAYER)
        removeIfPresent(style, PHOTO_BUBBLE_SOURCE)
        removeIfPresent(style, ROUTE_LABEL_LAYER)
        removeIfPresent(style, ROUTE_LABEL_SOURCE)
        labelImages.values.forEach { runCatching { style.removeImage(it) } }
        labelImages.clear()
        lastLabelGeoJson = null
        removeIfPresent(style, MARKER_LAYER)
        removeIfPresent(style, MARKER_SOURCE)
        removeIfPresent(style, POSITION_SOURCE)
        removeIfPresent(style, POSITION_HALO_LAYER)
        removeIfPresent(style, POSITION_LAYER)
    }

    /**
     * Draws exactly the given routes: the ones that are no longer listed (for instance a
     * previous trip the user has just hidden) are removed from the map.
     */
    fun syncRoutes(style: Style, overlays: List<RouteOverlay>) {
        val wanted = overlays.mapTo(HashSet()) { it.tripId }
        appliedRoutes.filterNot { it in wanted }.forEach { removeRoute(style, it) }
        overlays.forEach { applyRoute(style, it) }
    }

    /**
     * Draws (or updates) a route with its own colour. The overlaid previous trips get a
     * dark casing underneath and animated dashes on top, for contrast on any base map.
     */
    fun applyRoute(style: Style, overlay: RouteOverlay) {
        if (overlay.points.size < 2) {
            removeRoute(style, overlay.tripId)
            return
        }

        val sourceId = routeSourceId(overlay.tripId)
        val layerId = routeLayerId(overlay.tripId)
        val geoJson = buildLineString(overlay.points)
        val properties = routeProperties(overlay)

        val source = style.getSourceAs<GeoJsonSource>(sourceId)
        if (source == null) {
            style.addSource(GeoJsonSource(sourceId, geoJson))
        } else {
            source.setGeoJson(geoJson)
        }

        val line = style.getLayerAs<LineLayer>(layerId)
        if (line == null) {
            val layer = LineLayer(layerId, sourceId).withProperties(*properties)
            // The routes always stay below their labels, the markers and the position of
            // the user, and the previous trips below the route that is being recorded,
            // no matter in which order they are switched on.
            val below = listOf(
                routeLayerId(CURRENT_ROUTE_ID).takeIf { overlay.tripId != CURRENT_ROUTE_ID },
                ROUTE_LABEL_LAYER,
                WAYPOINT_LAYER,
                PHOTO_BUBBLE_LAYER,
                MARKER_LAYER,
                POSITION_HALO_LAYER,
            ).firstOrNull { it != null && style.getLayer(it) != null }
            if (below != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
        } else {
            // The colour of an overlay can change (chosen in the history, or by its
            // position in the list when another trip is hidden).
            line.setProperties(*properties)
        }
        applyDecorations(style, overlay, sourceId, layerId)
        appliedRoutes += overlay.tripId
    }

    /** Adds, updates or removes the casing and the animated dashes of a route. */
    private fun applyDecorations(style: Style, overlay: RouteOverlay, sourceId: String, layerId: String) {
        val casingId = routeCasingLayerId(overlay.tripId)
        val antsId = routeAntsLayerId(overlay.tripId)
        if (!overlay.dashed) {
            removeIfPresent(style, casingId)
            removeIfPresent(style, antsId)
            animatedRoutes -= overlay.tripId
            return
        }
        val casingProperties = arrayOf<PropertyValue<*>>(
            PropertyFactory.lineColor(CASING_COLOR),
            PropertyFactory.lineWidth(CASING_WIDTH),
            PropertyFactory.lineOpacity(CASING_OPACITY),
            PropertyFactory.lineCap("round"),
            PropertyFactory.lineJoin("round"),
        )
        val casing = style.getLayerAs<LineLayer>(casingId)
        if (casing == null) {
            style.addLayerBelow(LineLayer(casingId, sourceId).withProperties(*casingProperties), layerId)
        } else {
            casing.setProperties(*casingProperties)
        }
        if (style.getLayer(antsId) == null) {
            style.addLayerAbove(
                LineLayer(antsId, sourceId).withProperties(
                    PropertyFactory.lineColor(Color.WHITE),
                    PropertyFactory.lineWidth(ANTS_WIDTH),
                    PropertyFactory.lineOpacity(ANTS_OPACITY),
                    PropertyFactory.lineCap("butt"),
                    PropertyFactory.lineJoin("round"),
                    PropertyFactory.lineDasharray(ANT_DASH_SEQUENCE[dashStep]),
                ),
                layerId,
            )
        }
        animatedRoutes += overlay.tripId
    }

    /**
     * Moves the animated dashes one step forward. It is called by a ticker on the main
     * thread while the map is visible.
     */
    fun stepAnimation(style: Style) {
        if (animatedRoutes.isEmpty()) return
        dashStep = (dashStep + 1) % ANT_DASH_SEQUENCE.size
        val dashes = PropertyFactory.lineDasharray(ANT_DASH_SEQUENCE[dashStep])
        animatedRoutes.forEach { tripId ->
            runCatching { style.getLayerAs<LineLayer>(routeAntsLayerId(tripId))?.setProperties(dashes) }
        }
    }

    private fun routeProperties(overlay: RouteOverlay): Array<PropertyValue<*>> = buildList {
        add(PropertyFactory.lineColor(overlay.color))
        add(PropertyFactory.lineWidth(if (overlay.dashed) 4.5f else 5f))
        add(PropertyFactory.lineOpacity(0.95f))
        add(PropertyFactory.lineCap("round"))
        add(PropertyFactory.lineJoin("round"))
    }.toTypedArray()

    /** Removes a single route from the map. */
    fun removeRoute(style: Style, tripId: Long) {
        removeIfPresent(style, routeAntsLayerId(tripId))
        removeIfPresent(style, routeCasingLayerId(tripId))
        removeIfPresent(style, routeLayerId(tripId))
        removeIfPresent(style, routeSourceId(tripId))
        appliedRoutes -= tripId
        animatedRoutes -= tripId
    }

    /**
     * Draws the names of the routes that ask for a label, at the point that lies at half
     * of their length. The labels are bitmaps, so they keep the same size on the screen
     * at every zoom level and need no glyphs.
     */
    fun applyRouteLabels(style: Style, overlays: List<RouteOverlay>, density: Float) {
        val labeled = overlays.filter { it.showLabel && it.points.size >= 2 }
        val wanted = labeled.mapTo(HashSet()) { it.tripId }
        labelImages.keys.filterNot { it in wanted }.forEach { tripId ->
            labelImages.remove(tripId)?.let { runCatching { style.removeImage(it) } }
        }
        labelAnchors.keys.retainAll(wanted)

        val features = labeled.mapNotNull { overlay ->
            val cached = labelAnchors[overlay.tripId]
            val anchor = if (cached != null && cached.first === overlay.points) {
                cached.second
            } else {
                RouteGeometry.pointAtHalfLength(overlay.points).also {
                    labelAnchors[overlay.tripId] = overlay.points to it
                }
            } ?: return@mapNotNull null
            val imageId = RouteLabelFactory.imageId(overlay.tripId, overlay.name, overlay.color)
            if (labelImages[overlay.tripId] != imageId) {
                labelImages[overlay.tripId]?.let { runCatching { style.removeImage(it) } }
                val registered = runCatching {
                    style.addImage(imageId, RouteLabelFactory.create(overlay.name, overlay.color, density))
                }.isSuccess
                if (!registered) return@mapNotNull null
                labelImages[overlay.tripId] = imageId
            }
            imageId to anchor
        }

        val geoJson = buildString {
            append("{\"type\":\"FeatureCollection\",\"features\":[")
            features.forEachIndexed { index, (imageId, anchor) ->
                if (index > 0) append(',')
                append("{\"type\":\"Feature\",\"properties\":{\"label\":\"").append(escape(imageId)).append('"')
                append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":")
                append(formatCoordinate(anchor))
                append("}}")
            }
            append("]}")
        }

        val source = style.getSourceAs<GeoJsonSource>(ROUTE_LABEL_SOURCE)
        if (source == null) {
            style.addSource(GeoJsonSource(ROUTE_LABEL_SOURCE, geoJson))
            val layer = SymbolLayer(ROUTE_LABEL_LAYER, ROUTE_LABEL_SOURCE).withProperties(
                PropertyFactory.iconImage(Expression.get("label")),
                // The tip of the pointer sits on the line.
                PropertyFactory.iconAnchor("bottom"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
            // Above every route line, below the markers and the position of the user.
            val below = listOf(WAYPOINT_LAYER, PHOTO_BUBBLE_LAYER, MARKER_LAYER, POSITION_HALO_LAYER)
                .firstOrNull { style.getLayer(it) != null }
            if (below != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
            lastLabelGeoJson = geoJson
        } else if (geoJson != lastLabelGeoJson) {
            source.setGeoJson(geoJson)
            lastLabelGeoJson = geoJson
        }
    }

    /** Draws the temporary marker of a point the user long-pressed, or hides it. */
    fun applyMarker(style: Style, point: GeoPoint?) {
        val geoJson = if (point == null) EMPTY_COLLECTION else buildPoint(emptyMap(), point)
        val source = style.getSourceAs<GeoJsonSource>(MARKER_SOURCE)
        if (source == null) {
            style.addSource(GeoJsonSource(MARKER_SOURCE, geoJson))
            val layer = CircleLayer(MARKER_LAYER, MARKER_SOURCE).withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor(MARKER_COLOR),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor(Color.WHITE),
            )
            if (style.getLayer(POSITION_HALO_LAYER) != null) {
                style.addLayerBelow(layer, POSITION_HALO_LAYER)
            } else {
                style.addLayer(layer)
            }
        } else {
            source.setGeoJson(geoJson)
        }
    }

    /** Draws the waypoints of the current trip and of the overlaid trips. */
    fun applyWaypoints(style: Style, waypoints: List<WaypointOverlay>, iconResolver: (String) -> String) {
        val geoJson = buildWaypointCollection(waypoints, iconResolver)

        val source = style.getSourceAs<GeoJsonSource>(WAYPOINT_SOURCE)
        if (source == null) {
            style.addSource(GeoJsonSource(WAYPOINT_SOURCE, geoJson))
            style.addLayer(
                SymbolLayer(WAYPOINT_LAYER, WAYPOINT_SOURCE).withProperties(
                    PropertyFactory.iconImage(Expression.get("icon")),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconSize(waypointIconSizeExpression()),
                ),
            )
        } else {
            source.setGeoJson(geoJson)
        }
    }

    /**
     * Draws the photograph bubbles. Only waypoints whose bubble image is already in the
     * style are passed in; the bubbles sit above the waypoint markers.
     */
    fun applyPhotoBubbles(style: Style, bubbles: List<WaypointOverlay>) {
        val geoJson = buildString {
            append("{\"type\":\"FeatureCollection\",\"features\":[")
            bubbles.forEachIndexed { index, waypoint ->
                if (index > 0) append(',')
                append("{\"type\":\"Feature\",\"properties\":{")
                append("\"id\":").append(waypoint.waypointId)
                append(",\"bubble\":\"").append(PhotoBubbleFactory.imageId(waypoint.waypointId)).append('"')
                append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":")
                append(formatCoordinate(waypoint.position))
                append("}}")
            }
            append("]}")
        }

        val source = style.getSourceAs<GeoJsonSource>(PHOTO_BUBBLE_SOURCE)
        if (source == null) {
            style.addSource(GeoJsonSource(PHOTO_BUBBLE_SOURCE, geoJson))
            val layer = SymbolLayer(PHOTO_BUBBLE_LAYER, PHOTO_BUBBLE_SOURCE).withProperties(
                PropertyFactory.iconImage(Expression.get("bubble")),
                // The tip of the pointer marks the place where the photograph was taken.
                PropertyFactory.iconAnchor("bottom"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
            // The bubbles stay below the position of the user, which must remain visible.
            if (style.getLayer(POSITION_HALO_LAYER) != null) {
                style.addLayerBelow(layer, POSITION_HALO_LAYER)
            } else {
                style.addLayer(layer)
            }
        } else {
            source.setGeoJson(geoJson)
        }
    }

    /** Draws the current position and the direction of movement. */
    fun applyPosition(style: Style, position: GeoPoint?, bearing: Float?) {
        val geoJson = if (position == null) {
            EMPTY_COLLECTION
        } else {
            buildPoint(
                properties = mapOf(
                    "bearing" to (bearing ?: 0f),
                ),
                position = position,
            )
        }

        val source = style.getSourceAs<GeoJsonSource>(POSITION_SOURCE)
        if (source == null) {
            style.addSource(GeoJsonSource(POSITION_SOURCE, geoJson))
            style.addLayer(
                CircleLayer(POSITION_HALO_LAYER, POSITION_SOURCE).withProperties(
                    PropertyFactory.circleRadius(16f),
                    PropertyFactory.circleColor(Color.argb(48, 255, 255, 255)),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                ),
            )
            style.addLayer(
                SymbolLayer(POSITION_LAYER, POSITION_SOURCE).withProperties(
                    PropertyFactory.iconImage(MapIconFactory.BEARING_ICON),
                    PropertyFactory.iconRotate(Expression.get("bearing")),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconSize(0.8f),
                    PropertyFactory.iconRotationAlignment("map"),
                ),
            )
        } else {
            source.setGeoJson(geoJson)
        }
    }

    private fun removeIfPresent(style: Style, id: String) {
        runCatching {
            if (style.getLayer(id) != null) style.removeLayer(id)
        }
        runCatching {
            if (style.getSource(id) != null) style.removeSource(id)
        }
    }

    private fun buildLineString(points: List<GeoPoint>): String = buildString {
        append("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},")
        append("\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
        points.forEachIndexed { index, point ->
            if (index > 0) append(',')
            append(formatCoordinate(point))
        }
        append("]}}]}")
    }

    private fun buildWaypointCollection(
        waypoints: List<WaypointOverlay>,
        iconResolver: (String) -> String,
    ): String = buildString {
        append("{\"type\":\"FeatureCollection\",\"features\":[")
        waypoints.forEachIndexed { index, waypoint ->
            if (index > 0) append(',')
            append("{\"type\":\"Feature\",\"properties\":{")
            append("\"id\":").append(waypoint.waypointId)
            append(",\"tripId\":").append(waypoint.tripId)
            append(",\"category\":\"").append(escape(waypoint.category)).append('"')
            append(",\"current\":").append(waypoint.isCurrentTrip)
            append(",\"icon\":\"").append(escape(iconResolver(waypoint.category))).append('"')
            append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":")
            append(formatCoordinate(waypoint.position))
            append("}}")
        }
        append("]}")
    }

    private fun buildPoint(properties: Map<String, Any>, position: GeoPoint): String = buildString {
        append("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{")
        properties.entries.forEachIndexed { index, entry ->
            if (index > 0) append(',')
            val (key, value) = entry
            append('"').append(key).append("\":")
            when (value) {
                is String -> append('"').append(escape(value)).append('"')
                is Float -> append(String.format(Locale.US, "%.1f", value))
                else -> append(value.toString())
            }
        }
        append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":")
        append(formatCoordinate(position))
        append("}}]}")
    }

    private fun formatCoordinate(point: GeoPoint): String =
        String.format(Locale.US, "[%.7f,%.7f]", point.longitude, point.latitude)

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        const val WAYPOINT_SOURCE = "mycotrack-waypoints"
        const val WAYPOINT_LAYER = "mycotrack-waypoints-layer"
        const val PHOTO_BUBBLE_SOURCE = "mycotrack-photo-bubbles"
        const val PHOTO_BUBBLE_LAYER = "mycotrack-photo-bubbles-layer"
        const val POSITION_SOURCE = "mycotrack-position-source"
        const val POSITION_LAYER = "mycotrack-position-layer"
        const val POSITION_HALO_LAYER = "mycotrack-position-halo-layer"

        const val ROUTE_LABEL_SOURCE = "mycotrack-route-labels"
        const val ROUTE_LABEL_LAYER = "mycotrack-route-labels-layer"
        const val MARKER_SOURCE = "mycotrack-pressed-point"
        const val MARKER_LAYER = "mycotrack-pressed-point-layer"

        private const val EMPTY_COLLECTION = "{\"type\":\"FeatureCollection\",\"features\":[]}"

        private val CASING_COLOR = Color.argb(255, 25, 25, 25)
        private const val CASING_WIDTH = 8.5f
        private const val CASING_OPACITY = 0.8f
        private const val ANTS_WIDTH = 2.5f
        private const val ANTS_OPACITY = 0.9f
        private val MARKER_COLOR = Color.rgb(229, 57, 53)

        /**
         * The dash patterns that, drawn one after the other, make the dashes march along
         * the line (in units of the line width).
         */
        private val ANT_DASH_SEQUENCE: List<Array<Float>> = listOf(
            arrayOf(0f, 4f, 3f),
            arrayOf(0.5f, 4f, 2.5f),
            arrayOf(1f, 4f, 2f),
            arrayOf(1.5f, 4f, 1.5f),
            arrayOf(2f, 4f, 1f),
            arrayOf(2.5f, 4f, 0.5f),
            arrayOf(3f, 4f, 0f),
            arrayOf(0f, 0.5f, 3f, 3.5f),
            arrayOf(0f, 1f, 3f, 3f),
            arrayOf(0f, 1.5f, 3f, 2.5f),
            arrayOf(0f, 2f, 3f, 2f),
            arrayOf(0f, 2.5f, 3f, 1.5f),
            arrayOf(0f, 3f, 3f, 1f),
            arrayOf(0f, 3.5f, 3f, 0.5f),
        )

        /** The identifier of the route that is being recorded. */
        const val CURRENT_ROUTE_ID = -1L

        fun routeSourceId(tripId: Long): String = "mycotrack-route-$tripId"

        fun routeLayerId(tripId: Long): String = "mycotrack-route-layer-$tripId"

        fun routeCasingLayerId(tripId: Long): String = "mycotrack-route-casing-$tripId"

        fun routeAntsLayerId(tripId: Long): String = "mycotrack-route-ants-$tripId"
    }
}
