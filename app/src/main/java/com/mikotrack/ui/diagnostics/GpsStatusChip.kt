package com.mikotrack.ui.diagnostics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikotrack.R
import com.mikotrack.domain.GpsQuality
import java.util.Locale

private val QualityGreen = Color(0xFF2E9D46)
private val QualityAmber = Color(0xFFF2A100)
private val QualityRed = Color(0xFFD32F2F)
private val QualityGrey = Color(0xFF8A8F8A)

/** The colour that represents a GPS quality level. */
fun gpsQualityColor(quality: GpsQuality): Color = when (quality) {
    GpsQuality.EXCELLENT, GpsQuality.GOOD -> QualityGreen
    GpsQuality.FAIR -> QualityAmber
    GpsQuality.POOR, GpsQuality.DISABLED -> QualityRed
    GpsQuality.SEARCHING -> QualityGrey
}

/** The GPS icon that represents a quality level. */
fun gpsQualityIcon(quality: GpsQuality): ImageVector = when (quality) {
    GpsQuality.DISABLED -> Icons.Filled.GpsOff
    GpsQuality.SEARCHING -> Icons.Filled.GpsNotFixed
    else -> Icons.Filled.GpsFixed
}

/** How many of the four signal bars are filled for a quality level. */
fun gpsQualityBars(quality: GpsQuality): Int = when (quality) {
    GpsQuality.EXCELLENT -> 4
    GpsQuality.GOOD -> 3
    GpsQuality.FAIR -> 2
    GpsQuality.POOR -> 1
    GpsQuality.SEARCHING, GpsQuality.DISABLED -> 0
}

/** The translated name of a quality level, e.g. "Good". */
@Composable
fun gpsQualityLabel(quality: GpsQuality): String = stringResource(
    when (quality) {
        GpsQuality.DISABLED -> R.string.diag_quality_disabled
        GpsQuality.SEARCHING -> R.string.diag_quality_searching
        GpsQuality.POOR -> R.string.diag_quality_poor
        GpsQuality.FAIR -> R.string.diag_quality_fair
        GpsQuality.GOOD -> R.string.diag_quality_good
        GpsQuality.EXCELLENT -> R.string.diag_quality_excellent
    },
)

/** Four bars of increasing height, the first [filled] of them in [color]. */
@Composable
fun SignalBars(filled: Int, color: Color, modifier: Modifier = Modifier, barWidth: Int = 3, maxHeight: Int = 14) {
    Row(
        modifier = modifier.height(maxHeight.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (i in 0 until 4) {
            Box(
                modifier = Modifier
                    .width(barWidth.dp)
                    .height((maxHeight * (i + 1) / 4f).dp)
                    .background(
                        color = if (i < filled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(1.dp),
                    ),
            )
        }
    }
}

/**
 * A compact pill that shows the GPS quality on the map: a coloured GPS icon, four signal
 * bars and the accuracy with the number of satellites used, e.g. `±4 m · 9`.
 */
@Composable
fun GpsStatusChip(
    quality: GpsQuality,
    accuracyMeters: Float?,
    satellitesUsed: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = gpsQualityColor(quality)
    val text = when (quality) {
        GpsQuality.DISABLED -> stringResource(R.string.diag_chip_off)
        GpsQuality.SEARCHING -> "…"
        else -> buildString {
            append(accuracyMeters?.let { String.format(Locale.US, "±%.0f m", it) } ?: "±? m")
            if (satellitesUsed != null) append(" · ").append(satellitesUsed)
        }
    }
    val description = stringResource(R.string.diag_chip_cd, gpsQualityLabel(quality), text)
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(36.dp)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onClick(); true }
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
        border = if (quality == GpsQuality.DISABLED) BorderStroke(1.5.dp, color) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = gpsQualityIcon(quality),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
            SignalBars(filled = gpsQualityBars(quality), color = color)
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = if (quality == GpsQuality.DISABLED) color else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}
