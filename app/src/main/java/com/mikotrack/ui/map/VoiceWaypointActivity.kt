package com.mikotrack.ui.map

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.ConfigurationCompat
import com.mikotrack.R
import com.mikotrack.location.RecordingService
import java.util.Locale

/**
 * Invisible helper activity behind the 🎤 action of the recording notification.
 *
 * It starts the speech recognizer of the system and hands the dictated sentence to
 * [RecordingService], which stores it as a mushroom waypoint at the current position.
 * The recognizer needs an activity to receive its result, so this activity exists for
 * exactly that: it never shows any interface and is removed from the task as soon as
 * the recognizer answers or the user cancels.
 */
class VoiceWaypointActivity : AppCompatActivity() {

    private lateinit var recognizer: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recognizer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val text = if (result.resultCode == RESULT_OK) {
                result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
            } else {
                ""
            }
            if (text.isNotEmpty()) {
                startService(
                    Intent(this, RecordingService::class.java)
                        .setAction(RecordingService.ACTION_TEXT_WAYPOINT)
                        .putExtra(RecordingService.EXTRA_WAYPOINT_TEXT, text),
                )
            }
            finish()
        }
        launchRecognizer()
    }

    private fun launchRecognizer() {
        val locale = ConfigurationCompat.getLocales(resources.configuration)[0] ?: Locale.getDefault()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.field_dictation_prompt))
        try {
            // Keep this invisible shell alive while the recognizer owns the screen:
            // the result callback below finishes it. noHistory must NOT be set on the
            // activity, or the proxy would be destroyed and the result lost.
            recognizer.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.field_dictation_unavailable, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
