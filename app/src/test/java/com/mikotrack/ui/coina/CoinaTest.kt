package com.mikotrack.ui.coina

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Validates the recipe bank shipped in the assets of the kitchen section. */
class CoinaTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val assetRoot = File("src/main/assets/coina")

    @Test
    fun `recipe bank is complete and consistent`() {
        val text = File(assetRoot, "recipes_ca.json").readText(Charsets.UTF_8)
        val recipes = json.decodeFromString<List<Recipe>>(text)

        assertEquals(20, recipes.size)
        assertEquals(20, recipes.map { it.id }.distinct().size)
        assertEquals(20, recipes.map { it.name }.distinct().size)

        recipes.forEach { recipe ->
            assertTrue("recipe ${recipe.id} needs a name", recipe.name.isNotBlank())
            assertTrue("recipe ${recipe.id} needs an emoji", recipe.emoji.isNotBlank())
            assertTrue("recipe ${recipe.id} needs history", recipe.history.length > 40)
            assertTrue("recipe ${recipe.id} needs ingredients", recipe.ingredients.size >= 4)
            assertTrue("recipe ${recipe.id} needs steps", recipe.steps.size >= 4)
            assertTrue("recipe ${recipe.id} needs links", recipe.links.isNotEmpty())
            recipe.links.forEach { link ->
                assertTrue("link of ${recipe.id} must be https", link.url.startsWith("https://"))
            }
            assertTrue("recipe ${recipe.id} needs a time", recipe.timeMin in 5..600)
            assertTrue("recipe ${recipe.id} needs servings", recipe.servings in 1..20)
            assertTrue("recipe ${recipe.id} needs an image", recipe.image.endsWith(".jpg"))
            assertTrue(
                "image of ${recipe.id} must ship in the assets",
                File(assetRoot, "images/${recipe.image}").isFile,
            )
            assertTrue("credit of ${recipe.id} needs an author", recipe.credit.author.isNotBlank())
            assertTrue(
                "credit of ${recipe.id} needs a licence url",
                recipe.credit.licenceUrl.startsWith("https://"),
            )
        }
    }
}
