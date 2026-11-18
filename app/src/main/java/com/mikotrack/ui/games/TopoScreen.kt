package com.mikotrack.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikotrack.R
import com.mikotrack.data.prefs.GameId
import kotlin.random.Random

/**
 * Whack-a-mole: puffball ghosts and golden puffballs pop out of nine soil holes.
 * Tap a ghost (+points, chains build a combo) or a gold puffball (big bonus), but
 * whacking a blue slug costs a life. Every 30 seconds the season turns to the next
 * level: the critters hide faster.
 */
private class TopoState {
    var score by mutableIntStateOf(0)
    var level by mutableIntStateOf(1)
    var lives by mutableIntStateOf(3)
    var combo by mutableIntStateOf(0)
    var secondsLeft by mutableIntStateOf(ROUND_SECONDS)
    var gameOver by mutableStateOf(false)
    var tick by mutableLongStateOf(0L)

    /** Per hole: kind (0 empty, 1 ghost, 2 gold, 3 slug, 4 whacked, 5 squawked), frames left, total. */
    val kinds = IntArray(9)
    val frames = IntArray(9)
    val totals = IntArray(9)
    var framesCount = 0

    fun start() {
        score = 0; level = 1; lives = 3; combo = 0
        secondsLeft = ROUND_SECONDS
        kinds.fill(0); frames.fill(0); totals.fill(0)
    }

    fun step() {
        if (gameOver) return
        framesCount += 1
        if (framesCount % 60 == 0) {
            secondsLeft -= 1
            if (secondsLeft <= 0) {
                level += 1
                score += 300
                secondsLeft = ROUND_SECONDS
            }
        }
        val hideFrames = (95 - level * 10).coerceAtLeast(38)
        for (i in 0 until 9) {
            if (kinds[i] == 0) {
                // Empty holes roll for a visitor; the odds grow with the level.
                val roll = Random.nextInt((220 - level * 25).coerceAtLeast(70))
                if (roll == 0) {
                    val kind = when {
                        Random.nextInt(100) < 22 -> 3
                        Random.nextInt(100) < 25 -> 2
                        else -> 1
                    }
                    val total = hideFrames + Random.nextInt(45)
                    kinds[i] = kind
                    frames[i] = total
                    totals[i] = total
                }
            } else if (kinds[i] == 1 || kinds[i] == 2) {
                frames[i] -= 1
                if (frames[i] <= 0) {
                    kinds[i] = 0
                    combo = 0 // the critter slipped away
                }
            } else if (kinds[i] == 4 || kinds[i] == 5) {
                frames[i] -= 1
                if (frames[i] <= 0) kinds[i] = 0
            }
        }
    }

    fun tap(index: Int) {
        if (gameOver) return
        when (kinds[index]) {
            1 -> {
                combo += 1
                score += 10 + combo * 2
                kinds[index] = 4; frames[index] = 10
            }
            2 -> {
                combo += 1
                score += 30 + combo * 5
                kinds[index] = 4; frames[index] = 10
            }
            3 -> {
                lives -= 1
                combo = 0
                score = (score - 15).coerceAtLeast(0)
                kinds[index] = 5; frames[index] = 14
                if (lives <= 0) gameOver = true
            }
        }
    }

    fun restart() {
        gameOver = false
        start()
    }

    companion object { const val ROUND_SECONDS = 30 }
}

@Composable
fun TopoScreen(
    viewModel: GamesViewModel,
    onBack: () -> Unit,
) {
    viewModel.selectGame(GameId.TOPO)
    val ranking by viewModel.currentRanking.collectAsStateWithLifecycle()
    val state = remember { TopoState().apply { start() } }

    LaunchedEffect(state.gameOver) {
        if (state.gameOver) viewModel.recordScore(GameId.TOPO, state.score, state.level)
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16)
            state.step()
            state.tick += 1
        }
    }

    GameScaffold(
        title = stringResource(R.string.game_topo),
        score = state.score,
        level = state.level,
        lives = state.lives,
        timer = state.secondsLeft,
        hint = stringResource(R.string.game_topo_hint),
        gameOver = state.gameOver,
        ranking = ranking,
        onBack = onBack,
        onRestart = { state.restart() },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.gameOver) {
                    detectTapGestures { offset ->
                        val col = (offset.x / (size.width / 3f)).toInt().coerceIn(0, 2)
                        val row = (offset.y / (size.height / 3f)).toInt().coerceIn(0, 2)
                        state.tap(row * 3 + col)
                    }
                },
        ) {
            // Hole state is mutated in place: read the frame counter to redraw.
            @Suppress("UNUSED_VARIABLE")
            val frame = state.tick

            drawRect(PixelArt.FOREST_DARK)
            val cellW = size.width / 3f
            val cellH = size.height / 3f
            for (i in 0 until 9) {
                val col = i % 3
                val row = i / 3
                val cx = col * cellW + cellW / 2f
                val cy = row * cellH + cellH / 2f
                val holeW = cellW * 0.72f
                val holeH = cellH * 0.5f

                // The soil hole.
                drawCircle(
                    color = PixelArt.SOIL,
                    radius = holeW / 2f,
                    center = Offset(cx, cy + holeH * 0.2f),
                )
                drawCircle(
                    color = PixelArt.NIGHT.copy(alpha = 0.55f),
                    radius = holeW * 0.38f,
                    center = Offset(cx, cy + holeH * 0.2f),
                )

                val kind = state.kinds[i]
                if (kind in 1..3 && state.totals[i] > 0) {
                    // Rising through the hole: the sprite slides up as frames run out.
                    val progress = 1f - state.frames[i].toFloat() / state.totals[i].toFloat()
                    val rise = (0.5f + 0.5f * progress).coerceIn(0.45f, 1f)
                    val sprite = when (kind) {
                        1 -> PixelArt.PUFFBALL_GHOST
                        2 -> PixelArt.GOLD_PUFF
                        else -> PixelArt.SLIME_BLOB
                    }
                    val sc = holeW / 8.6f
                    val spriteH = sprite.rows.size * sc
                    val topOfHole = cy + holeH * 0.2f - holeH * 0.45f
                    val spriteTop = topOfHole + spriteH * (1f - rise)
                    val clip = Path().apply {
                        addOval(
                            androidx.compose.ui.geometry.Rect(
                                cx - holeW / 2f,
                                topOfHole - spriteH,
                                cx + holeW / 2f,
                                cy + holeH * 0.75f,
                            ),
                        )
                    }
                    clipPath(clip) {
                        drawSprite(sprite, Offset(cx - 4f * sc, spriteTop), sc)
                    }
                } else if (kind == 4 || kind == 5) {
                    // Whack burst: a star of spores over the hole.
                    val burstColor = if (kind == 4) PixelArt.CAP_GOLD else PixelArt.SLIME
                    for (d in 0 until 6) {
                        val ang = Math.toRadians(d * 60.0 + state.tick * 12.0)
                        drawCircle(
                            burstColor,
                            radius = 4f,
                            center = Offset(
                                cx + (holeW * 0.42f) * kotlin.math.cos(ang).toFloat(),
                                cy - holeH * 0.2f + (holeH * 0.42f) * kotlin.math.sin(ang).toFloat(),
                            ),
                        )
                    }
                }
            }
        }
    }
}
