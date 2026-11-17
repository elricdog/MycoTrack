package com.mikotrack.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import kotlinx.coroutines.delay
import com.mikotrack.R
import com.mikotrack.data.prefs.GameId
import kotlin.math.abs
import kotlin.random.Random

/**
 * Pixel arkanoid: a wooden basket slides under the finger and bounces a ball against
 * rows of mushrooms. Red caps are worth 10 points, green boletes 15 and golden
 * chanterelles 25; clearing every brick opens the next row set, faster.
 */
private class ArkanoidState {
    var score by mutableIntStateOf(0)
    var level by mutableIntStateOf(1)
    var lives by mutableIntStateOf(3)
    var gameOver by mutableStateOf(false)
    var tick by mutableLongStateOf(0L)

    /** 0..1 positions, relative to the playfield. */
    var paddleX by mutableStateOf(0.5f)
    var ballX by mutableStateOf(0.5f)
    var ballY by mutableStateOf(0.7f)
    var velX by mutableStateOf(0.012f)
    var velY by mutableStateOf(-0.016f)
    var bricks by mutableStateOf(emptyList<Brick>())
    var serve = true

    data class Brick(val col: Int, val row: Int, val kind: Int, var alive: Boolean = true)

    fun newLevel() {
        val rows = (2 + level).coerceAtMost(6)
        bricks = buildList {
            for (row in 0 until rows) {
                for (col in 0 until 8) {
                    // A few holes keep the maze interesting.
                    if (Random.nextInt(8) == 0) continue
                    val kind = when {
                        row == 0 -> 2
                        row % 2 == 0 -> 1
                        else -> 0
                    }
                    add(Brick(col, row, kind))
                }
            }
        }
        serve = true
        ballX = 0.5f
        ballY = 0.7f
    }

    fun launch() {
        val dir = if (Random.nextBoolean()) 1 else -1
        velX = dir * (0.010f + level * 0.001f)
        velY = -(0.016f + level * 0.001f)
        serve = false
    }

    fun step() {
        if (gameOver) return
        if (serve) {
            ballX = paddleX
            ballY = 0.72f
            return
        }
        ballX += velX
        ballY += velY
        // Walls.
        if (ballX < 0.02f) { ballX = 0.02f; velX = abs(velX) }
        if (ballX > 0.98f) { ballX = 0.98f; velX = -abs(velX) }
        if (ballY < 0.02f) { ballY = 0.02f; velY = abs(velY) }
        // The paddle sits at y = 0.86 and is 0.22 wide.
        if (velY > 0 && ballY in 0.84f..0.88f && abs(ballX - paddleX) < 0.13f) {
            velY = -abs(velY)
            // The impact point steers the ball, like in the arcade original.
            val offset = (ballX - paddleX) / 0.13f
            velX = velX * 0.8f + offset * 0.008f
        }
        // Bricks: a 8 x 6 grid between y = 0.06 and 0.5.
        val col = (ballX * 8).toInt()
        val row = ((ballY - 0.06f) / 0.44f * 6).toInt()
        if (ballY in 0.06f..0.5f && row in 0 until 6 && col in 0 until 8) {
            val hit = bricks.firstOrNull { it.col == col && it.row == row && it.alive }
            if (hit != null) {
                hit.alive = false
                score += when (hit.kind) { 2 -> 25; 1 -> 15; else -> 10 }
                velY = -velY
                if (bricks.none { it.alive }) {
                    level += 1
                    newLevel()
                }
            }
        }
        // Lost the ball.
        if (ballY > 1.02f) {
            lives -= 1
            if (lives <= 0) gameOver = true else serve = true
        }
    }

    fun restart() {
        score = 0; level = 1; lives = 3; gameOver = false
        paddleX = 0.5f
        newLevel()
    }
}

@Composable
fun ArkanoidScreen(
    viewModel: GamesViewModel,
    onBack: () -> Unit,
) {
    viewModel.selectGame(GameId.ARKANOID)
    val ranking by viewModel.currentRanking.collectAsStateWithLifecycle()
    val state = remember { ArkanoidState().apply { newLevel() } }

    LaunchedEffect(state.gameOver) {
        if (state.gameOver) viewModel.recordScore(GameId.ARKANOID, state.score, state.level)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            state.step()
            state.tick += 1
        }
    }

    GameScaffold(
        title = stringResource(R.string.game_arkanoid),
        score = state.score,
        level = state.level,
        lives = state.lives,
        hint = stringResource(R.string.game_arkanoid_hint),
        gameOver = state.gameOver,
        ranking = ranking,
        onBack = onBack,
        onRestart = { state.restart() },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.gameOver) {
                    detectTapGestures { state.launch() }
                }
                .pointerInput(state.gameOver) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        state.paddleX = (state.paddleX + dragAmount / size.width).coerceIn(0.11f, 0.89f)
                    }
                },
        ) {
            val cell = size.width / 8f
            drawRect(PixelArt.NIGHT_CELL)
            // Subtle forest floor at the bottom.
            drawRect(PixelArt.FOREST_DARK.copy(alpha = 0.35f), Offset(0f, size.height * 0.9f), Size(size.width, size.height * 0.1f))

            // The mushrooms.
            for (brick in state.bricks) {
                if (!brick.alive) continue
                val sprite = when (brick.kind) {
                    2 -> PixelArt.CHANTERELLE
                    1 -> PixelArt.GREEN_BOLETE
                    else -> PixelArt.AGARIC
                }
                val origin = Offset(brick.col * cell, 0.06f * size.height + brick.row * (0.44f * size.height / 6))
                drawSprite(sprite, origin, cell / 8f)
            }

            // The ball is a little puffball.
            drawSprite(
                PixelArt.PUFFBALL_GHOST,
                Offset(state.ballX * size.width - cell / 4f, state.ballY * size.height - cell / 4f),
                cell / 4f,
            )

            // The basket: a woven pixel paddle.
            val paddleW = 0.22f * size.width
            val paddleX = state.paddleX * size.width - paddleW / 2f
            drawRect(PixelArt.CAP_BROWN, Offset(paddleX, size.height * 0.86f), Size(paddleW, size.height * 0.03f))
            drawRect(PixelArt.STEM_SHADE, Offset(paddleX, size.height * 0.86f), Size(paddleW, size.height * 0.008f))

            if (state.serve && !state.gameOver) {
                drawRect(
                    PixelArt.CAP_GOLD,
                    Offset(size.width * 0.3f, size.height * 0.55f),
                    Size(size.width * 0.4f, 4f),
                )
            }
        }
    }
}
