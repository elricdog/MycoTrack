package com.mikotrack.maps

import com.mikotrack.R
import androidx.annotation.StringRes
import android.content.Context
import android.util.Log
import com.mikotrack.data.db.OfflineAreaDao
import com.mikotrack.data.db.OfflineAreaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Manages the offline map areas: seeding the ones that ship with the application,
 * downloading new ones, listing them and handing the URL template that MapLibre needs
 * to display them.
 */
class OfflineMapManager(
    context: Context,
    private val dao: OfflineAreaDao,
    private val downloader: MbtilesDownloader = MbtilesDownloader(),
) {

    private val appContext = context.applicationContext
    private val tileServer = LocalTileServer(appContext)

    /** All offline areas, newest first. */
    fun observeAreas(): Flow<List<OfflineAreaEntity>> = dao.observeAll()

    /** The directory that stores the MBTiles files. */
    private val offlineDirectory: File
        get() = File(appContext.filesDir, "offline").apply { if (!exists()) mkdirs() }

    /**
     * Copies the offline packs that ship inside the APK into the private storage and
     * registers them in the database.
     *
     * The copy is done once: the files are only written again when their size does not
     * match the one of the packaged asset, which happens after an application update
     * that ships a newer map.
     */
    suspend fun seedBundledAreas(): List<OfflineAreaEntity> = withContext(Dispatchers.IO) {
        val seeded = mutableListOf<OfflineAreaEntity>()
        for (pack in BUNDLED_PACKS) {
            runCatching { seedBundledArea(pack) }
                .onSuccess { seeded += it }
                .onFailure { Log.e(TAG, "Could not seed the bundled pack ${pack.assetPath}", it) }
        }
        seeded
    }

    /** Copies a single bundled pack and returns the row that describes it. */
    private suspend fun seedBundledArea(pack: BundledPack): OfflineAreaEntity {
        val target = File(offlineDirectory, pack.fileName)
        val assetSize = assetSize(pack.assetPath)

        val needsCopy = !target.exists() || target.length() != assetSize
        if (needsCopy) {
            copyAsset(pack.assetPath, target)
        }

        val existing = dao.getBundledByName(pack.displayName)
        val entity = OfflineAreaEntity(
            id = existing?.id ?: 0L,
            name = pack.displayName,
            layerId = pack.layerId,
            filePath = target.absolutePath,
            minLatitude = pack.bounds.minLatitude,
            minLongitude = pack.bounds.minLongitude,
            maxLatitude = pack.bounds.maxLatitude,
            maxLongitude = pack.bounds.maxLongitude,
            minZoom = pack.minZoom,
            maxZoom = pack.maxZoom,
            tileCount = pack.tileCount,
            sizeBytes = target.length(),
            createdAt = existing?.createdAt ?: 0L,
            bundled = true,
            format = MbtilesStore.FORMAT_JPEG,
            tileSize = pack.tileSize,
        )

        val storedId = if (existing == null) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            existing.id
        }
        tileServer.register(storedId, target)
        Log.i(TAG, "Bundled offline area '${pack.displayName}' is ready at ${target.name}")
        return entity.copy(id = storedId)
    }

    /**
     * The length in bytes of a packaged asset.
     *
     * The asset is opened as a file descriptor when it is stored uncompressed inside
     * the APK, which is the case for the MBTiles files, and read as a stream otherwise.
     */
    private fun assetSize(assetPath: String): Long {
        val descriptor = try {
            appContext.assets.openFd(assetPath)
        } catch (ignored: IOException) {
            // The asset is compressed inside the APK and has no file descriptor.
            null
        }
        if (descriptor != null) {
            descriptor.use { return it.length }
        }

        var total = 0L
        appContext.assets.open(assetPath).use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                total += read
            }
        }
        return total
    }

    /** Copies a packaged asset into the private storage of the application. */
    private fun copyAsset(assetPath: String, target: File) {
        val temporary = File(target.parentFile, target.name + ".part")
        try {
            appContext.assets.open(assetPath).use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        } catch (e: IOException) {
            temporary.delete()
            throw IOException("Could not copy the bundled map $assetPath", e)
        }
    }

    /**
     * Downloads a new offline area.
     *
     * The tiles are fetched from the same server that the selected layer uses, so the
     * offline copy is visually identical to the online map.
     */
    suspend fun downloadArea(
        layer: MapLayer,
        name: String,
        bounds: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        onProgress: (downloaded: Int, total: Int) -> Unit = { _, _ -> },
    ): OfflineAreaEntity = withContext(Dispatchers.IO) {
        val template = layer.tileUrlTemplate
            ?: error("The ${layer.id} layer cannot be downloaded for offline use")

        val file = File(offlineDirectory, "area_${System.currentTimeMillis()}.mbtiles")
        val result = downloader.download(
            context = appContext,
            target = file,
            name = name,
            tileUrlTemplate = template,
            bounds = bounds,
            minZoom = minZoom,
            maxZoom = maxZoom,
            tileSize = layer.tileSize,
            onProgress = onProgress,
        )

        val entity = OfflineAreaEntity(
            name = name,
            layerId = layer.id,
            filePath = file.absolutePath,
            minLatitude = bounds.minLatitude,
            minLongitude = bounds.minLongitude,
            maxLatitude = bounds.maxLatitude,
            maxLongitude = bounds.maxLongitude,
            minZoom = minZoom,
            maxZoom = maxZoom,
            tileCount = result.downloadedTiles,
            sizeBytes = result.sizeBytes,
            createdAt = System.currentTimeMillis(),
            bundled = false,
            format = MbtilesStore.FORMAT_PNG,
            tileSize = layer.tileSize,
        )
        val id = dao.insert(entity)
        tileServer.register(id, file)
        entity.copy(id = id)
    }

    /** Registers every stored area with the local tile server. */
    suspend fun registerStoredAreas() = withContext(Dispatchers.IO) {
        dao.getAll().forEach { area ->
            val file = File(area.filePath)
            if (file.exists()) tileServer.register(area.id, file)
        }
    }

    /**
     * Everything the map needs to display an offline area.
     *
     * The tile size is part of the description because the bundled packs do not all
     * use the same one: the orthophotography is stored in 256 pixel tiles while the
     * topographic map is stored in 512 pixel tiles.
     */
    fun sourceFor(area: OfflineAreaEntity): OfflineSource? {
        tileServer.register(area.id, File(area.filePath))
        val served = tileServer.area(area.id) ?: return null
        return OfflineSource(
            url = served.url,
            // The tile size of the database is the one the area was written with.
            // The metadata of the file is only a fallback for the areas that were
            // stored before the column was added.
            tileSize = if (area.tileSize > 0) area.tileSize else served.tileSize,
            minZoom = area.minZoom,
            maxZoom = area.maxZoom,
        )
    }

    /**
     * The URL template of the orientation overlay, served by the local tile server.
     * Starts the server when needed, so it must not be called on the main thread.
     */
    fun aspectTileUrl(): String? = tileServer.aspectUrl()

    /**
     * The URL template of the soil overlay, served by the local tile server.
     * Starts the server when needed, so it must not be called on the main thread.
     */
    fun soilTileUrl(): String? = tileServer.soilUrl()

    /**
     * Deletes an offline area and its MBTiles file.
     *
     * The areas that ship with the application are never deleted: they are restored
     * on the next launch and they are the only maps available without a download.
     */
    suspend fun deleteArea(area: OfflineAreaEntity) {
        if (area.bundled) return
        tileServer.unregister(area.id)
        runCatching { File(area.filePath).delete() }
        dao.deleteById(area.id)
    }

    /** Releases the local tile server. */
    fun shutdown() {
        tileServer.stop()
    }

    /**
     * The description of an offline pack that is shipped inside the application.
     *
     * The data comes from the Institut Cartogràfic i Geològic de Catalunya, which
     * publishes the orthophotography and the topographic map of Catalonia under the
     * Creative Commons Attribution 4.0 licence.
     */
    data class BundledPack(
        val assetPath: String,
        val fileName: String,
        val displayName: String,
        @StringRes val displayNameRes: Int,
        val layerId: String,
        val bounds: BoundingBox,
        val minZoom: Int,
        val maxZoom: Int,
        val tileCount: Int,
        val tileSize: Int,
    )

    companion object {
        private const val TAG = "OfflineMapManager"

        /**
         * The translated name of a bundled area. The stored name stays in English
         * because it is the key used to find the area again.
         */
        @StringRes
        fun bundledNameRes(storedName: String): Int? =
            BUNDLED_PACKS.firstOrNull { it.displayName == storedName }?.displayNameRes

        /** The bounds of Catalonia, used by every bundled pack. */
        private val CATALONIA = BoundingBox(
            minLatitude = 40.52,
            minLongitude = 0.15,
            maxLatitude = 42.86,
            maxLongitude = 3.33,
        )

        /**
         * The offline maps that ship with the application.
         *
         * Both packs cover the whole of Catalonia and are stored as JPEG tiles, which
         * MapLibre decodes natively.
         */
        val BUNDLED_PACKS: List<BundledPack> = listOf(
            BundledPack(
                assetPath = "offline/catalonia_ortho.mbtiles",
                fileName = "bundled_catalonia_ortho.mbtiles",
                displayName = "Catalonia orthophotography",
                displayNameRes = R.string.offline_bundled_catalonia_ortho,
                layerId = MapLayer.ICGC_ORTHO.id,
                bounds = CATALONIA,
                minZoom = 10,
                maxZoom = 13,
                tileCount = 7049,
                tileSize = 256,
            ),
            BundledPack(
                assetPath = "offline/catalonia_topo.mbtiles",
                fileName = "bundled_catalonia_topo.mbtiles",
                displayName = "Catalonia topographic",
                displayNameRes = R.string.offline_bundled_catalonia_topo,
                layerId = MapLayer.ICGC_TOPO.id,
                bounds = CATALONIA,
                minZoom = 10,
                maxZoom = 12,
                tileCount = 1793,
                tileSize = 512,
            ),
        )
    }
}
