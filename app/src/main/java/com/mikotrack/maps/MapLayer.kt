package com.mikotrack.maps

import androidx.annotation.StringRes
import com.mikotrack.R

/**
 * Public elevation tiles in the terrarium encoding, used for the hillshade layer.
 */
const val TERRAIN_DEM_URL = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"

/**
 * The description of an offline area that is being displayed.
 *
 * @param url the URL template of the local tile server.
 * @param tileSize the edge length in pixels of a tile of the area.
 * @param minZoom the lowest zoom level that the area contains.
 * @param maxZoom the highest zoom level that the area contains.
 */
data class OfflineSource(
    val url: String,
    val tileSize: Int,
    val minZoom: Int,
    val maxZoom: Int,
)

/**
 * The interchangeable base map layers of MycoTrack.
 *
 * Every layer is a raster source, which keeps the application compatible with the free
 * and open tile servers of the Institut Cartogràfic i Geològic de Catalunya (ICGC) and
 * with OpenStreetMap without requiring a single API key.
 */
enum class MapLayer(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val tileUrlTemplate: String?,
    val attribution: String,
    val tileSize: Int,
    val maxZoom: Int,
    val minZoom: Int = 0,
) {
    /** Standard OpenStreetMap street map. */
    OSM_STREET(
        id = "OSM_STREET",
        labelRes = R.string.layer_osm_street,
        descriptionRes = R.string.layer_osm_street_description,
        tileUrlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        attribution = "© OpenStreetMap contributors",
        tileSize = 256,
        maxZoom = 19,
    ),

    /** ICGC topographic base map of Catalonia. */
    ICGC_TOPO(
        id = "ICGC_TOPO",
        labelRes = R.string.layer_icgc_topo,
        descriptionRes = R.string.layer_icgc_topo_description,
        tileUrlTemplate = "https://geoserveis.icgc.cat/servei/catalunya/mapa-base/wmts/topografic/MON3857NW/{z}/{x}/{y}.png",
        attribution = "© Institut Cartogràfic i Geològic de Catalunya",
        tileSize = 512,
        maxZoom = 18,
    ),

    /** ICGC orthophotography (aerial photography) of Catalonia. */
    ICGC_ORTHO(
        id = "ICGC_ORTHO",
        labelRes = R.string.layer_icgc_ortho,
        descriptionRes = R.string.layer_icgc_ortho_description,
        tileUrlTemplate = "https://geoserveis.icgc.cat/servei/catalunya/mapa-base/wmts/orto/MON3857NW/{z}/{x}/{y}.png",
        attribution = "© Institut Cartogràfic i Geològic de Catalunya",
        tileSize = 256,
        maxZoom = 18,
    ),

    /** ICGC hybrid view: orthophotography with the topographic labels on top. */
    ICGC_HYBRID(
        id = "ICGC_HYBRID",
        labelRes = R.string.layer_icgc_hybrid,
        descriptionRes = R.string.layer_icgc_hybrid_description,
        tileUrlTemplate = "https://geoserveis.icgc.cat/servei/catalunya/mapa-base/wmts/orto-hibrida/MON3857NW/{z}/{x}/{y}.png",
        attribution = "© Institut Cartogràfic i Geològic de Catalunya",
        tileSize = 256,
        maxZoom = 18,
    ),

    /** Relief and hillshade derived from open elevation tiles. */
    TERRAIN(
        id = "TERRAIN",
        labelRes = R.string.layer_terrain,
        descriptionRes = R.string.layer_terrain_description,
        tileUrlTemplate = TERRAIN_DEM_URL,
        attribution = "Elevation tiles © Mapzen / OpenStreetMap contributors",
        tileSize = 256,
        maxZoom = 15,
    ),

    /** Maps downloaded in advance and stored as MBTiles. */
    OFFLINE(
        id = "OFFLINE",
        labelRes = R.string.layer_offline,
        descriptionRes = R.string.layer_offline_description,
        tileUrlTemplate = null,
        attribution = "Stored offline on this device",
        tileSize = 256,
        maxZoom = 19,
    ),
    ;

    /** The raster source identifier used inside the MapLibre style. */
    val sourceId: String get() = "source-$id"

    /** The raster layer identifier used inside the MapLibre style. */
    val layerId: String get() = "layer-$id"

    companion object {
        fun fromId(id: String?): MapLayer? = entries.firstOrNull { it.id == id }

        /** Layers that can be downloaded for offline use. */
        val downloadable: List<MapLayer> = listOf(OSM_STREET, ICGC_TOPO, ICGC_ORTHO, ICGC_HYBRID)
    }
}
