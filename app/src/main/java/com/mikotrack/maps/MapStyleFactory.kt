package com.mikotrack.maps

import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.HillshadeLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterDemSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/**
 * Builds the MapLibre styles for the different base layers.
 *
 * The application never hardcodes a style URL: every layer is described here, which keeps
 * the map sources interchangeable and makes it trivial to add new ones.
 */
object MapStyleFactory {

    /** Identifier of the greyscale topographic layer used to complement the hillshade. */
    private const val ICGC_TOPO_GREY_URL =
        "https://geoserveis.icgc.cat/servei/catalunya/mapa-base/wmts/topografic-gris/MON3857NW/{z}/{x}/{y}.png"

    private const val ICGC_TOPO_GREY_SOURCE = "source-icgc-topo-grey"
    private const val ICGC_TOPO_GREY_LAYER = "layer-icgc-topo-grey"

    /** The highest zoom level published by the greyscale topographic layer of the ICGC. */
    private const val ICGC_TOPO_GREY_MAX_ZOOM = 18

    /**
     * The colour of the empty map that is shown while the offline area is not ready.
     *
     * It is the dark forest green of the application, so the transition between the
     * empty map and the tiles of the offline pack is not jarring.
     */
    private const val OFFLINE_BACKGROUND_COLOR = 0xFF1F2A20.toInt()

    /**
     * Builds a style for the given [layer].
     *
     * @param offlineUrl the URL template of the local tile server when the offline layer is used.
     * @param offlineTileSize the edge length in pixels of the tiles of the offline area.
     * @param offlineMinZoom the lowest zoom level that the offline area contains.
     * @param offlineMaxZoom the highest zoom level that the offline area contains.
     */
    fun build(
        layer: MapLayer,
        offlineUrl: String? = null,
        offlineTileSize: Int = 256,
        offlineMinZoom: Int = 0,
        offlineMaxZoom: Int = 22,
    ): Style.Builder {
        val builder = Style.Builder()

        return when (layer) {
            MapLayer.TERRAIN -> builder
                .withSource(
                    RasterDemSource(
                        layer.sourceId,
                        tileSet(layer.tileUrlTemplate!!, layer.minZoom, layer.maxZoom),
                        layer.tileSize,
                    ),
                )
                .withLayer(
                    HillshadeLayer(layer.layerId, layer.sourceId).withProperties(
                        PropertyFactory.hillshadeExaggeration(0.45f),
                        PropertyFactory.hillshadeShadowColor(Expression.color(0x33000000)),
                        PropertyFactory.hillshadeHighlightColor(Expression.color(0x66FFFFFF)),
                        PropertyFactory.hillshadeAccentColor(Expression.color(0x00000000)),
                    ),
                )
                .withSource(
                    RasterSource(
                        ICGC_TOPO_GREY_SOURCE,
                        tileSet(ICGC_TOPO_GREY_URL, 0, ICGC_TOPO_GREY_MAX_ZOOM),
                        512,
                    ),
                )
                .withLayer(
                    RasterLayer(ICGC_TOPO_GREY_LAYER, ICGC_TOPO_GREY_SOURCE).withProperties(
                        PropertyFactory.rasterOpacity(0.45f),
                    ),
                )

            MapLayer.OFFLINE -> {
                val url = offlineUrl
                if (url == null) {
                    // The offline layer must never fall back to an online source: the
                    // whole point of the offline map is that it works without a
                    // connection. While the local tile server is still starting up, the
                    // map is drawn with a plain background instead of silently showing
                    // the street map.
                    builder.withLayer(
                        BackgroundLayer(layer.layerId).withProperties(
                            PropertyFactory.backgroundColor(OFFLINE_BACKGROUND_COLOR),
                        ),
                    )
                } else {
                    // The zoom range of the area has to be published to the renderer:
                    // the offline packs only contain a limited range of zoom levels, and
                    // without it the renderer would ask for tiles that do not exist and
                    // the map would stay empty.
                    builder.withSource(
                        RasterSource(
                            layer.sourceId,
                            tileSet(url, offlineMinZoom, offlineMaxZoom),
                            offlineTileSize,
                        ),
                    ).withLayer(RasterLayer(layer.layerId, layer.sourceId))
                }
            }

            else -> builder
                .withSource(
                    RasterSource(
                        layer.sourceId,
                        tileSet(layer.tileUrlTemplate!!, layer.minZoom, layer.maxZoom),
                        layer.tileSize,
                    ),
                )
                .withLayer(RasterLayer(layer.layerId, layer.sourceId))
        }
    }

    /**
     * Builds the same style as [build], but as a complete style document.
     *
     * The map snapshotter used by the image exports cannot receive sources and layers
     * through a [Style.Builder] (MapLibre adds them before its native peer exists), so
     * the exports describe the whole style in JSON. [extraSources] and [extraLayers]
     * are drawn on top of the base layer.
     */
    fun buildJson(
        layer: MapLayer,
        offlineUrl: String? = null,
        offlineTileSize: Int = 256,
        offlineMinZoom: Int = 0,
        offlineMaxZoom: Int = 22,
        extraSources: Map<String, JSONObject> = emptyMap(),
        extraLayers: List<JSONObject> = emptyList(),
    ): String {
        val sources = JSONObject()
        val layers = JSONArray()

        fun rasterSource(url: String, minZoom: Int, maxZoom: Int, tileSize: Int, type: String = "raster") =
            JSONObject()
                .put("type", type)
                .put("tiles", JSONArray().put(url))
                .put("tileSize", tileSize)
                .put("minzoom", minZoom)
                .put("maxzoom", maxZoom)
                .put("scheme", "xyz")

        fun rasterLayer(id: String, source: String, opacity: Double? = null) = JSONObject()
            .put("id", id)
            .put("type", "raster")
            .put("source", source)
            .apply { if (opacity != null) put("paint", JSONObject().put("raster-opacity", opacity)) }

        when (layer) {
            MapLayer.TERRAIN -> {
                sources.put(
                    layer.sourceId,
                    rasterSource(layer.tileUrlTemplate!!, layer.minZoom, layer.maxZoom, layer.tileSize, "raster-dem")
                        .put("encoding", "terrarium"),
                )
                layers.put(
                    JSONObject()
                        .put("id", layer.layerId)
                        .put("type", "hillshade")
                        .put("source", layer.sourceId)
                        .put(
                            "paint",
                            JSONObject()
                                .put("hillshade-exaggeration", 0.45)
                                .put("hillshade-shadow-color", "rgba(0,0,0,0.2)")
                                .put("hillshade-highlight-color", "rgba(255,255,255,0.4)")
                                .put("hillshade-accent-color", "rgba(0,0,0,0)"),
                        ),
                )
                sources.put(ICGC_TOPO_GREY_SOURCE, rasterSource(ICGC_TOPO_GREY_URL, 0, ICGC_TOPO_GREY_MAX_ZOOM, 512))
                layers.put(rasterLayer(ICGC_TOPO_GREY_LAYER, ICGC_TOPO_GREY_SOURCE, 0.45))
            }

            MapLayer.OFFLINE -> {
                if (offlineUrl == null) {
                    layers.put(
                        JSONObject()
                            .put("id", layer.layerId)
                            .put("type", "background")
                            .put("paint", JSONObject().put("background-color", cssColor(OFFLINE_BACKGROUND_COLOR))),
                    )
                } else {
                    sources.put(layer.sourceId, rasterSource(offlineUrl, offlineMinZoom, offlineMaxZoom, offlineTileSize))
                    layers.put(rasterLayer(layer.layerId, layer.sourceId))
                }
            }

            else -> {
                sources.put(
                    layer.sourceId,
                    rasterSource(layer.tileUrlTemplate!!, layer.minZoom, layer.maxZoom, layer.tileSize),
                )
                layers.put(rasterLayer(layer.layerId, layer.sourceId))
            }
        }

        extraSources.forEach { (id, source) -> sources.put(id, source) }
        extraLayers.forEach { layers.put(it) }

        return JSONObject()
            .put("version", 8)
            .put("sources", sources)
            .put("layers", layers)
            .toString()
    }

    /** Formats an ARGB colour as a CSS colour, the notation used by the style documents. */
    fun cssColor(argb: Int): String = String.format(
        java.util.Locale.US,
        "rgba(%d,%d,%d,%.3f)",
        (argb shr 16) and 0xFF,
        (argb shr 8) and 0xFF,
        argb and 0xFF,
        ((argb ushr 24) and 0xFF) / 255.0,
    )

    /**
     * Builds the tile set of a source, publishing the zoom levels it really contains.
     *
     * The renderer needs the range to avoid asking for tiles that do not exist: a
     * request that goes beyond the maximum zoom level of the source comes back empty
     * and leaves a hole in the map. The scheme is published as well, so that the
     * renderer does not have to assume the default one.
     */
    private fun tileSet(urlTemplate: String, minZoom: Int, maxZoom: Int): TileSet =
        TileSet(urlTemplate, urlTemplate).apply {
            this.minZoom = minZoom.toFloat()
            this.maxZoom = maxZoom.toFloat()
            this.version = "2.2.0"
            this.scheme = "xyz"
        }
}
