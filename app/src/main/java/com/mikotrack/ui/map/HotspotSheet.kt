package com.mikotrack.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikotrack.R
import com.mikotrack.core.Formatters
import com.mikotrack.maps.Hotspot
import com.mikotrack.maps.HotspotFinder

/**
 * The details of one of the hotspot marks of a multi-species search: how likely the
 * place is, how far it is from the user, which of the selected species could be found
 * there, and the action to walk to it with the external maps application.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotSheet(
    hotspot: Hotspot,
    distanceMeters: Double?,
    onDismiss: () -> Unit,
    onGoThere: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.hotspot_sheet_title, hotspot.rank)) },
                supportingContent = {
                    val likelihood = distanceMeters?.let {
                        stringResource(R.string.hotspot_sheet_likelihood_distance, HotspotFinder.percent(hotspot.score), Formatters.distance(it))
                    } ?: stringResource(R.string.hotspot_sheet_likelihood, HotspotFinder.percent(hotspot.score))
                    Text(likelihood)
                },
                leadingContent = {
                    Icon(Icons.Filled.Landscape, contentDescription = null)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            HorizontalDivider()
            if (hotspot.speciesNames.isNotEmpty()) {
                Text(
                    stringResource(R.string.hotspot_sheet_species),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                hotspot.speciesNames.forEach { name ->
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onGoThere, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null)
                    Text(
                        stringResource(R.string.hotspot_sheet_go),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
