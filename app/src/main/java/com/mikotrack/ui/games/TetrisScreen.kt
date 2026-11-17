package com.mikotrack.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.mikotrack.R
import com.mikotrack.data.prefs.GameId
import kotlin.random.Random

/**
 * Pixel tetris: the pieces are mushrooms drawn with the shared palette. Tap the left
 * or the right half to slide the piece, tap the middle to turn it, swipe down to drop
 * it, swipe up for a quarter turn. Clearing rows scores 100, 300, 500 and 800 points
 * times the level; every ten rows the pieces fall faster.
 */
private class TetrisState {

    companion object {
        const val COLS = 10
        const val ROWS = 18

        /** The seven shapes, each as a list of rotations; every rotation is 4 cells. */
        val SHAPES: List<List<List<Pair<Int, Int>>>> = listOf(
            // I
            listOf(listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1), listOf(2 to 0, 2 to 1, 2 to 2, 2 to 3)),
            // O
            listOf(listOf(1 to 0, 2 to 0, 1 to 1, 2 to 1)),
            // T
            listOf(listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1), listOf(1 to 0, 1 to 1, 1 to 2, 2 to 1), listOf(0 to 1, 1 to 1, 2 to 1, 1 to 2), listOf(1 to 0, 1 to 1, 1 to 2, 0 to 1)),
            // S
            listOf(listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1), listOf(1 to 0, 1 to 1, 2 to 1, 2 to 2)),
            // Z
            listOf(listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1), listOf(2 to 0, 1 to 1, 2 to 1, 1 to 2)),
            // J
            listOf(listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1), listOf(1 to 0, 2 to 0, 1 to 1, 1 to 2), listOf(0 to 1, 1 to 1, 2 to 1, 2 to 2), listOf(1 to 0, 1 to 1, 0 to 2, 1 to 2)),
            // L
            listOf(listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1), listOf(1 to 0, 1 to 1, 1 to 2, 2 to 2), listOf(0 to 1, 1 to 1, 2 to 1, 0 to 2), listOf(0 to 0, 1 to 0, 1 to 1, 1 to 2)),
        )

        /** One palette entry per shape, in the order of [SHAPES]. */
        val COLORS = listOf(
            PixelArt.SLIME, PixelArt.CAP_GOLD, PixelArt.CAP_RED,
            PixelArt.FOREST, PixelArt.CAP_BROWN, PixelArt.CAP_ORANGE, PixelArt.LEAF,
        )
    }

    var score by mutableIntStateOf(0)
    var level by mutableIntStateOf(1)
    var lines by mutableIntStateOf(0)
    var gameOver by mutableStateOf(false)
    var tick by mutableLongStateOf(0L)

    /** The settled board: 0 empty, 1..7 the shape colour index + 1. */
    val grid = Array(ROWS) { IntArray(COLS) }
    var shape by mutableIntStateOf(0)
    var rot by mutableIntStateOf(0)
    var pieceX by mutableIntStateOf(3)
    var pieceY by mutableIntStateOf(0)
    var nextShape by mutableIntStateOf(Random.nextInt(7))

    fun cells(s: Int = shape, r: Int = rot): List<Pair<Int, Int>> {
        val rotations = SHAPES[s]
        return rotations[((r % rotations.size) + rotations.size) % rotations.size]
    }

    fun fits(s: Int, r: Int, x: Int, y: Int): Boolean =
        cells(s, r).all { (cx, cy) ->
            val nx = x + cx
            val ny = y + cy
            nx in 0 until COLS && ny < ROWS && (ny < 0 || grid[ny][nx] == 0)
        }

    fun spawn() {
        shape = nextShape
        nextShape = Random.nextInt(7)
        rot = 0
        pieceX = 3
        pieceY = 0
        if (!fits(shape, rot, pieceX, pieceY)) gameOver = true
    }

    fun tryMove(dx: Int, dy: Int, newRot: Int = rot) {
        if (fits(shape, newRot, pieceX + dx, pieceY + dy)) {
            pieceX += dx
            pieceY += dy
            rot = newRot
        } else if (dy > 0) {
            lock()
        }
    }

    fun rotate() {
        val r = rot + 1
        // Wall kicks: try the plain rotation, then nudges.
        for (kick in intArrayOf(0, -1, 1, -2, 2)) {
            if (fits(shape, r, pieceX + kick, pieceY)) {
                pieceX += kick
                rot = r
                return
            }
        }
    }

    fun hardDrop() {
        var dropped = 0
        while (fits(shape, rot, pieceX, pieceY + 1)) {
            pieceY += 1
            dropped += 1
        }
        score += dropped * 2
        lock()
    }

    fun step() {
        if (gameOver) return
        tick += 1
        val period = (24 - level * 2).coerceAtLeast(6)
        if (tick % period == 0L) tryMove(0, 1)
    }

    private fun lock() {
        for ((cx, cy) in cells()) {
            val ny = pieceY + cy
            if (ny in 0 until ROWS) grid[ny][pieceX + cx] = shape + 1
        }
        clearRows()
        spawn()
    }

    private fun clearRows() {
        var cleared = 0
        var y = ROWS - 1
        while (y >= 0) {
            if (grid[y].all { it != 0 }) {
                for (yy in y downTo 1) grid[yy] = grid[yy - 1].copyOf()
                grid[0] = IntArray(COLS)
                cleared += 1
            } else {
                y -= 1
            }
        }
        if (cleared > 0) {
            lines += cleared
            score += intArrayOf(0, 100, 300, 500, 800)[cleared] * level
            level = 1 + lines / 10
        }
    }

    fun restart() {
        score = 0; level = 1; lines = 0; gameOver = false; tick = 0
        for (row in grid) row.fill(0)
        spawn()
    }
}

@Composable
fun TetrisScreen(
    viewModel: GamesViewModel,
    onBack: () -> Unit,
) {
    viewModel.selectGame(GameId.TETRIS)
    val ranking by viewModel.currentRanking.collectAsStateWithLifecycle()
    val state = remember { TetrisState().apply { spawn() } }

    LaunchedEffect(state.gameOver) {
        if (state.gameOver) viewModel.recordScore(GameId.TETRIS, state.score, state.level)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(50)
            state.step()
        }
    }

    GameScaffold(
        title = stringResource(R.string.game_tetris),
        score = state.score,
        level = state.level,
        lives = null,
        hint = stringResource(R.string.game_tetris_hint),
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
                        when {
                            offset.x < size.width / 3f -> state.tryMove(-1, 0)
                            offset.x > 2f * size.width / 3f -> state.tryMove(1, 0)
                            else -> state.rotate()
                        }
                    }
                }
                .pointerInput(state.gameOver) {
                    detectDragGestures { _, dragAmount ->
                        // Only deliberate swipes: a clear vertical drag drops or turns.
                        if (kotlin.math.abs(dragAmount.y) > 24f && kotlin.math.abs(dragAmount.y) > kotlin.math.abs(dragAmount.x)) {
                            if (dragAmount.y > 0) state.hardDrop() else state.rotate()
                        }
                    }
                },
        ) {
            val cell = minOf(size.width / (TetrisState.COLS + 4f), size.height / TetrisState.ROWS)
            val boardW = cell * TetrisState.COLS
            val boardH = cell * TetrisState.ROWS
            val left = (size.width - boardW) / 2f
            val top = (size.height - boardH) / 2f

            drawRect(PixelArt.NIGHT_CELL, Offset(left - 4f, top - 4f), Size(boardW + 8f, boardH + 8f))
            drawRect(PixelArt.NIGHT, Offset(left, top), Size(boardW, boardH))

            fun cellOrigin(x: Int, y: Int) = Offset(left + x * cell, top + y * cell)

            // Settled cells: each block is a pixel mushroom cap.
            for (y in 0 until TetrisState.ROWS) {
                for (x in 0 until TetrisState.COLS) {
                    val v = state.grid[y][x]
                    if (v == 0) continue
                    val color = TetrisState.COLORS[v - 1]
                    drawRect(color, cellOrigin(x, y), Size(cell - 1f, cell - 1f))
                    drawRect(color.copy(alpha = 0.45f), cellOrigin(x, y), Size(cell - 1f, (cell - 1f) * 0.3f))
                }
            }
            // The falling piece.
            for ((cx, cy) in state.cells()) {
                val x = state.pieceX + cx
                val y = state.pieceY + cy
                if (y < 0) continue
                val color = TetrisState.COLORS[state.shape]
                drawRect(color, cellOrigin(x, y), Size(cell - 1f, cell - 1f))
                drawRect(PixelArt.SPOT.copy(alpha = 0.5f), cellOrigin(x, y), Size(cell - 1f, (cell - 1f) * 0.3f))
            }
            // The next piece, in the side panel.
            val panel = cell * 3
            drawRect(PixelArt.NIGHT_CELL, Offset(left + boardW + 8f, top), Size(panel, panel))
            for ((cx, cy) in TetrisState.SHAPES[state.nextShape][0]) {
                drawRect(
                    TetrisState.COLORS[state.nextShape],
                    Offset(left + boardW + 8f + (cx + 1) * panel / 5f, top + (cy + 1) * panel / 5f),
                    Size(panel / 5f - 1f, panel / 5f - 1f),
                )
            }
        }
    }
}
