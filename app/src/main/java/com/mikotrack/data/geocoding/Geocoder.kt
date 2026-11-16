package com.mikotrack.data.geocoding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** A place found by a search. [bounds] is `null` when the service gave no extent. */
data class PlaceResult(
    val name: String,
    val type: String?,
    val locality: String?,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val bounds: PlaceBounds?,
)

data class PlaceBounds(
    val minLatitude: Double,
    val minLongitude: Double,
    val maxLatitude: Double,
    val maxLongitude: Double,
)

/** The address of a point, as given by a reverse geocoding. Every part may be missing. */
data class PlaceAddress(
    val road: String?,
    val houseNumber: String?,
    val locality: String?,
    val municipality: String?,
    val county: String?,
    val province: String?,
    val state: String?,
    val postcode: String?,
    val country: String?,
    /** The nearest named place or natural feature (a peak, a hamlet, a wood...). */
    val nearbyFeature: String?,
    val displayName: String?,
)

/** A camera area used to prefer the results that are close to what the user sees. */
data class ViewBox(val west: Double, val south: Double, val east: Double, val north: Double)

/** Why a geocoding request failed. */
class GeocodingException(val kind: Kind, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Kind { NETWORK, SERVER }
}

@Serializable
internal data class NominatimPlace(
    val lat: String? = null,
    val lon: String? = null,
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val category: String? = null,
    val type: String? = null,
    @SerialName("addresstype") val addressType: String? = null,
    @SerialName("boundingbox") val boundingBox: List<String>? = null,
    val address: Map<String, String>? = null,
)

/** Turns the answers of Nominatim (format `jsonv2`) into the models of the application. */
object NominatimParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val LOCALITY_KEYS = listOf("village", "town", "city", "hamlet", "suburb", "municipality")
    private val MUNICIPALITY_KEYS = listOf("municipality", "city", "town", "village")
    private val ROAD_KEYS = listOf(
        "road", "track", "path", "footway", "pedestrian", "cycleway", "bridleway", "steps", "highway",
    )
    private val FEATURE_KEYS = listOf(
        "peak", "natural", "mountain_pass", "water", "wood", "forest", "valley", "isolated_dwelling",
        "hamlet", "locality", "farm", "leisure", "tourism", "amenity", "neighbourhood", "quarter",
    )
    private val NATURAL_CATEGORIES = setOf(
        "natural", "place", "leisure", "tourism", "landuse", "waterway", "mountain_pass",
    )

    fun parseSearch(body: String): List<PlaceResult> {
        val places = json.decodeFromString<List<NominatimPlace>>(body)
        return places.mapNotNull { place ->
            val latitude = place.lat?.toDoubleOrNull() ?: return@mapNotNull null
            val longitude = place.lon?.toDoubleOrNull() ?: return@mapNotNull null
            val displayName = place.displayName.orEmpty()
            val name = place.name?.takeIf { it.isNotBlank() }
                ?: displayName.substringBefore(',').trim().takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            PlaceResult(
                name = name,
                type = (place.addressType ?: place.type)?.replace('_', ' '),
                locality = place.address?.let { address ->
                    LOCALITY_KEYS.firstNotNullOfOrNull { key -> address[key]?.takeIf { it != name } }
                        ?: address["county"]?.takeIf { it != name }
                },
                displayName = displayName,
                latitude = latitude,
                longitude = longitude,
                bounds = parseBounds(place.boundingBox),
            )
        }
    }

    /** Returns `null` when no address is known for the point. */
    fun parseReverse(body: String): PlaceAddress? {
        val element = json.parseToJsonElement(body)
        if (element !is JsonObject || element.containsKey("error")) return null
        val place = json.decodeFromJsonElement(NominatimPlace.serializer(), element)
        val address = place.address.orEmpty()
        val road = ROAD_KEYS.firstNotNullOfOrNull { address[it] }
        val locality = LOCALITY_KEYS.firstNotNullOfOrNull { address[it] }
        val municipality = MUNICIPALITY_KEYS.firstNotNullOfOrNull { address[it] }
        val ownName = place.name?.takeIf {
            it.isNotBlank() && it != road && place.category in NATURAL_CATEGORIES
        }
        val feature = ownName ?: FEATURE_KEYS.firstNotNullOfOrNull { key ->
            address[key]?.takeIf { it != locality && it != road }
        }
        return PlaceAddress(
            road = road,
            houseNumber = address["house_number"],
            locality = locality,
            municipality = municipality?.takeIf { it != locality },
            county = address["county"],
            province = address["province"],
            state = address["state"],
            postcode = address["postcode"],
            country = address["country"],
            nearbyFeature = feature,
            displayName = place.displayName,
        )
    }

    /** Nominatim gives `[south, north, west, east]` as strings. */
    internal fun parseBounds(box: List<String>?): PlaceBounds? {
        if (box == null || box.size != 4) return null
        val values = box.map { it.toDoubleOrNull() ?: return null }
        return PlaceBounds(
            minLatitude = minOf(values[0], values[1]),
            maxLatitude = maxOf(values[0], values[1]),
            minLongitude = minOf(values[2], values[3]),
            maxLongitude = maxOf(values[2], values[3]),
        )
    }
}

/**
 * The geocoding of places with Nominatim (OpenStreetMap). The usage policy allows at
 * most one request per second and asks for an identifying User-Agent; the requests are
 * only sent when the user submits a search.
 */
class Geocoder(
    private val userAgent: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    suspend fun search(query: String, language: String, viewBox: ViewBox? = null): List<PlaceResult> {
        val body = get(searchUrl(baseUrl, query, language, viewBox))
        return runCatching { NominatimParser.parseSearch(body) }
            .getOrElse { throw GeocodingException(GeocodingException.Kind.SERVER, "Bad answer", it) }
    }

    suspend fun reverse(latitude: Double, longitude: Double, language: String): PlaceAddress? {
        val body = get(reverseUrl(baseUrl, latitude, longitude, language))
        return runCatching { NominatimParser.parseReverse(body) }
            .getOrElse { throw GeocodingException(GeocodingException.Kind.SERVER, "Bad answer", it) }
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        throttle()
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/json")
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw GeocodingException(GeocodingException.Kind.SERVER, "HTTP $code")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: GeocodingException) {
            throw e
        } catch (e: IOException) {
            throw GeocodingException(GeocodingException.Kind.NETWORK, e.message ?: "network", e)
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://nominatim.openstreetmap.org"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val MIN_INTERVAL_MS = 1_100L
        private const val SEARCH_LIMIT = 10

        private val throttleLock = Mutex()
        private var lastRequestAt = 0L

        /** Waits until a second has passed since the previous request of the process. */
        private suspend fun throttle() = throttleLock.withLock {
            val wait = lastRequestAt + MIN_INTERVAL_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()
        }

        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

        fun searchUrl(baseUrl: String, query: String, language: String, viewBox: ViewBox?): String = buildString {
            append(baseUrl).append("/search?format=jsonv2&addressdetails=1&limit=").append(SEARCH_LIMIT)
            append("&q=").append(encode(query.trim()))
            if (language.isNotBlank()) append("&accept-language=").append(encode(language))
            if (viewBox != null) {
                // Nominatim expects <x1>,<y1>,<x2>,<y2>; without "bounded" it only prefers it.
                val box = String.format(
                    Locale.US, "%.5f,%.5f,%.5f,%.5f",
                    viewBox.west, viewBox.north, viewBox.east, viewBox.south,
                )
                append("&viewbox=").append(encode(box))
            }
        }

        fun reverseUrl(baseUrl: String, latitude: Double, longitude: Double, language: String): String = buildString {
            append(baseUrl).append("/reverse?format=jsonv2&zoom=18&addressdetails=1")
            append(String.format(Locale.US, "&lat=%.6f&lon=%.6f", latitude, longitude))
            if (language.isNotBlank()) append("&accept-language=").append(encode(language))
        }

        /** A box of about [radiusKm] around a point, used to bias the search. */
        fun viewBoxAround(latitude: Double, longitude: Double, radiusKm: Double = 50.0): ViewBox {
            val dLat = radiusKm / 111.0
            val dLon = radiusKm / (111.0 * kotlin.math.cos(Math.toRadians(latitude)).coerceAtLeast(0.01))
            return ViewBox(
                west = (longitude - dLon).coerceAtLeast(-180.0),
                south = (latitude - dLat).coerceAtLeast(-90.0),
                east = (longitude + dLon).coerceAtMost(180.0),
                north = (latitude + dLat).coerceAtMost(90.0),
            )
        }
    }
}
