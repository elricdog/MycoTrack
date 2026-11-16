package com.mikotrack.data.repository

import android.content.Context
import androidx.annotation.StringRes
import com.mikotrack.R
import com.mikotrack.data.db.CategoryEntity

/**
 * The built-in waypoint categories.
 *
 * The key stored in the database is stable so that renaming a category in a later
 * version of the app never breaks existing waypoints.
 */
enum class DefaultCategory(
    val key: String,
    @StringRes val labelRes: Int,
    val color: Long,
    val symbol: String,
) {
    MUSHROOM_FOUND("MUSHROOM_FOUND", R.string.category_mushroom_found, 0xFF7CB342, "mushroom"),
    MUSHROOM_AREA("MUSHROOM_AREA", R.string.category_mushroom_area, 0xFF9CCC65, "grass"),
    INTERESTING_MUSHROOM("INTERESTING_MUSHROOM", R.string.category_interesting_mushroom, 0xFFFFB300, "star"),
    PROMISING_AREA("PROMISING_AREA", R.string.category_promising_area, 0xFF8D6E63, "eye"),
    PARKING("PARKING", R.string.category_parking, 0xFF42A5F5, "car"),
    POINT_OF_INTEREST("POINT_OF_INTEREST", R.string.category_point_of_interest, 0xFFAB47BC, "place"),
    OTHER("OTHER", R.string.category_other, 0xFF9E9E9E, "circle"),
    ;

    fun toEntity(): CategoryEntity = CategoryEntity(
        name = key,
        color = color,
        symbol = symbol,
        builtIn = true,
    )

    companion object {
        fun fromKey(key: String): DefaultCategory? = entries.firstOrNull { it.key == key }

        /** The translated name of a category; custom categories show their own name. */
        fun labelFor(context: Context, key: String): String =
            fromKey(key)?.let { context.getString(it.labelRes) }
                ?: key.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

        fun colorFor(key: String): Long = fromKey(key)?.color ?: DefaultCategory.OTHER.color
    }
}
