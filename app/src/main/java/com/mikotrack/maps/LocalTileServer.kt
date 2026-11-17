package com.mikotrack.maps

import android.content.Context
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * A tiny HTTP server that serves the tiles of the downloaded MBTiles files to MapLibre.
 *
 * The server only listens on the loopback interface, so the offline maps never leave the
 * device and no additional permission is required.
 */
class LocalTileServer(private val context: Context) {

    /** The description of an offline area that is served by the local server. */
    data class Area(
        val url: String,
        val tileSize: Int,
        val format: String,
    )

    private var serverSocket: ServerSocket? = null
    private var worker: Thread? = null
    private val stores = ConcurrentHashMap<Long, MbtilesStore>()
    private val storePaths = ConcurrentHashMap<Long, String>()
    private val formats = ConcurrentHashMap<Long, String>()

    /** Computes the tiles of the orientation overlay, created on the first request. */
    private val aspectTiles by lazy { AspectTileProvider(context) }

    /** Recolours the tiles of the soil overlay, created on the first request. */
    private val soilTiles by lazy { SoilTileProvider(context) }

    /** Computes the tiles of the likely areas overlay, created on the first request. */
    private val suitabilityTiles by lazy { SuitabilityTileProvider(context, aspectTiles, soilTiles) }

    /** Paints the tiles of the rainfall overlay, created on the first request. */
    private val rainTiles by lazy { RainTileProvider(context) }

    /**
     * The likely areas tiles are slow (they gather three sources each), so they are
     * computed by a few threads of their own and never take every worker of the server.
     */
    private val suitabilityExecutor = Executors.newFixedThreadPool(SUITABILITY_THREADS) { runnable ->
        Thread(runnable, "mycotrack-suitability-server").apply { isDaemon = true }
    }

    /** The port the server listens on, or `null` when it is not running. */
    val port: Int? get() = serverSocket?.localPort

    /** Starts the server if it is not running yet. */
    @Synchronized
    fun start(): Int? {
        serverSocket?.let { return it.localPort }
        return runCatching {
            val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
            serverSocket = socket
            worker = Thread({ acceptLoop(socket) }, "mbtiles-server").apply {
                isDaemon = true
                start()
            }
            Log.i(TAG, "The local tile server is listening on 127.0.0.1:${socket.localPort}")
            socket.localPort
        }.onFailure { Log.e(TAG, "Could not start the local tile server", it) }.getOrNull()
    }

    /** Stops the server and releases every open database. */
    @Synchronized
    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        worker = null
        stores.values.forEach { runCatching { it.close() } }
        stores.clear()
        storePaths.clear()
        formats.clear()
    }

    /**
     * Registers an offline area so that its tiles can be served.
     *
     * The database of an area is opened only once: the file is locked for the whole
     * lifetime of the area, so re-opening it for every style change would waste
     * resources and slow the map down.
     */
    fun register(areaId: Long, file: File) {
        if (!file.exists()) {
            Log.w(TAG, "The offline file ${file.name} does not exist")
            return
        }
        val path = file.absolutePath
        if (storePaths[areaId] == path && stores.containsKey(areaId)) return

        runCatching {
            stores.remove(areaId)?.let { runCatching { it.close() } }
            storePaths.remove(areaId)
            val store = MbtilesStore(context, file)
            stores[areaId] = store
            storePaths[areaId] = path
            formats[areaId] = runCatching { store.format() }.getOrDefault(MbtilesStore.FORMAT_JPEG)
            Log.i(TAG, "Registered the offline area $areaId (${file.name})")
        }.onFailure {
            Log.e(TAG, "Could not register the offline area $areaId (${file.name})", it)
        }
    }

    /** Removes an offline area from the server. */
    fun unregister(areaId: Long) {
        stores.remove(areaId)?.let { runCatching { it.close() } }
        storePaths.remove(areaId)
        formats.remove(areaId)
    }

    /**
     * The description of an offline area: the URL template MapLibre has to use to load
     * its tiles, the edge length of a tile and the encoding of the tiles.
     */
    fun area(areaId: Long): Area? {
        val currentPort = port ?: start() ?: return null
        val format = formats[areaId] ?: MbtilesStore.FORMAT_JPEG
        val extension = if (format == MbtilesStore.FORMAT_PNG) "png" else "jpg"
        val tileSize = runCatching { stores[areaId]?.tileSize() }.getOrNull() ?: 256
        return Area(
            url = "http://127.0.0.1:$currentPort/$areaId/{z}/{x}/{y}.$extension",
            tileSize = tileSize,
            format = format,
        )
    }

    /**
     * The URL template of the orientation (aspect) overlay, whose tiles are computed on
     * the device from the public elevation tiles. Starts the server when needed, so it
     * must not be called on the main thread.
     */
    fun aspectUrl(): String? {
        val currentPort = port ?: start() ?: return null
        return "http://127.0.0.1:$currentPort/$ASPECT_PATH/{z}/{x}/{y}.png"
    }

    /**
     * The URL template of the soil overlay, whose tiles are recoloured on the device from
     * the IGME lithological map. Starts the server when needed, so it must not be called
     * on the main thread.
     */
    fun soilUrl(): String? {
        val currentPort = port ?: start() ?: return null
        return "http://127.0.0.1:$currentPort/$SOIL_PATH/{z}/{x}/{y}.png"
    }

    /**
     * The URL template of the likely areas overlay, whose tiles are computed on the
     * device from the three other habitat sources; the search goes in the query string
     * (see [SuitabilitySearch.toQuery]). Starts the server when needed, so it must not be
     * called on the main thread.
     */
    fun suitabilityUrl(): String? {
        val currentPort = port ?: start() ?: return null
        return "http://127.0.0.1:$currentPort/$SUITABILITY_PATH/{z}/{x}/{y}.png"
    }

    /**
     * The URL template of the rainfall overlay, whose tiles are painted from the lattice
     * downloaded by the application. The window (in days) and the version of the lattice
     * go in the query string, so new data gets new URLs. Starts the server when needed,
     * so it must not be called on the main thread.
     */
    fun rainUrl(): String? {
        val currentPort = port ?: start() ?: return null
        return "http://127.0.0.1:$currentPort/$RAIN_PATH/{z}/{x}/{y}.png"
    }

    private fun acceptLoop(socket: ServerSocket) {
        // The orientation tiles may wait for downloads, so there are enough workers
        // to keep serving the offline tiles meanwhile.
        val executor = Executors.newFixedThreadPool(WORKER_THREADS)
        try {
            while (!socket.isClosed) {
                val client = socket.accept()
                executor.submit { handle(client) }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Tile server stopped: ${e.message}")
        } finally {
            executor.shutdownNow()
        }
    }

    private fun handle(client: Socket) {
        // The likely areas requests are handed over to their own pool, which closes them.
        var handedOver = false
        try {
            val socket = client
            val input = socket.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            // The habitat tiles carry their options in the query string.
            val path = parts[1].substringBefore('?')
            val query = parts[1].substringAfter('?', "")
            // Consume the headers, the tile requests never carry a body.
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
            }

            val segments = path.trim('/').split('/')
            if (segments.size != 4) {
                writeError(socket, 404)
                return
            }

            if (segments[0] == ASPECT_PATH) {
                serveAspect(socket, segments, query)
                return
            }
            if (segments[0] == SOIL_PATH) {
                serveSoil(socket, segments, query)
                return
            }
            if (segments[0] == SUITABILITY_PATH) {
                handedOver = true
                suitabilityExecutor.submit { socket.use { serveSuitability(it, segments, query) } }
                return
            }
            if (segments[0] == RAIN_PATH) {
                serveRain(socket, segments, query)
                return
            }

            val areaId = segments[0].toLongOrNull()
            val zoom = segments[1].toIntOrNull()
            val x = segments[2].toIntOrNull()
            val y = segments[3].substringBefore('.').toIntOrNull()
            if (areaId == null || zoom == null || x == null || y == null) {
                writeError(socket, 404)
                return
            }

            val store = stores[areaId]
            val data = store?.getTile(zoom, x, y)
            if (data == null) {
                Log.d(TAG, "Tile $areaId/$zoom/$x/$y is not in the pack")
                writeError(socket, 404)
            } else {
                val output = BufferedOutputStream(socket.getOutputStream())
                output.write(
                    ("HTTP/1.1 200 OK\r\n" +
                        "Content-Type: ${contentTypeOf(data)}\r\n" +
                        "Content-Length: ${data.size}\r\n" +
                        "Cache-Control: max-age=86400\r\n" +
                        "\r\n").toByteArray(),
                )
                output.write(data)
                output.flush()
                Log.d(TAG, "Tile $areaId/$zoom/$x/$y served (${data.size} bytes)")
            }
        } finally {
            if (!handedOver) runCatching { client.close() }
        }
    }

    private fun serveSuitability(socket: Socket, segments: List<String>, query: String) {
        val zoom = segments[1].toIntOrNull()
        val x = segments[2].toIntOrNull()
        val y = segments[3].substringBefore('.').toIntOrNull()
        val search = SuitabilitySearch.fromQuery(query)
        val data = if (zoom != null && x != null && y != null && search != null) {
            runCatching { suitabilityTiles.tile(zoom, x, y, search) }
                .onFailure { Log.w(TAG, "Could not compute the likely areas tile $zoom/$x/$y", it) }
                .getOrNull()
        } else {
            null
        }
        writeHabitatTile(socket, data)
    }

    private fun serveRain(socket: Socket, segments: List<String>, query: String) {
        val zoom = segments[1].toIntOrNull()
        val x = segments[2].toIntOrNull()
        val y = segments[3].substringBefore('.').toIntOrNull()
        // The accumulation window in days; anything unexpected falls back to the default.
        val window = queryParameter(query, "w")?.toIntOrNull()?.takeIf { it > 0 } ?: 7
        val data = if (zoom != null && x != null && y != null) {
            runCatching { rainTiles.tile(zoom, x, y, window) }
                .onFailure { Log.w(TAG, "Could not paint the rainfall tile $zoom/$x/$y", it) }
                .getOrNull()
        } else {
            null
        }
        writeHabitatTile(socket, data)
    }

    private fun serveAspect(socket: Socket, segments: List<String>, query: String) {
        val zoom = segments[1].toIntOrNull()
        val x = segments[2].toIntOrNull()
        val y = segments[3].substringBefore('.').toIntOrNull()
        val classes = AspectHatch.decode(queryParameter(query, "c"))
        val data = if (zoom != null && x != null && y != null) {
            runCatching { aspectTiles.tile(zoom, x, y, classes) }
                .onFailure { Log.w(TAG, "Could not compute the aspect tile $zoom/$x/$y", it) }
                .getOrNull()
        } else {
            null
        }
        writeHabitatTile(socket, data)
    }

    private fun serveSoil(socket: Socket, segments: List<String>, query: String) {
        val zoom = segments[1].toIntOrNull()
        val x = segments[2].toIntOrNull()
        val y = segments[3].substringBefore('.').toIntOrNull()
        val mask = SoilClassifier.decodeMask(queryParameter(query, "m"))
            ?: SoilClassifier.allVisible(HabitatSources.soilLegend.size)
        val data = if (zoom != null && x != null && y != null) {
            runCatching { soilTiles.tile(zoom, x, y, mask) }
                .onFailure { Log.w(TAG, "Could not produce the soil tile $zoom/$x/$y", it) }
                .getOrNull()
        } else {
            null
        }
        writeHabitatTile(socket, data)
    }

    private fun queryParameter(query: String, name: String): String? = query.split('&')
        .firstOrNull { it.substringBefore('=') == name }
        ?.substringAfter('=', "")

    private fun writeHabitatTile(socket: Socket, data: ByteArray?) {
        if (data == null) {
            // The tile could not be produced, usually because the elevation or soil
            // download failed. A 500 (never a cached 404) tells MapLibre the tile is
            // only unavailable for the moment, so it asks for it again the next time
            // it is visible instead of keeping the hole.
            writeError(socket, 500)
            return
        }
        val output = BufferedOutputStream(socket.getOutputStream())
        output.write(
            ("HTTP/1.1 200 OK\r\n" +
                "Content-Type: image/png\r\n" +
                "Content-Length: ${data.size}\r\n" +
                "Cache-Control: max-age=604800\r\n" +
                "\r\n").toByteArray(),
        )
        output.write(data)
        output.flush()
    }

    /**
     * The content type of a tile, detected from its first bytes.
     *
     * The detection is used instead of the declared format of the pack because a pack
     * may mix encodings: the ICGC orthophotography is served as JPEG while its
     * topographic counterpart is served as PNG.
     */
    private fun contentTypeOf(data: ByteArray): String = when {
        data.size >= 3 &&
            data[0] == 0xFF.toByte() &&
            data[1] == 0xD8.toByte() &&
            data[2] == 0xFF.toByte() -> "image/jpeg"

        data.size >= 8 &&
            data[0] == 0x89.toByte() &&
            data[1] == 0x50.toByte() &&
            data[2] == 0x4E.toByte() &&
            data[3] == 0x47.toByte() -> "image/png"

        else -> "image/jpeg"
    }

    private fun writeError(socket: Socket, code: Int) {        runCatching {
            val body = (if (code == 500) "Tile not available" else "Not found").toByteArray()
            val output = BufferedOutputStream(socket.getOutputStream())
            output.write(
                ("HTTP/1.1 $code ${if (code == 500) "Internal Server Error" else "Not Found"}\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: ${body.size}\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "\r\n").toByteArray(),
            )
            output.write(body)
            output.flush()
        }
    }

    companion object {
        /**
         * The URL template of the likely areas overlay on the same server as the URL
         * template of the orientation overlay ([aspectUrl]).
         */
        fun suitabilityUrlFor(aspectUrl: String): String =
            aspectUrl.replace("/$ASPECT_PATH/", "/$SUITABILITY_PATH/")

        private const val TAG = "LocalTileServer"

        /** The first path segment of the orientation overlay tiles. */
        private const val ASPECT_PATH = "aspect"

        /** The first path segment of the soil overlay tiles. */
        private const val SOIL_PATH = "soil"

        /** The first path segment of the likely areas overlay tiles. */
        private const val SUITABILITY_PATH = "suitability"

        /** The first path segment of the rainfall overlay tiles. */
        private const val RAIN_PATH = "rain"
        private const val WORKER_THREADS = 8
        private const val SUITABILITY_THREADS = 3
    }
}
