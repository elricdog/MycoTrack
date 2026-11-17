package com.mikotrack.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikotrack.R
import com.mikotrack.data.prefs.GameScore
import java.text.DateFormat
import java.util.Date

/**
 * Shared frame of the easter egg games: a retro header with the score, the level and
 * the lives, the playfield drawn by the game itself, and the ranking shown when the
 * game is over. The monospaced font and the pixel palette give every game the same
 * arcade look.
 */
@Composable
fun GameScaffold(
    title: String,
    score: Int,
    level: Int,
    lives: Int?,
    hint: String,
    gameOver: boolean,
    ranking: List<GameScore>,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelArt.NIGHT),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.game_back), tint = PixelArt.CREAM)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                color = PixelArt.CREAM,
                modifier = Modifier.weight(1f),
            )
            PixelStat(label = stringResource(R.string.game_score_label), value = score.toString())
            PixelStat(label = stringResource(R.string.game_level_label), value = level.toString())
            if (lives != null) {
                PixelStat(label = stringResource(R.string.game_lives_label), value = "x$lives")
            }
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = PixelArt.CREAM.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            content()
            if (gameOver) {
                GameOverCard(
                    ranking = ranking,
                    onRestart = onRestart,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun PixelStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = PixelArt.CREAM.copy(alpha = 0.6f),
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = PixelArt.CAP_GOLD,
        )
    }
}

/** The panel shown on top of the playfield when a game ends: score and top ranks. */
@Composable
private fun GameOverCard(
    ranking: List<GameScore>,
    onRestart: () -> Unit,
    onBack: () -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = PixelArt.NIGHT_CELL),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.game_over),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                color = PixelArt.CAP_GOLD,
            )
            Text(
                text = stringResource(R.string.game_ranking_title),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                color = PixelArt.CREAM,
            )
            if (ranking.isEmpty()) {
                Text(
                    text = stringResource(R.string.game_ranking_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = PixelArt.CREAM.copy(alpha = 0.7f),
                )
            }
            ranking.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${index + 1}.",
                        fontFamily = FontFamily.Monospace,
                        color = if (index == 0) PixelArt.CAP_GOLD else PixelArt.CREAM.copy(alpha = 0.8f),
                        modifier = Modifier.width(28.dp),
                    )
                    Text(
                        text = entry.score.toString(),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = PixelArt.CREAM,
                        modifier = Modifier.width(72.dp),
                    )
                    Text(
                        text = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(entry.atMillis)),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = PixelArt.CREAM.copy(alpha = 0.6f),
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
            Button(onClick = onRestart) {
                Text(stringResource(R.string.game_play_again), fontFamily = FontFamily.Monospace)
            }
            OutlinedButton(onClick = onBack) {
                Text(stringResource(R.string.game_back), fontFamily = FontFamily.Monospace, color = PixelArt.CREAM)
            }
        }
    }
}
