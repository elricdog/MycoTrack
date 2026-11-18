package com.mikotrack.ui.games

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate

/**
 * Pixel art helpers shared by all the games of the easter egg. Sprites are defined
 * as grids of characters, one per pixel, mapped to a palette; every game draws them
 * as square cells, which keeps the chunky look on screens of any size.
 */
object PixelArt {

    /** The mushroom palette shared by the games (earthy reds, cream, forest greens). */    val CAP_RED = Color(0xFFC62828)
    val CAP_DARK_RED = Color(0xFF8E1B1B)
    val CAP_ORANGE = Color(0xFFEF6C00)
    val CAP_BROWN = Color(0xFF795548)
    val CAP_GOLD = Color(0xFFFFC107)
    val STEM = Color(0xFFF5E9D4)
    val STEM_SHADE = Color(0xFFD8C8A8)
    val SPOT = Color(0xFFFFFDE7)
    val FOREST = Color(0xFF2E7D32)
    val FOREST_DARK = Color(0xFF1B5E20)
    val NIGHT = Color(0xFF10131C)
    val NIGHT_CELL = Color(0xFF1B2130)
    val LEAF = Color(0xFF9CCC65)
    val SOIL = Color(0xFF5D4037)
    val CREAM = Color(0xFFFFF8E1)
    val SLIME = Color(0xFF4FC3F7)

    /** A sprite: rows of palette keys, '.' marks a transparent pixel. */
    data class Sprite(val rows: List<String>, val palette: Map<Char, Color>)

    /** Builds every sprite off the main thread so the first game frame is never janky. */
    fun warmUp() {
        listOf(CEP, AGARIC, CHANTERELLE, GREEN_BOLETE, PUFFBALL_GHOST)
    }

    // --- shared sprites -------------------------------------------------------------------

    /** A classic red-capped mushroom (cep), 8 x 8. */
    val CEP = Sprite(
        listOf(
            "..RRRR..",
            ".RWRRWR.",
            "RWRRRWRW",
            "RRRRRRRR",
            ".SSSSSS.",
            "..S.ss..",
            "..S.ss..",
            "..SSSS..",
        ),
        mapOf(
            'R' to CAP_RED,
            'W' to SPOT,
            'S' to STEM,
            's' to STEM_SHADE,
        ),
    )

    /** A small fly agaric used as a brick, 8 x 6. */
    val AGARIC = Sprite(
        listOf(
            ".RRRR.",
            "RWRRRW",
            "RRWRRR",
            "RRRRRR",
            ".SSSS.",
            ".SssS.",
        ),
        mapOf(
            'R' to CAP_RED,
            'W' to SPOT,
            'S' to STEM,
            's' to STEM_SHADE,
        ),
    )

    /** A golden chanterelle, 8 x 6 (used as a bonus brick). */
    val CHANTERELLE = Sprite(
        listOf(
            ".OOOO.",
            "OGGGGO",
            "OGGGGO",
            ".OOOO.",
            ".SssS.",
            ".SSSS.",
        ),
        mapOf(
            'O' to CAP_ORANGE,
            'G' to CAP_GOLD,
            'S' to STEM,
            's' to STEM_SHADE,
        ),
    )

    /** A green bolete, 8 x 6 (used as a plain brick). */
    val GREEN_BOLETE = Sprite(
        listOf(
            ".FFFF.",
            "FfFFfF",
            "FFFFFF",
            ".SSSS.",
            ".SssS.",
            ".SSSS.",
        ),
        mapOf(
            'F' to FOREST,
            'f' to LEAF,
            'S' to STEM,
            's' to STEM_SHADE,
        ),
    )

    /** A little ghost shaped as a puffball, 8 x 8. */
    val PUFFBALL_GHOST = Sprite(
        listOf(
            "..WWWW..",
            ".WEWWEW.",
            "WWWWWWWW",
            "WWWWWWWW",
            "WWWWWWWW",
            "WWWWWWWW",
            "W.WW.W.W",
            ".W.WW.W.",
        ),
        mapOf(
            'W' to Color(0xFFEDE7F6),
            'E' to NIGHT,
        ),
    )
}

/** Draws a [PixelArt.Sprite] at [origin] with [cell] sized pixels. */
fun DrawScope.drawSprite(sprite: PixelArt.Sprite, origin: Offset, cell: Float) {
    translate(origin.x, origin.y) {
        sprite.rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, key ->
                val color = sprite.palette[key] ?: return@forEachIndexed
                drawRect(color, Offset(x * cell, y * cell), Size(cell, cell))
            }
        }
    }
}
