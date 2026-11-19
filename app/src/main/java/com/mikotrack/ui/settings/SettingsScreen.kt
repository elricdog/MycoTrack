package com.mikotrack.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import com.mikotrack.maps.OfflineMapManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikotrack.BuildConfig
import com.mikotrack.R
import com.mikotrack.core.AppLanguage
import com.mikotrack.core.Permissions
import com.mikotrack.data.prefs.MAX_ZOOM_LEVEL
import com.mikotrack.data.prefs.MIN_ZOOM_LEVEL
import com.mikotrack.data.prefs.ParkingIconStyles
import com.mikotrack.data.prefs.ThemeMode
import com.mikotrack.domain.CheckInPolicy
import com.mikotrack.location.LocationTracker
import com.mikotrack.maps.BoundingBox
import com.mikotrack.maps.MapLayer
import com.mikotrack.ui.common.EmptyState
import com.mikotrack.ui.common.SectionCard
import com.mikotrack.ui.map.MapButton
import com.mikotrack.ui.map.MapButtonGroup
import com.mikotrack.ui.theme.ForestGreen
import com.mikotrack.ui.update.ChangelogDialog
import com.mikotrack.ui.update.CheckUpdatesButton
import com.mikotrack.ui.update.updateStatusText
import com.mikotrack.update.UpdateManager
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Where users can support the development of the application. */
private const val DONATE_URL = "https://paypal.me/elricdog"

/** The settings screen: GPS, appearance, offline maps and permissions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onOpenPermissions: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenGames: () -> Unit = {},
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val gpsInterval by viewModel.gpsInterval.collectAsStateWithLifecycle()
    val maxAccuracy by viewModel.maxAccuracy.collectAsStateWithLifecycle()
    val maxJump by viewModel.maxJump.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val defaultZoom by viewModel.defaultZoom.collectAsStateWithLifecycle()
    val hiddenMapButtons by viewModel.hiddenMapButtons.collectAsStateWithLifecycle()
    val offlineAreas by viewModel.offlineAreas.collectAsStateWithLifecycle()
    val activeAreaId by viewModel.activeAreaId.collectAsStateWithLifecycle()
    val download by viewModel.download.collectAsStateWithLifecycle()
    val autoUpdateChecks by viewModel.autoUpdateChecks.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val gamesUnlocked by viewModel.gamesUnlocked.collectAsStateWithLifecycle()

    var showDownloadDialog by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }

    // Easter egg: taps on the banner spawn sparkles, and five taps in a row unlock
    // the games hub.
    val sparkBursts = remember { androidx.compose.runtime.mutableStateListOf<SparkBurst>() }
    val tapTimes = remember { mutableListOf<Long>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            // The inset is applied outside of the scroll, so the content never slides
            // under the status bar.
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(title = stringResource(R.string.settings_about)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    // The easter egg: taps on the banner spawn sparkles, five taps unlock the games.
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val now = System.currentTimeMillis()
                            sparkBursts.add(SparkBurst(offset.x, offset.y, now))
                            tapTimes.add(now)
                            tapTimes.removeAll { now - it > 3_000L }
                            if (tapTimes.size >= 5) {
                                tapTimes.clear()
                                viewModel.unlockGames()
                            }
                        }
                    },
            ) {
                Image(
                    painter = painterResource(R.drawable.brand_banner),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                )
                SparklesOverlay(sparkBursts)
            }
            LaunchedEffect(sparkBursts.size) {
                delay(1_800)
                val now = System.currentTimeMillis()
                sparkBursts.removeAll { now - it.atMillis > 1_600L }
            }
            if (gamesUnlocked) {
                OutlinedButton(
                    onClick = onOpenGames,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_games_button))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.brand_mascot),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(56.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_about_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_about_licences),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_donate_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(DONATE_URL))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_donate_button))
            }
            // Report a problem: opens a new issue on the project's GitHub, with the
            // version and the device filled in.
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(newIssueUrl()))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.settings_report_bugs))
            }
        }

        SectionCard(title = stringResource(R.string.gps_settings)) {
            SettingSlider(
                label = stringResource(R.string.settings_gps_interval, gpsInterval / 1000),
                value = gpsInterval.toFloat(),
                range = 500f..30_000f,
                steps = 5,
                onValueChange = { viewModel.setGpsInterval(it.toInt()) },
            )
            Text(
                text = stringResource(R.string.settings_gps_interval_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingSlider(
                label = stringResource(R.string.settings_max_accuracy_value, maxAccuracy),
                value = maxAccuracy.toFloat(),
                range = 5f..200f,
                steps = 9,
                onValueChange = { viewModel.setMaxAccuracy(it.toInt()) },
            )
            SettingSlider(
                label = stringResource(R.string.settings_max_jump_value, maxJump),
                value = maxJump.toFloat(),
                range = 10f..1_000f,
                steps = 9,
                onValueChange = { viewModel.setMaxJump(it.toInt()) },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_keep_screen_on), modifier = Modifier.weight(1f))
                Switch(checked = keepScreenOn, onCheckedChange = viewModel::setKeepScreenOn)
            }
            Text(
                text = stringResource(R.string.diag_settings_entry_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.GpsFixed, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.diag_settings_entry))
            }
        }

        SectionCard(title = stringResource(R.string.field_settings_safety)) {
            val autoParking by viewModel.autoParking.collectAsStateWithLifecycle()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.field_settings_auto_parking), modifier = Modifier.weight(1f))
                Switch(checked = autoParking, onCheckedChange = viewModel::setAutoParking)
            }
            Text(
                text = stringResource(R.string.field_settings_auto_parking_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // The "are you ok?" check-in of a recorded trip.
            val checkInEnabled by viewModel.checkInEnabled.collectAsStateWithLifecycle()
            val checkInMinutes by viewModel.checkInMinutes.collectAsStateWithLifecycle()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.field_settings_checkin), modifier = Modifier.weight(1f))
                Switch(checked = checkInEnabled, onCheckedChange = viewModel::setCheckInEnabled)
            }
            Text(
                text = stringResource(R.string.field_settings_checkin_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (checkInEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CheckInPolicy.CHOICES_MINUTES.forEach { minutes ->
                        FilterChip(
                            selected = checkInMinutes == minutes,
                            onClick = { viewModel.setCheckInMinutes(minutes) },
                            label = { Text(stringResource(R.string.field_settings_checkin_minutes, minutes)) },
                            leadingIcon = {
                                // Expose the selection to accessibility and UI automation.
                                if (checkInMinutes == minutes) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }

            // The look of the parking marker on the map: icon and badge colour.
            val parkingIconStyle by viewModel.parkingIconStyle.collectAsStateWithLifecycle()
            val parkingIconColor by viewModel.parkingIconColor.collectAsStateWithLifecycle()
            var showParkingColors by remember { mutableStateOf(false) }
            Text(
                text = stringResource(R.string.field_settings_parking_icon),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParkingIconStyles.ALL.forEach { style ->
                    val iconRes = ParkingIconStyles.drawableRes(style)
                    val selected = style == parkingIconStyle
                    OutlinedButton(
                        onClick = { viewModel.setParkingIconStyle(style) },
                        modifier = Modifier.size(52.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = CircleShape,
                        border = if (selected) {
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        },
                    ) {
                        if (iconRes != null) {
                            Icon(
                                painterResource(iconRes),
                                contentDescription = stringResource(parkingIconLabel(style)),
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showParkingColors = true }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(parkingIconColor)),
                )
                Text(
                    text = stringResource(R.string.field_settings_parking_color),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            if (showParkingColors) {
                ParkingColorDialog(
                    current = parkingIconColor,
                    onSelect = { color ->
                        viewModel.setParkingIconColor(color)
                        showParkingColors = false
                    },
                    onDismiss = { showParkingColors = false },
                )
            }
        }

        SectionCard(title = stringResource(R.string.nav_map)) {
            SettingSlider(
                label = stringResource(R.string.settings_default_zoom, defaultZoom.toInt()),
                value = defaultZoom,
                range = MIN_ZOOM_LEVEL..MAX_ZOOM_LEVEL,
                steps = (MAX_ZOOM_LEVEL - MIN_ZOOM_LEVEL).toInt() - 1,
                onValueChange = { viewModel.setDefaultZoom(it.toInt().toFloat()) },
            )
            Text(
                text = stringResource(R.string.settings_default_zoom_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.settings_map_buttons)) {
            Text(
                text = stringResource(R.string.settings_map_buttons_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MapButton.entries
                .filter { it.group == MapButtonGroup.BUTTON }
                .forEach { button -> MapButtonSetting(button, hiddenMapButtons, viewModel) }
        }

        SectionCard(title = stringResource(R.string.settings_map_widgets)) {
            Text(
                text = stringResource(R.string.settings_map_widgets_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MapButton.entries
                .filter { it.group == MapButtonGroup.WIDGET }
                .forEach { button -> MapButtonSetting(button, hiddenMapButtons, viewModel) }
        }

        SectionCard(title = stringResource(R.string.appearance)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = {
                            Text(
                                text = stringResource(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> R.string.theme_system
                                        ThemeMode.LIGHT -> R.string.theme_light
                                        ThemeMode.DARK -> R.string.theme_dark
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }

        SectionCard(title = stringResource(R.string.settings_language)) {
            // The language is applied by AppCompat, which recreates the screen.
            val currentLanguage = remember { AppLanguage.current() }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = currentLanguage == language,
                        onClick = { if (language != currentLanguage) AppLanguage.apply(language) },
                        label = { Text(stringResource(language.labelRes)) },
                    )
                }
            }
        }

        SectionCard(
            title = stringResource(R.string.offline_maps),
            trailing = {
                TextButton(onClick = { showDownloadDialog = true }) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.settings_download))
                }
            },
        ) {
            if (download.running) {
                LinearProgressIndicator(
                    progress = { if (download.total > 0) download.downloaded.toFloat() / download.total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = pluralStringResource(R.plurals.settings_download_progress, download.total, download.downloaded, download.total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            download.error?.let { error ->
                Text(
                    text = stringResource(R.string.settings_download_failed, error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (offlineAreas.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.settings_offline_empty_title),
                    hint = stringResource(R.string.settings_offline_empty_hint),
                )
            } else {
                offlineAreas.forEach { area ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // The name shrinks first, so the badge is never wrapped
                                // letter by letter with long names or translations.
                                Text(
                                    text = OfflineMapManager.bundledNameRes(area.name)
                                        ?.takeIf { area.bundled }
                                        ?.let { stringResource(it) }
                                        ?: area.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (area.bundled) {
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        text = stringResource(R.string.settings_offline_included),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                            Text(
                                text = pluralStringResource(
                                    R.plurals.settings_offline_area_details,
                                    area.tileCount,
                                    area.tileCount,
                                    area.sizeBytes / 1024 / 1024,
                                    area.minZoom,
                                    area.maxZoom,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!area.bundled) {
                            IconButton(onClick = { viewModel.deleteArea(area) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.delete_area),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        TextButton(onClick = { viewModel.setActiveArea(area.id) }) {
                            Text(
                                stringResource(
                                    if (activeAreaId == area.id) R.string.settings_offline_in_use else R.string.settings_offline_use,
                                ),
                            )
                        }
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.permissions)) {
            Text(
                text = stringResource(R.string.settings_permissions_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PermissionRow(
                label = stringResource(R.string.perm_foreground_location_title),
                granted = Permissions.hasForegroundLocation(context),
                onClick = onOpenPermissions,
            )
            PermissionRow(
                label = stringResource(R.string.perm_background_location_title),
                granted = Permissions.hasBackgroundLocation(context),
                onClick = onOpenPermissions,
            )
            PermissionRow(
                label = stringResource(R.string.perm_notifications_title),
                granted = Permissions.hasNotifications(context),
                onClick = onOpenPermissions,
            )
            PermissionRow(
                label = stringResource(R.string.perm_camera_title),
                granted = Permissions.hasCamera(context),
                onClick = onOpenPermissions,
            )
            OutlinedButton(onClick = onOpenPermissions, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_open_permission_centre))
            }
        }

        SectionCard(title = stringResource(R.string.settings_updates)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setAutoUpdateChecks(!autoUpdateChecks) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_auto_update),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = autoUpdateChecks, onCheckedChange = { viewModel.setAutoUpdateChecks(it) })
            }
            Text(
                text = stringResource(R.string.settings_auto_update_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CheckUpdatesButton(
                checking = updateState is UpdateManager.State.Checking ||
                    updateState is UpdateManager.State.Downloading,
                onCheck = { viewModel.checkForUpdates() },
            )
            // The history of the application: what each version brought, read from the
            // GitHub releases.
            OutlinedButton(
                onClick = { showChangelog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.settings_changelog))
            }
            val status = updateStatusText(updateState, BuildConfig.VERSION_NAME)
            if (status.isNotEmpty()) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showChangelog) {
        ChangelogDialog(
            fetch = { viewModel.fetchChangelog() },
            onDismiss = { showChangelog = false },
        )
    }

    if (showDownloadDialog) {
        DownloadDialog(
            onDismiss = { showDownloadDialog = false },
            onConfirm = { layer, name, bounds, minZoom, maxZoom ->
                viewModel.downloadArea(layer, name, bounds, minZoom, maxZoom)
                showDownloadDialog = false
            },
        )
    }
}

/** One optional control of the map, with the switch that shows or hides it. */
@Composable
private fun MapButtonSetting(
    button: MapButton,
    hiddenMapButtons: Set<String>,
    viewModel: SettingsViewModel,
) {
    val visible = button.key !in hiddenMapButtons
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.setMapButtonVisible(button.key, !visible) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = button.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(button.label, modifier = Modifier.weight(1f))
        Switch(
            checked = visible,
            onCheckedChange = { viewModel.setMapButtonVisible(button.key, it) },
        )
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onClick) {
            Text(stringResource(if (granted) R.string.permissions_granted else R.string.grant), color = if (granted) ForestGreen else MaterialTheme.colorScheme.error)
        }
    }
}

/** The dialog that configures a new offline area. */
@Composable
private fun DownloadDialog(
    onDismiss: () -> Unit,
    onConfirm: (MapLayer, String, BoundingBox, Int, Int) -> Unit,
) {
    val context = LocalContext.current
    // The area is centred on the last known position of the device, which is the
    // place the user is most likely to walk through.
    val lastKnown = remember(context) { LocationTracker(context).lastKnownLocation() }
    var layer by remember { mutableStateOf(MapLayer.ICGC_TOPO) }
    val defaultName = stringResource(R.string.settings_download_default_name)
    var name by remember { mutableStateOf(defaultName) }
    var minZoom by remember { mutableStateOf(10f) }
    var maxZoom by remember { mutableStateOf(13f) }
    var latitude by remember { mutableStateOf("%.5f".format(Locale.US, lastKnown?.latitude ?: 41.4)) }
    var longitude by remember { mutableStateOf("%.5f".format(Locale.US, lastKnown?.longitude ?: 1.8)) }
    var radiusKm by remember { mutableStateOf(5f) }

    // The area cannot contain more zoom levels than the layer publishes, otherwise
    // the download would ask for tiles that the server does not have.
    val highestZoom = layer.maxZoom
    val lowestAllowed = 8f
    val effectiveMin = minZoom.coerceIn(lowestAllowed, (highestZoom - 1).coerceAtLeast(8).toFloat())
    val effectiveMax = maxZoom.coerceIn(effectiveMin + 1f, highestZoom.toFloat())

    val centreLatitude = latitude.toDoubleOrNull()
    val centreLongitude = longitude.toDoubleOrNull()
    val centreIsValid = centreLatitude != null && centreLongitude != null &&
        centreLatitude in -85.0..85.0 && centreLongitude in -180.0..180.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_area)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_download_name)) },
                    singleLine = true,
                )
                Text(stringResource(R.string.settings_download_layer), style = MaterialTheme.typography.titleMedium)
                Column {
                    MapLayer.downloadable.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilterChip(
                                selected = layer == option,
                                onClick = { layer = option },
                                label = { Text(stringResource(option.labelRes)) },
                            )
                        }
                    }
                }
                Text(stringResource(R.string.settings_download_centre), style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it },
                        label = { Text(stringResource(R.string.settings_download_latitude)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it },
                        label = { Text(stringResource(R.string.settings_download_longitude)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = stringResource(R.string.settings_download_radius, radiusKm.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.material3.Slider(
                    value = radiusKm,
                    onValueChange = { radiusKm = it },
                    valueRange = 1f..50f,
                    steps = 48,
                )
                Text(
                    text = stringResource(R.string.settings_download_zoom, effectiveMin.toInt(), effectiveMax.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.material3.Slider(
                    value = effectiveMin,
                    onValueChange = { minZoom = it.coerceAtMost(effectiveMax - 1f) },
                    valueRange = lowestAllowed..(highestZoom - 1).toFloat(),
                    steps = (highestZoom - 10).coerceAtLeast(0),
                )
                androidx.compose.material3.Slider(
                    value = effectiveMax,
                    onValueChange = { maxZoom = it.coerceAtLeast(effectiveMin + 1f) },
                    valueRange = (effectiveMin + 1f)..highestZoom.toFloat(),
                    steps = (highestZoom - 10).coerceAtLeast(0),
                )
                Text(
                    text = stringResource(R.string.settings_download_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = centreIsValid,
                onClick = {
                    val centreLat = centreLatitude ?: 41.4
                    val centreLon = centreLongitude ?: 1.8
                    val latitudeDelta = radiusKm / 111.0
                    val longitudeDelta =
                        radiusKm / (111.0 * cos(Math.toRadians(centreLat)).coerceAtLeast(0.01))
                    onConfirm(
                        layer,
                        name,
                        BoundingBox(
                            minLatitude = centreLat - latitudeDelta,
                            minLongitude = centreLon - longitudeDelta,
                            maxLatitude = centreLat + latitudeDelta,
                            maxLongitude = centreLon + longitudeDelta,
                        ),
                        effectiveMin.toInt(),
                        effectiveMax.toInt(),
                    )
                },
            ) { Text(stringResource(R.string.settings_download)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** The label resource of a parking icon style. */
private fun parkingIconLabel(style: String): Int = when (style) {
    ParkingIconStyles.CAR -> R.string.parking_icon_car
    ParkingIconStyles.MOTORCYCLE -> R.string.parking_icon_motorcycle
    ParkingIconStyles.BICYCLE -> R.string.parking_icon_bicycle
    ParkingIconStyles.CAMPERVAN -> R.string.parking_icon_campervan
    ParkingIconStyles.GARAGE -> R.string.parking_icon_garage
    else -> R.string.parking_icon_car
}

/** The badge colours the user can pick for parking waypoints. */
private val PARKING_COLOR_OPTIONS = listOf(
    0xFF42A5F5, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF7CB342,
    0xFF00897B, 0xFF8E24AA, 0xFF8D6E63, 0xFF607D8B, 0xFF212121,
)

/** Lets the user choose the colour of the parking waypoint badge. */
@Composable
private fun ParkingColorDialog(
    current: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.field_settings_parking_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PARKING_COLOR_OPTIONS.chunked(5).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        row.forEach { color ->
                            val selected = color == current
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(color))
                                    .clickable { onSelect(color) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = if (Color(color).luminance() > 0.6f) {
                                            Color.Black
                                        } else {
                                            Color.White
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * The URL of a new GitHub issue, prefilled with the application version and the device
 * so the report starts with the basics already written.
 */
private fun newIssueUrl(): String {
    val title = java.net.URLEncoder.encode("[v" + BuildConfig.VERSION_NAME + "] ", "UTF-8")
    val body = buildString {
        append("**MycoTrack version:** ").append(BuildConfig.VERSION_NAME).append('\n')
        append("**Device:** ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        append("**Android:** ").append(Build.VERSION.RELEASE).append("\n\n")
        append("**What happened:**\n\n")
        append("**Steps to reproduce:**\n1. \n\n")
        append("**What did you expect:**\n")
    }
    return "https://github.com/elricdog/MycoTrack/issues/new?labels=bug&title=$title&body=" +
        java.net.URLEncoder.encode(body, "UTF-8")
}

/** A firework burst spawned by a tap on the banner, in canvas coordinates. */
internal class SparkBurst(val x: Float, val y: Float, val atMillis: Long)

private val FIREWORK_PALETTE = listOf(
    Color(0xFFFFD54F), // gold
    Color(0xFFFF5252), // red
    Color(0xFF9CCC65), // green
    Color(0xFFFFFDE7), // cream
    Color(0xFFFFB300), // amber
)

/** Deterministic pseudo-random in 0..1 from an index and the burst origin. */
private fun sparkNoise(i: Int, seed: Float): Float {
    val v = sin(i * 12.9898f + seed * 0.01f) * 43758.5453f
    return v - kotlin.math.floor(v)
}

/**
 * The easter egg fireworks: every burst in [bursts] explodes at the point that was
 * tapped, radiating coloured sparks with gravity, fading trails and a few secondary
 * cracks that pop a moment later.
 */
@Composable
internal fun BoxScope.SparklesOverlay(bursts: List<SparkBurst>) {
    if (bursts.isEmpty()) return
    // A per-frame ticker: the canvas reads [frameNow], and the coroutine refreshes it
    // every vsync, so the explosion animates until every burst has expired.
    var frameNow by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis())
    }
    if (bursts.isNotEmpty()) {
        androidx.compose.runtime.LaunchedEffect(bursts.size) {
            while (true) {
                frameNow = System.currentTimeMillis()
                androidx.compose.runtime.withFrameNanos { }
            }
        }
    }
    Canvas(
        modifier = Modifier
            .matchParentSize()
            // The explosion may fly past the banner's rounded clip.
            .graphicsLayer { clip = false },
    ) {
        val now = frameNow
        val dp = density
        val gravity = 26f * dp // px/s²
        for (burst in bursts) {
            val age = (now - burst.atMillis) / 1_000f
            if (age < 0f || age > 1.6f) continue

            // Main shell: 26 sparks radiating from the finger, with gravity and trails.
            val life = 1.25f
            if (age <= life) {
                val fade = 1f - age / life
                for (i in 0 until 26) {
                    val angle = i * (2.0 * Math.PI / 26.0) + sparkNoise(i, burst.x) * 0.4
                    val speed = (55f + 85f * sparkNoise(i + 7, burst.y)) * dp
                    val trail = 3
                    for (t in trail downTo 0) {
                        val ta = age - t * 0.06f
                        if (ta < 0f) continue
                        val px = burst.x + cos(angle.toFloat()) * speed * ta
                        val py = burst.y + sin(angle.toFloat()) * speed * ta + 0.5f * gravity * ta * ta
                        val radius = (5f - 3f * ta).coerceAtLeast(1.2f) * 1f
                        drawCircle(
                            color = FIREWORK_PALETTE[i % FIREWORK_PALETTE.size],
                            radius = radius,
                            center = Offset(px, py),
                            alpha = (fade * (1f - t * 0.3f)).coerceIn(0f, 1f),
                        )
                    }
                }
            }

            // Secondary cracks: three small pops that fly out of the first blast.
            for (k in 0 until 3) {
                val start = 0.35f + k * 0.28f
                val crackLife = 0.55f
                val ca = age - start
                if (ca < 0f || ca > crackLife) continue
                val cAngle = k * 2.1f + sparkNoise(k, burst.x)
                val cx = burst.x + cos(cAngle) * 55f * dp
                val cy = burst.y + sin(cAngle) * 55f * dp
                val fade = 1f - ca / crackLife
                for (i in 0 until 10) {
                    val angle = i * (2.0 * Math.PI / 10.0) + sparkNoise(i, burst.y + k)
                    val speed = (30f + 45f * sparkNoise(i + 3, burst.x)) * dp
                    val px = cx + cos(angle.toFloat()) * speed * ca
                    val py = cy + sin(angle.toFloat()) * speed * ca + 0.5f * gravity * ca * ca
                    drawCircle(
                        color = FIREWORK_PALETTE[(i + k * 2) % FIREWORK_PALETTE.size],
                        radius = (3.2f - 2f * ca).coerceAtLeast(1f),
                        center = Offset(px, py),
                        alpha = fade,
                    )
                }
                // The flash of the crack itself.
                drawCircle(
                    color = Color.White,
                    radius = (7f * (1f - fade)).coerceAtLeast(1f),
                    center = Offset(cx, cy),
                    alpha = fade * 0.8f,
                )
            }
        }
    }
}
