package com.mikotrack.ui.map

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikotrack.R
import com.mikotrack.ui.theme.DangerRed
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** How long the END button must be held down to finish the trip. */
const val HOLD_TO_STOP_MILLIS = 3_000

/**
 * The END button. To avoid finishing a trip by accident it must be held down for
 * [HOLD_TO_STOP_MILLIS]: while it is pressed a lighter band fills it from the left and the
 * remaining seconds are counted down; releasing it earlier cancels and shows a hint.
 */
@Composable
fun HoldToStopButton(
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val hint = stringResource(R.string.recording_hold_to_finish_hint)
    val label = stringResource(R.string.stop_recording)

    val pressing = progress.value > 0f
    val remainingSeconds = ceil((1f - progress.value) * HOLD_TO_STOP_MILLIS / 1000f).toInt().coerceAtLeast(1)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DangerRed)
            .semantics {
                role = Role.Button
                // Accessibility services cannot hold a button: they finish it directly.
                onClick(label) {
                    onStop()
                    true
                }
            }
            .pointerInput(onStop) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    var completed = false
                    val job: Job = scope.launch {
                        progress.snapTo(0.001f)
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = HOLD_TO_STOP_MILLIS,
                                easing = LinearEasing,
                            ),
                        )
                        completed = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        progress.snapTo(0f)
                        onStop()
                    }
                    waitForUpOrCancellation()
                    if (!completed) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(200)) }
                        Toast.makeText(context, hint, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // The band that fills the button while it is held.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(Color.White.copy(alpha = 0.35f)),
        )
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // In the narrow side panel of landscape the label needs all the room.
            val narrow = maxWidth < 170.dp
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!narrow) {
                    Icon(Icons.Filled.Stop, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.size(8.dp))
                }
                BasicText(
                    text = if (pressing) {
                        stringResource(R.string.recording_hold_to_finish_countdown, remainingSeconds)
                    } else {
                        stringResource(R.string.recording_hold_to_finish)
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 10.sp,
                        maxFontSize = MaterialTheme.typography.titleMedium.fontSize,
                    ),
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}
