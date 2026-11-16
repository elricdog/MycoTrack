package com.mikotrack.ui.mushrooms

import com.mikotrack.maps.HabitatSources
import com.mikotrack.maps.SuitabilityScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the consistency of the bundled mushroom guide. */
class MushroomCatalogTest {

    private val assetDir = listOf("src/main/assets/mushrooms", "app/src/main/assets/mushrooms")
        .map(::File)
        .first { it.isDirectory }

    private val species = MushroomCatalog.parseSpecies(File(assetDir, "species.json").readText())

    @Test
    fun `every species has texts in every language`() {
        assertTrue(species.size >= 30)
        assertEquals(species.size, species.map { it.id }.toSet().size)
        MushroomCatalog.SUPPORTED_LANGUAGES.forEach { language ->
            val texts = MushroomCatalog.parseTexts(File(assetDir, "texts_$language.json").readText())
            assertEquals("Unknown ids in $language", species.map { it.id }.toSet(), texts.keys)
            texts.forEach { (id, text) ->
                listOf(text.commonName, text.culinary, text.habitat, text.season, text.lookAlikes).forEach {
                    assertTrue("$id has an empty text in $language", it.isNotBlank())
                }
            }
            assertEquals(species.size, MushroomCatalog.combine(species, texts).size)
        }
    }

    @Test
    fun `every species has a valid season, rating and credited photo`() {
        species.forEach { data ->
            assertTrue("${data.id} has no season", data.season.isNotEmpty())
            assertTrue("${data.id} has an invalid season", data.season.all { it.isValid })
            assertTrue("${data.id} has an invalid rating", data.edibility in 1..5)
            assertTrue("${data.id} has no image", File(assetDir, "images/${data.image}").isFile)
            with(data.imageCredit) {
                listOf(author, licence, licenceUrl, sourceUrl).forEach {
                    assertTrue("${data.id} has an incomplete credit", it.isNotBlank())
                }
                assertTrue(sourceUrl.startsWith("https://commons.wikimedia.org/"))
            }
        }
    }

    @Test
    fun `every species has a valid habitat preference`() {
        val treeKeys = HabitatSources.treeLegend.map { it.key }.toSet() + SuitabilityScorer.NO_FOREST_KEY
        species.forEach { data ->
            with(data.habitat) {
                assertTrue("${data.id} has no tree affinities", trees.isNotEmpty())
                assertTrue("${data.id} has unknown tree keys", treeKeys.containsAll(trees.keys))
                assertEquals("${data.id} has invalid soil keys", setOf("acid", "basic", "other"), soil.keys)
                assertEquals("${data.id} has invalid aspect keys", setOf("n", "s", "e"), aspect.keys)
                (trees.values + soil.values + aspect.values).forEach {
                    assertTrue("${data.id} has an affinity out of range", it in 0f..1f)
                }
            }
        }
    }

    @Test
    fun `the escarlet is a distinct species of the guide`() {
        val escarlet = species.single { it.id == "escarlet" }
        assertEquals("Hygrophorus russula", escarlet.scientificName)
        val pinetell = species.single { it.id == "pinetell" }
        assertTrue(escarlet.scientificName != pinetell.scientificName)
    }

    @Test
    fun `every gallery and look-alike photo is present, captioned and freely licensed`() {
        val allowedLicences = Regex("""^(Public domain|CC0 1\.0|CC BY(-SA)? \d\.\d)$""")
        val languages = listOf("ca", "es", "en")
        val lookAlikeTexts = languages.associateWith {
            MushroomCatalog.parseTexts(File(assetDir, "texts_$it.json").readText())
        }
        species.forEach { data ->
            assertTrue("${data.id} has ${data.gallery.size} gallery photos", data.gallery.size in 2..4)
            assertTrue("${data.id} has ${data.lookAlikeImages.size} look-alike photos", data.lookAlikeImages.size in 1..3)
            (data.gallery + data.lookAlikeImages).forEach { photo ->
                val where = "${data.id}/${photo.image}"
                assertTrue("$where is missing", File(assetDir, "images/${photo.image}").isFile)
                languages.forEach {
                    assertTrue("$where has no caption in $it", !photo.caption[it].isNullOrBlank())
                }
                with(photo.credit) {
                    listOf(author, licence, licenceUrl, sourceUrl).forEach {
                        assertTrue("$where has an incomplete credit", it.isNotBlank())
                    }
                    assertTrue("$where has a non-free licence: $licence", allowedLicences.matches(licence))
                    assertTrue(sourceUrl.startsWith("https://commons.wikimedia.org/"))
                }
            }
            data.gallery.forEach { assertTrue(it.image.startsWith("${data.id}_g")) }
            data.lookAlikeImages.forEach { photo ->
                val name = photo.scientificName
                assertTrue("${data.id}/${photo.image} has no scientific name", !name.isNullOrBlank())
                assertTrue(photo.image.startsWith("la_"))
                val genus = name!!.substringBefore(' ')
                languages.forEach {
                    assertTrue(
                        "$genus is not mentioned in the look-alikes of ${data.id} in $it",
                        lookAlikeTexts.getValue(it).getValue(data.id).lookAlikes.contains(genus),
                    )
                }
            }
        }
        val photos = species.flatMap { it.gallery }.map { it.image }
        assertEquals("Gallery photos are shared", photos.size, photos.toSet().size)
        val lookAlikes = species.flatMap { it.lookAlikeImages }
        lookAlikes.groupBy { it.image }.forEach { (image, uses) ->
            assertEquals("$image shows several species", 1, uses.map { it.scientificName }.toSet().size)
            assertEquals("$image has inconsistent toxicity", 1, uses.map { it.toxic }.toSet().size)
        }
    }

    @Test
    fun `a season can wrap around the new year`() {
        val winter = SeasonRange(from = 10, to = 1)
        assertTrue(12 in winter)
        assertTrue(1 in winter)
        assertFalse(2 in winter)
        assertFalse(9 in winter)
    }
}
