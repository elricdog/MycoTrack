package com.mikotrack.maps

import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/**
 * Adds and removes the habitat raster overlays (soil, tree species and slope orientation).
 *
 * They are drawn right above the base map and below every overlay of the application
 * (routes, waypoints, photograph bubbles and the position), whose layers all start
 * with [OVERLAY_PREFIX]. From bottom to top: the soil, the tree species, the
 * orientation, whose sparse textures let the other two show through, and the likely
 * areas of a species.
 *
 * The URL of a source changes with the classes the user selects (and with the port of
 * the local server); a source with a new URL is replaced, so MapLibre loads its tiles
 * again.
 */
class HabitatLayerManager {

    /** The URL template every habitat source of the current style was created with. */
    private val urlsInStyle = HashMap<String, String>()

    /** Brings the style in line with the requested habitat layers. */
    fun apply(style: Style, layers: HabitatLayers) {
        if (!style.isFullyLoaded) return
        sync(style, SOIL, layers.soilTileUrl)
        sync(style, TREES, layers.treesTileUrl)
        sync(style, ASPECT, layers.aspectTileUrl)
        sync(style, SUITABILITY, layers.suitabilityTileUrl)
    }

    private fun sync(style: Style, overlay: Overlay, url: String?) {
        if (url == null) {
            remove(style, overlay)
            return
        }
        if (style.getSource(overlay.source) != null && urlsInStyle[overlay.source] != url) remove(style, overlay)
        if (style.getLayer(overlay.layer) != null) return
        if (style.getSource(overlay.source) == null) {
            style.addSource(
                RasterSource(
                    overlay.source,
                    TileSet("2.2.0", url).apply {
                        minZoom = overlay.minZoom.toFloat()
                        maxZoom = overlay.maxZoom.toFloat()
                        scheme = "xyz"
                        attribution = overlay.attribution
                    },
                    overlay.tileSize,
                ),
            )
            urlsInStyle[overlay.source] = url
        }
        val layer = RasterLayer(overlay.layer, overlay.source)
        overlay.opacity?.let { layer.setProperties(PropertyFactory.rasterOpacity(it)) }
        insert(style, layer, below(style, overlay))
    }

    /** The layer the overlay goes right below, keeping the order of [ORDER]. */
    private fun below(style: Style, overlay: Overlay): String? {
        val above = ORDER.drop(ORDER.indexOf(overlay) + 1)
        return above.firstOrNull { style.getLayer(it.layer) != null }?.layer ?: firstOverlayLayer(style)
    }

    /** The lowest layer that belongs to the overlays of the application, if any. */
    private fun firstOverlayLayer(style: Style): String? = style.layers
        .map { it.id }
        .firstOrNull { id -> id.startsWith(OVERLAY_PREFIX) && ORDER.none { it.layer == id } }

    private fun insert(style: Style, layer: Layer, below: String?) {
        if (below != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
    }

    private fun remove(style: Style, overlay: Overlay) {
        runCatching { if (style.getLayer(overlay.layer) != null) style.removeLayer(overlay.layer) }
        runCatching { if (style.getSource(overlay.source) != null) style.removeSource(overlay.source) }
        urlsInStyle.remove(overlay.source)
    }

    private class Overlay(
        val source: String,
        val layer: String,
        val minZoom: Int,
        val maxZoom: Int,
        val tileSize: Int,
        val attribution: String,
        val opacity: Float?,
    )

    private companion object {
        const val OVERLAY_PREFIX = "mycotrack-"

        val SOIL = Overlay(
            HabitatSources.SOIL_SOURCE, HabitatSources.SOIL_LAYER,
            SoilTileProvider.MIN_ZOOM, SoilTileProvider.MAX_ZOOM, 256,
            HabitatSources.SOIL_ATTRIBUTION, HabitatSources.SOIL_OPACITY,
        )
        val TREES = Overlay(
            HabitatSources.TREES_SOURCE, HabitatSources.TREES_LAYER,
            HabitatSources.TREES_MIN_ZOOM, HabitatSources.TREES_MAX_ZOOM, HabitatSources.TREES_TILE_SIZE,
            HabitatSources.TREES_ATTRIBUTION, HabitatSources.TREES_OPACITY,
        )

        // Beyond the highest zoom level MapLibre scales up the tiles of that level.
        val ASPECT = Overlay(
            HabitatSources.ASPECT_SOURCE, HabitatSources.ASPECT_LAYER,
            AspectTileProvider.MIN_ZOOM, AspectTileProvider.MAX_ZOOM, 256,
            HabitatSources.ASPECT_ATTRIBUTION, null,
        )

        // The likely areas go on top: their colours must stay readable over the others.
        val SUITABILITY = Overlay(
            HabitatSources.SUITABILITY_SOURCE, HabitatSources.SUITABILITY_LAYER,
            SuitabilityTileProvider.MIN_ZOOM, SuitabilityTileProvider.MAX_ZOOM, 256,
            HabitatSources.SUITABILITY_ATTRIBUTION, null,
        )

        /** The habitat overlays from bottom to top. */
        val ORDER = listOf(SOIL, TREES, ASPECT, SUITABILITY)
    }
}
