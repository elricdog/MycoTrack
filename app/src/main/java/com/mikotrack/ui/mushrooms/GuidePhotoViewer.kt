package com.mikotrack.ui.mushrooms

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.mikotrack.R
import com.mikotrack.export.ExportManager
import com.mikotrack.export.PhotoExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * A photograph of the guide, as shown in the thumbnails and in the full-screen viewer.
 *
 * @param image the file name inside the `images/` folder of the guide assets.
 * @param caption the caption in the language of the guide, if any.
 * @param scientificName the species shown, when it is not obvious (look-alikes).
 */
data class GuidePhoto(
    val image: String,
    val credit: ImageCredit,
    val caption: String?,
    val scientificName: String? = null,
    val toxic: Boolean = false,
    val lookAlike: Boolean = false,
) {
    val assetPath: String get() = "${MushroomCatalog.ASSET_DIR}/images/$image"
    val uri: String get() = "file:///android_asset/$assetPath"

    companion object {
        /** The main photograph of a species followed by its gallery. */
        fun speciesPhotos(mushroom: Mushroom, language: String): List<GuidePhoto> =
            listOf(
                GuidePhoto(
                    image = mushroom.data.image,
                    credit = mushroom.data.imageCredit,
                    caption = mushroom.commonName,
                    scientificName = mushroom.scientificName,
                ),
            ) + mushroom.data.gallery.map { it.toPhoto(language, lookAlike = false) }

        /** The photographs of the dangerous look-alikes of a species. */
        fun lookAlikePhotos(mushroom: Mushroom, language: String): List<GuidePhoto> =
            mushroom.data.lookAlikeImages.map { it.toPhoto(language, lookAlike = true) }

        private fun GuideImage.toPhoto(language: String, lookAlike: Boolean) = GuidePhoto(
            image = image,
            credit = credit,
            caption = captionIn(language),
            scientificName = scientificName,
            toxic = toxic,
            lookAlike = lookAlike,
        )
    }
}

/**
 * A full-screen viewer of a group of photographs of the guide: swipe between them, pinch
 * or double-tap to zoom, and share or save the current one.
 */
@Composable
fun GuidePhotoViewer(
    photos: List<GuidePhoto>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (photos.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
        val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, photos.lastIndex)) { photos.size }
        var chromeVisible by rememberSaveable { mutableStateOf(true) }
        val scales = remember { mutableStateMapOf<Int, Float>() }
        val current = photos[pagerState.currentPage.coerceIn(0, photos.lastIndex)]

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = (scales[pagerState.currentPage] ?: 1f) <= ZoomMath.MIN_SCALE,
                beyondViewportPageCount = 1,
            ) { page ->
                ZoomableImage(
                    photo = photos[page],
                    isCurrent = page == pagerState.currentPage,
                    onScaleChange = { scales[page] = it },
                    onTap = { chromeVisible = !chromeVisible },
                )
            }
            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                ViewerTopBar(
                    photo = current,
                    position = pagerState.currentPage + 1,
                    count = photos.size,
                    onClose = onDismiss,
                )
            }
            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ViewerCaption(current)
            }
        }
    }
}

private val ChromeBackground = Color.Black.copy(alpha = 0.55f)

@Composable
private fun ViewerTopBar(photo: GuidePhoto, position: Int, count: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun launchAction(action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                android.util.Log.e("MikoTrack", "Guide photo action failed", error)
                Toast.makeText(context, context.getString(R.string.guide_photos_action_error), Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    fun save() = launchAction {
        GuidePhotoFiles.saveToPictures(context, photo)
        Toast.makeText(
            context,
            context.getString(R.string.guide_photos_saved, PhotoExporter.ALBUM),
            Toast.LENGTH_LONG,
        ).show()
    }

    // Android 9 and older need the storage permission to write into the public gallery.
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            save()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.gallery_storage_permission_required),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChromeBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.guide_photos_close), tint = Color.White)
        }
        Text(
            text = stringResource(R.string.guide_photos_counter, position, count),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (busy) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
        IconButton(
            enabled = !busy,
            onClick = {
                launchAction {
                    val intent = GuidePhotoFiles.shareIntent(context, photo)
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.guide_photos_share)))
                }
            },
        ) {
            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.guide_photos_share), tint = Color.White)
        }
        IconButton(
            enabled = !busy,
            onClick = {
                val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsPermission) {
                    storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    save()
                }
            },
        ) {
            Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.guide_photos_save), tint = Color.White)
        }
    }
}

@Composable
private fun ViewerCaption(photo: GuidePhoto) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChromeBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (photo.lookAlike) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (photo.toxic) Color(0xFFFF6B6B) else Color.LightGray,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(if (photo.toxic) R.string.guide_photos_toxic else R.string.guide_photos_not_edible),
                    color = if (photo.toxic) Color(0xFFFF6B6B) else Color.LightGray,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        photo.scientificName?.takeIf { it != photo.caption }?.let {
            Text(it, color = Color.White, style = MaterialTheme.typography.titleSmall, fontStyle = FontStyle.Italic)
        }
        photo.caption?.let {
            Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = stringResource(R.string.mushroom_guide_photo_credit, photo.credit.author, photo.credit.licence),
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (photo.credit.sourceUrl.isNotBlank()) {
                TextButton(onClick = { runCatching { uriHandler.openUri(photo.credit.sourceUrl) } }) {
                    Text(stringResource(R.string.guide_photos_source), color = Color.White)
                }
            }
            if (photo.credit.licenceUrl.isNotBlank()) {
                TextButton(onClick = { runCatching { uriHandler.openUri(photo.credit.licenceUrl) } }) {
                    Text(stringResource(R.string.guide_photos_licence), color = Color.White)
                }
            }
        }
    }
}

/** A photograph that can be zoomed with two fingers or a double tap and panned when zoomed. */
@Composable
private fun ZoomableImage(
    photo: GuidePhoto,
    isCurrent: Boolean,
    onScaleChange: (Float) -> Unit,
    onTap: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var container by remember { mutableStateOf(IntSize.Zero) }
    var intrinsic by remember { mutableStateOf(Size.Unspecified) }
    val scope = rememberCoroutineScope()

    fun update(newScale: Float, newX: Float, newY: Float) {
        val width = container.width.toFloat()
        val height = container.height.toFloat()
        val (fittedWidth, fittedHeight) = if (intrinsic.hasArea()) {
            ZoomMath.fitSize(intrinsic.width, intrinsic.height, width, height)
        } else {
            width to height
        }
        scale = newScale
        offsetX = ZoomMath.clampOffset(newX, fittedWidth, newScale, width)
        offsetY = ZoomMath.clampOffset(newY, fittedHeight, newScale, height)
        onScaleChange(newScale)
    }

    fun animateTo(targetScale: Float, focus: Offset) {
        val startScale = scale
        val startX = offsetX
        val startY = offsetY
        val targetX = if (targetScale <= ZoomMath.MIN_SCALE) 0f else ZoomMath.zoomOffset(startX, focus.x, startScale, targetScale)
        val targetY = if (targetScale <= ZoomMath.MIN_SCALE) 0f else ZoomMath.zoomOffset(startY, focus.y, startScale, targetScale)
        scope.launch {
            animate(0f, 1f) { fraction, _ ->
                update(
                    startScale + (targetScale - startScale) * fraction,
                    startX + (targetX - startX) * fraction,
                    startY + (targetY - startY) * fraction,
                )
            }
        }
    }

    LaunchedEffect(isCurrent) {
        if (!isCurrent && scale != ZoomMath.MIN_SCALE) update(ZoomMath.MIN_SCALE, 0f, 0f)
    }

    AsyncImage(
        model = photo.uri,
        contentDescription = photo.caption ?: photo.scientificName,
        contentScale = ContentScale.Fit,
        onSuccess = { state -> intrinsic = state.painter.intrinsicSize },
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { container = it }
            .pointerInput(photo.image) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { position ->
                        if (scale > ZoomMath.MIN_SCALE) {
                            animateTo(ZoomMath.MIN_SCALE, Offset.Zero)
                        } else {
                            animateTo(
                                ZoomMath.DOUBLE_TAP_SCALE,
                                Offset(position.x - size.width / 2f, position.y - size.height / 2f),
                            )
                        }
                    },
                )
            }
            .pointerInput(photo.image) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        // A single finger at rest scale is left to the pager, to swipe between photos.
                        if (pressed >= 2 || scale > ZoomMath.MIN_SCALE) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val newScale = (scale * zoom).coerceIn(ZoomMath.MIN_SCALE, ZoomMath.MAX_SCALE)
                            val focusX = if (centroid.isSpecified) centroid.x - size.width / 2f else 0f
                            val focusY = if (centroid.isSpecified) centroid.y - size.height / 2f else 0f
                            update(
                                newScale,
                                ZoomMath.zoomOffset(offsetX, focusX, scale, newScale) + pan.x,
                                ZoomMath.zoomOffset(offsetY, focusY, scale, newScale) + pan.y,
                            )
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale < ZoomMath.MIN_SCALE + 0.05f && scale != ZoomMath.MIN_SCALE) {
                        update(ZoomMath.MIN_SCALE, 0f, 0f)
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            },
    )
}

private fun Size.hasArea(): Boolean = this != Size.Unspecified && width > 0f && height > 0f

/** Copies the photographs of the guide out of the assets, to share or save them. */
private object GuidePhotoFiles {

    private fun fileName(photo: GuidePhoto): String = "MycoTrack_" + photo.image.substringAfterLast('/')

    private fun mimeType(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    /** Builds an intent that shares the photograph, with its credit as text. */
    suspend fun shareIntent(context: Context, photo: GuidePhoto): Intent {
        val file = withContext(Dispatchers.IO) {
            // The "export/" folder of the cache is exposed by the FileProvider of the app.
            val directory = File(context.cacheDir, "export/guide").apply { mkdirs() }
            File(directory, fileName(photo)).also { target ->
                context.assets.open(photo.assetPath).use { input ->
                    FileOutputStream(target).use { input.copyTo(it) }
                }
            }
        }
        val credit = context.getString(R.string.mushroom_guide_photo_credit, photo.credit.author, photo.credit.licence)
        val text = listOfNotNull(photo.scientificName, credit, photo.credit.sourceUrl.takeIf { it.isNotBlank() })
            .joinToString("\n")
        return ExportManager(context).shareIntent(file, mimeType(file.name)).putExtra(Intent.EXTRA_TEXT, text)
    }

    /** Copies the photograph into the public "Pictures" album of the app. */
    suspend fun saveToPictures(context: Context, photo: GuidePhoto) = withContext(Dispatchers.IO) {
        val name = fileName(photo)
        val mime = mimeType(name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/${PhotoExporter.ALBUM}")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("The photograph could not be added to the gallery.")
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    context.assets.open(photo.assetPath).use { it.copyTo(output) }
                } ?: error("The photograph could not be written.")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        } else {
            @Suppress("DEPRECATION")
            val album = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), PhotoExporter.ALBUM)
            if (!album.exists() && !album.mkdirs()) error("The album folder could not be created.")
            val target = File(album, name)
            context.assets.open(photo.assetPath).use { input -> FileOutputStream(target).use { input.copyTo(it) } }
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
        }
    }
}
