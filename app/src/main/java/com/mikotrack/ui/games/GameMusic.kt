package com.mikotrack.ui.games

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * A tiny chiptune synthesiser: it renders a melody as square-wave samples through an
 * [AudioTrack], so a game can carry music without shipping any audio asset. The
 * [playForever] suspends, writing note after note, until its coroutine is cancelled
 * (the AudioTrack is released in the process).
 */
internal class ChiptuneLoop(
    /** The melody as (frequency in Hz, length in beats) pairs; 0 Hz is a rest. */
    private val melody: List<Pair<Double, Double>>,
    private val beatSeconds: Double = 0.30,
    private val sampleRate: Int = 22050,
    private val volume: Double = 0.22,
) {
    suspend fun playForever(scope: CoroutineScope) = withContext(Dispatchers.IO) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(sampleRate) // half a second of 16-bit mono
            .build()
        try {
            track.play()
            while (scope.isActive) {
                for ((freq, beats) in melody) {
                    if (!scope.isActive) break
                    writeNote(track, freq, beats * beatSeconds)
                }
            }
        } finally {
            try {
                track.stop()
            } catch (_: IllegalStateException) {
                // Already stopped.
            }
            track.release()
        }
    }

    /**
     * Streams one note to [track]. [AudioTrack.write] blocks while the buffer is
     * full, which paces the melody without extra timers.
     */
    private fun writeNote(track: AudioTrack, freq: Double, seconds: Double) {
        val frames = (seconds * sampleRate).toInt().coerceAtLeast(1)
        val chunk = ShortArray(2048)
        var written = 0
        while (written < frames) {
            val n = minOf(chunk.size, frames - written)
            for (k in 0 until n) {
                val t = (written + k) / sampleRate.toDouble()
                val progress = (written + k).toDouble() / frames
                // Fast attack, exponential decay, soft release: a plucked voice.
                val envelope = (1.0 - exp(-t * 40.0)) * exp(-t * 1.6) *
                    (if (progress > 0.9) (1.0 - progress) * 10.0 else 1.0)
                val wave = if (freq <= 0.0) 0.0 else if ((t * freq) % 1.0 < 0.5) 1.0 else -1.0
                // A softer second partial keeps the square wave from sounding harsh.
                val soft = if (freq <= 0.0) 0.0 else sin(2.0 * PI * freq * t) * 0.45
                val sample = (wave + soft) / 1.45 * envelope * volume
                chunk[k] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }
            if (track.write(chunk, 0, n) <= 0) return // track stopped
            written += n
        }
    }
}

/**
 * Korobeiniki, the 19th-century Russian folk theme the original Tetris music is
 * based on (public domain), as a [ChiptuneLoop] melody.
 */
internal fun korobeinikiTune(): ChiptuneLoop {
    val a4 = 440.0; val b4 = 493.88; val c5 = 523.25; val d5 = 587.33
    val e5 = 659.25; val f5 = 698.46; val g5 = 783.99; val a5 = 880.0
    return ChiptuneLoop(
        listOf(
            e5 to 1.0, b4 to 0.5, c5 to 0.5, d5 to 1.0, c5 to 0.5, b4 to 0.5,
            a4 to 1.0, a4 to 0.5, c5 to 0.5, e5 to 1.0, d5 to 0.5, c5 to 0.5,
            b4 to 1.5, c5 to 0.5, d5 to 1.0, e5 to 1.0,
            c5 to 1.0, a4 to 1.0, a4 to 2.0,
            0.0 to 1.0,
            d5 to 1.0, f5 to 1.0, a5 to 1.0, g5 to 0.5, f5 to 0.5,
            e5 to 1.5, c5 to 0.5, e5 to 1.0, d5 to 0.5, c5 to 0.5,
            b4 to 1.0, b4 to 0.5, c5 to 0.5, d5 to 1.0, e5 to 1.0,
            c5 to 1.0, a4 to 1.0, a4 to 2.0,
            0.0 to 2.0,
        ),
    )
}

/**
 * The theme of Beethoven's "Ode to Joy" (public domain) as (frequency, beats) pairs.
 * Shared by the rhythm game's chiptune and its beatmap, so notes and music match.
 */
internal fun odeToJoyNotes(): List<Pair<Double, Double>> {
    val c4 = 261.63; val d4 = 293.66; val e4 = 329.63; val f4 = 349.23; val g4 = 392.00
    fun phrase(endLong: List<Pair<Double, Double>>) = listOf(
        e4 to 1.0, e4 to 1.0, f4 to 1.0, g4 to 1.0,
        g4 to 1.0, f4 to 1.0, e4 to 1.0, d4 to 1.0,
        c4 to 1.0, c4 to 1.0, d4 to 1.0, e4 to 1.0,
    ) + endLong
    val longShort = listOf(e4 to 1.5, d4 to 0.5)
    val longRest = listOf(e4 to 1.5, 0.0 to 0.5)
    return phrase(longShort) + phrase(longRest) +
        listOf(
            g4 to 1.0, g4 to 1.0, f4 to 1.0, e4 to 1.0,
            d4 to 1.0, c4 to 1.0, d4 to 1.0, e4 to 1.0,
            e4 to 1.0, e4 to 1.0, f4 to 1.0, g4 to 1.0,
            g4 to 1.0, f4 to 1.0, e4 to 1.0, d4 to 1.0,
        ) + phrase(longShort) + phrase(longRest)
}

/** The same melody as a playable [ChiptuneLoop]. */
internal fun odeToJoyTune(beatSeconds: Double = 0.30): ChiptuneLoop =
    ChiptuneLoop(odeToJoyNotes(), beatSeconds = beatSeconds)
