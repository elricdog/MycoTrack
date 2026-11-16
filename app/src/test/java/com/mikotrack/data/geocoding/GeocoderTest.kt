package com.mikotrack.data.geocoding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeocoderTest {

    private val searchJson = """
        [
          {"place_id":1,"lat":"41.6083","lon":"2.2877","category":"boundary","type":"administrative",
           "addresstype":"town","name":"Granollers","display_name":"Granollers, Vallès Oriental, Barcelona, Catalunya, España",
           "address":{"town":"Granollers","county":"Vallès Oriental","province":"Barcelona","state":"Catalunya","country":"España"},
           "boundingbox":["41.5799","41.6232","2.2563","2.3149"]},
          {"place_id":2,"lat":"41.7744","lon":"2.4639","category":"natural","type":"peak","addresstype":"peak",
           "name":"","display_name":"Turó de l'Home, Fogars de Montclús, Vallès Oriental",
           "address":{"village":"Fogars de Montclús","county":"Vallès Oriental"}},
          {"place_id":3,"lat":"bad","lon":"2.0","name":"Broken"}
        ]
    """.trimIndent()

    private val reverseJson = """
        {"place_id":9,"lat":"41.6","lon":"2.29","category":"highway","type":"track","name":"Camí de la Font",
         "display_name":"Camí de la Font, Can Riera, Cànoves i Samalús, Vallès Oriental, Barcelona, Catalunya, 08445, España",
         "address":{"road":"Camí de la Font","hamlet":"Can Riera","village":"Samalús","municipality":"Cànoves i Samalús",
                    "county":"Vallès Oriental","province":"Barcelona","state":"Catalunya","postcode":"08445",
                    "country":"España","country_code":"es"}}
    """.trimIndent()

    @Test
    fun parsesSearchResults() {
        val results = NominatimParser.parseSearch(searchJson)
        assertEquals(2, results.size)
        val town = results[0]
        assertEquals("Granollers", town.name)
        assertEquals("town", town.type)
        assertEquals("Vallès Oriental", town.locality)
        assertEquals(41.6083, town.latitude, 1e-9)
        assertEquals(PlaceBounds(41.5799, 2.2563, 41.6232, 2.3149), town.bounds)
        val peak = results[1]
        assertEquals("Turó de l'Home", peak.name)
        assertEquals("Fogars de Montclús", peak.locality)
        assertNull(peak.bounds)
    }

    @Test
    fun parsesEmptySearch() {
        assertTrue(NominatimParser.parseSearch("[]").isEmpty())
    }

    @Test
    fun parsesReverseAddress() {
        val address = NominatimParser.parseReverse(reverseJson)!!
        assertEquals("Camí de la Font", address.road)
        assertEquals("Samalús", address.locality)
        assertEquals("Cànoves i Samalús", address.municipality)
        assertEquals("Vallès Oriental", address.county)
        assertEquals("Barcelona", address.province)
        assertEquals("Catalunya", address.state)
        assertEquals("08445", address.postcode)
        assertEquals("España", address.country)
        assertEquals("Can Riera", address.nearbyFeature)
    }

    @Test
    fun reverseErrorMeansNoAddress() {
        assertNull(NominatimParser.parseReverse("""{"error":"Unable to geocode"}"""))
    }

    @Test
    fun buildsUrls() {
        val search = Geocoder.searchUrl(
            "https://example.org",
            " Sant Llorenç ",
            "ca",
            ViewBox(west = 2.0, south = 41.0, east = 3.0, north = 42.0),
        )
        assertEquals(
            "https://example.org/search?format=jsonv2&addressdetails=1&limit=10&q=Sant+Lloren%C3%A7" +
                "&accept-language=ca&viewbox=2.00000%2C42.00000%2C3.00000%2C41.00000",
            search,
        )
        assertEquals(
            "https://example.org/reverse?format=jsonv2&zoom=18&addressdetails=1&lat=41.600000&lon=2.290000&accept-language=es",
            Geocoder.reverseUrl("https://example.org", 41.6, 2.29, "es"),
        )
    }
}
