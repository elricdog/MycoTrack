package com.mikotrack.ui.quiz

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Small wrapper around [TextToSpeech] for the hands-free quiz: reads a prompt
 * and reports back on the main thread once the player heard the whole thing,
 * which is when the game starts listening for the spoken answer.
 *
 * The engine is created lazily on first speech and released with the game.
 * When the voice pack for the quiz language is missing the engine falls back
 * to its default language, so the game still speaks instead of muting.
 */
class QuizTts(context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val appContext = context.applicationContext

    private var engine: TextToSpeech? = null
    private var ready = false
    private val initWaiters = mutableListOf<(Boolean) -> Unit>()

    /**
     * Increments on every [speak] / [stop]. Utterance ids carry the generation
     * they were spoken with, so callbacks from earlier utterances are ignored
     * once the game moved on.
     */
    private var generation = 0
    private val doneCallbacks = mutableMapOf<Int, () -> Unit>()

    /** Reads [text]; [onDone] runs on the main thread when playback finished. */
    fun speak(text: String, language: Locale, onDone: () -> Unit = {}) {
        val token = ++generation
        doneCallbacks.keys.retainAll(setOf(token))
        doneCallbacks[token] = onDone
        ensureEngine { engineReady ->
            if (token != generation) {
                doneCallbacks.remove(token)
            } else if (!engineReady) {
                doneCallbacks.remove(token)?.invoke()
            } else {
                engine?.setLanguage(language)
                engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "quiz-$token")
            }
        }
    }

    /** Cuts any speech short without invoking its pending completion. */
    fun stop() {
        generation++
        doneCallbacks.clear()
        val active = engine ?: return
        mainHandler.post { active.stop() }
    }

    fun shutdown() {
        generation++
        doneCallbacks.clear()
        val active = engine
        engine = null
        ready = false
        initWaiters.clear()
        if (active != null) {
            mainHandler.post {
                active.stop()
                active.shutdown()
            }
        }
    }

    private fun ensureEngine(onReady: (Boolean) -> Unit) {
        if (engine != null) {
            if (ready) onReady(true) else initWaiters += onReady
            return
        }
        initWaiters += onReady
        engine = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) = postDone(utteranceId)

                    override fun onError(utteranceId: String?) = postDone(utteranceId)
                })
            }
            val waiters = initWaiters.toList()
            initWaiters.clear()
            waiters.forEach { it(ready) }
        }
    }

    private fun postDone(utteranceId: String?) {
        mainHandler.post {
            val token = utteranceId?.removePrefix("quiz-")?.toIntOrNull() ?: return@post
            if (token == generation) {
                doneCallbacks.remove(token)?.invoke()
            }
        }
    }
}
