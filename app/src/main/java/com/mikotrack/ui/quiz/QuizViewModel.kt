package com.mikotrack.ui.quiz

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mikotrack.R
import com.mikotrack.data.prefs.QuizScore
import com.mikotrack.data.prefs.QuizScores
import com.mikotrack.data.prefs.SettingsStore
import com.mikotrack.maps.HabitatSources
import com.mikotrack.ui.mushrooms.MushroomCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Number of failed answers that end a quiz run. */
const val QUIZ_LIVES = 5

/** Points awarded for a correct answer, and per point of current streak. */
private const val QUIZ_BASE_POINTS = 10
private const val QUIZ_STREAK_BONUS = 2
private const val QUIZ_MAX_STREAK_BONUS_STEPS = 5

/** Misunderstood answers tolerated before voice play falls back to touch. */
private const val QUIZ_VOICE_FALLBACK = 3

/** The screen the quiz is showing right now. */
enum class QuizPhase { START, LOADING, PLAYING, FEEDBACK, GAME_OVER }

/** What a pending speech recognition is expected to capture. */
enum class QuizListenKind { ANSWER, NAME }

/** A speech recognition the screen must launch; token rises per request. */
data class QuizListenRequest(val token: Int, val kind: QuizListenKind)

/** Everything the quiz screen renders. */
data class QuizUiState(
    val phase: QuizPhase = QuizPhase.LOADING,
    val question: QuizQuestion? = null,
    /** 1-based number of the question in the current run. */
    val questionNumber: Int = 0,
    val lives: Int = QUIZ_LIVES,
    val score: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val correctCount: Int = 0,
    /** Chosen option while in [QuizPhase.FEEDBACK], null in other phases. */
    val selectedOption: Int? = null,
    val lastAnswerCorrect: Boolean = false,
    /** Points awarded by the just-answered question (shown in the feedback). */
    val lastEarnedPoints: Int = 0,
    val finishedScore: Int = 0,
    val finishedBestStreak: Int = 0,
    val finishedCorrect: Int = 0,
    /** True once the finished run has been saved to the leaderboard. */
    val scoreSaved: Boolean = false,
    /** Number of questions available in the pool for the current language. */
    val poolSize: Int = 0,
)

/**
 * Drives one quiz run: builds the deck out of the mushroom guide and the
 * curated culture banks, deals questions without repeating recent ones, keeps
 * score and lives, and records the leaderboard entry at the end.
 */
class QuizViewModel(
    application: Application,
    private val settingsStore: SettingsStore,
) : AndroidViewModel(application) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(QuizUiState())
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    val ranking: StateFlow<List<QuizScore>> = settingsStore.quizScores.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val storedName: StateFlow<String> = settingsStore.quizPlayerName.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "",
    )

    /** Whether the quiz speaks and accepts spoken answers. */
    val voiceMode: StateFlow<Boolean> = settingsStore.quizVoiceMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    private val _listenRequest = MutableStateFlow<QuizListenRequest?>(null)

    /** Non-null while the screen must open the speech recognizer. */
    val listenRequest: StateFlow<QuizListenRequest?> = _listenRequest.asStateFlow()

    private var ttsEngine: QuizTts? = null

    /** Lazily creates the speech engine; only voice play ever touches it. */
    private fun tts(): QuizTts = ttsEngine ?: QuizTts(getApplication()).also { ttsEngine = it }

    /** True while the current run drives its flow by voice. */
    private var voiceRun = false
    private var listenToken = 0
    private var notUnderstood = 0
    private var cancellations = 0

    private val speechLocale: java.util.Locale
        get() = java.util.Locale.getDefault()

    fun setVoiceMode(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setQuizVoiceMode(enabled) }
        if (!enabled) stopVoice()
    }

    /** Full deck for the current language, null while assets are loading. */
    private var deck: List<QuizQuestion>? = null

    /** Questions still unanswered this cycle, consumed from the front. */
    private var queue: List<QuizQuestion> = emptyList()

    /** Keys of the questions asked this cycle, persisted to survive restarts. */
    private var seenKeys = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            seenKeys = settingsStore.quizSeenKeys.first().toMutableSet()
            prepareDeck()
        }
    }

    /** Loads deck assets off the main thread and moves to the start screen. */
    private suspend fun prepareDeck() {
        val built = deck ?: withContext(Dispatchers.IO) { buildDeck() }.also { deck = it }
        _state.value = _state.value.copy(phase = QuizPhase.START, poolSize = built.size)
    }

    private fun buildDeck(): List<QuizQuestion> {
        val context = getApplication<Application>()
        val language = MushroomCatalog.languageOf(java.util.Locale.getDefault())
        val mushrooms = MushroomCatalog.load(context, language)
        val curated = loadCuratedQuestions(context, language)
        val prompts = QuizPrompts(
            scientificName = context.getString(R.string.quiz_prompt_scientific_name),
            commonName = context.getString(R.string.quiz_prompt_common_name),
            season = context.getString(R.string.quiz_prompt_season),
            edibility = context.getString(R.string.quiz_prompt_edibility),
            habitat = context.getString(R.string.quiz_prompt_habitat),
        )
        val treeLabels = HabitatSources.treeLegend.associate { it.key to context.getString(it.labelRes) }
        return buildQuizDeck(mushrooms, curated, prompts, treeLabels, kotlin.random.Random(System.nanoTime()))
    }

    private fun loadCuratedQuestions(context: Application, language: String): List<CuratedQuestion> {
        val languages = buildList { add(language); add("ca"); add("es"); add("en") }
        for (lang in languages) {
            val parsed = runCatching {
                context.assets.open("mushrooms/quiz_$lang.json")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                    .let { json.decodeFromString<List<CuratedQuestion>>(it) }
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return emptyList()
    }

    /** Starts a new run; with [voice] the run is played hands free. */
    fun startGame(voice: Boolean) {
        val full = deck ?: return
        var fresh = full.filter { it.key !in seenKeys }.shuffled()
        if (fresh.isEmpty()) {
            // The whole pool has been asked: start a new cycle.
            seenKeys.clear()
            fresh = full.shuffled()
        }
        queue = fresh
        persistSeenKeys()
        voiceRun = voice
        notUnderstood = 0
        cancellations = 0
        _state.value = QuizUiState(
            phase = QuizPhase.PLAYING,
            question = fresh.first(),
            questionNumber = 1,
            lives = QUIZ_LIVES,
        )
        if (voiceRun) speakCurrentQuestion()
    }

    /** Registers the player's answer and shows the feedback. */
    fun answer(optionIndex: Int) {
        val current = _state.value
        if (current.phase != QuizPhase.PLAYING) return
        // A tap resolves any listening still in flight.
        _listenRequest.value = null
        ttsEngine?.stop()
        val question = current.question ?: return
        val correct = optionIndex == question.answerIndex
        val streak = if (correct) current.streak + 1 else 0
        val bonus = if (correct) {
            QUIZ_STREAK_BONUS * current.streak.coerceAtMost(QUIZ_MAX_STREAK_BONUS_STEPS)
        } else {
            0
        }
        _state.value = current.copy(
            phase = QuizPhase.FEEDBACK,
            selectedOption = optionIndex,
            lastAnswerCorrect = correct,
            lastEarnedPoints = if (correct) QUIZ_BASE_POINTS + bonus else 0,
            streak = streak,
            bestStreak = maxOf(current.bestStreak, streak),
            score = current.score + if (correct) QUIZ_BASE_POINTS + bonus else 0,
            correctCount = current.correctCount + if (correct) 1 else 0,
            lives = current.lives - if (correct) 0 else 1,
        )
        if (voiceRun) speakFeedbackThenNext(_state.value)
    }

    /** Moves to the next question, or ends the run when the lives are gone. */
    fun next() {
        val current = _state.value
        if (current.phase != QuizPhase.FEEDBACK) return
        if (current.question != null) {
            seenKeys += current.question.key
            persistSeenKeys()
        }
        if (current.lives <= 0) {
            _state.value = current.copy(
                phase = QuizPhase.GAME_OVER,
                selectedOption = null,
                finishedScore = current.score,
                finishedBestStreak = current.bestStreak,
                finishedCorrect = current.correctCount,
                scoreSaved = false,
            )
            if (voiceRun) speakGameOver()
            return
        }
        queue = queue.drop(1)
        val nextQuestion = queue.firstOrNull()
        if (nextQuestion == null) {
            // Deck exhausted mid-run: reuse it after clearing the memory.
            seenKeys.clear()
            persistSeenKeys()
            val shuffled = deck?.shuffled().orEmpty()
            queue = shuffled
            if (shuffled.isEmpty()) {
                _state.value = current.copy(
                    phase = QuizPhase.GAME_OVER,
                    finishedScore = current.score,
                    finishedBestStreak = current.bestStreak,
                    finishedCorrect = current.correctCount,
                )
                if (voiceRun) speakGameOver()
                return
            }
            _state.value = current.copy(
                phase = QuizPhase.PLAYING,
                question = shuffled.first(),
                questionNumber = current.questionNumber + 1,
                selectedOption = null,
            )
            if (voiceRun) speakCurrentQuestion()
            return
        }
        _state.value = current.copy(
            phase = QuizPhase.PLAYING,
            question = nextQuestion,
            questionNumber = current.questionNumber + 1,
            selectedOption = null,
        )
        if (voiceRun) speakCurrentQuestion()
    }

    private fun speakCurrentQuestion() {
        val context = getApplication<Application>()
        val question = _state.value.question ?: return
        val spoken = spokenOptions(question)
        tts().speak(
            context.getString(R.string.quiz_speak_question, question.prompt, spoken),
            speechLocale,
        ) { startListening(QuizListenKind.ANSWER) }
    }

    private fun speakFeedbackThenNext(updated: QuizUiState) {
        val context = getApplication<Application>()
        val question = updated.question ?: return
        val verdicts = context.resources.getStringArray(R.array.quiz_verdicts)
        val verdict = verdicts[if (updated.lastAnswerCorrect) 0 else 1]
        tts().speak(
            context.getString(
                R.string.quiz_speak_feedback,
                verdict,
                question.options[question.answerIndex],
                question.fact ?: "",
            ),
            speechLocale,
        ) { if (_state.value.phase == QuizPhase.FEEDBACK) next() }
    }

    private fun speakGameOver() {
        val context = getApplication<Application>()
        val finished = _state.value
        tts().speak(
            context.getString(R.string.quiz_speak_game_over, finished.finishedScore, finished.finishedCorrect),
            speechLocale,
        ) { askName() }
    }

    private fun askName() {
        val context = getApplication<Application>()
        val stored = storedName.value
        val prompt = if (stored.isNotBlank()) {
            context.getString(R.string.quiz_speak_name_stored, stored)
        } else {
            context.getString(R.string.quiz_speak_name)
        }
        tts().speak(prompt, speechLocale) { startListening(QuizListenKind.NAME) }
    }

    /**
     * Text read out for the options: star ratings become plain numbers with
     * the localized word for "stars", and every option is numbered so the
     * player can answer "opción dos".
     */
    private fun spokenOptions(question: QuizQuestion): String {
        val context = getApplication<Application>()
        val starsWord = context.getString(R.string.quiz_speak_stars)
        return question.options.mapIndexed { index, option ->
            val stars = option.count { c -> c == '\u2605' }
            val spoken = if (stars > 0 && option.all { c -> c == '\u2605' || c == ' ' }) {
                "$stars $starsWord"
            } else {
                option
            }
            context.getString(R.string.quiz_speak_option, index + 1, spoken)
        }.joinToString(" ")
    }

    private fun startListening(kind: QuizListenKind) {
        listenToken++
        _listenRequest.value = QuizListenRequest(listenToken, kind)
    }

    /** A speech recognizer transcript for the pending [listenRequest]. */
    fun onVoiceResult(transcript: String) {
        val request = _listenRequest.value ?: return
        _listenRequest.value = null
        val context = getApplication<Application>()
        if (transcript.isBlank()) {
            notUnderstood++
            onListeningFailed(
                context.getString(R.string.quiz_speak_not_understood_retry, QUIZ_VOICE_FALLBACK - notUnderstood)
            )
            return
        }
        when (request.kind) {
            QuizListenKind.NAME -> {
                notUnderstood = 0
                saveScore(transcript.trim())
                tts().speak(context.getString(R.string.quiz_speak_saved), speechLocale)
            }

            QuizListenKind.ANSWER -> {
                if (_state.value.phase != QuizPhase.PLAYING) return
                when (val command = QuizVoice.command(transcript)) {
                    VoiceCommand.REPEAT -> {
                        notUnderstood = 0
                        speakCurrentQuestion()
                    }

                    VoiceCommand.STOP -> {
                        stopVoice()
                        quitGame()
                    }

                    VoiceCommand.PLAY, null -> {
                        val question = _state.value.question ?: return
                        val match = QuizVoice.matchAnswer(transcript, question)
                        if (match != null) {
                            notUnderstood = 0
                            answer(match)
                        } else {
                            notUnderstood++
                            onListeningFailed(
                                context.getString(
                                    R.string.quiz_speak_not_understood_retry,
                                    QUIZ_VOICE_FALLBACK - notUnderstood,
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    /** The player cancelled (or the recognizer failed) a listening round. */
    fun onVoiceCancelled() {
        val request = _listenRequest.value ?: return
        _listenRequest.value = null
        if (request.kind == QuizListenKind.NAME) {
            // Name by voice skipped: leave the field for the keyboard.
            ttsEngine?.stop()
            return
        }
        cancellations++
        if (cancellations >= 2) {
            stopAutoListen()
        }
    }

    /**
     * Announces [message] and listens again, falling back to touch play
     * after a few unsuccessful attempts so nobody gets stuck.
     */
    private fun onListeningFailed(message: String) {
        val context = getApplication<Application>()
        if (notUnderstood >= QUIZ_VOICE_FALLBACK) {
            notUnderstood = 0
            stopAutoListen()
            tts().speak(context.getString(R.string.quiz_speak_tap_fallback), speechLocale)
            return
        }
        tts().speak(message, speechLocale) { startListening(QuizListenKind.ANSWER) }
    }

    /** Stops re-opening the recognizer; the run continues by tapping. */
    private fun stopAutoListen() {
        _listenRequest.value = null
        voiceRun = false
    }

    /** Stops voice expectations when the recognizer cannot even be launched. */
    fun stopVoiceForLaunchFailure() {
        stopVoiceInternal()
    }

    /** Silences speech and speech expectations without leaving the screen. */
    private fun stopVoice() {
        stopVoiceInternal()
    }

    private fun stopVoiceInternal() {
        _listenRequest.value = null
        voiceRun = false
        notUnderstood = 0
        cancellations = 0
        ttsEngine?.stop()
    }

    /** Stores the finished run under [name] in the leaderboard. */
    fun saveScore(name: String) {
        val current = _state.value
        if (current.scoreSaved) return
        val clean = QuizScores.sanitize(name)
        if (clean.isBlank()) return
        val score = current.finishedScore
        val savedAt = System.currentTimeMillis()
        viewModelScope.launch {
            settingsStore.setQuizPlayerName(clean)
            settingsStore.recordQuizScore(clean, score, savedAt)
        }
        _state.value = current.copy(scoreSaved = true)
    }

    /** Leaves the game over screen and returns to the start screen. */
    fun backToStart() {
        stopVoice()
        _state.value = QuizUiState(phase = QuizPhase.START, poolSize = deck?.size ?: 0)
    }

    /** Abandons a run in progress and returns to the start screen. */
    fun quitGame() {
        stopVoice()
        _state.value = QuizUiState(phase = QuizPhase.START, poolSize = deck?.size ?: 0)
    }

    override fun onCleared() {
        ttsEngine?.shutdown()
        ttsEngine = null
        super.onCleared()
    }

    private fun persistSeenKeys() {
        viewModelScope.launch { settingsStore.setQuizSeenKeys(seenKeys.toList()) }
    }
}
