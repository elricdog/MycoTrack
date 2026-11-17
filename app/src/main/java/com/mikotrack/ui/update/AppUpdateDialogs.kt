package com.mikotrack.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikotrack.R
import com.mikotrack.update.AppUpdate
import com.mikotrack.update.UpdateFailure
import com.mikotrack.update.UpdateManager
import java.util.Locale

/**
 * The dialogs of the self update system, hosted above the navigation graph so that the
 * update offered on start is visible on every screen. They react to the state of the
 * [UpdateManager]: an available release asks the user, a running download shows the
 * progress, a downloaded APK offers the installer, and a forced check reports failures.
 */
@Composable
fun AppUpdateHost(updateManager: UpdateManager) {
    val state by updateManager.state.collectAsStateWithLifecycle()
    when (val current = state) {
        is UpdateManager.State.Available -> UpdateAvailableDialog(
            update = current.update,
            onUpdate = { updateManager.download(current.update) },
            onLater = { updateManager.dismiss() },
        )

        is UpdateManager.State.Downloading -> UpdateProgressDialog(
            downloaded = current.downloaded,
            total = current.total,
            onCancel = { updateManager.cancelDownload() },
        )

        is UpdateManager.State.ReadyToInstall -> UpdateReadyDialog(
            update = current.update,
            canInstall = updateManager.canRequestInstalls(),
            onInstall = { updateManager.launchInstall(current.file) },
            onOpenSettings = { updateManager.openInstallPermissionSettings() },
            onDismiss = { updateManager.dismissReady() },
        )

        is UpdateManager.State.Failed -> UpdateFailedDialog(
            failure = current.failure,
            onDismiss = { updateManager.cancelDownload() },
        )

        else -> Unit
    }
}

/** Asks the user whether to update, showing the new version, the size and the notes. */
@Composable
private fun UpdateAvailableDialog(
    update: AppUpdate,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.height(360.dp).verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(
                        R.string.update_available_text,
                        update.newVersion,
                        update.currentVersion,
                        formatSize(update.asset.size),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val notes = update.release.body?.takeIf { it.isNotBlank() }
                if (notes != null) {
                    Text(
                        text = stringResource(R.string.update_release_notes),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onUpdate) { Text(stringResource(R.string.update_button_update)) } },
        dismissButton = {
            TextButton(onClick = onLater) { Text(stringResource(R.string.update_button_later)) }
        },
    )
}

/** The progress of the APK download, with the option to abort it. */
@Composable
private fun UpdateProgressDialog(
    downloaded: Long,
    total: Long,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.update_download_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (total > 0L) {
                    LinearProgressIndicator(
                        progress = { (downloaded.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.update_download_progress, formatSize(downloaded), formatSize(total)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = formatSize(downloaded),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.update_button_cancel)) }
        },
    )
}

/** The APK is downloaded: opens the installer, asking for the install permission first. */
@Composable
private fun UpdateReadyDialog(
    update: AppUpdate,
    canInstall: Boolean,
    onInstall: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_ready_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.update_ready_text, update.newVersion),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!canInstall) {
                    Text(
                        text = stringResource(R.string.update_need_install_permission),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (canInstall) {
                Button(onClick = onInstall) { Text(stringResource(R.string.update_button_install)) }
            } else {
                Button(onClick = onOpenSettings) { Text(stringResource(R.string.update_button_open_settings)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_button_later)) }
        },
    )
}

/** Reports why a forced check or a download failed. */
@Composable
private fun UpdateFailedDialog(failure: UpdateFailure, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_failed_title)) },
        text = { Text(text = stringResource(failureMessage(failure)), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.update_button_ok)) }
        },
    )
}

/** The message resource that explains a failure. */
fun failureMessage(failure: UpdateFailure): Int = when (failure) {
    UpdateFailure.Network -> R.string.update_failed_network
    UpdateFailure.RateLimited -> R.string.update_failed_rate_limited
    UpdateFailure.Http -> R.string.update_failed_http
    UpdateFailure.NoAsset -> R.string.update_failed_no_asset
    UpdateFailure.Storage -> R.string.update_failed_storage
}

/** The state of the last check, as a one line status for the settings screen. */
@Composable
fun updateStatusText(state: UpdateManager.State, versionName: String): String = when (state) {
    is UpdateManager.State.Checking -> stringResource(R.string.update_status_checking)
    is UpdateManager.State.UpToDate -> stringResource(R.string.update_status_up_to_date, versionName)
    is UpdateManager.State.Available -> stringResource(R.string.update_status_available, state.update.newVersion)
    is UpdateManager.State.Downloading -> stringResource(R.string.update_status_downloading)
    is UpdateManager.State.ReadyToInstall -> stringResource(R.string.update_status_ready)
    is UpdateManager.State.Failed -> stringResource(failureMessage(state.failure))
    is UpdateManager.State.Idle -> ""
}

/** Formats a size in bytes as a compact human readable string. */
fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format(Locale.getDefault(), "%.0f kB", bytes / 1_000.0)
    else -> "$bytes B"
}

/** A compact status row: label on the left, state on the right. */
@Composable
fun StatusRow(label: String, status: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The outline button of the settings that triggers a forced check. */
@Composable
fun CheckUpdatesButton(
    checking: Boolean,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(onClick = onCheck, enabled = !checking, modifier = modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_check_updates))
    }
}
