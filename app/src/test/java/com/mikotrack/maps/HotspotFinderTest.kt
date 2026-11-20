package com.mikotrack.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests of the hotspot election of a multi-species search. */
class HotspotFinderTest {

    private fun candidate(
        latitude: Double,
        longitude: Double,
        score: Float,
        species: List<Pair<String, Float>> = listOf("a" to score),
    ) = HotspotCandidate(latitude, longitude, score, species)

    @Test
    fun `the scan zoom keeps tiles close to the radius`() {
        // A 1 km radius is scanned about deeper than a 16 km one.
        val near = HotspotFinder.chooseZoom(42.0, 1_000.0)
        val far = HotspotFinder.chooseZoom(42.0, 16_000.0)
        assertTrue(near > far)
        assertTrue(near in HotspotFinder.MIN_ZOOM..HotspotFinder.MAX_ZOOM)
        // Extreme radii stay inside the allowed range.
        assertEquals(HotspotFinder.MAX_ZOOM, HotspotFinder.chooseZoom(42.0, 10.0))
        assertEquals(HotspotFinder.MIN_ZOOM, HotspotFinder.chooseZoom(42.0, 5_000_000.0))
    }

    @Test
    fun `the tile range covers the circle`() {
        val (rows, columns) = HotspotFinder.tileRange(12, 42.0, 2.0, 5_000.0)
        assertTrue(rows.first <= rows.last)
        assertTrue(columns.first <= columns.last)
        // The tile of the centre is inside the range.
        val tiles = 1 shl 12
        val centreColumn = ((2.0 + 180.0) / 360.0 * tiles).toInt()
        assertTrue(centreColumn in columns)
        // Zoom 0 has a single row and column.
        assertEquals(0..0, HotspotFinder.tileRange(0, 42.0, 2.0, 1_000.0).first)
        assertEquals(0..0, HotspotFinder.tileRange(0, 42.0, 2.0, 1_000.0).second)
    }

    @Test
    fun `the hotspots are the best separated candidates`() {
        val best = candidate(42.0, 2.0, 0.9f)
        val near = candidate(42.0001, 2.0, 0.85f) // about 11 m from the best
        val second = candidate(42.05, 2.0, 0.8f) // about 5.5 km away
        val weak = candidate(41.9, 2.05, 0.3f) // under the minimum score
        val picked = HotspotFinder.pick(listOf(weak, near, second, best), minSeparationMeters = 300.0)
        assertEquals(2, picked.size)
        assertEquals(best, picked[0])
        assertEquals(second, picked[1])
    }

    @Test
    fun `the hotspots never exceed the limit`() {
        val spread = (0..10).map { candidate(42.0 + it * 0.1, 2.0 + it * 0.1, 0.9f - it * 0.03f) }
        val picked = HotspotFinder.pick(spread, minSeparationMeters = 300.0, limit = 3)
        assertEquals(3, picked.size)
        assertEquals(spread[0], picked[0])
    }

    @Test
    fun `a hotspot names the species that suit it`() {
        val spot = candidate(
            42.0, 2.0, 0.95f,
            species = listOf(
                "rossinyol" to 0.95f,
                "ou_de_bord" to 0.7f,
                "pastanac" to 0.9f,
                "llet" to 0.4f, // under the species minimum
                "mascana" to 0.6f, // well behind the best score
            ),
        )
        val species = HotspotFinder.speciesAt(spot)
        // ou_de_bord (0.7) is more than 0.2 behind the best score (0.95) and left out.
        assertEquals(listOf("rossinyol" to 0.95f, "pastanac" to 0.9f), species)
        assertTrue(species.size <= HotspotFinder.MAX_SPECIES)
    }

    @Test
    fun `a multi species search round-trips through the tile query`() {
        val search = SuitabilitySearch("a,b,c", "Rovell, Ou bord", 42.12345, 2.54321, 5_000)
        assertEquals(listOf("a", "b", "c"), search.speciesIds)
        assertTrue(search.isMultiSpecies)
        val restored = SuitabilitySearch.fromQuery(search.toQuery())
        assertEquals(search.speciesIds, restored?.speciesIds)
        assertEquals(42.12345, restored?.latitude ?: 0.0, 1e-4)
        assertEquals(5_000, restored?.radiusMeters)
        // A single species search is not multi.
        assertTrue(!SuitabilitySearch("a", "A", 42.0, 2.0, 1_000).isMultiSpecies)
    }

    @Test
    fun `the probability is shown as a whole percentage`() {
        assertEquals(93, HotspotFinder.percent(0.934f))
    }
}
