package com.mikotrack.ui.mushrooms

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale

/** A range of months, from 1 (January) to 12 (December). It may wrap around the new year. */
@Serializable
data class SeasonRange(val from: Int, val to: Int) {
    val isValid: Boolean get() = from in 1..12 && to in 1..12

    operator fun contains(month: Int): Boolean =
        if (from <= to) month in from..to else month >= from || month <= to
}

/** The licence and origin of a photograph. */
@Serializable
data class ImageCredit(
    val author: String,
    val licence: String,
    val licenceUrl: String,
    val sourceUrl: String,
)

/**
 * Where a species tends to fruit, as affinities from 0 (never) to 1 (preferred) for the
 * classes of the habitat overlays. Classes that are not listed count as unknown and get
 * a neutral score; an empty map means the species does not depend on that factor.
 *
 * @param trees affinities keyed by the `HabitatSources.treeLegend` keys (e.g. `scots_pine`).
 * @param soil affinities keyed by `acid`, `basic` and `other` (the `SoilGroup`s, lowercase).
 * @param aspect affinities keyed by `n` (north-facing), `s` (south-facing) and `e` (east/west).
 */
@Serializable
data class HabitatPreference(
    val trees: Map<String, Float> = emptyMap(),
    val soil: Map<String, Float> = emptyMap(),
    val aspect: Map<String, Float> = emptyMap(),
)

/**
 * An extra photograph of the guide, stored in `images/`.
 *
 * @param caption short captions keyed by language (`ca`, `es`, `en`).
 * @param scientificName the species shown, for the photographs of look-alikes.
 * @param toxic whether the species shown is poisonous (look-alike galleries).
 */
@Serializable
data class GuideImage(
    val image: String,
    val credit: ImageCredit,
    val caption: Map<String, String> = emptyMap(),
    val scientificName: String? = null,
    val toxic: Boolean = false,
) {
    val uri: String get() = "file:///android_asset/${MushroomCatalog.ASSET_DIR}/images/$image"

    /** The caption in [language], falling back to English and then to any caption. */
    fun captionIn(language: String): String? =
        caption[language] ?: caption["en"] ?: caption.values.firstOrNull()
}

/** The language-independent data of a species, as stored in `species.json`. */
@Serializable
data class SpeciesData(
    val id: String,
    val scientificName: String,
    val image: String,
    val imageCredit: ImageCredit,
    val season: List<SeasonRange>,
    val edibility: Int,
    val habitat: HabitatPreference = HabitatPreference(),
    /** More photographs of the species: other angles, ages or varieties. */
    val gallery: List<GuideImage> = emptyList(),
    /** Photographs of the dangerous species it can be confused with. */
    val lookAlikeImages: List<GuideImage> = emptyList(),
)

@Serializable
private data class SpeciesFile(val species: List<SpeciesData>)

/** The translated texts of a species, as stored in `texts_xx.json`. */
@Serializable
data class SpeciesTexts(
    val commonName: String,
    val culinary: String,
    val habitat: String,
    val season: String,
    val lookAlikes: String,
)

/** A species of the guide with the texts in the current language. */
data class Mushroom(
    val data: SpeciesData,
    val texts: SpeciesTexts,
) {
    val id: String get() = data.id
    val commonName: String get() = texts.commonName
    val scientificName: String get() = data.scientificName
    val edibility: Int get() = data.edibility
    val imageUri: String get() = "file:///android_asset/${MushroomCatalog.ASSET_DIR}/images/${data.image}"

    fun isInSeason(month: Int): Boolean = data.season.any { month in it }

    /** Whether the species matches a free-text search, ignoring case and accents. */
    fun matches(query: String): Boolean {
        val needle = MushroomCatalog.normalize(query)
        if (needle.isEmpty()) return true
        return listOf(commonName, scientificName, id).any { MushroomCatalog.normalize(it).contains(needle) }
    }
}

/** Loads and caches the bundled mushroom guide. */
object MushroomCatalog {
    const val ASSET_DIR = "mushrooms"
    val SUPPORTED_LANGUAGES = listOf("en", "ca", "es")
    private const val DEFAULT_LANGUAGE = "en"

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, List<Mushroom>>()

    fun parseSpecies(content: String): List<SpeciesData> =
        json.decodeFromString(SpeciesFile.serializer(), content).species

    fun parseTexts(content: String): Map<String, SpeciesTexts> =
        json.decodeFromString<Map<String, SpeciesTexts>>(content)

    /** Joins the species with their texts; texts missing in [texts] are taken from [fallback]. */
    fun combine(
        species: List<SpeciesData>,
        texts: Map<String, SpeciesTexts>,
        fallback: Map<String, SpeciesTexts> = emptyMap(),
    ): List<Mushroom> = species.mapNotNull { data ->
        (texts[data.id] ?: fallback[data.id])?.let { Mushroom(data, it) }
    }

    fun languageOf(locale: Locale?): String =
        locale?.language?.takeIf { it in SUPPORTED_LANGUAGES } ?: DEFAULT_LANGUAGE

    /** Loads the guide in [language]. It reads assets, so call it off the main thread. */
    @Synchronized
    fun load(context: Context, language: String): List<Mushroom> {
        val lang = if (language in SUPPORTED_LANGUAGES) language else DEFAULT_LANGUAGE
        cache[lang]?.let { return it }
        val assets = context.assets
        fun read(name: String) =
            assets.open("$ASSET_DIR/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val species = parseSpecies(read("species.json"))
        val fallback = parseTexts(read("texts_$DEFAULT_LANGUAGE.json"))
        val texts = if (lang == DEFAULT_LANGUAGE) {
            fallback
        } else {
            runCatching { parseTexts(read("texts_$lang.json")) }.getOrDefault(fallback)
        }
        return combine(species, texts, fallback).also { cache[lang] = it }
    }

    fun normalize(text: String): String =
        Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}
