package com.mikotrack.ui.gallery

import com.mikotrack.ui.common.categoryLabel
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.mikotrack.R
import com.mikotrack.core.Formatters
import com.mikotrack.data.repository.DefaultCategory
import com.mikotrack.domain.model.Waypoint
import com.mikotrack.export.ExportManager
import com.mikotrack.export.PhotoExporter
import com.mikotrack.ui.common.ColorDot
import com.mikotrack.ui.common.ConfirmationDialog
import com.mikotrack.ui.common.categoryIcon
import com.mikotrack.ui.common.EmptyState
import com.mikotrack.ui.theme.ForestGreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/** The ways the photographs of the gallery can be exported. */
private enum class PhotoExportAction(@StringRes val labelRes: Int, @StringRes val descriptionRes: Int) {
    SHARE(R.string.gallery_action_share, R.string.gallery_action_share_description),
    SAVE(R.string.gallery_action_save, R.string.gallery_action_save_description),
    ZIP(R.string.gallery_action_zip, R.string.gallery_action_zip_description),
}

/**
 * The gallery with all the waypoints: the geotagged photographs and, as coloured tiles,
 * the waypoints without photo. [onShowOnMap] opens the map tab.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onShowOnMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val waypoints by viewModel.waypoints.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val filter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val tripNames by viewModel.tripNames.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = remember { PhotoExporter(context) }
    val exportManager = remember { ExportManager(context) }

    var selected by remember { mutableStateOf<Waypoint?>(null) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var exportTargets by remember { mutableStateOf<List<Waypoint>?>(null) }
    var pendingSave by remember { mutableStateOf<List<Waypoint>?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<List<Waypoint>?>(null) }

    // Photographs that disappear (deleted or filtered out) leave the selection.
    LaunchedEffect(waypoints) {
        val visible = waypoints.map { it.id }.toSet()
        selectedIds = selectedIds intersect visible
    }

    fun runExport(action: PhotoExportAction, targets: List<Waypoint>) {
        scope.launch {
            isExporting = true
            try {
                when (action) {
                    PhotoExportAction.SHARE -> context.startActivity(
                        Intent.createChooser(
                            exporter.shareIntent(targets),
                            context.getString(R.string.gallery_action_share),
                        ),
                    )

                    PhotoExportAction.SAVE -> {
                        val saved = exporter.saveToDevice(targets)
                        Toast.makeText(
                            context,
                            context.resources.getQuantityString(
                                R.plurals.gallery_photos_saved,
                                targets.size,
                                saved,
                                targets.size,
                                PhotoExporter.ALBUM,
                            ),
                            Toast.LENGTH_LONG,
                        ).show()
                    }

                    PhotoExportAction.ZIP -> {
                        val file = exporter.exportZip(targets, tripNames)
                        context.startActivity(
                            Intent.createChooser(
                                exportManager.shareIntent(file),
                                context.getString(R.string.gallery_action_share),
                            ),
                        )
                    }
                }
                selectedIds = emptySet()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                android.util.Log.e("MikoTrack", "The export of the photos failed", error)
                exportError = error.message
                    ?: context.getString(R.string.export_msg_generic_error, error.javaClass.simpleName)
            } finally {
                isExporting = false
            }
        }
    }

    // Android 9 and older need the storage permission to write into the public gallery.
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val targets = pendingSave
        pendingSave = null
        if (granted && targets != null) {
            runExport(PhotoExportAction.SAVE, targets)
        } else if (!granted) {
            exportError = context.getString(R.string.gallery_storage_permission_required)
        }
    }

    fun startExport(action: PhotoExportAction, targets: List<Waypoint>) {
        val needsPermission = action == PhotoExportAction.SAVE &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingSave = targets
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            runExport(action, targets)
        }
    }

    val photos = waypoints.filter { !it.imagePath.isNullOrBlank() }
    val selectionMode = selectedIds.isNotEmpty()
    val selectedPhotos = photos.filter { it.id in selectedIds }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        if (selectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selectedIds = emptySet() }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.gallery_clear_selection))
                }
                Text(
                    text = pluralStringResource(R.plurals.gallery_selected_count, selectedIds.size, selectedIds.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                IconButton(onClick = { selectedIds = waypoints.map { it.id }.toSet() }) {
                    Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.gallery_select_all))
                }
                // Only photographs can be exported: the action is off when none is selected.
                IconButton(
                    onClick = { exportTargets = selectedPhotos },
                    enabled = selectedPhotos.isNotEmpty() && !isExporting,
                ) {
                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.gallery_export_selected))
                }
                IconButton(onClick = { pendingDelete = waypoints.filter { it.id in selectedIds } }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.galmap_delete_selected),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = filter == null,
                        onClick = { viewModel.setCategoryFilter(null) },
                        label = { Text(stringResource(R.string.gallery_filter_all)) },
                    )
                    categories.forEach { category ->
                        FilterChip(
                            selected = filter == category,
                            onClick = { viewModel.setCategoryFilter(category) },
                            label = { Text(categoryLabel(category)) },
                        )
                    }
                }
                IconButton(
                    onClick = { exportTargets = photos },
                    enabled = photos.isNotEmpty() && !isExporting,
                ) {
                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.gallery_export_photos))
                }
            }
        }

        if (waypoints.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.gallery_empty),
                hint = stringResource(R.string.gallery_empty_hint),
                icon = Icons.Filled.PhotoLibrary,
                image = painterResource(R.drawable.brand_mascot),
            )
        } else {
            if (!selectionMode) {
                Text(
                    text = stringResource(R.string.galmap_long_press_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(waypoints, key = { it.id }) { waypoint ->
                    val isSelected = waypoint.id in selectedIds
                    fun toggle() {
                        selectedIds = if (isSelected) selectedIds - waypoint.id else selectedIds + waypoint.id
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .then(
                                if (isSelected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                } else {
                                    Modifier
                                },
                            )
                            .combinedClickable(
                                onClick = { if (selectionMode) toggle() else selected = waypoint },
                                onLongClick = { toggle() },
                            ),
                    ) {
                        WaypointThumbnail(waypoint = waypoint, modifier = Modifier.fillMaxSize())
                        if (isSelected) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.25f)),
                            )
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = stringResource(R.string.gallery_selected),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(26.dp)
                                    .background(Color.White, CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }

    selected?.let { waypoint ->
        WaypointEditor(
            waypoint = waypoint,
            tripName = tripNames[waypoint.tripId] ?: "",
            onDismiss = { selected = null },
            onSave = { description, category ->
                viewModel.update(waypoint.id, description, category)
                selected = null
            },
            onDelete = { pendingDelete = listOf(waypoint) },
            onShare = { startExport(PhotoExportAction.SHARE, listOf(waypoint)) },
            onSaveToDevice = { startExport(PhotoExportAction.SAVE, listOf(waypoint)) },
            onShowOnMap = {
                selected = null
                viewModel.showOnMap(waypoint, onReady = onShowOnMap)
            },
        )
    }

    pendingDelete?.let { targets ->
        val single = targets.singleOrNull()
        ConfirmationDialog(
            title = if (single != null) {
                stringResource(R.string.galmap_delete_title)
            } else {
                pluralStringResource(R.plurals.galmap_delete_selected_title, targets.size, targets.size)
            },
            message = when {
                single == null -> stringResource(R.string.galmap_delete_selected_message)
                single.imagePath.isNullOrBlank() -> stringResource(R.string.galmap_delete_message_no_photo)
                else -> stringResource(R.string.galmap_delete_message_photo)
            },
            confirmLabel = stringResource(R.string.delete),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = {
                val ids = targets.map { it.id }
                viewModel.deleteAll(ids)
                selectedIds = selectedIds - ids.toSet()
                if (selected?.id in ids) selected = null
                pendingDelete = null
                Toast.makeText(
                    context,
                    context.resources.getQuantityString(R.plurals.galmap_deleted, ids.size, ids.size),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onDismiss = { pendingDelete = null },
        )
    }

    exportTargets?.let { targets ->
        AlertDialog(
            onDismissRequest = { exportTargets = null },
            title = {
                Text(pluralStringResource(R.plurals.gallery_export_title, targets.size, targets.size))
            },
            text = {
                Column {
                    PhotoExportAction.entries.forEach { action ->
                        ListItem(
                            headlineContent = { Text(stringResource(action.labelRes)) },
                            supportingContent = { Text(stringResource(action.descriptionRes, PhotoExporter.ALBUM)) },
                            leadingContent = {
                                Icon(
                                    imageVector = when (action) {
                                        PhotoExportAction.SHARE -> Icons.Filled.Share
                                        PhotoExportAction.SAVE -> Icons.Filled.Download
                                        PhotoExportAction.ZIP -> Icons.Filled.FolderZip
                                    },
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.clickable {
                                exportTargets = null
                                startExport(action, targets)
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { exportTargets = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (isExporting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.export_msg_exporting)) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text(stringResource(R.string.gallery_preparing_photos))
                }
            },
            confirmButton = {},
        )
    }

    exportError?.let { message ->
        AlertDialog(
            onDismissRequest = { exportError = null },
            title = { Text(stringResource(R.string.export_failed)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { exportError = null }) { Text(stringResource(R.string.export_msg_close)) }
            },
        )
    }
}

/** The editor of a single photograph. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaypointEditor(
    waypoint: Waypoint,
    tripName: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onSaveToDevice: () -> Unit,
    onShowOnMap: () -> Unit,
) {
    var description by remember(waypoint.id) { mutableStateOf(waypoint.description) }
    var category by remember(waypoint.id) { mutableStateOf(waypoint.category) }
    val hasPhoto = waypoint.imagePath?.let { File(it).exists() } == true

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WaypointThumbnail(
                waypoint = waypoint,
                large = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (hasPhoto) 4f / 3f else 2f)
                    .clip(RoundedCornerShape(14.dp)),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onShare,
                    enabled = hasPhoto,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.export_share), modifier = Modifier.padding(start = 6.dp), maxLines = 1)
                }
                OutlinedButton(
                    onClick = onSaveToDevice,
                    enabled = hasPhoto,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.save), modifier = Modifier.padding(start = 6.dp), maxLines = 1)
                }
            }
            OutlinedButton(
                onClick = onShowOnMap,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.galmap_show_on_map),
                    modifier = Modifier.padding(start = 6.dp),
                    maxLines = 1,
                )
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    stringResource(R.string.delete),
                    modifier = Modifier.padding(start = 6.dp),
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                )
            }

            Text(
                text = tripName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = Formatters.coordinates(waypoint.latitude, waypoint.longitude) +
                    " · " + Formatters.altitude(waypoint.altitude),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.description)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.category), style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DefaultCategory.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { category = option.key }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ColorDot(Color(option.color))
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (option.key == category) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (option.key == category) {
                            Text(
                                text = stringResource(R.string.gallery_selected),
                                style = MaterialTheme.typography.labelSmall,
                                color = ForestGreen,
                            )
                        }
                    }
                }
            }

            TextButton(onClick = { onSave(description, category) }) { Text(stringResource(R.string.gallery_save_changes)) }
        }
    }
}

/**
 * The picture of a waypoint: its photograph or, when it has none (or the file cannot be
 * read), a tile in the colour of its category with the category icon and its text.
 */
@Composable
private fun WaypointThumbnail(
    waypoint: Waypoint,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val imagePath = waypoint.imagePath
    var photoFailed by remember(imagePath) { mutableStateOf(false) }
    if (!imagePath.isNullOrBlank() && !photoFailed) {
        AsyncImage(
            model = File(imagePath),
            contentDescription = waypoint.description,
            contentScale = ContentScale.Crop,
            onState = { photoFailed = it is AsyncImagePainter.State.Error },
            modifier = modifier,
        )
    } else {
        CategoryTile(waypoint = waypoint, large = large, modifier = modifier)
    }
}

/** A waypoint without photo: category colour, category icon and description or label. */
@Composable
private fun CategoryTile(
    waypoint: Waypoint,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val background = Color(DefaultCategory.colorFor(waypoint.category))
    val darkContent = background.luminance() > 0.5f
    val content = if (darkContent) Color.Black.copy(alpha = 0.85f) else Color.White
    val label = waypoint.description.ifBlank { categoryLabel(waypoint.category) }
    Box(
        modifier = modifier.background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = categoryIcon(waypoint.category),
            contentDescription = stringResource(R.string.galmap_no_photo),
            tint = content,
            modifier = Modifier
                .padding(bottom = if (large) 12.dp else 18.dp)
                .size(if (large) 64.dp else 40.dp),
        )
        Text(
            text = label,
            color = content,
            style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(if (darkContent) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}