package com.mikotrack.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
 * Pixel pacman in the forest: the spore dots are eaten with the mushroom hero, the
 * puffball ghosts chase it through the mossy paths of the clearing. Swipe in any
 * direction to steer. Dots are worth 10 points, the glowing golden spores 50, and
 * eating a ghost 200. Clearing the clearing opens the next, faster level.
 */
private class PacmanState {

    companion object {
        val WALL: Set<Char> = setOf('#')

        /** The clearing: 15 x 17. '#' wall, '.' dot, 'o' power spore, 'P' hero, 'G' ghost. */
        private const val MAP =
            "###############" +
            "#o.....#.....o#" +
            "#.###.###.###.#" +
            "#.#.o..#..o.#.#" +
            "#...###.###...#" +
            "###.#.....#.###" +
            "#...#.G.G.#...#" +
            "#.###.###.###.#" +
            "#o..P..#..P..o#" +
            "#.###.###.###.#" +
            "###.#.....#.###" +
            "#...###.###...#" +
            "#.#.o..#..o.#.#" +
            "#.###.###.###.#" +
            "#o.....#.....o#" +
            "###############"
        const val W = 15
        const val H = 17
        private val DIRS = listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f))
    }

    var score by mutableIntStateOf(0)
    var level by mutableIntStateOf(1)
    var lives by mutableIntStateOf(3)
    var gameOver by mutableStateOf(false)
    var tick by mutableLongStateOf(0L)
    var fright by mutableIntStateOf(0)

    val tiles = Array(H) { CharArray(W) }
    var heroX by mutableIntStateOf(2)
    var heroY by mutableIntStateOf(8)
    var heroDir by mutableStateOf(Offset(-1f, 0f))
    var pendingDir by mutableStateOf(Offset(0f, 0f))
    var ghosts by mutableStateOf(listOf(Offset(7f, 6f), Offset(10f, 6f)))
    var ghostDirs by mutableStateOf(listOf(Offset(0f, -1f), Offset(0f, 1f)))
    var openDots by mutableIntStateOf(0)

    fun loadMap() {
        for (y in 0 until H) for (x in 0 until W) tiles[y][x] = MAP[y * W + x]
        openDots = tiles.sumOf { row -> row.count { it == '.' || it == 'o' } }
        heroX = 2; heroY = 8; heroDir = Offset(1f, 0f); pendingDir = Offset(0f, 0f)
        ghosts = listOf(Offset(7f, 6f), Offset(10f, 6f))
        ghostDirs = listOf(Offset(0f, -1f), Offset(0f, 1f))
    }

    fun wallAt(x: Int, y: Int) = x < 0 || y < 0 || x >= W || y >= H || tiles[y][x] in WALL

    fun canGo(x: Int, y: Int, d: Offset) = !wallAt(x + d.x.toInt(), y + d.y.toInt())

    fun eat(x: Int, y: Int) {
        when (tiles[y][x]) {
            '.' -> { tiles[y][x] = ' '; openDots -= 1; score += 10 }
            'o' -> { tiles[y][x] = ' '; openDots -= 1; score += 50; fright = 60 }
        }
        if (openDots <= 0) {
            level += 1
            loadMap()
        }
    }

    fun step() {
        if (gameOver) return
        tick += 1
        if (fright > 0) fright -= 1

        // The hero moves one cell every two ticks, in the pending direction when free.
        if (tick % 2 == 0L) {
            if (pendingDir != Offset.Zero && canGo(heroX, heroY, pendingDir)) heroDir = pendingDir
            if (canGo(heroX, heroY, heroDir)) {
                heroX += heroDir.x.toInt()
                heroY += heroDir.y.toInt()
                eat(heroX, heroY)
            }
        }
        // The ghosts move every three ticks (faster with the level), chasing the hero.
        val ghostPeriod = (3 - (level / 2)).coerceAtLeast(1)
        if (tick % ghostPeriod == 0L) {
            val next = mutableListOf<Offset>()
            val nextDirs = mutableListOf<Offset>()
            ghosts.forEachIndexed { i, g ->
                var gx = g.x.toInt()
                var gy = g.y.toInt()
                val options = DIRS.filter { canGo(gx, gy, it) && it != -ghostDirs[i] }
                val pick = if (options.isEmpty()) -ghostDirs[i] else {
                    options.minByOrNull { d ->
                        val nx = gx + d.x.toInt()
                        val ny = gy + d.y.toInt()
                        val dist = kotlin.math.abs(nx - heroX) + kotlin.math.abs(ny - heroY)
                        // Frightened ghosts run away; normal ghosts chase, with a pinch of chance.
                        val signed = if (fright > 0) -dist else dist
                        if (Random.nextInt(4) == 0) Random.nextDouble() else signed.toDouble()
                    } ?: ghostDirs[i]
                if (canGo(gx, gy, pick)) {
                    gx += pick.x.toInt()
                    gy += pick.y.toInt()
                }
                next += Offset(gx.toFloat(), gy.toFloat())
                nextDirs += pick
            }
            ghosts = next
            ghostDirs = nextDirs
        }

        // Collisions.
        ghosts.forEachIndexed { i, g ->
            if (g.x.toInt() == heroX && g.y.toInt() == heroY) {
                if (fright > 0) {
                    score += 200
                    ghosts = ghosts.toMutableList().also { it[i] = Offset(7f, 6f) }
                } else {
                    lives -= 1
                    if (lives <= 0) {
                        gameOver = true
                    } else {
                        heroX = 2; heroY = 8; heroDir = Offset(1f, 0f)
                        ghosts = listOf(Offset(7f, 6f), Offset(10f, 6f))
                    }
                }
            }
        }
    }

    fun restart() {
        score = 0; level = 1; lives = 3; gameOver = false; tick = 0; fright = 0
        loadMap()
    }
}

@Composable
fun PacmanScreen(
    viewModel: GamesViewModel,
    onBack: () -> Unit,
) {
    viewModel.selectGame(GameId.PACMAN)
    val ranking by viewModel.currentRanking.collectAsStateWithLifecycle()
    val state = remember { PacmanState().apply { loadMap() } }

    LaunchedEffect(state.gameOver) {
        if (state.gameOver) viewModel.recordScore(GameId.PACMAN, state.score, state.level)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60)
            state.step()
        }
    }

    GameScaffold(
        title = stringResource(R.string.game_pacman),
        score = state.score,
        level = state.level,
        lives = state.lives,
        hint = stringResource(R.string.game_pacman_hint),
        gameOver = state.gameOver,
        ranking = ranking,
        onBack = onBack,
        onRestart = { state.restart() },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.gameOver) {
                    var accX = 0f
                    var accY = 0f
                    detectDragGestures(onDragStart = { accX = 0f; accY = 0f }) { change, drag ->
                        change.consume()
                        accX += drag.x
                        accY += drag.y
                        val threshold = 30f
                        if (kotlin.math.abs(accX) > threshold || kotlin.math.abs(accY) > threshold) {
                            state.pendingDir = if (kotlin.math.abs(accX) > kotlin.math.abs(accY)) {
                                Offset(kotlin.math.sign(accX), 0f)
                            } else {
                                Offset(0f, kotlin.math.sign(accY))
                            }
                            accX = 0f; accY = 0f
                        }
                    }
                },
        ) {
            val cell = minOf(size.width / PacmanState.W, size.height / PacmanState.H)
            val left = (size.width - cell * PacmanState.W) / 2f
            val top = (size.height - cell * PacmanState.H) / 2f

            drawRect(PixelArt.NIGHT, Offset(left - 4f, top - 4f), Size(cell * PacmanState.W + 8f, cell * PacmanState.H + 8f))
            drawRect(PixelArt.NIGHT_CELL, Offset(left, top), Size(cell * PacmanState.W, cell * PacmanState.H))

            for (y in 0 until PacmanState.H) {
                for (x in 0 until PacmanState.W) {
                    val ox = left + x * cell
                    val oy = top + y * cell
                    when (state.tiles[y][x]) {
                        '#' -> {
                            drawRect(PixelArt.FOREST_DARK, Offset(ox + 1f, oy + 1f), Size(cell - 2f, cell - 2f))
                            drawRect(PixelArt.FOREST.copy(alpha = 0.6f), Offset(ox + 2f, oy + 2f), Size(cell - 4f, (cell - 4f) * 0.4f))
                        }
                        '.' -> drawCircle(PixelArt.CREAM, radius = cell * 0.09f, center = Offset(ox + cell / 2f, oy + cell / 2f))
                        'o' -> {
                            drawCircle(PixelArt.CAP_GOLD, radius = cell * 0.22f, center = Offset(ox + cell / 2f, oy + cell / 2f))
                            drawCircle(PixelArt.SPOT, radius = cell * 0.08f, center = Offset(ox + cell / 2f, oy + cell / 2f))
                        }
                    }
                }
            }

            // The hero mushroom and the ghost puffballs.
            drawSprite(
                PixelArt.CEP,
                Offset(left + state.heroX * cell, top + state.heroY * cell),
                cell / 8f,
            )
            val ghostPalette = if (state.fright > 0) {
                PixelArt.PUFFBALL_GHOST.palette + mapOf('W' to PixelArt.SLIME)
            } else {
                PixelArt.PUFFBALL_GHOST.palette
            }
            for (g in state.ghosts) {
                drawSprite(
                    PixelArt.PUFFBALL_GHOST.copy(palette = ghostPalette),
                    Offset(left + g.x * cell, top + g.y * cell),
                    cell / 8f,
                )
            }
        }
    }
}
