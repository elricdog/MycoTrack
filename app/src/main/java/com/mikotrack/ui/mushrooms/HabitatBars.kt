package com.mikotrack.ui.mushrooms

import androidx.annotation.StringRes
import com.mikotrack.R
import com.mikotrack.maps.HabitatSources
import com.mikotrack.maps.SoilGroup
import com.mikotrack.maps.SuitabilityScorer
import kotlin.math.roundToInt

/** A bar of the habitat chart of a species: an affinity from 0 to 1 for a habitat class. */
data class AffinityBar(
    val key: String,
    @StringRes val labelRes: Int,
    val color: Long,
    val value: Float,
) {
    val percent: Int get() = (value * 100f).roundToInt()
}

/** Builds the bars of the habitat chart from the [HabitatPreference] of a species. */
object HabitatBars {
    /** Tree affinities below this value are not worth a bar. */
    const val MIN_TREE_AFFINITY = 0.05f

    /** The colour of the open ground (no forest), which has no entry in the tree legend. */
    const val OPEN_GROUND_COLOR = 0xFFC9B27CL

    /** The tree affinities, strongest first; unknown keys and weak affinities are dropped. */
    fun trees(preference: HabitatPreference): List<AffinityBar> {
        val legend = HabitatSources.treeLegend
        val order = legend.withIndex().associate { it.value.key to it.index }
        return preference.trees.mapNotNull { (key, raw) ->
            val value = raw.coerceIn(0f, 1f)
            when {
                value < MIN_TREE_AFFINITY -> null
                key == SuitabilityScorer.NO_FOREST_KEY ->
                    AffinityBar(key, R.string.guide_habitat_open_ground, OPEN_GROUND_COLOR, value)
                else -> legend.firstOrNull { it.key == key }?.let { AffinityBar(key, it.labelRes, it.color, value) }
            }
        }.sortedWith(compareByDescending<AffinityBar> { it.value }.thenBy { order[it.key] ?: Int.MAX_VALUE })
    }

    /** The soil affinities, in the order of [SoilGroup]. */
    fun soil(preference: HabitatPreference): List<AffinityBar> = SoilGroup.entries.mapNotNull { group ->
        val key = group.name.lowercase()
        preference.soil[key]?.let { AffinityBar(key, group.labelRes, group.color, it.coerceIn(0f, 1f)) }
    }

    /** The orientation affinities, in the order of the orientation legend. */
    fun aspect(preference: HabitatPreference): List<AffinityBar> = HabitatSources.aspectLegend.mapNotNull { entry ->
        preference.aspect[entry.key]?.let { AffinityBar(entry.key, entry.labelRes, entry.color, it.coerceIn(0f, 1f)) }
    }
}
