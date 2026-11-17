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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.mikotrack.R
import com.mikotrack.data.prefs.GameId
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pixel arkanoid: a wooden basket slides under the finger and bounces spore balls
 * against rows of mushrooms. Red caps are worth 10 points, green boletes 15 and
 * golden chanterelles 25. Broken golden caps drop power-ups: three balls at once
 * or a burst of spore bullets shot from the basket with a double tap. Clearing
 * every brick opens the next row set, faster.
 */
private class ArkanoidState {
    var score by mutableIntStateOf(0)
    var level by mutableIntStateOf(1)
    var lives by mutableIntStateOf(3)
    var gameOver by mutableStateOf(false)
    var tick by mutableLongStateOf(0L)

    /** 0..1 positions, relative to the playfield. */
    var paddleX by mutableStateOf(0.5f)
    var balls by mutableStateOf(listOf(Ball(0.5f, 0.7f)))
    var bricks by mutableStateOf(emptyList<Brick>())
    var drops by mutableStateOf(emptyList<Drop>())
    var bullets by mutableStateOf(emptyList<Bullet>())
    /** Frames of rapid-fire left after grabbing a shooter. */
    var fireFrames by mutableIntStateOf(0)
    var serve = true

    data class Ball(var x: Float, var y: Float, var vx: Float = 0.012f, var vy: Float = -0.016f)
    data class Brick(val col: Int, val row: Int, val kind: Int, var alive: Boolean = true)
    /** A falling power-up: kind 0 = multiball, 1 = shooter. */
    data class Drop(var x: Float, var y: Float, val kind: Int)
    data class Bullet(var x: Float, var y: Float)

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
        balls = listOf(Ball(0.5f, 0.7f))
        drops = emptyList()
        bullets = emptyList()
        fireFrames = 0
    }

    fun launch() {
        val dir = if (Random.nextBoolean()) 1 else -1
        balls = balls.map { it.copy(vx = dir * (0.010f + level * 0.001f), vy = -(0.016f + level * 0.001f)) }
        serve = false
    }

    /** Double tap: shoots spore bullets from the basket (rapid-fire after a pickup). */
    fun shoot() {
        if (serve) return
        bullets = bullets + listOf(Bullet(paddleX - 0.05f, 0.84f), Bullet(paddleX + 0.05f, 0.84f))
    }

    private fun tryFire() {
        if (fireFrames > 0 && tick % 12L == 0L) shoot()
    }

    fun step() {
        if (gameOver) return
        if (serve) {
            balls = balls.map { it.copy(x = paddleX, y = 0.72f) }
            return
        }
        tryFire()

        val aliveBalls = mutableListOf<Ball>()
        for (ball in balls) {
            ball.x += ball.vx
            ball.y += ball.vy
            // Walls.
            if (ball.x < 0.02f) { ball.x = 0.02f; ball.vx = abs(ball.vx) }
            if (ball.x > 0.98f) { ball.x = 0.98f; ball.vx = -abs(ball.vx) }
            if (ball.y < 0.02f) { ball.y = 0.02f; ball.vy = abs(ball.vy) }
            // The paddle sits at y = 0.86 and is 0.22 wide.
            if (ball.vy > 0 && ball.y in 0.84f..0.88f && abs(ball.x - paddleX) < 0.13f) {
                ball.vy = -abs(ball.vy)
                // The impact point steers the ball, like in the arcade original.
                val offset = (ball.x - paddleX) / 0.13f
                ball.vx = ball.vx * 0.8f + offset * 0.008f
            }
            // Bricks: a 8 x 6 grid between y = 0.06 and 0.5.
            val col = (ball.x * 8).toInt()
            val row = ((ball.y - 0.06f) / 0.44f * 6).toInt()
            if (ball.y in 0.06f..0.5f && row in 0 until 6 && col in 0 until 8) {
                val hit = bricks.firstOrNull { it.col == col && it.row == row && it.alive }
                if (hit != null) {
                    hit.alive = false
                    score += when (hit.kind) { 2 -> 25; 1 -> 15; else -> 10 }
                    ball.vy = -ball.vy
                    // Golden caps sometimes release a power-up.
                    if (hit.kind == 2 && Random.nextInt(3) != 0) {
                        drops = drops + Drop((hit.col + 0.5f) / 8f, 0.06f + hit.row * 0.0733f, Random.nextInt(2))
                    }
                    if (bricks.none { it.alive }) {
                        level += 1
                        newLevel()
                        return
                    }
                }
            }
            // Lost the ball.
            if (ball.y < 1.02f) aliveBalls += ball
        }
        if (aliveBalls.isEmpty()) {
            lives -= 1
            if (lives <= 0) gameOver = true else {
                balls = listOf(Ball(0.5f, 0.7f))
                serve = true
            }
        } else {
            balls = aliveBalls
        }

        // Falling power-ups: caught by the basket or lost in the undergrowth.
        if (drops.isNotEmpty()) {
            val still = drops.map { it.copy(y = it.y + 0.004f) }.filter { it.y < 1.0f }
            val caught = still.filter { it.y in 0.84f..0.9f && abs(it.x - paddleX) < 0.14f }
            for (d in caught) {
                when (d.kind) {
                    0 -> {
                        // Multiball: two siblings leave the caught ball at angles.
                        val b = balls.firstOrNull()
                        if (b != null) {
                            balls = balls + Ball(b.x, b.y, 0.011f, -0.015f) + Ball(b.x, b.y, -0.011f, -0.015f)
                        }
                    }
                    1 -> fireFrames = 8 * 60
                }
            }
            drops = still.filter { it !in caught }
        }
        if (fireFrames > 0) fireFrames -= 1

        // Bullets fly up and punch bricks.
        if (bullets.isNotEmpty()) {
            val remaining = mutableListOf<Bullet>()
            var changed = false
            for (bu in bullets.map { it.copy(y = it.y - 0.02f) }) {
                if (bu.y <= 0.02f) continue
                val col = (bu.x * 8).toInt()
                val row = ((bu.y - 0.06f) / 0.44f * 6).toInt()
                val hit = if (bu.y in 0.06f..0.5f && row in 0 until 6 && col in 0 until 8) {
                    bricks.firstOrNull { it.col == col && it.row == row && it.alive }
                } else null
                if (hit == null) {
                    remaining += bu
                } else {
                    changed = true
                    hit.alive = false
                    score += when (hit.kind) { 2 -> 25; 1 -> 15; else -> 10 }
                    if (hit.kind == 2 && Random.nextInt(3) != 0) {
                        drops = drops + Drop((hit.col + 0.5f) / 8f, 0.06f + hit.row * 0.0733f, Random.nextInt(2))
                    }
                }
            }
            if (!changed && remaining.size == bullets.size) {
                bullets = remaining
            } else {
                bullets = remaining
                if (bricks.none { it.alive }) {
                    level += 1
                    newLevel()
                    return
                }
            }
        }
    }

    fun restart() {
        score = 0; level = 1; lives = 3; gameOver = false
        paddleX = 0.5f
        newLevel()
    }
}

/** A spore ball, 8 x 8: a round spotted cap, drawn as a proper ball. */
private val SPORE_BALL = PixelArt.Sprite(
    listOf(
        "..RRRR..",
        ".RWRRWR.",
        "RRWWRRWR",
        "RRRRRRRR",
        "RRRBBRRR",
        "RBBBBBBR",
        ".BBBBBB.",
        "..BBBB..",
    ),
    mapOf(
        'R' to PixelArt.CAP_RED,
        'W' to PixelArt.SPOT,
        'B' to PixelArt.CAP_DARK_RED,
    ),
)

/** A spore ball: a pixel mushroom-cap ball drawn centred on (cx, cy). */
internal fun DrawScope.drawSporeBall(cx: Float, cy: Float, r: Float) {
    drawSprite(SPORE_BALL, Offset(cx - r, cy - r), r / 4f)
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
                    detectTapGestures(
                        onDoubleTap = { state.shoot() },
                        onTap = { state.launch() },
                    )
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

            // The spore balls: real pixel balls with a soft glow, centred on their
            // physics positions.
            val ballR = size.width / 26f
            for (ball in state.balls) {
                drawCircle(
                    color = PixelArt.CAP_GOLD.copy(alpha = 0.18f),
                    radius = ballR * 1.45f,
                    center = Offset(ball.x * size.width, ball.y * size.height),
                )
                drawSporeBall(ball.x * size.width, ball.y * size.height, ballR)
            }

            // Falling power-ups: gold = multiball, blue = shooter.
            for (drop in state.drops) {
                val dc = Offset(drop.x * size.width, drop.y * size.height)
                drawCircle(
                    color = if (drop.kind == 0) PixelArt.CAP_GOLD else PixelArt.SLIME,
                    radius = size.width / 30f,
                    center = dc,
                )
                drawCircle(
                    color = PixelArt.NIGHT,
                    radius = size.width / 30f,
                    center = dc,
                    style = Stroke(width = 2f),
                )
                // Tiny pixel marks: three dots for multiball, an arrow for the shooter.
                val mark = PixelArt.SPOT
                if (drop.kind == 0) {
                    for (dx in -1..1) {
                        drawCircle(mark, radius = 2.5f, center = Offset(dc.x + dx * 6f, dc.y))
                    }
                } else {
                    drawRect(mark, Offset(dc.x - 2f, dc.y - 6f), Size(4f, 12f))
                    drawCircle(mark, radius = 3f, center = Offset(dc.x, dc.y - 6f))
                }
            }

            // Spore bullets.
            for (bu in state.bullets) {
                drawCircle(
                    PixelArt.SLIME,
                    radius = size.width / 60f,
                    center = Offset(bu.x * size.width, bu.y * size.height),
                )
            }

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
            // While the shooter is armed, the basket glows.
            if (state.fireFrames > 0 && !state.gameOver) {
                drawRect(
                    PixelArt.SLIME.copy(alpha = 0.5f + 0.3f * sin(state.tick / 6f)),
                    Offset(paddleX, size.height * 0.86f - 3f),
                    Size(paddleW, 3f),
                )
            }
        }
    }
}
