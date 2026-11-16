package com.mikotrack.ui.history

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikotrack.R
import com.mikotrack.ui.map.OverlayPalette

/** A colour that the user can give to a route, chosen to stand out on green base maps. */
data class TripColorOption(val color: Int, @StringRes val labelRes: Int)

/** The route colours offered to the user: strong hues away from the green of the forest. */
val TripColorOptions: List<TripColorOption> = listOf(
    TripColorOption(0xFFE53935.toInt(), R.string.tripcolor_red),
    TripColorOption(0xFFFF6D00.toInt(), R.string.tripcolor_orange),
    TripColorOption(0xFFFFEA00.toInt(), R.string.tripcolor_yellow),
    TripColorOption(0xFFFF00FF.toInt(), R.string.tripcolor_magenta),
    TripColorOption(0xFFFF4F9A.toInt(), R.string.tripcolor_pink),
    TripColorOption(0xFF8E24AA.toInt(), R.string.tripcolor_purple),
    TripColorOption(0xFF2962FF.toInt(), R.string.tripcolor_blue),
    TripColorOption(0xFF1A237E.toInt(), R.string.tripcolor_navy),
    TripColorOption(0xFF00E5FF.toInt(), R.string.tripcolor_cyan),
    TripColorOption(0xFF6D4C41.toInt(), R.string.tripcolor_brown),
    TripColorOption(0xFF000000.toInt(), R.string.tripcolor_black),
    TripColorOption(0xFFFFFFFF.toInt(), R.string.tripcolor_white),
)

/**
 * The colour of a route: a filled dot for a chosen [color] or, when it is `null`, a
 * multicoloured ring with an "A", because the automatic colour depends on the order of
 * the routes overlaid on the map.
 */
@Composable
fun TripColorDot(
    color: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
) {
    val description = stringResource(R.string.tripcolor_change)
    val clickable = if (onClick != null) {
        Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
    } else {
        Modifier
    }
    // The touch target is larger than the dot itself.
    Box(
        modifier = modifier
            .then(clickable)
            .padding(if (onClick != null) 10.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        ColorSwatch(color = color, size = size)
    }
}

@Composable
private fun ColorSwatch(color: Int?, size: Dp, selected: Boolean = false) {
    val outline = MaterialTheme.colorScheme.outline
    if (color == null) {
        Box(
            modifier = Modifier
                .size(size)
                .border(
                    width = (size.value / 7f).coerceAtLeast(2f).dp,
                    brush = Brush.sweepGradient(OverlayPalette.map { Color(it) } + Color(OverlayPalette.first())),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(size * 0.55f))
            } else {
                Text(
                    text = stringResource(R.string.tripcolor_auto_badge),
                    fontSize = (size.value * 0.5f).sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        val fill = Color(color)
        Box(
            modifier = Modifier
                .size(size)
                .background(fill, CircleShape)
                .border(1.dp, outline.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (fill.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(size * 0.6f),
                )
            }
        }
    }
}

/** Lets the user choose the colour of a route on the map, or go back to the automatic one. */
@Composable
fun TripColorDialog(
    current: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tripcolor_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TripColorOptions.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        row.forEach { option ->
                            val label = stringResource(option.labelRes)
                            val isSelected = option.color == current
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button) { onSelect(option.color) }
                                    .semantics {
                                        contentDescription = label
                                        selected = isSelected
                                    }
                                    .padding(6.dp),
                            ) {
                                ColorSwatch(color = option.color, size = 40.dp, selected = isSelected)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { onSelect(null) }
                        .semantics { selected = current == null }
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ColorSwatch(color = null, size = 40.dp, selected = current == null)
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.tripcolor_automatic),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (current == null) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(
                            text = stringResource(R.string.tripcolor_automatic_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
