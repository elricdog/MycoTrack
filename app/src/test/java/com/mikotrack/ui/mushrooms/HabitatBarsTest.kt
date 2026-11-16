package com.mikotrack.ui.mushrooms

import com.mikotrack.R
import com.mikotrack.maps.HabitatSources
import com.mikotrack.maps.SoilGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitatBarsTest {

    @Test
    fun `trees are sorted by affinity, weak and unknown ones dropped`() {
        val preference = HabitatPreference(
            trees = mapOf(
                "holm_oak" to 0.4f,
                "scots_pine" to 1f,
                "beech" to 0.04f,
                "made_up_tree" to 0.9f,
                "none" to 0.6f,
            ),
        )

        val bars = HabitatBars.trees(preference)

        assertEquals(listOf("scots_pine", "none", "holm_oak"), bars.map { it.key })
        assertEquals(R.string.guide_habitat_open_ground, bars[1].labelRes)
        assertEquals(HabitatBars.OPEN_GROUND_COLOR, bars[1].color)
        val pine = HabitatSources.treeLegend.first { it.key == "scots_pine" }
        assertEquals(pine.labelRes, bars[0].labelRes)
        assertEquals(pine.color, bars[0].color)
        assertEquals(100, bars[0].percent)
    }

    @Test
    fun `equal tree affinities keep the legend order and values are clamped`() {
        val preference = HabitatPreference(trees = mapOf("beech" to 0.5f, "holm_oak" to 0.5f, "birch" to 1.7f))

        val bars = HabitatBars.trees(preference)

        assertEquals(listOf("birch", "holm_oak", "beech"), bars.map { it.key })
        assertEquals(1f, bars[0].value, 0f)
    }

    @Test
    fun `soil and aspect follow the legend order with their colours`() {
        val preference = HabitatPreference(
            soil = mapOf("basic" to 0.2f, "acid" to 1f),
            aspect = mapOf("s" to 0.3f, "n" to 1f, "e" to 0.6f),
        )

        val soil = HabitatBars.soil(preference)
        val aspect = HabitatBars.aspect(preference)

        assertEquals(listOf("acid", "basic"), soil.map { it.key })
        assertEquals(SoilGroup.ACID.color, soil[0].color)
        assertEquals(SoilGroup.BASIC.labelRes, soil[1].labelRes)
        assertEquals(listOf("n", "s", "e"), aspect.map { it.key })
        assertEquals(20, soil[1].percent)
    }

    @Test
    fun `empty preferences give no bars`() {
        val empty = HabitatPreference()
        assertTrue(HabitatBars.trees(empty).isEmpty())
        assertTrue(HabitatBars.soil(empty).isEmpty())
        assertTrue(HabitatBars.aspect(empty).isEmpty())
    }
}
