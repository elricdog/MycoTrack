package com.mikotrack.maps

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A place inside the radius of a likely areas search, with the score of the best of the
 * searched species and the score every species reaches in the cell (in the order the
 * species were searched).
 */
data class HotspotCandidate(
    val latitude: Double,
    val longitude: Double,
    val score: Float,
    val speciesScores: List<Pair<String, Float>>,
)

/**
 * One of the hotspots marked on the map: the places of a multi-species search where more
 * species of the selection reach a better score.
 *
 * @param rank the order of the hotspot, from 1, drawn in its marker.
 * @param speciesNames the species of the selection that suit the place, best first.
 */
data class Hotspot(
    val rank: Int,
    val latitude: Double,
    val longitude: Double,
    val score: Float,
    val speciesNames: List<String>,
)

/**
 * Pure logic of the hotspots of a multi-species search: the zoom level the habitat tiles
 * are scanned at and the election of the few best separated places among the candidates.
 */
object HotspotFinder {

    /** Candidates scoring less than this are not hotspots. */
    const val MIN_SCORE = 0.5f

    /** The species shown at a hotspot must reach at least this score in the place. */
    const val SPECIES_MIN_SCORE = 0.55f

    /** The maximum number of species named at a hotspot. */
    const val MAX_SPECIES = 3

    /** The coarsest zoom level the scan uses; its tiles are already very large. */
    const val MIN_ZOOM = 8

    /** The deepest scan level, which keeps the number of tiles to download moderate. */
    const val MAX_ZOOM = 12

    /** The meters per pixel of a Web Mercator tile at a zoom level and latitude. */
    fun pixelMeters(zoom: Int, latitude: Double): Double =
        156_543.03392804125 * cos(Math.toRadians(latitude)) / (1 shl zoom)

    /**
     * The zoom level to scan: its tiles of 256 pixels measure about the radius of the
     * search, so one to a handful of tiles cover the circle without losing detail.
     */
    fun chooseZoom(latitude: Double, radiusMeters: Double): Int {
        val radius = radiusMeters.toInt()
        val cosine = cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
        val zoom = floor(log2(256.0 * 156_543.03392804125 * cosine / max(1.0, radius.toDouble()))).toInt()
        return zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    /**
     * The tiles at [zoom] intersecting the circle of the search, as the range of rows and
     * the range of columns of the Web Mercator tile grid.
     */
    fun tileRange(
        zoom: Int,
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ): Pair<IntRange, IntRange> {
        val (southWest, northEast) = SuitabilityScorer.circleBounds(latitude, longitude, radiusMeters)
        val tiles = (1 shl zoom).toDouble()
        val last = tiles.toInt() - 1
        fun column(lon: Double) = floor((lon + 180.0) / 360.0 * tiles).toInt().coerceIn(0, last)
        fun row(lat: Double): Int {
            val radian = Math.toRadians(lat.coerceIn(-85.05, 85.05))
            val y = (1.0 - ln(Math.tan(radian) + 1.0 / cos(radian)) / Math.PI) / 2.0 * tiles
            return floor(y).toInt().coerceIn(0, last)
        }
        return row(northEast.latitude)..row(southWest.latitude) to
            column(southWest.longitude)..column(northEast.longitude)
    }

    /**
     * Elects up to [limit] hotspots among the candidates: the best scoring ones that are
     * at least [minSeparationMeters] apart, so the three marks do not crowd together in
     * the same forest.
     */
    fun pick(
        candidates: List<HotspotCandidate>,
        minScore: Float = MIN_SCORE,
        minSeparationMeters: Double,
        limit: Int = 3,
    ): List<HotspotCandidate> {
        val chosen = ArrayList<HotspotCandidate>(limit)
        for (candidate in candidates.asSequence().filter { it.score >= minScore }.sortedByDescending { it.score }) {
            if (chosen.size >= limit) break
            if (chosen.none {
                    SuitabilityScorer.distanceMeters(candidate.latitude, candidate.longitude, it.latitude, it.longitude) < minSeparationMeters
                }
            ) {
                chosen += candidate
            }
        }
        return chosen
    }

    /**
     * The species that suit a candidate, best first: the ones reaching at least
     * [SPECIES_MIN_SCORE] and not far behind the best score of the place.
     */
    fun speciesAt(candidate: HotspotCandidate): List<Pair<String, Float>> =
        candidate.speciesScores
            .filter { (id, score) -> id.isNotEmpty() && score >= SPECIES_MIN_SCORE && score >= candidate.score - 0.2f }
            .sortedByDescending { (_, score) -> score }
            .take(MAX_SPECIES)

    /** The probability of a hotspot as a rounded percentage, for the sheets. */
    fun percent(score: Float): Int = (score * 100f).roundToInt()
}
