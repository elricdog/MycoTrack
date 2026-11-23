package com.mikotrack.ui.coina

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Attribution of a recipe photograph. */
@Serializable
data class RecipeCredit(
    val author: String,
    val licence: String,
    val licenceUrl: String,
    val sourceUrl: String,
)

/** An external link that explains a recipe (video, article...). */
@Serializable
data class RecipeLink(
    val label: String,
    val url: String,
)

/** A mushroom recipe as stored in the recipe bank JSON. */
@Serializable
data class Recipe(
    val id: String,
    val name: String,
    val emoji: String,
    /** File name inside the `coina/images/` asset folder. */
    val image: String,
    val history: String,
    val ingredients: List<String>,
    val steps: List<String>,
    val links: List<RecipeLink>,
    val timeMin: Int,
    val servings: Int,
    val credit: RecipeCredit,
) {
    val imageUri: String get() = "file:///android_asset/coina/images/$image"
}

/** Loads the recipe bank from the app assets. */
object CoinaCatalog {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The recipes in [language], falling back to Catalan. The bank is curated in
     * Catalan; the interface around it is translated in the settings language.
     */
    fun load(context: Context, language: String): List<Recipe> {
        for (lang in listOf(language, "ca")) {
            val parsed = runCatching {
                context.assets.open("coina/recipes_$lang.json")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                    .let { json.decodeFromString<List<Recipe>>(it) }
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return emptyList()
    }
}
