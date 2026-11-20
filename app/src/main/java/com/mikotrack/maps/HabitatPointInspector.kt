package com.mikotrack.maps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.mikotrack.ui.mushrooms.HabitatPreference
import com.mikotrack.ui.mushrooms.MushroomCatalog
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** The orientation of the terrain at a point. */
data class TerrainAtPoint(
    val aspectClass: AspectClass,
    /** The compass direction the slope faces, in degrees clockwise from north. */
    val aspectDegrees: Double,
    val slopeDegrees: Double,
    val elevationMeters: Double,
) {
    val slopePercent: Double get() = tan(Math.toRadians(slopeDegrees)) * 100.0
}

/** A tree species of a forest stand and the share of the canopy it occupies. */
data class TreeShare(val name: String, val percent: Int)

/** The forest at a point, from the MITECO forest map. */
sealed interface TreesAtPoint {
    /** The point is not in a mapped forest stand. */
    data object NoForest : TreesAtPoint

    /**
     * A forest stand.
     *
     * @param formationId the `id_supraforarb` code of the formation, if known.
     * @param formationName the name of the formation given by the service (in Spanish).
     * @param legend the entry of [HabitatSources.treeLegend] of the formation, if any.
     * @param species the main tree species, with their share of the canopy.
     * @param canopyCoverPercent the fraction of the ground covered by the tree canopy.
     */
    data class Forest(
        val formationId: Int?,
        val formationName: String?,
        val legend: HabitatLegendEntry?,
        val species: List<TreeShare> = emptyList(),
        val canopyCoverPercent: Int? = null,
    ) : TreesAtPoint
}

/** The substrate at a point, from the IGME lithological map. */
sealed interface SoilAtPoint {
    /** The point is outside the map (for instance, at sea or abroad). */
    data object OutsideMap : SoilAtPoint

    /**
     * A lithology of the map.
     *
     * @param name the name given by the service (in Spanish).
     * @param lithology the entry of [HabitatSources.soilLegend], if it is known.
     */
    data class Lithology(val name: String, val lithology: SoilLithology?) : SoilAtPoint
}

/** How likely a species is at a point, from 0 to 1. */
data class SuitabilityAtPoint(val speciesName: String, val score: Float) {
    val level: Level get() = HabitatPointMath.suitabilityLevel(score)

    enum class Level { LOW, MEDIUM, HIGH }
}

/** The state of one value of the habitat section while it is fetched. */
sealed interface PointValue<out T> {
    data object Loading : PointValue<Nothing>
    data object Failed : PointValue<Nothing>
    data class Ready<T>(val value: T) : PointValue<T>
}

/**
 * The habitat values at a point; a `null` value belongs to a layer that is not shown
 * and is not displayed.
 */
data class HabitatPointInfo(
    val aspect: PointValue<TerrainAtPoint>? = null,
    val trees: PointValue<TreesAtPoint>? = null,
    val soil: PointValue<SoilAtPoint>? = null,
    val suitability: PointValue<SuitabilityAtPoint>? = null,
    val rain: PointValue<RainAtPoint>? = null,
) {
    companion object {
        /** The initial state for the visible layers of [layers]. */
        fun loading(layers: HabitatLayers): HabitatPointInfo = HabitatPointInfo(
            aspect = PointValue.Loading.takeIf { layers.aspect },
            trees = PointValue.Loading.takeIf { layers.trees },
            soil = PointValue.Loading.takeIf { layers.soil },
            suitability = PointValue.Loading.takeIf { layers.suitabilityShown },
            rain = PointValue.Loading.takeIf { layers.rain },
        )
    }
}

/**
 * The rain accumulated at a point over each window of the overlay, in millimetres, in
 * the order of [RainRepository.WINDOWS] (3, 7, 15 and 30 days).
 */
data class RainAtPoint(
    val millimeters3: Float,
    val millimeters7: Float,
    val millimeters15: Float,
    val millimeters30: Float,
) {
    /** The accumulation over the given window, in millimetres. */
    fun millimeters(windowDays: Int): Float = when (windowDays) {
        3 -> millimeters3
        15 -> millimeters15
        30 -> millimeters30
        else -> millimeters7
    }
}

/**
 * Reads the habitat layers at a single point: the orientation and slope from the
 * Terrarium elevation tiles, the forest formation from a WMS GetFeatureInfo request to
 * the MITECO forest map, the lithology from a GetFeatureInfo request to the IGME map,
 * and the score of the likely areas from the three of them.
 *
 * Every input is fetched off the main thread with a timeout of [TIMEOUT_MS]; collecting
 * [inspect] is cancelled with the sheet, which abandons the pending requests.
 */
class HabitatPointInspector private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val cache = HabitatTileCache(appContext)
    private val elevationTiles = AspectTileProvider(appContext)

    /**
     * The values of the visible layers of [layers] at a point, emitted every time one of
     * them is ready. The orientation, forest and soil are also fetched when only the
     * likely areas are shown, because the score needs them.
     */
    fun inspect(latitude: Double, longitude: Double, layers: HabitatLayers): Flow<HabitatPointInfo> = channelFlow {
        val mutex = Mutex()
        var state = HabitatPointInfo.loading(layers)
        send(state)
        suspend fun update(change: (HabitatPointInfo) -> HabitatPointInfo) {
            mutex.withLock {
                state = change(state)
                send(state)
            }
        }
        val search = layers.suitabilitySearch?.takeIf { layers.suitabilityShown }
        coroutineScope {
            val terrain = if (layers.aspect || search != null) async { load { terrainAt(latitude, longitude) } } else null
            val trees = if (layers.trees || search != null) async { load { treesAt(latitude, longitude) } } else null
            val soil = if (layers.soil || search != null) async { load { soilAt(latitude, longitude) } } else null
            if (layers.aspect && terrain != null) launch { val value = terrain.await(); update { it.copy(aspect = value) } }
            if (layers.trees && trees != null) launch { val value = trees.await(); update { it.copy(trees = value) } }
            if (layers.soil && soil != null) launch { val value = soil.await(); update { it.copy(soil = value) } }
            if (layers.rain) launch {
                // The totals come from the lattice downloaded by the overlay itself, so
                // this needs no extra request; it only fails when there is no lattice.
                val totals = runCatching {
                    RainRepository.get(appContext).totalsAt(latitude, longitude)
                }.getOrNull()
                val value = if (totals != null && totals.size == RainRepository.WINDOWS.size) {
                    PointValue.Ready(
                        RainAtPoint(totals[0], totals[1], totals[2], totals[3]),
                    )
                } else {
                    PointValue.Failed
                }
                update { it.copy(rain = value) }
            }
            if (search != null) {
                launch {
                    val preferences = load { preferencesOf(search.speciesIds) }
                    val value = if (preferences is PointValue.Ready) {
                        // A place counts as good when it suits at least one species.
                        val score = preferences.value.maxOf { preference ->
                            HabitatPointMath.score(
                                preference,
                                (terrain?.await() as? PointValue.Ready)?.value,
                                trees?.await(),
                                (soil?.await() as? PointValue.Ready)?.value,
                            )
                        }
                        PointValue.Ready(SuitabilityAtPoint(search.speciesName, score))
                    } else {
                        PointValue.Failed
                    }
                    update { it.copy(suitability = value) }
                }
            }
        }
    }

    /** Runs a blocking call on [executor], giving up after [TIMEOUT_MS] or on any error. */
    private suspend fun <T : Any> load(block: () -> T?): PointValue<T> {
        val value = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<T?> { continuation ->
                val future = executor.submit {
                    val result = runCatching(block)
                        .onFailure { Log.d(TAG, "Habitat value not available: ${it.message}") }
                        .getOrNull()
                    // Resuming a cancelled continuation is ignored.
                    continuation.resume(result)
                }
                continuation.invokeOnCancellation { future.cancel(true) }
            }
        }
        return if (value != null) PointValue.Ready(value) else PointValue.Failed
    }

    private fun terrainAt(latitude: Double, longitude: Double): TerrainAtPoint? {
        val zoom = TERRAIN_ZOOM
        val (pixelX, pixelY) = HabitatPointMath.globalPixel(latitude, longitude, zoom, TILE_SIZE)
        val column = floor(pixelX).toInt()
        val row = floor(pixelY).toInt()
        val worldPixels = TILE_SIZE shl zoom
        // The tiles that hold the pixel and its eight neighbours, downloaded in parallel.
        val tiles = HashSet<Pair<Int, Int>>()
        for (dy in -1..1) for (dx in -1..1) {
            val x = Math.floorMod(column + dx, worldPixels) / TILE_SIZE
            val y = (row + dy).coerceIn(0, worldPixels - 1) / TILE_SIZE
            tiles += x to y
        }
        val futures = tiles.associateWith { (x, y) -> executor.submit<FloatArray?> { elevationTiles.elevationTile(zoom, x, y) } }
        val grids = futures.mapValues { (_, future) -> future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS) ?: return null }
        val elevation = { gx: Int, gy: Int ->
            val x = Math.floorMod(gx, worldPixels)
            val y = gy.coerceIn(0, worldPixels - 1)
            val grid = grids.getValue((x / TILE_SIZE) to (y / TILE_SIZE))
            grid[(y % TILE_SIZE) * TILE_SIZE + x % TILE_SIZE]
        }
        val cell = AspectClassifier.pixelSizeMeters(zoom, row / TILE_SIZE, row % TILE_SIZE, TILE_SIZE)
        return HabitatPointMath.terrainAt(elevation, column, row, cell)
    }

    private fun treesAt(latitude: Double, longitude: Double): TreesAtPoint? {
        val url = HabitatPointMath.treesFeatureInfoUrl(latitude, longitude)
        // The service sometimes answers without features for a stand it knows, so an
        // empty answer is asked again once before the point is taken as unforested.
        var parsed: TreesAtPoint? = null
        for (attempt in 0 until 2) {
            val response = cache.download(url)?.toString(Charsets.UTF_8) ?: continue
            parsed = runCatching { HabitatPointMath.parseTreesFeatureInfo(response) }.getOrNull() ?: continue
            if (parsed is TreesAtPoint.Forest) return parsed
        }
        if (parsed != null) return parsed
        // Without a usable answer, the colour of the map at the point tells the formation.
        val bytes = cache.download(HabitatPointMath.treesPixelUrl(latitude, longitude)) ?: return null
        val pixel = centrePixel(bytes) ?: return null
        return HabitatPointMath.treesFromPixel(pixel)
    }

    private fun soilAt(latitude: Double, longitude: Double): SoilAtPoint? {
        val response = cache.download(HabitatPointMath.soilFeatureInfoUrl(latitude, longitude))
            ?.toString(Charsets.UTF_8) ?: return null
        return HabitatPointMath.parseSoilFeatureInfo(response)
    }

    /** The habitats of the searched species (the affinities do not depend on the language). */
    private fun preferencesOf(speciesIds: List<String>): List<HabitatPreference>? =
        MushroomCatalog.load(appContext, "en")
            .mapNotNull { entry -> speciesIds.firstOrNull { it == entry.id }?.let { entry.data.habitat } }
            .takeIf { it.isNotEmpty() }

    private fun centrePixel(bytes: ByteArray): Int? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
            inPremultiplied = false
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return try {
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        private const val TAG = "HabitatPointInspector"
        const val TIMEOUT_MS = 8_000L

        /** The deepest zoom of the elevation tiles, the one the overlay is drawn from up close. */
        private const val TERRAIN_ZOOM = AspectTileProvider.ELEVATION_MAX_ZOOM
        private const val TILE_SIZE = 256

        private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "mycotrack-habitat-point").apply { isDaemon = true }
        }

        @Volatile
        private var instance: HabitatPointInspector? = null

        fun get(context: Context): HabitatPointInspector =
            instance ?: synchronized(this) { instance ?: HabitatPointInspector(context).also { instance = it } }
    }
}

/** The pure parts of [HabitatPointInspector]: requests, parsing and terrain maths. */
object HabitatPointMath {

    private const val HALF_WORLD_METERS = 20_037_508.342789244

    /** Half the side, in metres, of the box around the point sent to the WMS services. */
    private const val QUERY_HALF_SIZE_METERS = 50.0

    /** The size in pixels of the image the point is the centre of (1 m per pixel). */
    private const val QUERY_SIZE_PX = 101

    /** The properties of the forest map that are requested (the geometry is not sent). */
    const val TREES_PROPERTIES =
        "id_supraforarb,descr_supraforarb,descr_forarb,nom_sp1,nm_o1,nom_sp2,nm_o2,nom_sp3,nm_o3,nm_fccarb"

    /** Scores below this are low and not drawn by the overlay; above [HIGH_SCORE] they are high. */
    const val MEDIUM_SCORE = SuitabilityScorer.MIN_VISIBLE_SCORE
    const val HIGH_SCORE = SuitabilityScorer.MIDDLE_SCORE

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The lithologies of the IGME map by their (accent-free, lower-case) name in the
     * service, as listed by the renderer of its layer.
     */
    private val SOIL_NAMES: Map<String, String> = mapOf(
        "gravas, conglomerados, arenas y limos" to "alluvium",
        "conglomerados, areniscas y lutitas" to "conglomerates_mudstones",
        "conglomerados, areniscas, arcillas y calizas. evaporitas" to "basin_evaporites",
        "areniscas, conglomerados, arcillas; calizas y evaporitas" to "basin_evaporites",
        "conglomerados, areniscas, calizas, yesos y arcillas versicolores" to "triassic_gypsum",
        "conglomerados, areniscas, pizarras y calizas. carbon" to "coal_series",
        "areniscas, pizarras y calizas" to "sandstones_slates_limestones",
        "calizas detriticas, calcarenitas, margas, arcillas y calizas" to "calcarenites_marls",
        "calizas, dolomias y margas. areniscas y conglomerados" to "limestones_dolomites",
        "dolomias, calizas y margas. areniscas" to "dolomites_marls",
        "cuarcitas, pizarras, areniscas y calizas" to "quartzites_slates",
        "pizarras, grauwackas, cuarictas y conglomerados" to "slates_greywackes",
        "micaesquistos, filitas, areniscas, marmoles, calizas, dolomias y margas" to "schists_marbles",
        "gneisses" to "gneiss",
        "migmatitas, marmoles y granitoides indiferenciados" to "migmatites",
        "vulcanitas y rocas volcanoclasticas" to "volcaniclastic",
        "otros granitoides" to "granitoids",
        "granitoides de dos micas" to "two_mica_granites",
        "serpentinitas y peridotitas. rocas basicas y ultrabasicas" to "ultramafic",
        "rocas volcanicas felsicas" to "felsic_volcanic",
        "rocas volcanicas maficas y felsicas" to "mixed_volcanic",
        "rocas volcanicas maficas" to "mafic_volcanic",
        "rocas plutonicas felsicas" to "felsic_plutonic",
        "rocas plutonicas maficas y felsicas" to "mixed_plutonic",
    )

    /** The Web Mercator coordinates (x, y), in metres, of a point. */
    fun mercator(latitude: Double, longitude: Double): Pair<Double, Double> {
        val x = longitude * HALF_WORLD_METERS / 180.0
        val clamped = latitude.coerceIn(-85.0511, 85.0511)
        val y = ln(tan((90.0 + clamped) * PI / 360.0)) * HALF_WORLD_METERS / PI
        return x to y
    }

    /** The position of a point in the pixels of the whole world at a zoom level. */
    fun globalPixel(latitude: Double, longitude: Double, zoom: Int, tileSize: Int): Pair<Double, Double> {
        val (x, y) = mercator(latitude, longitude)
        val worldPixels = tileSize.toDouble() * (1 shl zoom)
        val column = (x + HALF_WORLD_METERS) / (2 * HALF_WORLD_METERS) * worldPixels
        val row = (HALF_WORLD_METERS - y) / (2 * HALF_WORLD_METERS) * worldPixels
        return column to row
    }

    private fun queryBox(latitude: Double, longitude: Double): String {
        val (x, y) = mercator(latitude, longitude)
        val d = QUERY_HALF_SIZE_METERS
        return listOf(x - d, y - d, x + d, y + d).joinToString(",") { String.format(Locale.ROOT, "%.2f", it) }
    }

    private fun featureInfoQuery(latitude: Double, longitude: Double): String {
        val centre = QUERY_SIZE_PX / 2
        return "&STYLES=&SRS=EPSG:3857&WIDTH=$QUERY_SIZE_PX&HEIGHT=$QUERY_SIZE_PX&X=$centre&Y=$centre" +
            "&FEATURE_COUNT=1&BBOX=${queryBox(latitude, longitude)}"
    }

    /** The GetFeatureInfo request of the forest map at a point (GeoJSON, without geometry). */
    fun treesFeatureInfoUrl(latitude: Double, longitude: Double): String =
        HabitatSources.TREES_WMS_URL.substringBefore('?') +
            "?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetFeatureInfo&LAYERS=ff_form_arbolada&QUERY_LAYERS=ff_form_arbolada" +
            "&INFO_FORMAT=application/json&PROPERTYNAME=$TREES_PROPERTIES" + featureInfoQuery(latitude, longitude)

    /** A 3 × 3 pixel image of the forest map centred on a point. */
    fun treesPixelUrl(latitude: Double, longitude: Double): String {
        val (x, y) = mercator(latitude, longitude)
        val d = 1.5
        val box = listOf(x - d, y - d, x + d, y + d).joinToString(",") { String.format(Locale.ROOT, "%.2f", it) }
        return HabitatSources.TREES_WMS_URL
            .replace("WIDTH=512&HEIGHT=512", "WIDTH=3&HEIGHT=3")
            .replace("{bbox-epsg-3857}", box)
    }

    /** The GetFeatureInfo request of the lithological map at a point (the ArcGIS XML format). */
    fun soilFeatureInfoUrl(latitude: Double, longitude: Double): String =
        HabitatSources.SOIL_WMS_URL.substringBefore('?') +
            "?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetFeatureInfo&LAYERS=0&QUERY_LAYERS=0&INFO_FORMAT=text/xml" +
            featureInfoQuery(latitude, longitude)

    /**
     * Parses the GeoJSON answer of [treesFeatureInfoUrl].
     *
     * @throws IllegalArgumentException when the answer is not a feature collection.
     */
    fun parseTreesFeatureInfo(response: String): TreesAtPoint {
        val root = runCatching { json.parseToJsonElement(response).jsonObject }.getOrNull()
        val features = root?.get("features") as? JsonArray
            ?: throw IllegalArgumentException("Not a feature collection")
        val properties = features.firstOrNull()?.jsonObject?.get("properties") as? JsonObject
            ?: return TreesAtPoint.NoForest
        fun text(key: String) = (properties[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        fun number(key: String) = (properties[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }
        val id = number("id_supraforarb")
        val species = (1..3).mapNotNull { index ->
            val name = text("nom_sp$index") ?: return@mapNotNull null
            // The occupation is given in tenths of the canopy.
            val tenths = number("nm_o$index") ?: return@mapNotNull null
            if (tenths <= 0) null else TreeShare(name, tenths * 10)
        }
        return TreesAtPoint.Forest(
            formationId = id,
            formationName = text("descr_supraforarb") ?: text("descr_forarb"),
            legend = id?.let { code -> HabitatSources.treeLegend.firstOrNull { code in it.codes } },
            species = species,
            canopyCoverPercent = number("nm_fccarb")?.takeIf { it in 1..100 },
        )
    }

    /** The forest at a point from the colour of the forest map there (ARGB, not premultiplied). */
    fun treesFromPixel(argb: Int): TreesAtPoint {
        if ((argb ushr 24) < 128) return TreesAtPoint.NoForest
        val palette = HabitatSources.treeLegend.map { (it.color and 0xFFFFFFL).toInt() }.toIntArray()
        val index = SoilClassifier.match(argb, palette)
        return TreesAtPoint.Forest(null, null, HabitatSources.treeLegend.getOrNull(index))
    }

    /** Parses the ArcGIS XML answer of [soilFeatureInfoUrl]. */
    fun parseSoilFeatureInfo(response: String): SoilAtPoint {
        require(response.contains("FeatureInfoResponse")) { "Not a feature info response" }
        val fields = Regex("<FIELDS\\b([^>]*)>").find(response) ?: return SoilAtPoint.OutsideMap
        val name = Regex("\\bLitologia=\"([^\"]*)\"").find(fields.groupValues[1])?.groupValues?.get(1)
            ?.let(::unescapeXml)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return SoilAtPoint.OutsideMap
        return SoilAtPoint.Lithology(name, lithologyOf(name))
    }

    /** The lithology of the soil legend a name of the IGME map stands for, if any. */
    fun lithologyOf(name: String): SoilLithology? {
        val key = SOIL_NAMES[normalize(name)] ?: return null
        return HabitatSources.soilLegend.firstOrNull { it.key == key }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")

    private fun unescapeXml(text: String): String =
        Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);").replace(text) { match ->
            when (val entity = match.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                else -> {
                    val code = if (entity.startsWith("#x")) entity.substring(2).toInt(16) else entity.substring(1).toInt()
                    String(Character.toChars(code))
                }
            }
        }

    /**
     * The orientation and slope of the terrain at a pixel of an elevation grid, with
     * Horn's method over its 3 × 3 neighbourhood (as the orientation overlay does).
     *
     * @param elevation the elevation in metres of a pixel (column, row); rows go from
     * north to south.
     * @param cellSizeMeters the ground size of a pixel.
     */
    fun terrainAt(elevation: (Int, Int) -> Float, column: Int, row: Int, cellSizeMeters: Double): TerrainAtPoint {
        fun z(dx: Int, dy: Int) = elevation(column + dx, row + dy).toDouble()
        val a = z(-1, -1)
        val b = z(0, -1)
        val c = z(1, -1)
        val d = z(-1, 0)
        val f = z(1, 0)
        val g = z(-1, 1)
        val h = z(0, 1)
        val i = z(1, 1)
        val dzdx = ((c + 2 * f + i) - (a + 2 * d + g)) / (8.0 * cellSizeMeters)
        val dzdy = ((a + 2 * b + c) - (g + 2 * h + i)) / (8.0 * cellSizeMeters)
        return TerrainAtPoint(
            aspectClass = AspectClassifier.classify(dzdx, dzdy),
            aspectDegrees = AspectClassifier.aspectDegrees(dzdx, dzdy),
            slopeDegrees = AspectClassifier.slopeDegrees(dzdx, dzdy),
            elevationMeters = z(0, 0),
        )
    }

    fun suitabilityLevel(score: Float): SuitabilityAtPoint.Level = when {
        score >= HIGH_SCORE -> SuitabilityAtPoint.Level.HIGH
        score >= MEDIUM_SCORE -> SuitabilityAtPoint.Level.MEDIUM
        else -> SuitabilityAtPoint.Level.LOW
    }

    /**
     * The score of a species at a point, as the likely areas overlay computes it. A
     * `null` input is unknown and counts as neutral.
     */
    fun score(
        preference: HabitatPreference,
        terrain: TerrainAtPoint?,
        trees: PointValue<TreesAtPoint>?,
        soil: SoilAtPoint?,
    ): Float {
        val model = SuitabilityScorer.Model(preference)
        val tree = when (val value = (trees as? PointValue.Ready)?.value) {
            TreesAtPoint.NoForest -> SuitabilityScorer.TREE_NONE
            is TreesAtPoint.Forest -> value.legend?.let { HabitatSources.treeLegend.indexOf(it) }?.takeIf { it >= 0 }
                ?: SuitabilityScorer.TREE_UNKNOWN
            null -> SuitabilityScorer.TREE_UNKNOWN
        }
        val group = (soil as? SoilAtPoint.Lithology)?.lithology?.group?.ordinal ?: SuitabilityScorer.UNKNOWN
        val aspect = terrain?.aspectClass?.ordinal ?: SuitabilityScorer.UNKNOWN
        return model.score(tree, group, aspect)
    }
}
