package com.mikotrack.maps

import androidx.annotation.StringRes
import com.mikotrack.R

/**
 * The habitat overlays that can be drawn on top of any base map, with the classes the
 * user chose to see in each of them.
 *
 * @param aspect whether the orientation of the slopes is shown.
 * @param trees whether the dominant tree species are shown.
 * @param soil whether the substrate (acid or basic rocks) is shown.
 * @param aspectClasses the orientation classes that are drawn.
 * @param hiddenTrees the keys of the [HabitatSources.treeLegend] entries that are hidden.
 * @param hiddenSoil the keys of the [HabitatSources.soilLegend] entries that are hidden.
 * @param aspectUrl the URL template of the orientation tiles on the local tile server,
 * or `null` while the server is not available.
 * @param soilUrl the URL template of the soil tiles on the local tile server, or `null`
 * while the server is not available.
 * @param suitability whether the likely areas of [suitabilitySearch] are shown.
 * @param suitabilitySearch the species and the circle of the likely areas, if the user
 * started a search from the mushroom guide.
 * @param suitabilityUrl the URL template of the likely areas tiles on the local tile
 * server, or `null` while the server is not available.
 */
data class HabitatLayers(
    val aspect: Boolean = false,
    val trees: Boolean = false,
    val soil: Boolean = false,
    val aspectClasses: Set<AspectClass> = AspectHatch.DEFAULT_CLASSES,
    val hiddenTrees: Set<String> = emptySet(),
    val hiddenSoil: Set<String> = emptySet(),
    val aspectUrl: String? = null,
    val soilUrl: String? = null,
    val suitability: Boolean = false,
    val suitabilitySearch: SuitabilitySearch? = null,
    val suitabilityUrl: String? = null,
) {
    val anyVisible: Boolean get() = aspect || trees || soil || suitabilityShown

    /** Whether the likely areas overlay is switched on and has a species to draw. */
    val suitabilityShown: Boolean get() = suitability && suitabilitySearch != null

    /** The likely areas tiles of the current search, or `null` when nothing is drawn. */
    val suitabilityTileUrl: String?
        get() {
            val search = suitabilitySearch?.takeIf { suitability } ?: return null
            return suitabilityUrl?.let { "$it?${search.toQuery()}" }
        }

    /** The orientation tiles of the selected classes, or `null` when nothing is drawn. */
    val aspectTileUrl: String?
        get() = aspectUrl?.takeIf { aspect && aspectClasses.isNotEmpty() }
            ?.let { "$it?c=${AspectHatch.encode(aspectClasses)}" }

    /** The tree tiles of the selected formations, or `null` when nothing is drawn. */
    val treesTileUrl: String? get() = if (trees) HabitatSources.treesUrl(hiddenTrees) else null

    /** The soil tiles of the selected lithologies, or `null` when nothing is drawn. */
    val soilTileUrl: String?
        get() {
            val base = soilUrl?.takeIf { soil } ?: return null
            val mask = HabitatSources.soilMask(hiddenSoil)
            return if (mask == 0L) null else "$base?m=${SoilClassifier.encodeMask(mask)}"
        }
}

/**
 * A search of the likely areas of a species around a point.
 *
 * @param speciesId the identifier of the species in the mushroom guide.
 * @param speciesName the common name of the species when the search started, shown in
 * the legend.
 * @param radiusMeters the radius of the circle the overlay is drawn in.
 */
data class SuitabilitySearch(
    val speciesId: String,
    val speciesName: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
) {
    /**
     * The query string of the tiles. Everything that changes the tiles is in it, so a
     * new search gets new URLs and MapLibre does not reuse the tiles of the previous one.
     */
    fun toQuery(): String = "s=${java.net.URLEncoder.encode(speciesId, "UTF-8")}" +
        "&la=${String.format(java.util.Locale.ROOT, "%.5f", latitude)}" +
        "&lo=${String.format(java.util.Locale.ROOT, "%.5f", longitude)}" +
        "&r=$radiusMeters"

    companion object {
        /** The radii, in kilometres, the user can choose from. */
        val RADIUS_OPTIONS_KM = listOf(1, 2, 5, 10, 20, 50)
        const val DEFAULT_RADIUS_KM = 2
        const val MAX_RADIUS_METERS = 50_000

        /** The search of a query string made by [toQuery], or `null` when it is not valid. */
        fun fromQuery(query: String): SuitabilitySearch? {
            val parameters = query.split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
            val id = parameters["s"]?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                ?.takeIf { it.isNotBlank() } ?: return null
            val latitude = parameters["la"]?.toDoubleOrNull()?.takeIf { it in -85.0..85.0 } ?: return null
            val longitude = parameters["lo"]?.toDoubleOrNull()?.takeIf { it in -180.0..180.0 } ?: return null
            val radius = parameters["r"]?.toIntOrNull()?.takeIf { it in 1..MAX_RADIUS_METERS } ?: return null
            return SuitabilitySearch(id, "", latitude, longitude, radius)
        }
    }
}

/** How the swatch of a legend entry is drawn. */
enum class LegendPattern { SOLID, NORTH_LINES, SOUTH_LINES, DOTS }

/**
 * A colour key of a habitat legend.
 *
 * @param key the stable identifier of the entry, stored in the settings.
 * @param codes the values of the source attribute the entry stands for, if any.
 */
data class HabitatLegendEntry(
    val key: String,
    val color: Long,
    @StringRes val labelRes: Int,
    val codes: List<Int> = emptyList(),
    val pattern: LegendPattern = LegendPattern.SOLID,
)

/** The substrate groups of the soil overlay and the colours they are drawn with. */
enum class SoilGroup(val color: Long, @StringRes val labelRes: Int) {
    /** Siliceous rocks (granite, gneiss, slate, quartzite…) that give acid soils. */
    ACID(0xFFD6307A, R.string.habitat_soil_group_acid),

    /** Limestone, dolomite, marl and gypsum, which give basic (calcareous) soils. */
    BASIC(0xFF3D9BE0, R.string.habitat_soil_group_basic),

    /** Mixed series, river deposits and volcanic or ultramafic rocks. */
    OTHER(0xFFA8A29A, R.string.habitat_soil_group_other),
}

/**
 * A lithology of the IGME lithological map.
 *
 * @param sourceColor the RGB colour of the lithology in the map of the service.
 */
data class SoilLithology(
    val key: String,
    val sourceColor: Int,
    val group: SoilGroup,
    @StringRes val labelRes: Int,
)

/** The sources, attributions and legends of the habitat overlays. */
object HabitatSources {

    /**
     * The forest formations of the Mapa Forestal de España 1:25.000 (MFE25, base of the
     * fourth National Forest Inventory), published by MITECO under CC BY 4.0. The
     * `ff_form_arbolada` layer colours every stand by its dominant species. GeoServer
     * re-projects it to Web Mercator and MapLibre fills in the bounding box of each tile.
     */
    const val TREES_WMS_URL =
        "https://iepnb.gob.es/geoserver/foto_fija_mfe/wms?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap" +
            "&LAYERS=ff_form_arbolada&STYLES=&FORMAT=image/png&TRANSPARENT=true&SRS=EPSG:3857" +
            "&WIDTH=512&HEIGHT=512&BBOX={bbox-epsg-3857}"

    /**
     * The attribute that holds the forest formation (`descr_supraforarb` is its name and
     * the attribute the style of the layer classifies by). GeoServer filters on it with
     * the `CQL_FILTER` parameter.
     */
    const val TREES_FILTER_ATTRIBUTE = "id_supraforarb"

    /**
     * The lithological map of Spain 1:1,000,000 of the IGME-CSIC (free and keyless; the
     * IGME data is published under CC BY 4.0). The local tile server requests it tile by
     * tile, adding the size and the bounding box.
     */
    const val SOIL_WMS_URL =
        "https://mapas.igme.es/gis/services/Cartografia_Geologica/IGME_Litologias_1M/MapServer/WMSServer" +
            "?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&LAYERS=0&STYLES=&FORMAT=image/png&TRANSPARENT=true" +
            "&SRS=EPSG:3857"

    const val TREES_ATTRIBUTION = "Mapa Forestal de España (MFE25) © MITECO, CC BY 4.0"
    const val ASPECT_ATTRIBUTION = "Orientation computed from elevation tiles © Mapzen / OpenStreetMap contributors"
    const val SOIL_ATTRIBUTION = "Mapa litológico de España 1:1.000.000 © IGME-CSIC, CC BY 4.0"

    /** The likely areas combine the three sources above. */
    const val SUITABILITY_ATTRIBUTION =
        "Likely areas computed from MFE25 © MITECO, IGME-CSIC lithology (CC BY 4.0) and elevation tiles © Mapzen / OpenStreetMap contributors"

    /** The service does not draw the map at smaller scales (1:6,400,000). */
    const val TREES_MIN_ZOOM = 7
    const val TREES_MAX_ZOOM = 18
    const val TREES_TILE_SIZE = 512

    /** The polygons are opaque, so they are faded to keep the base map readable. */
    const val TREES_OPACITY = 0.55f
    const val SOIL_OPACITY = 0.4f

    const val TREES_SOURCE = "mycotrack-habitat-trees-source"
    const val TREES_LAYER = "mycotrack-habitat-trees-layer"
    const val ASPECT_SOURCE = "mycotrack-habitat-aspect-source"
    const val ASPECT_LAYER = "mycotrack-habitat-aspect-layer"
    const val SOIL_SOURCE = "mycotrack-habitat-soil-source"
    const val SOIL_LAYER = "mycotrack-habitat-soil-layer"
    const val SUITABILITY_SOURCE = "mycotrack-habitat-suitability-source"
    const val SUITABILITY_LAYER = "mycotrack-habitat-suitability-layer"

    /** The colour keys of the orientation layer (opaque versions of the textures). */
    val aspectLegend: List<HabitatLegendEntry> = listOf(
        HabitatLegendEntry("n", 0xFF1450DC, R.string.habitat_aspect_north, pattern = LegendPattern.NORTH_LINES),
        HabitatLegendEntry("s", 0xFFF06E14, R.string.habitat_aspect_south, pattern = LegendPattern.SOUTH_LINES),
        HabitatLegendEntry("e", 0xFF96783C, R.string.habitat_aspect_neutral, pattern = LegendPattern.DOTS),
    )

    /** The orientation class of an [aspectLegend] entry. */
    fun aspectClassOf(entry: HabitatLegendEntry): AspectClass = AspectHatch.decode(entry.key).first()

    /**
     * The forest formations of the MFE that occur in Catalonia, with the colours of the
     * `form_arbolada` style of the service and the `id_supraforarb` codes they cover
     * (both checked against the service: style, WFS and filtered GetMap requests).
     */
    val treeLegend: List<HabitatLegendEntry> = listOf(
        HabitatLegendEntry("holm_oak", 0xFF00A639, R.string.habitat_tree_holm_oak, listOf(18)),
        HabitatLegendEntry("cork_oak", 0xFF65A685, R.string.habitat_tree_cork_oak, listOf(19)),
        HabitatLegendEntry("downy_oak", 0xFF50D982, R.string.habitat_tree_downy_oak, listOf(14)),
        HabitatLegendEntry("portuguese_oak", 0xFF41C16E, R.string.habitat_tree_portuguese_oak, listOf(16, 17)),
        HabitatLegendEntry("sessile_oak", 0xFF62A86F, R.string.habitat_tree_sessile_oak, listOf(4)),
        HabitatLegendEntry("pyrenean_oak", 0xFF31A84B, R.string.habitat_tree_pyrenean_oak, listOf(15)),
        HabitatLegendEntry("beech", 0xFF80F297, R.string.habitat_tree_beech, listOf(1)),
        HabitatLegendEntry("chestnut", 0xFF6ADE1D, R.string.habitat_tree_chestnut, listOf(29)),
        HabitatLegendEntry("birch", 0xFF5C8944, R.string.habitat_tree_birch, listOf(8)),
        HabitatLegendEntry("strawberry_tree", 0xFF6FC653, R.string.habitat_tree_strawberry_tree, listOf(43)),
        HabitatLegendEntry("wild_olive", 0xFF2ED151, R.string.habitat_tree_wild_olive, listOf(35)),
        HabitatLegendEntry("mixed_broadleaf", 0xFF00734C, R.string.habitat_tree_mixed_broadleaf, listOf(5000)),
        HabitatLegendEntry("scots_pine", 0xFF897044, R.string.habitat_tree_scots_pine, listOf(21)),
        HabitatLegendEntry("mountain_pine", 0xFFB57448, R.string.habitat_tree_mountain_pine, listOf(22)),
        HabitatLegendEntry("black_pine", 0xFF9C7A50, R.string.habitat_tree_black_pine, listOf(25)),
        HabitatLegendEntry("aleppo_pine", 0xFFB49000, R.string.habitat_tree_aleppo_pine, listOf(24)),
        HabitatLegendEntry("stone_pine", 0xFF8E5E1D, R.string.habitat_tree_stone_pine, listOf(23)),
        HabitatLegendEntry("maritime_pine", 0xFF895A44, R.string.habitat_tree_maritime_pine, listOf(61)),
        HabitatLegendEntry("fir_other_conifers", 0xFFBD6128, R.string.habitat_tree_fir_other_conifers, listOf(1200)),
        HabitatLegendEntry("mixed_conifers", 0xFF78CF3A, R.string.habitat_tree_mixed_conifers, listOf(4000)),
        HabitatLegendEntry(
            "mixed_conifer_broadleaf", 0xFFC1DF86, R.string.habitat_tree_mixed_conifer_broadleaf, listOf(3000),
        ),
        HabitatLegendEntry("riparian", 0xFF4DB37C, R.string.habitat_tree_riparian, listOf(9000)),
        HabitatLegendEntry("other_broadleaf", 0xFF898944, R.string.habitat_tree_other_broadleaf, listOf(1300)),
        HabitatLegendEntry("junipers", 0xFFD7793E, R.string.habitat_tree_junipers, listOf(1400, 7)),
        HabitatLegendEntry("plantations", 0xFFA99F26, R.string.habitat_tree_plantations, listOf(1100)),
        HabitatLegendEntry("native_non_native", 0xFF732600, R.string.habitat_tree_native_non_native, listOf(2000)),
        HabitatLegendEntry("non_native", 0xFF655E5F, R.string.habitat_tree_non_native, listOf(7000, 8000, 6000, 27)),
    )

    /**
     * The WMS URL template of the tree layer drawing only the visible formations, or
     * `null` when every formation is hidden. With every formation visible no filter is
     * sent, so the formations that are not in the legend are drawn too.
     */
    fun treesUrl(hidden: Set<String>): String? {
        val shown = treeLegend.filter { it.key !in hidden }
        if (shown.size == treeLegend.size) return TREES_WMS_URL
        if (shown.isEmpty()) return null
        val codes = shown.flatMap { it.codes }.distinct().sorted().joinToString(",")
        return "$TREES_WMS_URL&CQL_FILTER=$TREES_FILTER_ATTRIBUTE%20IN%20($codes)"
    }

    /**
     * The lithologies of the IGME map with the colours of its style (checked against the
     * renderer of the service and its rendered tiles) and their substrate group: siliceous
     * rocks give acid soils, carbonate and gypsum rocks basic ones, and the mixed series
     * either. Two lithologies of the Tertiary basins share the same colour in the service,
     * so they are one entry.
     */
    val soilLegend: List<SoilLithology> = listOf(
        SoilLithology("granitoids", rgb(255, 0, 128), SoilGroup.ACID, R.string.habitat_soil_granitoids),
        SoilLithology("two_mica_granites", rgb(230, 0, 0), SoilGroup.ACID, R.string.habitat_soil_two_mica_granites),
        SoilLithology("felsic_plutonic", rgb(255, 84, 0), SoilGroup.ACID, R.string.habitat_soil_felsic_plutonic),
        SoilLithology("gneiss", rgb(169, 0, 230), SoilGroup.ACID, R.string.habitat_soil_gneiss),
        SoilLithology("migmatites", rgb(217, 89, 0), SoilGroup.ACID, R.string.habitat_soil_migmatites),
        SoilLithology("slates_greywackes", rgb(255, 115, 223), SoilGroup.ACID, R.string.habitat_soil_slates_greywackes),
        SoilLithology("quartzites_slates", rgb(152, 230, 0), SoilGroup.ACID, R.string.habitat_soil_quartzites_slates),
        SoilLithology("felsic_volcanic", rgb(255, 171, 0), SoilGroup.ACID, R.string.habitat_soil_felsic_volcanic),
        SoilLithology("limestones_dolomites", rgb(115, 223, 255), SoilGroup.BASIC, R.string.habitat_soil_limestones_dolomites),
        SoilLithology("dolomites_marls", rgb(0, 169, 230), SoilGroup.BASIC, R.string.habitat_soil_dolomites_marls),
        SoilLithology("calcarenites_marls", rgb(255, 211, 127), SoilGroup.BASIC, R.string.habitat_soil_calcarenites_marls),
        SoilLithology("basin_evaporites", rgb(230, 179, 0), SoilGroup.BASIC, R.string.habitat_soil_basin_evaporites),
        SoilLithology("triassic_gypsum", rgb(255, 190, 190), SoilGroup.OTHER, R.string.habitat_soil_triassic_gypsum),
        SoilLithology("sandstones_slates_limestones", rgb(255, 219, 0), SoilGroup.OTHER, R.string.habitat_soil_sandstones_slates_limestones),
        SoilLithology("coal_series", rgb(156, 156, 156), SoilGroup.OTHER, R.string.habitat_soil_coal_series),
        SoilLithology("schists_marbles", rgb(0, 132, 168), SoilGroup.OTHER, R.string.habitat_soil_schists_marbles),
        SoilLithology("conglomerates_mudstones", rgb(204, 89, 0), SoilGroup.OTHER, R.string.habitat_soil_conglomerates_mudstones),
        SoilLithology("alluvium", rgb(255, 230, 191), SoilGroup.OTHER, R.string.habitat_soil_alluvium),
        SoilLithology("volcaniclastic", rgb(0, 66, 204), SoilGroup.OTHER, R.string.habitat_soil_volcaniclastic),
        SoilLithology("mafic_volcanic", rgb(168, 168, 0), SoilGroup.OTHER, R.string.habitat_soil_mafic_volcanic),
        SoilLithology("mixed_volcanic", rgb(163, 255, 115), SoilGroup.OTHER, R.string.habitat_soil_mixed_volcanic),
        SoilLithology("mixed_plutonic", rgb(196, 0, 255), SoilGroup.OTHER, R.string.habitat_soil_mixed_plutonic),
        SoilLithology("ultramafic", rgb(0, 112, 255), SoilGroup.OTHER, R.string.habitat_soil_ultramafic),
    )

    /** The visibility mask of the soil tiles: bit `i` stands for `soilLegend[i]`. */
    fun soilMask(hidden: Set<String>): Long {
        var mask = 0L
        soilLegend.forEachIndexed { index, lithology -> if (lithology.key !in hidden) mask = mask or (1L shl index) }
        return mask
    }

    private fun rgb(red: Int, green: Int, blue: Int): Int = (red shl 16) or (green shl 8) or blue
}
