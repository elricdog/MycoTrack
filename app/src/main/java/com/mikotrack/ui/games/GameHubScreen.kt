package com.mikotrack.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikotrack.R
import com.mikotrack.data.prefs.GameId
import com.mikotrack.data.prefs.GameScore
import java.text.DateFormat
import java.util.Date

/**
 * The games hub unlocked by the easter egg: pixel mushroom artwork, one card per
 * game with its own top ranking, and a coming-soon card for future games.
 */
@Composable
fun GameHubScreen(
    viewModel: GamesViewModel,
    onBack: () -> Unit,
    onOpenGame: (GameId) -> Unit,
) {
    val scores by viewModel.scores.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.game_back))
            }
            Text(
                text = stringResource(R.string.games_hub_title),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = stringResource(R.string.games_hub_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        GameCard(
            title = stringResource(R.string.game_arkanoid),
            blurb = stringResource(R.string.game_arkanoid_blurb),
            ranking = scores[GameId.ARKANOID].orEmpty(),
            icon = { drawMushroom(this, PixelArt.AGARIC) },
            onClick = { onOpenGame(GameId.ARKANOID) },
        )
        GameCard(
            title = stringResource(R.string.game_tetris),
            blurb = stringResource(R.string.game_tetris_blurb),
            ranking = scores[GameId.TETRIS].orEmpty(),
            icon = { drawMushroom(this, PixelArt.CHANTERELLE) },
            onClick = { onOpenGame(GameId.TETRIS) },
        )
        GameCard(
            title = stringResource(R.string.game_pacman),
            blurb = stringResource(R.string.game_pacman_blurb),
            ranking = scores[GameId.PACMAN].orEmpty(),
            icon = { drawMushroom(this, PixelArt.CEP) },
            onClick = { onOpenGame(GameId.PACMAN) },
        )
        GameCard(
            title = stringResource(R.string.game_rhythm),
            blurb = stringResource(R.string.game_rhythm_blurb),
            ranking = scores[GameId.RHYTHM].orEmpty(),
            icon = { drawMushroom(this, PixelArt.GOLD_PUFF) },
            onClick = { onOpenGame(GameId.RHYTHM) },
        )
        GameCard(
            title = stringResource(R.string.game_memory),
            blurb = stringResource(R.string.game_memory_blurb),
            ranking = scores[GameId.MEMORY].orEmpty(),
            icon = { drawMushroom(this, PixelArt.AMATISTA) },
            onClick = { onOpenGame(GameId.MEMORY) },
        )
        GameCard(
            title = stringResource(R.string.game_topo),
            blurb = stringResource(R.string.game_topo_blurb),
            ranking = scores[GameId.TOPO].orEmpty(),
            icon = { drawMushroom(this, PixelArt.SLIME_BLOB) },
            onClick = { onOpenGame(GameId.TOPO) },
        )
        GameCard(
            title = stringResource(R.string.game_solitaire),
            blurb = stringResource(R.string.game_solitaire_blurb),
            ranking = scores[GameId.SOLITAIRE].orEmpty(),
            icon = { drawMushroom(this, PixelArt.GREEN_BOLETE) },
            onClick = { onOpenGame(GameId.SOLITAIRE) },
        )
        // A placeholder for the games that will come later.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Canvas(modifier = Modifier.size(48.dp)) { drawMushroom(this, PixelArt.PUFFBALL_GHOST) }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.game_soon_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = stringResource(R.string.game_soon_blurb),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun GameCard(
    title: String,
    blurb: String,
    ranking: List<GameScore>,
    icon: (DrawScope.() -> Unit),
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PixelArt.NIGHT_CELL),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Canvas(modifier = Modifier.size(56.dp), onDraw = icon)
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = PixelArt.CAP_GOLD,
                )
                Text(
                    text = blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = PixelArt.CREAM.copy(alpha = 0.75f),
                )
                if (ranking.isEmpty()) {
                    Text(
                        text = stringResource(R.string.game_ranking_empty),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = PixelArt.CREAM.copy(alpha = 0.45f),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.game_best_score, ranking.first().score),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = PixelArt.CREAM,
                    )
                    ranking.drop(1).take(4).forEachIndexed { index, entry ->
                        Text(
                            text = "  ${index + 2}. ${entry.score}  ·  " +
                                DateFormat.getDateInstance(DateFormat.SHORT).format(Date(entry.atMillis)),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = PixelArt.CREAM.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }
    }
}

/** Draws a sprite centred in the canvas. */
private fun drawMushroom(scope: DrawScope, sprite: PixelArt.Sprite) {
    with(scope) {
        val cell = minOf(size.width / sprite.rows.first().length, size.height / sprite.rows.size)
        val w = sprite.rows.first().length * cell
        val h = sprite.rows.size * cell
        with(PixelArt) { drawSprite(sprite, Offset((size.width - w) / 2f, (size.height - h) / 2f), cell) }
    }
}
