package com.mikotrack.ui.mushrooms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mikotrack.R
import com.mikotrack.ui.common.SectionCard

private val ThumbnailHeight = 160.dp
private val ThumbnailWidth = 213.dp
private val ToxicRed = Color(0xFFC62828)

/** The extra photographs of a species, as a horizontal strip of thumbnails. */
@Composable
fun GuideGallerySection(photos: List<GuidePhoto>, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (photos.isEmpty()) return
    SectionCard(title = stringResource(R.string.guide_photos_more), modifier = modifier) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(photos, key = { _, photo -> photo.image }) { index, photo ->
                GalleryThumbnail(photo = photo, onClick = { onOpen(index) })
            }
        }
    }
}

@Composable
private fun GalleryThumbnail(photo: GuidePhoto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(ThumbnailWidth)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = stringResource(R.string.guide_photos_open), role = Role.Image, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.caption,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(ThumbnailHeight)
                .clip(RoundedCornerShape(12.dp)),
        )
        photo.caption?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "© " + photo.credit.author,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The photographs of the dangerous look-alikes, framed in red with a toxicity badge. */
@Composable
fun LookAlikePhotoRow(photos: List<GuidePhoto>, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (photos.isEmpty()) return
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(photos, key = { _, photo -> photo.image }) { index, photo ->
            LookAlikeCard(photo = photo, onClick = { onOpen(index) })
        }
    }
}

@Composable
private fun LookAlikeCard(photo: GuidePhoto, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .width(ThumbnailWidth)
            .border(2.dp, ToxicRed, shape)
            .clip(shape)
            .clickable(onClickLabel = stringResource(R.string.guide_photos_open), role = Role.Image, onClick = onClick),
    ) {
        Box {
            AsyncImage(
                model = photo.uri,
                contentDescription = listOfNotNull(photo.scientificName, photo.caption).joinToString(". "),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ThumbnailHeight),
            )
            ToxicityBadge(toxic = photo.toxic, modifier = Modifier.padding(8.dp))
        }
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            photo.scientificName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            photo.caption?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "© " + photo.credit.author,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ToxicityBadge(toxic: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (toxic) ToxicRed else Color(0xFF616161),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                text = stringResource(if (toxic) R.string.guide_photos_toxic else R.string.guide_photos_not_edible),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** A collapsible chart of the trees, soils and slope orientations the species prefers. */
@Composable
fun HabitatChartCard(habitat: HabitatPreference, modifier: Modifier = Modifier) {
    val trees = HabitatBars.trees(habitat)
    val soil = HabitatBars.soil(habitat)
    val aspect = HabitatBars.aspect(habitat)
    if (trees.isEmpty() && soil.isEmpty() && aspect.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val state = stringResource(if (expanded) R.string.guide_habitat_chart_expanded else R.string.guide_habitat_chart_collapsed)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = state }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Forest, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.guide_habitat_chart_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation))
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BarGroup(stringResource(R.string.guide_habitat_chart_trees), trees)
                BarGroup(stringResource(R.string.guide_habitat_chart_soil), soil)
                BarGroup(stringResource(R.string.guide_habitat_chart_aspect), aspect)
                Text(
                    text = stringResource(R.string.guide_habitat_chart_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BarGroup(title: String, bars: List<AffinityBar>) {
    if (bars.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        bars.forEach { AffinityBarRow(it) }
    }
}

@Composable
private fun AffinityBarRow(bar: AffinityBar) {
    val label = stringResource(bar.labelRes)
    val description = stringResource(R.string.guide_habitat_bar_description, label, bar.percent)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.42f),
        )
        Box(
            modifier = Modifier
                .weight(0.58f)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (bar.value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(bar.value)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(bar.color)),
                )
            }
        }
        Text(
            text = "${bar.percent}%",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(44.dp),
        )
    }
}
