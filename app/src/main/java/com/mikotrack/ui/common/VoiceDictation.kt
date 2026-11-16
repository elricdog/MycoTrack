package com.mikotrack.ui.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import com.mikotrack.R
import java.util.Locale

/**
 * A microphone button that dictates text with the speech recognizer of the system.
 *
 * The recognizer runs in its own activity, so the application never records audio and
 * needs no microphone permission. The button is hidden when no recognizer is installed.
 */
@Composable
fun DictationButton(onText: (String) -> Unit) {
    val context = LocalContext.current
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val prompt = stringResource(R.string.field_dictation_prompt)
    val unavailable = stringResource(R.string.field_dictation_unavailable)
    val intent = remember(locale, prompt) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
    }
    val available = remember(intent) { intent.resolveActivity(context.packageManager) != null }
    if (!available) return

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (text.isNotEmpty()) onText(text)
    }
    IconButton(
        onClick = {
            try {
                launcher.launch(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
            }
        },
    ) {
        Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.field_dictate))
    }
}

/** Appends a dictated sentence to an existing text, separated by a single space. */
fun appendDictation(existing: String, dictated: String): String {
    val addition = dictated.trim()
    if (addition.isEmpty()) return existing
    val base = existing.trimEnd()
    return if (base.isEmpty()) addition else "$base $addition"
}
