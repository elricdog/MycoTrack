package com.mikotrack.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.mikotrack.BuildConfig
import com.mikotrack.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Why the last update check or download failed. */
enum class UpdateFailure { Network, RateLimited, Http, NoAsset, Storage }

/**
 * Checks GitHub for a newer release and, when the user accepts, downloads the APK that
 * suits the device and opens the system installer.
 *
 * The state is a [StateFlow] observed by the update dialogs (shown over the whole
 * application) and by the settings screen. Checks are silent on start (at most once a
 * day, and the user can turn them off in the settings); a check from the settings is
 * forced and reports its result, including failures.
 */
class UpdateManager(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {

    /** The observable state of the update system. */
    sealed interface State {
        /** Nothing happening; the update system is invisible. */
        data object Idle : State

        /** A check is running. */
        data object Checking : State

        /** The latest release is not newer than the installed version. */
        data class UpToDate(val checkedAt: Long) : State

        /** A newer release was found and the user has been asked about it. */
        data class Available(val update: AppUpdate) : State

        /** The APK is being downloaded. [total] is 0 when the size is unknown. */
        data class Downloading(val downloaded: Long, val total: Long) : State

        /** The APK is downloaded and the installer can be launched. */
        data class ReadyToInstall(val update: AppUpdate, val file: File) : State

        /** The last check (forced) or download failed. */
        data class Failed(val failure: UpdateFailure, val detail: String = "") : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var downloadJob: Job? = null

    /**
     * Asks GitHub for the latest release. A silent check ([force] = false) respects the
     * settings (auto checks on, at most one check per day, releases the user dismissed)
     * and never leaves a visible failure; a forced check from the settings always runs
     * and reports the outcome.
     */
    fun check(force: Boolean = false) {
        val current = _state.value
        if (current is State.Checking || current is State.Downloading) return
        scope.launch {
            if (!force) {
                if (!settingsStore.autoUpdateChecks.first()) return@launch
                val last = settingsStore.lastUpdateCheck.first()
                if (last > 0L && System.currentTimeMillis() - last < CHECK_INTERVAL_MILLIS) return@launch
            }
            _state.value = State.Checking
            try {
                val release = fetchLatestRelease()
                val checkedAt = System.currentTimeMillis()
                settingsStore.setLastUpdateCheck(checkedAt)
                val currentVersion = BuildConfig.VERSION_NAME
                val isNewer = compareVersions(releaseVersion(release.tagName), currentVersion) > 0
                val dismissed = settingsStore.dismissedUpdateTag.first() == release.tagName
                when {
                    !isNewer -> _state.value = State.UpToDate(checkedAt)
                    dismissed && !force -> _state.value = State.Idle
                    else -> {
                        val asset = pickApkAsset(release.assets, Build.SUPPORTED_ABIS.toList())
                        if (asset == null) {
                            _state.value = State.Failed(UpdateFailure.NoAsset, release.tagName)
                        } else {
                            _state.value = State.Available(AppUpdate(currentVersion, release, asset))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
                val failure = when {
                    e is RateLimitedException -> UpdateFailure.RateLimited
                    e is NoAssetException -> UpdateFailure.NoAsset
                    e is HttpException -> UpdateFailure.Http
                    e is IOException -> UpdateFailure.Network
                    else -> UpdateFailure.Network
                }
                // A silent failure on start stays invisible.
                _state.value = if (force) State.Failed(failure, e.message ?: "") else State.Idle
            }
        }
    }

    /** Downloads the APK of [update] with progress, then moves to [State.ReadyToInstall]. */
    fun download(update: AppUpdate) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            // Drop APKs left by previous attempts.
            dir.listFiles()?.filter { it.name.endsWith(".apk") }?.forEach { it.delete() }
            val target = File(dir, update.asset.name)
            _state.value = State.Downloading(0, update.asset.size)
            try {
                withContext(Dispatchers.IO) { downloadFile(update.asset.downloadUrl, target) }
                if (update.asset.size > 0L && target.length() != update.asset.size) {
                    target.delete()
                    _state.value = State.Failed(UpdateFailure.Storage, update.asset.name)
                    return@launch
                }
                _state.value = State.ReadyToInstall(update, target)
            } catch (e: Exception) {
                Log.w(TAG, "Update download failed", e)
                target.delete()
                _state.value = State.Failed(
                    if (e is IOException) UpdateFailure.Network else UpdateFailure.Storage,
                    e.message ?: "",
                )
            }
        }
    }

    /** Cancels a running download and goes back to idle. */
    fun cancelDownload() {
        downloadJob?.cancel()
        _state.value = State.Idle
    }

    /** The user declined this release; silent checks skip it, forced checks show it again. */
    fun dismiss() {
        val update = (_state.value as? State.Available)?.update ?: return
        scope.launch { settingsStore.setDismissedUpdateTag(update.release.tagName) }
        _state.value = State.Idle
    }

    /** Abandons a finished download (the file is kept until the next attempt). */
    fun dismissReady() {
        _state.value = State.Idle
    }

    /**
     * Launches the system installer for the downloaded APK. On Android 8 and newer the
     * app must be allowed to install apps from unknown sources; [canRequestInstalls]
     * tells whether that permission is already granted, and [openInstallPermissionSettings]
     * opens the system screen that grants it.
     */
    fun launchInstall(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Whether the app is allowed to install APKs (granted in the system settings). */
    fun canRequestInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen that allows MycoTrack to install apps. */
    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** GETs the latest release from the GitHub API. */
    private fun fetchLatestRelease(): GitHubRelease =
        json.decodeFromString(GitHubRelease.serializer(), httpGet(LATEST_RELEASE_URL))

    /**
     * GETs every published release, newest first, for the changelog. Drafts are dropped;
     * the list is exactly what the releases page of the repository shows.
     */
    suspend fun fetchChangelog(): List<GitHubRelease> = withContext(Dispatchers.IO) {
        json.decodeFromString(ListSerializer(GitHubRelease.serializer()), httpGet(RELEASES_URL))
            .filter { !it.draft }
    }

    private fun httpGet(url: String): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty(
                    "User-Agent",
                    "MycoTrack/${BuildConfig.VERSION_NAME} (Android update check)",
                )
            }
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                HttpURLConnection.HTTP_FORBIDDEN, 429 -> throw RateLimitedException()
                HttpURLConnection.HTTP_NOT_FOUND -> throw NoAssetException()
                else -> throw HttpException(connection.responseCode)
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection?.disconnect()
        }
    }

    /** Streams [url] to [target] with periodic progress updates. */
    private fun downloadFile(url: String, target: File) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MycoTrack/${BuildConfig.VERSION_NAME} (Android)")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 }
                ?: (_state.value as? State.Downloading)?.total?.takeIf { it > 0 }
                ?: 0L
            var downloaded = 0L
            var lastEmit = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 200) {
                            lastEmit = now
                            _state.value = State.Downloading(downloaded, total)
                        }
                    }
                }
            }
        } finally {
            connection?.disconnect()
        }
    }

    private class RateLimitedException : IOException("rate limited")
    private class NoAssetException : IOException("no such release")
    private class HttpException(val code: Int) : IOException("HTTP $code")

    private companion object {
        const val TAG = "UpdateManager"
        const val CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
    }
}
