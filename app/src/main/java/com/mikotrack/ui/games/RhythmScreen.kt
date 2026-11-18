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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mikotrack.R
import com.mikotrack.data.prefs.GameId

/**
 * Rhythm game: the "Ode to Joy" plays as a chiptune while a mushroom note falls
 * down its lane, landing on the golden line exactly as the tune sounds the note.
 * Tap the lane in time: perfect hits pay double. Five escaped notes end the jam,
 * and every completed loop of the melody raises the level (and the point rate).
 */
private const val BEAT_SECONDS = 0.30
private const val TRAVEL_BEATS = 4.0

/** One melody note falling down a lane. State: 0 upcoming, 1 hit, 2 missed. */
private class BeatNote(val beat: Double, val lane: Int, var state: Int = 0)

private class RhythmState {
    var score by mutableIntStateOf(0)
    var level by mutableIntStateOf(1)
    var lives by mutableIntStateOf(5)
    var combo by mutableIntStateOf(0)
    var gameOver by mutableStateOf(false)
    var tick by mutableLongStateOf(0L)
    var notes by mutableStateOf(emptyList<BeatNote>())

    /** Beat clock: 0 at the start of the current loop. */
    var beat = 0.0
    var loopBeats = 0.0
    var laneFlash = -1
    var flashTick = 0L

    fun buildLoop() {
        var beatAcc = 0.0
        notes = odeToJoyNotes().mapNotNull { (freq, beats) ->
            val start = beatAcc
            beatAcc += beats
            if (freq <= 0.0) null else BeatNote(start, laneFor(freq))
        }
        loopBeats = beatAcc
        beat = 0.0
    }

    private fun laneFor(freq: Double): Int = when {
        freq < 280.0 -> 0
        freq < 320.0 -> 1
        freq < 355.0 -> 2
        else -> 3
    }

    fun step(dtBeats: Double) {
        if (gameOver) return
        beat += dtBeats
        if (beat >= loopBeats) {
            // The melody looped: that is a "level". Rebuild the same beatmap.
            level += 1
            score += 500
            buildLoop()
            return
        }
        for (n in notes) {
            if (n.state == 0 && beat > n.beat + 0.35) {
                n.state = 2
                combo = 0
                lives -= 1
                if (lives <= 0) {
                    gameOver = true
                    return
                }
            }
        }
    }

    /** Tap on a lane: judge the closest live note of that lane. */
    fun tap(lane: Int) {
        if (gameOver) return
        val candidate = notes.filter { it.state == 0 && it.lane == lane }
            .minByOrNull { kotlin.math.abs(it.beat - beat) } ?: return
        val delta = kotlin.math.abs(candidate.beat - beat)
        when {
            delta <= 0.07 -> {
                candidate.state = 1
                combo += 1
                score += 100 * level + combo * 5
                laneFlash = lane
                flashTick = tick
            }
            delta <= 0.16 -> {
                candidate.state = 1
                combo += 1
                score += 50 * level
                laneFlash = lane
                flashTick = tick
            }
        }
    }

    fun restart() {
        score = 0; level = 1; lives = 5; combo = 0; gameOver = false
        buildLoop()
    }
}

@Composable
fun RhythmScreen(
    viewModel: GamesViewModel,
    onBack: () -> Unit,
) {
    viewModel.selectGame(GameId.RHYTHM)
    val ranking by viewModel.currentRanking.collectAsStateWithLifecycle()
    val state = remember { RhythmState().apply { buildLoop() } }

    LaunchedEffect(state.gameOver) {
        if (state.gameOver) viewModel.recordScore(GameId.RHYTHM, state.score, state.level)
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { PixelArt.warmUp() }
        launch { odeToJoyTune(beatSeconds = BEAT_SECONDS).playForever(this@LaunchedEffect) }
        var prev = 0L
        while (true) {
            withFrameNanos { now ->
                if (prev != 0L) state.step((now - prev) / 1_000_000_000.0 / BEAT_SECONDS)
                prev = now
                state.tick += 1
            }
        }
    }

    GameScaffold(
        title = stringResource(R.string.game_rhythm),
        score = state.score,
        level = state.level,
        lives = state.lives,
        hint = stringResource(R.string.game_rhythm_hint),
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
                        val lane = (offset.x / (size.width / 4f)).toInt().coerceIn(0, 3)
                        state.tap(lane)
                    }
                },
        ) {
            // Notes are mutated in place: read the frame counter so the canvas
            // redraws after every physics step.
            @Suppress("UNUSED_VARIABLE")
            val frame = state.tick

            val laneW = size.width / 4f
            val hitY = size.height * 0.82f
            drawRect(PixelArt.NIGHT)
            for (lane in 0 until 4) {
                drawLine(
                    color = PixelArt.NIGHT_CELL,
                    start = Offset((lane + 1) * laneW, 0f),
                    end = Offset((lane + 1) * laneW, size.height),
                    strokeWidth = 2f,
                )
            }
            // The hit zone glows briefly on every judged tap.
            if (state.laneFlash >= 0 && state.tick - state.flashTick < 12) {
                drawRect(
                    PixelArt.CAP_GOLD.copy(alpha = 0.10f),
                    Offset(state.laneFlash * laneW, 0f),
                    Size(laneW, size.height),
                )
            }
            drawRect(PixelArt.CAP_GOLD, Offset(0f, hitY), Size(size.width, 4f))

            val cell = laneW / 9f
            for (n in state.notes) {
                if (n.state != 0) continue
                val progress = (state.beat - (n.beat - TRAVEL_BEATS)) / TRAVEL_BEATS
                if (progress < 0.0) continue
                val x = n.lane * laneW + laneW / 2f - 4f * cell
                val y = progress.toFloat() * hitY - 4f * cell
                val noteSprite = when (n.lane) {
                    0 -> PixelArt.CEP
                    1 -> PixelArt.AGARIC
                    2 -> PixelArt.CHANTERELLE
                    else -> PixelArt.GREEN_BOLETE
                }
                drawSprite(noteSprite, Offset(x, y), cell)
            }
            // Combo counter: a slim bar above the hit line while a chain is alive.
            if (state.combo > 1) {
                drawRect(
                    PixelArt.SLIME.copy(alpha = 0.8f),
                    Offset(size.width * 0.42f, size.height * 0.03f),
                    Size(size.width * 0.16f, 3f),
                )
            }
            // A small frame around the hit line targets.
            for (lane in 0 until 4) {
                drawRect(
                    PixelArt.CREAM.copy(alpha = 0.35f),
                    Offset(lane * laneW + laneW * 0.2f, hitY - 14f),
                    Size(laneW * 0.6f, 2f),
                )
            }
        }
    }
}
