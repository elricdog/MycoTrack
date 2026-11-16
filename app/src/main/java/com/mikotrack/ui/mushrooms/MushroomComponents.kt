package com.mikotrack.ui.mushrooms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mikotrack.R
import com.mikotrack.ui.theme.Amber
import java.text.DateFormatSymbols
import java.util.Locale

/** The language of the guide, taken from the configuration of the current context. */
@Composable
fun guideLanguage(): String {
    val configuration = LocalConfiguration.current
    return MushroomCatalog.languageOf(if (configuration.locales.isEmpty) null else configuration.locales[0])
}

/** The warning that the guide is no substitute for an expert. */
@Composable
fun MushroomDisclaimerCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.mushroom_guide_disclaimer_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.mushroom_guide_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** The edibility of a species, from one to five stars. */
@Composable
fun EdibilityStars(rating: Int, modifier: Modifier = Modifier, starSize: Dp = 16.dp) {
    val description = stringResource(R.string.mushroom_guide_edibility_description, rating)
    Row(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        repeat(5) { index ->
            Icon(
                imageVector = if (index < rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = if (index < rating) Amber else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(starSize),
            )
        }
    }
}

/**
 * A twelve-month bar with the months of the season highlighted. When [showInitials] is set,
 * every cell shows the initial of its month and the current month is outlined.
 */
@Composable
fun SeasonBar(
    mushroom: Mushroom,
    currentMonth: Int,
    modifier: Modifier = Modifier,
    showInitials: Boolean = false,
) {
    val initials = stringResource(R.string.mushroom_guide_month_initials).split(',')
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val monthNames = DateFormatSymbols.getInstance(locale).months
    val activeNames = (1..12).filter { mushroom.isInSeason(it) }.joinToString { monthNames[it - 1] }
    val description = stringResource(R.string.mushroom_guide_season_description, activeNames)
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(if (showInitials) 3.dp else 2.dp),
    ) {
        for (month in 1..12) {
            val inSeason = mushroom.isInSeason(month)
            val shape = RoundedCornerShape(if (showInitials) 6.dp else 2.dp)
            var cell = Modifier
                .weight(1f)
                .height(if (showInitials) 28.dp else 6.dp)
                .background(if (inSeason) active else inactive, shape)
            if (showInitials && month == currentMonth) {
                cell = cell.border(2.dp, MaterialTheme.colorScheme.tertiary, shape)
            }
            Box(modifier = cell, contentAlignment = Alignment.Center) {
                if (showInitials) {
                    Text(
                        text = initials.getOrElse(month - 1) { "" },
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        fontWeight = if (month == currentMonth) FontWeight.Bold else FontWeight.Normal,
                        color = if (inSeason) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
