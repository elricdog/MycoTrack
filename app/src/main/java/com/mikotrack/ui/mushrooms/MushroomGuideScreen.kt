package com.mikotrack.ui.mushrooms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mikotrack.R
import com.mikotrack.ui.common.EmptyState

/** The list of species of the edible mushroom guide. */
@Composable
fun MushroomGuideScreen(
    viewModel: MushroomGuideViewModel,
    onOpenSpecies: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = guideLanguage()
    LaunchedEffect(language) { viewModel.load(language) }

    val all by viewModel.species.collectAsStateWithLifecycle()
    val visible by viewModel.visibleSpecies.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val onlyInSeason by viewModel.onlyInSeason.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            label = { Text(stringResource(R.string.mushroom_guide_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = null)
                    }
                }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        FilterChip(
            selected = onlyInSeason,
            onClick = { viewModel.setOnlyInSeason(!onlyInSeason) },
            label = { Text(stringResource(R.string.mushroom_guide_in_season)) },
            leadingIcon = if (onlyInSeason) {
                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else {
                null
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (all == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "disclaimer") { MushroomDisclaimerCard() }
            if (visible.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.mushroom_guide_no_match),
                        hint = stringResource(R.string.mushroom_guide_no_match_hint),
                        icon = Icons.Filled.Spa,
                    )
                }
            }
            items(visible, key = { it.id }) { mushroom ->
                MushroomCard(
                    mushroom = mushroom,
                    currentMonth = viewModel.currentMonth,
                    onClick = { onOpenSpecies(mushroom.id) },
                )
            }
        }
    }
}

/** A card with the photo, the names, the season and the edibility of a species. */
@Composable
private fun MushroomCard(
    mushroom: Mushroom,
    currentMonth: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = mushroom.imageUri,
                contentDescription = mushroom.commonName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = mushroom.commonName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = mushroom.scientificName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SeasonBar(mushroom = mushroom, currentMonth = currentMonth)
                EdibilityStars(rating = mushroom.edibility)
            }
        }
    }
}
