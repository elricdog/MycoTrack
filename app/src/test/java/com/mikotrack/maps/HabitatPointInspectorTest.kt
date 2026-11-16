package com.mikotrack.maps

import com.mikotrack.ui.mushrooms.HabitatPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan
import kotlin.math.floor

/**
 * Tests of the habitat values at a point: parsing of the GetFeatureInfo answers of the
 * MITECO and IGME services (real answers captured from the servers) and the terrain
 * computed from an elevation grid.
 */
class HabitatPointInspectorTest {

    // --- Forest map (MITECO GeoServer, INFO_FORMAT=application/json with PROPERTYNAME) ---

    private val holmOakResponse = """{"type":"FeatureCollection","features":[{"type":"Feature","id":"ff_form_arbolada.410489","geometry":null,"properties":{"id_supraforarb":18,"descr_supraforarb":"Encinares","descr_forarb":"Encinares (Quercus ilex)","nom_sp1":"Quercus ilex ","nm_o1":9,"nom_sp2":"Juniperus communis","nm_o2":1,"nom_sp3":"Sin especie arbórea","nm_o3":0,"nm_fccarb":20}}],"totalFeatures":"unknown","numberReturned":1,"timeStamp":"2026-11-16T00:22:54.615Z","crs":null}"""

    private val beechResponse = """{"type":"FeatureCollection","features":[{"type":"Feature","id":"ff_form_arbolada.811964","geometry":null,"properties":{"id_supraforarb":1,"descr_supraforarb":"Hayedos","descr_forarb":"Hayedos (Fagus sylvatica)","nom_sp1":"Fagus sylvatica","nm_o1":10,"nom_sp2":"Sin especie arbórea","nm_o2":0,"nom_sp3":"Sin especie arbórea","nm_o3":0,"nm_fccarb":90}}],"totalFeatures":"unknown","numberReturned":1,"timeStamp":"2026-11-16T00:22:55.297Z","crs":null}"""

    private val mountainPineResponse = """{"type":"FeatureCollection","features":[{"type":"Feature","id":"ff_form_arbolada.751549","geometry":null,"properties":{"id_supraforarb":22,"descr_supraforarb":"Pinar de pino negro","descr_forarb":"Pinar de pino negro (Pinus uncinata)","nom_sp1":"Pinus uncinata","nm_o1":9,"nom_sp2":"Sin especie arbórea","nm_o2":0,"nom_sp3":"Sin especie arbórea","nm_o3":0,"nm_fccarb":80}}],"totalFeatures":"unknown","numberReturned":1,"timeStamp":"2026-11-16T00:22:55.552Z","crs":null}"""

    private val noForestResponse = """{"type":"FeatureCollection","features":[],"totalFeatures":"unknown","numberReturned":0,"timeStamp":"2026-11-16T00:22:54.914Z","crs":null}"""

    // --- Lithological map (IGME ArcGIS WMS, INFO_FORMAT=text/xml) ---

    private val acidResponse = """<?xml version="1.0" encoding="UTF-8"?>

<FeatureInfoResponse xmlns:esri_wms="http://www.esri.com/wms" xmlns="http://www.esri.com/wms">
<FIELDS OBJECTID="4691" Shape="Polígono" Identificador="45" Litologia="Cuarcitas, pizarras, areniscas y calizas"></FIELDS>
</FeatureInfoResponse>
"""

    private val basicResponse = """<?xml version="1.0" encoding="UTF-8"?>

<FeatureInfoResponse xmlns:esri_wms="http://www.esri.com/wms" xmlns="http://www.esri.com/wms">
<FIELDS OBJECTID="4024" Shape="Polígono" Identificador="79" Litologia="Dolomías, calizas y margas. Areniscas"></FIELDS>
</FeatureInfoResponse>
"""

    private val outsideResponse = """<?xml version="1.0" encoding="UTF-8"?>

<FeatureInfoResponse xmlns:esri_wms="http://www.esri.com/wms" xmlns="http://www.esri.com/wms"></FeatureInfoResponse>
"""

    @Test
    fun `forest answer gives the formation, the species and the canopy cover`() {
        val forest = HabitatPointMath.parseTreesFeatureInfo(holmOakResponse) as TreesAtPoint.Forest
        assertEquals(18, forest.formationId)
        assertEquals("Encinares", forest.formationName)
        assertEquals("holm_oak", forest.legend?.key)
        assertEquals(listOf(TreeShare("Quercus ilex", 90), TreeShare("Juniperus communis", 10)), forest.species)
        assertEquals(20, forest.canopyCoverPercent)
    }

    @Test
    fun `species without occupation are left out`() {
        val beech = HabitatPointMath.parseTreesFeatureInfo(beechResponse) as TreesAtPoint.Forest
        assertEquals("beech", beech.legend?.key)
        assertEquals(listOf(TreeShare("Fagus sylvatica", 100)), beech.species)
        assertEquals(90, beech.canopyCoverPercent)

        val pine = HabitatPointMath.parseTreesFeatureInfo(mountainPineResponse) as TreesAtPoint.Forest
        assertEquals("mountain_pine", pine.legend?.key)
    }

    @Test
    fun `a formation outside the legend keeps the name of the service`() {
        val response = holmOakResponse.replace("\"id_supraforarb\":18", "\"id_supraforarb\":999")
        val forest = HabitatPointMath.parseTreesFeatureInfo(response) as TreesAtPoint.Forest
        assertNull(forest.legend)
        assertEquals("Encinares", forest.formationName)
    }

    @Test
    fun `empty feature collection means no forest`() {
        assertEquals(TreesAtPoint.NoForest, HabitatPointMath.parseTreesFeatureInfo(noForestResponse))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an error page is not taken as no forest`() {
        HabitatPointMath.parseTreesFeatureInfo("<html><body>Service unavailable</body></html>")
    }

    @Test
    fun `forest pixel colour matches the legend`() {
        assertEquals(TreesAtPoint.NoForest, HabitatPointMath.treesFromPixel(0x00FFFFFF))
        val forest = HabitatPointMath.treesFromPixel(0xFF00A639.toInt()) as TreesAtPoint.Forest
        assertEquals("holm_oak", forest.legend?.key)
    }

    @Test
    fun `lithology answers give the lithology and its group`() {
        val acid = HabitatPointMath.parseSoilFeatureInfo(acidResponse) as SoilAtPoint.Lithology
        assertEquals("Cuarcitas, pizarras, areniscas y calizas", acid.name)
        assertEquals("quartzites_slates", acid.lithology?.key)
        assertEquals(SoilGroup.ACID, acid.lithology?.group)

        val basic = HabitatPointMath.parseSoilFeatureInfo(basicResponse) as SoilAtPoint.Lithology
        assertEquals("dolomites_marls", basic.lithology?.key)
        assertEquals(SoilGroup.BASIC, basic.lithology?.group)
    }

    @Test
    fun `lithology answer outside the map`() {
        assertEquals(SoilAtPoint.OutsideMap, HabitatPointMath.parseSoilFeatureInfo(outsideResponse))
    }

    @Test
    fun `lithology names are unescaped and unknown ones are kept`() {
        val response = acidResponse.replace(
            "Cuarcitas, pizarras, areniscas y calizas",
            "Calizas &amp; margas &quot;nuevas&quot;",
        )
        val lithology = HabitatPointMath.parseSoilFeatureInfo(response) as SoilAtPoint.Lithology
        assertEquals("Calizas & margas \"nuevas\"", lithology.name)
        assertNull(lithology.lithology)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an error page is not taken as outside the lithological map`() {
        HabitatPointMath.parseSoilFeatureInfo("<html><body>Error</body></html>")
    }

    @Test
    fun `every lithology of the service maps to the legend colour of the same name`() {
        // The names and colours of the renderer of the IGME layer.
        val renderer = mapOf(
            "Gravas, conglomerados, arenas y limos" to 0xFFE6BF,
            "Conglomerados, areniscas y lutitas" to 0xCC5900,
            "Conglomerados, areniscas, arcillas y calizas. Evaporitas" to 0xE6B300,
            "Areniscas, conglomerados, arcillas; calizas y evaporitas" to 0xE6B300,
            "Conglomerados, areniscas, calizas, yesos y arcillas versicolores" to 0xFFBEBE,
            "Conglomerados, areniscas, pizarras y calizas. Carbón" to 0x9C9C9C,
            "Areniscas, pizarras y calizas" to 0xFFDB00,
            "Calizas detríticas, calcarenitas, margas, arcillas y calizas" to 0xFFD37F,
            "Calizas, dolomías y margas. Areniscas y conglomerados" to 0x73DFFF,
            "Dolomías, calizas y margas. Areniscas" to 0x00A9E6,
            "Cuarcitas, pizarras, areniscas y calizas" to 0x98E600,
            "Pizarras, grauwackas, cuarictas y conglomerados" to 0xFF73DF,
            "Micaesquistos, filitas, areniscas, mármoles, calizas, dolomías y margas" to 0x0084A8,
            "Gneisses" to 0xA900E6,
            "Migmatitas, mármoles y granitoides indiferenciados" to 0xD95900,
            "Vulcanitas y rocas volcanoclásticas" to 0x0042CC,
            "Otros granitoides" to 0xFF0080,
            "Granitoides de dos micas" to 0xE60000,
            "Serpentinitas y peridotitas. Rocas básicas y ultrabásicas" to 0x0070FF,
            "Rocas volcánicas félsicas" to 0xFFAB00,
            "Rocas volcánicas máficas y félsicas" to 0xA3FF73,
            "Rocas volcánicas máficas" to 0xA8A800,
            "Rocas plutónicas félsicas" to 0xFF5400,
            "Rocas plutónicas máficas y félsicas" to 0xC400FF,
        )
        renderer.forEach { (name, color) ->
            assertEquals(name, color, HabitatPointMath.lithologyOf(name)?.sourceColor)
        }
    }

    // --- Terrain ---

    private val cell = 10.0

    private fun terrain(height: (column: Int, row: Int) -> Double): TerrainAtPoint =
        HabitatPointMath.terrainAt({ column, row -> height(column, row).toFloat() }, 50, 50, cell)

    @Test
    fun `slope rising to the south faces north`() {
        // Rows go from north to south: 10 % uphill towards the south.
        val result = terrain { _, row -> 1000.0 + 0.1 * row * cell }
        assertEquals(AspectClass.NORTH, result.aspectClass)
        assertEquals(0.0, result.aspectDegrees, 0.01)
        assertEquals(10.0, result.slopePercent, 0.01)
        assertEquals(Math.toDegrees(atan(0.1)), result.slopeDegrees, 0.01)
        assertEquals(1000.0 + 0.1 * 50 * cell, result.elevationMeters, 0.01)
    }

    @Test
    fun `slope rising to the west faces east`() {
        val result = terrain { column, _ -> 2000.0 - 0.5 * column * cell }
        assertEquals(AspectClass.NEUTRAL, result.aspectClass)
        assertEquals(90.0, result.aspectDegrees, 0.01)
        assertEquals(50.0, result.slopePercent, 0.01)
        assertEquals(26.565, result.slopeDegrees, 0.01)
    }

    @Test
    fun `slope rising to the north faces south`() {
        val result = terrain { _, row -> 500.0 - 0.3 * row * cell }
        assertEquals(AspectClass.SOUTH, result.aspectClass)
        assertEquals(180.0, result.aspectDegrees, 0.01)
    }

    @Test
    fun `slope rising to the north east faces south west`() {
        val result = terrain { column, row -> 0.2 * column * cell - 0.2 * row * cell }
        assertEquals(225.0, result.aspectDegrees, 0.01)
        assertEquals(Math.toDegrees(atan(0.2 * Math.sqrt(2.0))), result.slopeDegrees, 0.01)
    }

    @Test
    fun `gentle slope is flat`() {
        val result = terrain { column, row -> 300.0 + 0.02 * column * cell + 0.01 * row * cell }
        assertEquals(AspectClass.FLAT, result.aspectClass)
        assertTrue(result.slopeDegrees < AspectClassifier.FLAT_SLOPE_DEGREES)
    }

    @Test
    fun `global pixel of a point`() {
        val (x, y) = HabitatPointMath.globalPixel(0.0, 0.0, 0, 256)
        assertEquals(128.0, x, 1e-6)
        assertEquals(128.0, y, 1e-6)
        // Montseny, at zoom 15: the tile must agree with the usual slippy map formula.
        val (px, py) = HabitatPointMath.globalPixel(41.79, 2.39, 15, 256)
        val n = 1 shl 15
        val tileX = floor((2.39 + 180.0) / 360.0 * n).toInt()
        val latitude = Math.toRadians(41.79)
        val tileY = floor((1 - Math.log(Math.tan(latitude) + 1 / Math.cos(latitude)) / Math.PI) / 2 * n).toInt()
        assertEquals(tileX, (px / 256).toInt())
        assertEquals(tileY, (py / 256).toInt())
    }

    // --- Likely areas ---

    @Test
    fun `score combines the three inputs and unknown ones count as neutral`() {
        val preference = HabitatPreference(
            trees = mapOf("holm_oak" to 1f),
            soil = mapOf("acid" to 1f),
            aspect = mapOf("n" to 1f),
        )
        val north = terrain { _, row -> 0.2 * row * cell }
        val holmOak = PointValue.Ready(HabitatPointMath.parseTreesFeatureInfo(holmOakResponse))
        val acid = HabitatPointMath.parseSoilFeatureInfo(acidResponse)
        assertEquals(1f, HabitatPointMath.score(preference, north, holmOak, acid), 1e-6f)

        val unknown = HabitatPointMath.score(preference, null, PointValue.Failed, null)
        assertEquals(SuitabilityScorer.NEUTRAL, unknown, 1e-6f)

        // A forest species does not fruit without forest.
        val noForest = PointValue.Ready(TreesAtPoint.NoForest)
        assertEquals(0f, HabitatPointMath.score(preference, north, noForest, acid), 1e-6f)
    }

    @Test
    fun `score levels`() {
        assertEquals(SuitabilityAtPoint.Level.LOW, HabitatPointMath.suitabilityLevel(0.2f))
        assertEquals(SuitabilityAtPoint.Level.MEDIUM, HabitatPointMath.suitabilityLevel(0.5f))
        assertEquals(SuitabilityAtPoint.Level.HIGH, HabitatPointMath.suitabilityLevel(0.85f))
    }
}
