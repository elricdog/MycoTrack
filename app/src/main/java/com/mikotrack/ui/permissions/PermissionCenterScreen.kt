package com.mikotrack.ui.permissions

import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikotrack.R
import com.mikotrack.core.PermissionRequirement
import com.mikotrack.core.findActivity

/**
 * The permission centre.
 *
 * It explains what every permission is used for, requests them one after the other and
 * shows the live state of each one. Permissions that the user has permanently denied can
 * only be granted from the system settings, so a button opens them directly.
 */
@Composable
fun PermissionCenterScreen(
    viewModel: PermissionCenterViewModel,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    onReady: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // The state is refreshed every time the screen comes back to the foreground, because
    // the permissions may have been changed in the system settings.
    var refreshToken by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshToken++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val blockedIds by viewModel.blockedPermissionIds.collectAsStateWithLifecycle()

    var pendingRequirement by remember { mutableStateOf<PermissionRequirement?>(null) }

    // The requests are done one at a time, in the order Android expects them.
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val finished = pendingRequirement
        pendingRequirement = null
        if (finished != null) {
            val granted = result.values.any { it } || finished.isGranted(context)
            if (!granted) {
                // The system stops showing the dialog after a couple of refusals; the
                // permission then has to be granted from the system settings.
                val activity = context.findActivity()
                val shouldShowRationale = activity != null && finished.permissions.any {
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
                }
                viewModel.setBlocked(finished.id, !shouldShowRationale)
            } else {
                viewModel.setBlocked(finished.id, false)
            }
        }
        refreshToken++
    }

    val missingRequired = remember(refreshToken) { PermissionRequirement.missingForRecording(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.permissions),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Image(
            painter = painterResource(R.drawable.brand_logo_wordmark),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        )
        Text(
            text = stringResource(R.string.permissions_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PermissionRequirement.ALL.forEach { requirement ->
            val granted = requirement.isGranted(context)
            val blocked = requirement.id in blockedIds
            PermissionCard(
                requirement = requirement,
                granted = granted,
                blocked = blocked,
                onRequest = {
                    if (blocked) {
                        openAppSettings(context)
                    } else {
                        pendingRequirement = requirement
                    }
                },
            )
        }

        // A single button that walks through the missing permissions one after the
        // other, which is the usual path when the application is opened for the
        // first time.
        if (missingRequired.isNotEmpty()) {
            val next = missingRequired.first()
            if (!next.isGranted(context) && next.id !in blockedIds) {
                OutlinedButton(
                    onClick = { pendingRequirement = next },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.permissions_grant_missing))
                }
            }
        }

        if (missingRequired.isEmpty()) {
            Text(
                text = stringResource(R.string.permissions_all_granted),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (onReady != null) {
                Button(onClick = onReady, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.permissions_start_recording))
                }
            }
        }

        if (onClose != null) {
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.permissions_back_to_map))
            }
        }
    }

    // The explanation is shown before the system dialog, so the user knows what is
    // being asked for and why.
    pendingRequirement?.let { requirement ->
        PermissionRationaleDialog(
            requirement = requirement,
            onRequest = {
                pendingRequirement = null
                launcher.launch(requirement.permissions.toTypedArray())
            },
            onDismiss = { pendingRequirement = null },
        )
    }
}

/** A card describing one permission, its purpose and its current state. */
@Composable
private fun PermissionCard(
    requirement: PermissionRequirement,
    granted: Boolean,
    blocked: Boolean,
    onRequest: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = iconFor(requirement),
                    contentDescription = null,
                    tint = if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        text = stringResource(requirement.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(if (requirement.required) R.string.permissions_required else R.string.permissions_optional),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (granted) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.permissions_granted),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    TextButton(onClick = onRequest) {
                        Text(stringResource(if (blocked) R.string.permissions_open_settings else R.string.grant))
                    }
                }
            }

            Text(
                text = stringResource(requirement.purposeRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (!granted) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = stringResource(requirement.consequenceRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** The icon that represents each permission. */
fun iconFor(requirement: PermissionRequirement): ImageVector = when (requirement) {
    PermissionRequirement.FOREGROUND_LOCATION -> Icons.Filled.LocationOn
    PermissionRequirement.NOTIFICATIONS -> Icons.Filled.Notifications
    PermissionRequirement.BACKGROUND_LOCATION -> Icons.Filled.LocationOn
    PermissionRequirement.CAMERA -> Icons.Filled.PhotoCamera
}

/**
 * Opens the system settings of the application, where the permissions that Android no
 * longer offers to ask for can be granted by hand.
 */
fun openAppSettings(context: android.content.Context) {
    runCatching {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
