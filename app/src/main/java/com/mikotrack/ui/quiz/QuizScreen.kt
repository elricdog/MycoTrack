package com.mikotrack.ui.quiz

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikotrack.R
import com.mikotrack.data.prefs.QuizScore
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** The four-option trivia game about mushrooms and the mushroom world. */
@Composable
fun QuizScreen(viewModel: QuizViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ranking by viewModel.ranking.collectAsStateWithLifecycle()
    val storedName by viewModel.storedName.collectAsStateWithLifecycle()
    val voiceMode by viewModel.voiceMode.collectAsStateWithLifecycle()
    val autoAdvance by viewModel.autoAdvance.collectAsStateWithLifecycle()
    val listenRequest by viewModel.listenRequest.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    // One recognizer intent per expected answer kind, in the phone language.
    fun recognizerIntent(prompt: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)

    val probe = remember { recognizerIntent("") }
    val voiceAvailable = remember(probe) { probe.resolveActivity(context.packageManager) != null }
    val recognizerUnavailable = stringResource(R.string.quiz_voice_unavailable)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            viewModel.onVoiceCancelled()
            return@rememberLauncherForActivityResult
        }
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        viewModel.onVoiceResult(text)
    }
    LaunchedEffect(listenRequest?.token) {
        val request = listenRequest ?: return@LaunchedEffect
        val prompt = context.getString(
            if (request.kind == QuizListenKind.NAME) R.string.quiz_voice_prompt_name
            else R.string.quiz_voice_prompt_answer,
        )
        try {
            launcher.launch(recognizerIntent(prompt))
        } catch (_: ActivityNotFoundException) {
            viewModel.stopVoiceForLaunchFailure()
            android.widget.Toast.makeText(context, recognizerUnavailable, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // Auto-advance: press "next" five seconds after each feedback, unless the
    // run is played by voice (the speech itself drives the pace then).
    LaunchedEffect(state.phase, state.questionNumber, autoAdvance, voiceMode) {
        if (
            autoAdvance &&
            !(voiceMode && voiceAvailable) &&
            state.phase == QuizPhase.FEEDBACK
        ) {
            kotlinx.coroutines.delay(5_000)
            viewModel.next()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(76.dp))
        when (state.phase) {
            QuizPhase.LOADING -> Text(
                text = "🍄",
                style = MaterialTheme.typography.displayMedium,
            )

            QuizPhase.START -> QuizStartContent(
                state = state,
                ranking = ranking,
                voiceMode = voiceMode,
                voiceAvailable = voiceAvailable,
                onVoiceModeChange = viewModel::setVoiceMode,
                autoAdvance = autoAdvance,
                onAutoAdvanceChange = viewModel::setAutoAdvance,
                onStart = { viewModel.startGame(voiceMode && voiceAvailable) },
            )

            QuizPhase.PLAYING, QuizPhase.FEEDBACK -> QuizPlayContent(
                state = state,
                listening = listenRequest?.kind == QuizListenKind.ANSWER,
                onAnswer = viewModel::answer,
                onNext = {
                    // Tapping while the recognizer is open ends that listening.
                    if (listenRequest != null) viewModel.onVoiceCancelled()
                    viewModel.next()
                },
                onQuit = viewModel::quitGame,
            )

            QuizPhase.GAME_OVER -> QuizGameOverContent(
                state = state,
                storedName = storedName,
                ranking = ranking,
                awaitingName = listenRequest?.kind == QuizListenKind.NAME,
                onSave = { name ->
                    if (listenRequest?.kind == QuizListenKind.NAME) viewModel.onVoiceCancelled()
                    viewModel.saveScore(name)
                },
                onPlayAgain = viewModel::backToStart,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun QuizStartContent(
    state: QuizUiState,
    ranking: List<QuizScore>,
    voiceMode: Boolean,
    voiceAvailable: Boolean,
    onVoiceModeChange: (Boolean) -> Unit,
    autoAdvance: Boolean,
    onAutoAdvanceChange: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    Text("🍄", style = MaterialTheme.typography.displayLarge)
    Text(
        text = stringResource(R.string.quiz_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.quiz_tagline),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
    if (state.poolSize > 0) {
        Text(
            text = stringResource(R.string.quiz_pool_size, state.poolSize),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onStart,
        enabled = state.poolSize > 0,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(
            text = "▶ " + stringResource(R.string.quiz_start) + " 🧺",
            style = MaterialTheme.typography.titleMedium,
        )
    }
    Spacer(Modifier.height(12.dp))
    if (voiceAvailable) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🎤 " + stringResource(R.string.quiz_voice_mode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.quiz_voice_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = voiceMode, onCheckedChange = onVoiceModeChange)
        }
    } else {
        Text(
            text = stringResource(R.string.quiz_voice_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "⏩ " + stringResource(R.string.quiz_auto_next),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.quiz_auto_next_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = autoAdvance, onCheckedChange = onAutoAdvanceChange)
    }
    Spacer(Modifier.height(24.dp))
    QuizRankingCard(ranking)
}

@Composable
private fun QuizRankingCard(ranking: List<QuizScore>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "🏆 " + stringResource(R.string.quiz_ranking),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (ranking.isEmpty()) {
                Text(
                    text = stringResource(R.string.quiz_ranking_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                ranking.forEachIndexed { index, entry -> QuizRankingRow(index, entry) }
            }
        }
    }
}

@Composable
private fun QuizRankingRow(index: Int, entry: QuizScore) {
    val medal = when (index) {
        0 -> "🥇"
        1 -> "🥈"
        2 -> "🥉"
        else -> "${index + 1}."
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$medal ${entry.name}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (index < 3) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${entry.score}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = " " + DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault())
                .format(Date(entry.atMillis)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuizPlayContent(
    state: QuizUiState,
    listening: Boolean,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    onQuit: () -> Unit,
) {
    val question = state.question ?: return
    val feedback = state.phase == QuizPhase.FEEDBACK

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onQuit, enabled = !feedback) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.quiz_quit))
        }
        Text(
            text = stringResource(R.string.quiz_question_number, state.questionNumber),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "🌟 ${state.score}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }

    // Lives, with the current streak on the right when it feels rewarding.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val livesLabel = stringResource(R.string.quiz_progress_a11y, state.lives, QUIZ_LIVES)
        Text(
            text = buildString {
                repeat(QUIZ_LIVES) { i -> append(if (i < state.lives) "❤️" else "🖤") }
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { contentDescription = livesLabel },
        )
        if (state.streak >= 2) {
            Text(
                text = stringResource(R.string.quiz_streak, state.streak),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(question.category.labelRes()),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    if (listening) {
        Text(
            text = "🎤 " + stringResource(R.string.quiz_voice_listening),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
    }
    question.options.forEachIndexed { index, option ->
        QuizOptionButton(
            text = option,
            selected = state.selectedOption == index,
            correct = feedback && index == question.answerIndex,
            wrong = feedback && state.selectedOption == index && index != question.answerIndex,
            enabled = !feedback,
            onClick = { onAnswer(index) },
        )
        Spacer(Modifier.height(8.dp))
    }

    if (feedback) {
        val correct = state.lastAnswerCorrect
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (correct) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.errorContainer,
                contentColor = if (correct) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (correct) {
                        "✅ " + stringResource(R.string.quiz_correct) +
                            " · " + stringResource(R.string.quiz_correct_bonus, state.lastEarnedPoints)
                    } else {
                        "❌ " + stringResource(R.string.quiz_wrong) +
                            " · " + stringResource(R.string.quiz_lost_life)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (!correct) {
                    Text(
                        text = "💡 " + stringResource(R.string.quiz_correct_answer, question.options[question.answerIndex]),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                question.fact?.let {
                    Text(text = "💡 $it", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onNext,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                text = if (state.lives <= 0) {
                    stringResource(R.string.quiz_game_over)
                } else {
                    stringResource(R.string.quiz_next) + " ➡"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun QuizOptionButton(
    text: String,
    selected: Boolean,
    correct: Boolean,
    wrong: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = when {
            correct -> MaterialTheme.colorScheme.primary
            wrong -> MaterialTheme.colorScheme.errorContainer
            else -> if (selected) MaterialTheme.colorScheme.surfaceBright
            else MaterialTheme.colorScheme.surfaceVariant
        },
        label = "quizOptionColor",
    )
    val content by animateColorAsState(
        targetValue = when {
            correct -> MaterialTheme.colorScheme.onPrimary
            wrong -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "quizOptionContent",
    )
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = container, contentColor = content),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QuizGameOverContent(
    state: QuizUiState,
    storedName: String,
    ranking: List<QuizScore>,
    awaitingName: Boolean,
    onSave: (String) -> Unit,
    onPlayAgain: () -> Unit,
) {
    var name by remember(storedName) { mutableStateOf(storedName) }

    Text("🏁", style = MaterialTheme.typography.displayLarge)
    Text(
        text = stringResource(R.string.quiz_game_over),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(
            R.string.quiz_final_result,
            state.finishedScore,
            state.finishedCorrect,
            state.finishedBestStreak,
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp),
    )
    Spacer(Modifier.height(16.dp))
    if (!state.scoreSaved) {
        if (awaitingName) {
            Text(
                text = "🎤 " + stringResource(R.string.quiz_voice_listening),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.quiz_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSave(name) },
            enabled = name.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.quiz_save_score))
        }
    } else {
        Text(
            text = "✅ " + stringResource(R.string.quiz_saved),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onPlayAgain,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text("🔄 " + stringResource(R.string.quiz_play_again), style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(24.dp))
    QuizRankingCard(ranking)
}

/** Maps a question category to its localised badge label. */
private fun QuizCategory.labelRes(): Int = when (this) {
    QuizCategory.SCIENTIFIC_NAME -> R.string.quiz_cat_scientific_name
    QuizCategory.COMMON_NAME -> R.string.quiz_cat_common_name
    QuizCategory.SEASON -> R.string.quiz_cat_season
    QuizCategory.EDIBILITY -> R.string.quiz_cat_edibility
    QuizCategory.HABITAT -> R.string.quiz_cat_habitat
    QuizCategory.GENUS -> R.string.quiz_cat_genus
    QuizCategory.MONTH -> R.string.quiz_cat_month
    QuizCategory.CULTURE -> R.string.quiz_cat_culture
    QuizCategory.CATALONIA -> R.string.quiz_cat_catalonia
    QuizCategory.FIGURES -> R.string.quiz_cat_figures
}
