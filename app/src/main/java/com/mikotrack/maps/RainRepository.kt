package com.mikotrack.maps

import android.content.Context
import android.util.Log
import com.mikotrack.data.db.MycoTrackDatabase
import com.mikotrack.data.db.RainDailyEntity
import com.mikotrack.data.db.RainMetaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.zone.ZoneRulesException
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * Downloads the daily precipitation of the free Open-Meteo service on a lattice over
 * Catalonia and keeps it in the database, so the rainfall overlay works offline with
 * the last downloaded days.
 *
 * The lattice is a regular grid of 0.1 degrees (about 11 km, the resolution of the
 * blended weather models); every point answers with the model cell it snaps to. The
 * points are requested in chunks of 100 (the service rejects longer URLs) and the
 * chunks are spaced, because the free service rate-limits bursts. One fetch covers 31
 * past days, so the 3, 7, 15 and 30-day windows are all computed locally; the lattice
 * is refreshed at most once every [REFRESH_HOURS].
 */
class RainRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dao by lazy { MycoTrackDatabase.getInstance(appContext).rainDao() }
    private val mutex = Mutex()

    /** The timestamp of the lattice in the database; it busts the tile URLs. */
    private val fetchedAt = AtomicLong(0)

    /** The grid of the current lattice, recomputed when [fetchedAt] changes. */
    @Volatile
    private var cachedGrid: RainGrid? = null

    /**
     * Downloads the lattice when it is missing or older than [REFRESH_HOURS]. Returns
     * whether fresh data is available afterwards.
     */
    suspend fun refresh(force: Boolean = false): Boolean = mutex.withLock {
        val meta = withContext(Dispatchers.IO) { dao.meta() }
        fetchedAt.set(meta?.fetchedAt ?: 0)
        val ageHours = meta?.let { (System.currentTimeMillis() - it.fetchedAt) / 3_600_000 }
        if (meta != null && !force && (ageHours ?: Long.MAX_VALUE) < REFRESH_HOURS) return@withLock true

        val points = lattice()
        val daily = ArrayList<RainDailyEntity>(points.size * 32)
        var days = setOf<String>()
        // The whole fetch runs off the main thread: the chunks are downloaded with
        // delays between them, which would block (and crash) the caller otherwise.
        withContext(Dispatchers.IO) {
            for (chunk in points.chunked(CHUNK_POINTS)) {
                val response = download(chunk) ?: break
                val parsed = parse(response, chunk, daily) ?: break
                days = parsed
            }
        }
        if (daily.isEmpty()) return@withLock meta != null
        val dayKeys = days.filter { it <= todayKey() }.sorted()
        if (daily.isEmpty() || dayKeys.isEmpty()) return@withLock meta != null
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            dao.clearDaily()
            dao.insertDaily(daily)
            dao.insertMeta(
                RainMetaEntity(
                    fetchedAt = now,
                    lat0 = points.first().latitude,
                    lon0 = points.first().longitude,
                    stepDeg = LATTICE_STEP,
                    rows = LATTICE_ROWS,
                    cols = LATTICE_COLS,
                ),
            )
        }
        fetchedAt.set(now)
        cachedGrid = null
        Log.i(TAG, "Rain lattice stored: ${points.size} nodes, ${dayKeys.size} days")
        true
    }

    /** The lattice of the current data, or `null` when nothing has been downloaded. */
    suspend fun grid(): RainGrid? {
        val version = currentVersion()
        cachedGrid?.takeIf { it.hasData }?.let { grid ->
            if (version == fetchedAt.get() && version != 0L) return grid
        }
        return mutex.withLock {
            cachedGrid?.takeIf { it.hasData && fetchedAt.get() == version && version != 0L }?.let { return it }
            withContext(Dispatchers.IO) {
                val meta = dao.meta() ?: return@withContext null
                fetchedAt.set(meta.fetchedAt)
                val rows = dao.all()
                if (rows.isEmpty()) return@withContext null
                val dayKeys = rows.map { it.day }.distinct().sorted()
                val dayIndex = dayKeys.withIndex().associate { (i, d) -> d to i }
                val nodes = meta.rows * meta.cols
                val values = FloatArray(nodes * dayKeys.size)
                // Every stored coordinate is the exact lattice point of a request, so
                // the node index follows from the lattice geometry.
                for (row in rows) {
                    val latitude = row.latitude / 1_000_000.0
                    val longitude = row.longitude / 1_000_000.0
                    val col = ((longitude - meta.lon0) / meta.stepDeg).roundToInt()
                    val r = ((latitude - meta.lat0) / meta.stepDeg).roundToInt()
                    if (col !in 0 until meta.cols || r !in 0 until meta.rows) continue
                    val node = r * meta.cols + col
                    val d = dayIndex[row.day] ?: continue
                    values[node * dayKeys.size + d] = row.millimeters
                }
                RainGrid(meta.lat0, meta.lon0, meta.stepDeg, meta.rows, meta.cols, dayKeys.toIntArray(), values)
                    .also { cachedGrid = it }
            }
        }
    }

    /** The timestamp of the current lattice, `0` when there is no data. */
    suspend fun currentVersion(): Long {
        if (fetchedAt.get() == 0L) fetchedAt.set(withContext(Dispatchers.IO) { dao.meta() }?.fetchedAt ?: 0)
        return fetchedAt.get()
    }

    /**
     * The millimetres of every window at a point, or `null` when the point is outside
     * the lattice. The first value is the 3-day total, the second the 7-day one, and so
     * on, following [WINDOWS].
     */
    suspend fun totalsAt(latitude: Double, longitude: Double): FloatArray? {
        val grid = grid() ?: return null
        val totals = WINDOWS.map { grid.windowTotals(it) }
        val result = FloatArray(WINDOWS.size)
        WINDOWS.forEachIndexed { i, window ->
            val mm = grid.sample(totals[i], latitude, longitude) ?: return null
            result[i] = mm.coerceAtLeast(0f)
        }
        return result
    }

    /** The lattice of the fetch: [LATTICE_ROWS] rows of [LATTICE_COLS] points. */
    private fun lattice(): List<LatticePoint> {
        val points = ArrayList<LatticePoint>(LATTICE_ROWS * LATTICE_COLS)
        for (r in 0 until LATTICE_ROWS) {
            val latitude = LATTICE_LAT0 + r * LATTICE_STEP
            for (c in 0 until LATTICE_COLS) {
                points += LatticePoint(latitude, LATTICE_LON0 + c * LATTICE_STEP)
            }
        }
        return points
    }

    private data class LatticePoint(val latitude: Double, val longitude: Double)

    /** Downloads one chunk of points, retrying the rate limit with a backoff. */
    private fun download(chunk: List<LatticePoint>): String? {
        val latitudes = chunk.joinToString(",") { String.format(java.util.Locale.ROOT, "%.2f", it.latitude) }
        val longitudes = chunk.joinToString(",") { String.format(java.util.Locale.ROOT, "%.2f", it.longitude) }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitudes&longitude=$longitudes" +
            "&daily=precipitation_sum&past_days=$PAST_DAYS&timezone=UTC"
        for (attempt in 0 until 3) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "MycoTrack (Android)")
                    setRequestProperty("Accept-Encoding", "gzip")
                }
                if (connection.responseCode == 429) {
                    Thread.sleep(RATE_LIMIT_BACKOFF_MS * (attempt + 1))
                    continue
                }
                if (connection.responseCode != 200) {
                    Log.w(TAG, "Open-Meteo answered ${connection.responseCode}")
                    return null
                }
                val stream = connection.inputStream
                val bytes = if (connection.contentEncoding == "gzip") {
                    java.util.zip.GZIPInputStream(stream).use { it.readBytes() }
                } else {
                    stream.use { it.readBytes() }
                }
                Thread.sleep(CHUNK_DELAY_MS)
                return bytes.toString(Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w(TAG, "Rain fetch failed (attempt ${attempt + 1}): ${e.javaClass.name}: ${e.message}", e)
                if (attempt == 2) return null
                Thread.sleep(CHUNK_DELAY_MS * 4)
            } finally {
                connection?.disconnect()
            }
        }
        return null
    }

    /**
     * Parses one chunk answer into [daily] (the rows of the chunk's points) and returns
     * the day keys, or `null` when the answer is not usable.
     */
    private fun parse(response: String, chunk: List<LatticePoint>, daily: MutableList<RainDailyEntity>): Set<String>? {
        val locations = runCatching { JSONArray(response) }.getOrNull() ?: return null
        if (locations.length() != chunk.size) return null
        val days = mutableSetOf<String>()
        for (i in 0 until locations.length()) {
            val location = locations.getJSONObject(i)
            val dailyObject = location.optJSONObject("daily") ?: return null
            val times = dailyObject.optJSONArray("time") ?: return null
            val sums = dailyObject.optJSONArray("precipitation_sum") ?: return null
            // The request is answered with the coordinates of the model cell, which are
            // stored as the node of the lattice.
            val latitude = location.optDouble("latitude", chunk[i].latitude)
            val longitude = location.optDouble("longitude", chunk[i].longitude)
            val latitudeKey = (latitude * 1_000_000).roundToInt()
            val longitudeKey = (longitude * 1_000_000).roundToInt()
            for (d in 0 until minOf(times.length(), sums.length())) {
                val key = times.getString(d)
                val day = dayOf(key) ?: continue
                if (day > todayEpochDay()) continue
                val millimeters = if (sums.isNull(d)) 0f else sums.getDouble(d).toFloat().coerceAtLeast(0f)
                daily += RainDailyEntity(latitudeKey, longitudeKey, day, millimeters)
                days += key
            }
        }
        return days
    }

    private fun dayOf(key: String): Int? = runCatching {
        LocalDate.parse(key, DateTimeFormatter.ISO_LOCAL_DATE).toEpochDay().toInt()
    }.getOrNull()

    private fun todayEpochDay(): Int = LocalDate.now(timeZone()).toEpochDay().toInt()

    private fun todayKey(): String = LocalDate.now(timeZone()).format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun timeZone() = try {
        TimeZone.getDefault().toZoneId()
    } catch (e: ZoneRulesException) {
        java.time.ZoneOffset.UTC
    }

    companion object {
        private const val TAG = "RainRepository"

        /** The windows the user can pick, in days. */
        val WINDOWS = listOf(3, 7, 15, 30)
        const val DEFAULT_WINDOW = 7

        /** The lattice: Catalonia plus a margin, at the model resolution. */
        private const val LATTICE_LAT0 = 40.40
        private const val LATTICE_LON0 = -0.10
        private const val LATTICE_STEP = 0.1
        private const val LATTICE_ROWS = 27
        private const val LATTICE_COLS = 37

        private const val PAST_DAYS = 31
        private const val CHUNK_POINTS = 100
        private const val CHUNK_DELAY_MS = 1_200L
        private const val RATE_LIMIT_BACKOFF_MS = 5_000L

        /** The lattice is downloaded at most once per this many hours. */
        const val REFRESH_HOURS = 12

        @Volatile
        private var instance: RainRepository? = null

        fun get(context: Context): RainRepository =
            instance ?: synchronized(this) {
                instance ?: RainRepository(context.applicationContext).also { instance = it }
            }
    }
}
