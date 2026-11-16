package com.mikotrack.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.mikotrack.data.repository.DefaultCategory

/** A simple mushroom (cap and stem), because Material Icons has no mushroom symbol. */
val MushroomIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Mushroom",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 3f)
            curveTo(6.5f, 3f, 2f, 6.8f, 2f, 11.5f)
            curveTo(2f, 12.3f, 2.7f, 13f, 3.5f, 13f)
            lineTo(20.5f, 13f)
            curveTo(21.3f, 13f, 22f, 12.3f, 22f, 11.5f)
            curveTo(22f, 6.8f, 17.5f, 3f, 12f, 3f)
            close()
            moveTo(9.2f, 14.5f)
            lineTo(14.8f, 14.5f)
            lineTo(15.5f, 19.4f)
            curveTo(15.6f, 20.3f, 15f, 21f, 14.1f, 21f)
            lineTo(9.9f, 21f)
            curveTo(9f, 21f, 8.4f, 20.3f, 8.5f, 19.4f)
            close()
        }
    }.build()
}

/** The icon that represents a waypoint category; custom categories get a generic pin. */
fun categoryIcon(key: String): ImageVector = when (DefaultCategory.fromKey(key)) {
    DefaultCategory.MUSHROOM_FOUND -> MushroomIcon
    DefaultCategory.MUSHROOM_AREA -> Icons.Filled.Grass
    DefaultCategory.INTERESTING_MUSHROOM -> Icons.Filled.Star
    DefaultCategory.PROMISING_AREA -> Icons.Filled.Visibility
    DefaultCategory.PARKING -> Icons.Filled.DirectionsCar
    DefaultCategory.POINT_OF_INTEREST -> Icons.Filled.Place
    DefaultCategory.OTHER, null -> Icons.Filled.LocationOn
}
