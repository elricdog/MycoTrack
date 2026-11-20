package com.mikotrack.ui.quiz

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
                onStart = viewModel::startGame,
            )

            QuizPhase.PLAYING, QuizPhase.FEEDBACK -> QuizPlayContent(
                state = state,
                onAnswer = viewModel::answer,
                onNext = viewModel::next,
                onQuit = viewModel::quitGame,
            )

            QuizPhase.GAME_OVER -> QuizGameOverContent(
                state = state,
                storedName = storedName,
                ranking = ranking,
                onSave = viewModel::saveScore,
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
    QuizCategory.CULTURE -> R.string.quiz_cat_culture
}
