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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikotrack.R
import com.mikotrack.data.prefs.GameId
import kotlin.random.Random

/**
 * Memory puzzle: the forest cards hide pairs of pixel mushrooms. Flip two cards
 * per move; matched pairs stay open and pay points. Clear the whole board before
 * the clock runs out to move to a bigger board (four columns, more rows).
 */
private class MemoryState {
    var score by mutableIntStateOf(0)
    var level by mutableIntStateOf(1)
    var moves by mutableIntStateOf(0)
    var secondsLeft by mutableIntStateOf(LEVEL_SECONDS)
    var gameOver by mutableStateOf(false)
    var cells by mutableStateOf(emptyList<MemCell>())
    var combo by mutableIntStateOf(0)

    /** Index of the single face-up card waiting for its pair (plain: not observed). */
    var first = -1
    var pending = -1 to -1
    var pendingFrames = 0
    var frames = 0

    data class MemCell(val sprite: Int, val faceUp: Boolean, val matched: Boolean)

    val rows get() = (4 + (level - 1)).coerceAtMost(6)
    val cols get() = 4

    fun newBoard() {
        val pairs = rows * cols / 2
        val deck = (0 until pairs).flatMap { pair -> listOf(pair % 8, pair % 8) }.toMutableList()
        deck.shuffle(Random)
        cells = deck.map { MemCell(it, faceUp = false, matched = false) }
        first = -1
        pending = -1 to -1
        pendingFrames = 0
    }

    fun start() {
        score = 0; level = 1; moves = 0; combo = 0
        secondsLeft = LEVEL_SECONDS
        newBoard()
    }

    fun step() {
        if (gameOver) return
        frames += 1
        if (pendingFrames > 0) {
            pendingFrames -= 1
            if (pendingFrames == 0) {
                val (a, b) = pending
                cells = cells.mapIndexed { i, c ->
                    if (i == a || i == b) c.copy(faceUp = false) else c
                }
                pending = -1 to -1
            }
        }
        if (frames % 60 == 0) {
            secondsLeft -= 1
            if (secondsLeft <= 0) gameOver = true
        }
    }

    fun tap(index: Int) {
        if (gameOver) return
        val cell = cells.getOrNull(index) ?: return
        if (cell.matched || cell.faceUp) return
        if (pendingFrames > 0) return
        cells = cells.toMutableList().also { it[index] = cell.copy(faceUp = true) }
        if (first == -1) {
            first = index
            return
        }
        moves += 1
        val a = first
        first = -1
        if (cells[a].sprite == cell.sprite) {
            combo += 1
            score += 100 + 10 * combo + level * 20
            cells = cells.toMutableList().also {
                it[index] = it[index].copy(matched = true)
                it[a] = it[a].copy(matched = true)
            }
            if (cells.none { !it.matched }) {
                // Board cleared: time left pays, and the board grows.
                score += secondsLeft * 10
                level += 1
                secondsLeft = (90 - 10 * (level - 1)).coerceAtLeast(45)
                newBoard()
            }
        } else {
            combo = 0
            pending = a to index
            pendingFrames = 45
        }
    }

    fun restart() {
        combo = 0
        gameOver = false
        start()
    }
}

private const val LEVEL_SECONDS = 90

@Composable
fun MemoryScreen(
    viewModel: GamesViewModel,
    onBack: () -> Unit,
) {
    viewModel.selectGame(GameId.MEMORY)
    val ranking by viewModel.currentRanking.collectAsStateWithLifecycle()
    val state = remember { MemoryState().apply { start() } }

    LaunchedEffect(state.gameOver) {
        if (state.gameOver) viewModel.recordScore(GameId.MEMORY, state.score, state.level)
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16)
            state.step()
        }
    }

    GameScaffold(
        title = stringResource(R.string.game_memory),
        score = state.score,
        level = state.level,
        lives = null,
        timer = state.secondsLeft,
        hint = stringResource(R.string.game_memory_hint),
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
                        val cellW = size.width / state.cols
                        val cellH = size.height / state.rows
                        val col = (offset.x / cellW).toInt().coerceIn(0, state.cols - 1)
                        val row = (offset.y / cellH).toInt().coerceIn(0, state.rows - 1)
                        state.tap(row * state.cols + col)
                    }
                },
        ) {
            drawRect(PixelArt.NIGHT)
            val cellW = size.width / state.cols
            val cellH = size.height / state.rows
            val pad = minOf(cellW, cellH) * 0.08f
            val sprites = listOf(
                PixelArt.CEP, PixelArt.AGARIC, PixelArt.CHANTERELLE, PixelArt.GREEN_BOLETE,
                PixelArt.PUFFBALL_GHOST, PixelArt.AMATISTA, PixelArt.SLIME_BLOB, PixelArt.GOLD_PUFF,
            )
            state.cells.forEachIndexed { index, cell ->
                val col = index % state.cols
                val row = index / state.cols
                val origin = Offset(col * cellW + pad, row * cellH + pad)
                val cardSize = Size(cellW - 2 * pad, cellH - 2 * pad)
                when {
                    cell.matched -> {
                        drawRect(PixelArt.FOREST_DARK.copy(alpha = 0.5f), origin, cardSize)
                    }
                    cell.faceUp -> {
                        drawRect(PixelArt.CREAM, origin, cardSize)
                        val sprite = sprites[cell.sprite]
                        val sc = minOf(cardSize.width / sprite.rows.first().length, cardSize.height / sprite.rows.size)
                        val w = sprite.rows.first().length * sc
                        val h = sprite.rows.size * sc
                        drawSprite(sprite, origin + Offset((cardSize.width - w) / 2f, (cardSize.height - h) / 2f), sc)
                    }
                    else -> {
                        drawRect(PixelArt.NIGHT_CELL, origin, cardSize)
                        // A hidden cap mark on the card back.
                        val mark = minOf(cardSize.width, cardSize.height) * 0.35f
                        drawCircle(PixelArt.CAP_RED.copy(alpha = 0.35f), radius = mark / 2f, center = origin + Offset(cardSize.width / 2f, cardSize.height / 2f))
                    }
                }
                drawRect(
                    PixelArt.CREAM.copy(alpha = 0.15f),
                    origin,
                    cardSize,
                    style = Stroke(width = 2f),
                )
            }
        }
    }
}
