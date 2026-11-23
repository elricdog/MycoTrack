package com.mikotrack.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The self update system.
 *
 * MycoTrack is distributed as a signed APK published in the GitHub releases of the
 * repository. On start (at most once a day) and on demand from the settings, the
 * application asks the GitHub API for the latest release, compares its tag with the
 * installed version and, when there is a newer one, offers to download the APK that
 * matches the device and open the system installer. Nothing is ever installed
 * silently: the user confirms the update and Android confirms the installation.
 */

/** The repository that publishes the releases, used for the GitHub API call. */
const val UPDATE_REPO = "elricdog/MycoTrack"

/** The GitHub API endpoint of the latest published (not draft, not prerelease) release. */
const val LATEST_RELEASE_URL = "https://api.github.com/repos/$UPDATE_REPO/releases/latest"

/** The GitHub API endpoint listing the published releases, newest first. */
const val RELEASES_URL = "https://api.github.com/repos/$UPDATE_REPO/releases?per_page=30"

/**
 * The Atom feed of published releases, newest first. It lives outside the GitHub API,
 * so it is not subject to the API quota, which shared mobile IPs exhaust often.
 */
const val LATEST_RELEASE_FEED_URL = "https://github.com/$UPDATE_REPO/releases.atom"

/** The tag of the newest release in an Atom feed (the first entry), null when absent. */
fun parseLatestTagFromFeed(feed: String): String? =
    Regex("releases/tag/([^\"<>\\s]+)").find(feed)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

/** The APK file names every release publishes, used to rebuild the download links. */
private val RELEASE_APK_NAMES = listOf(
    "app-arm64-v8a-release.apk",
    "app-armeabi-v7a-release.apk",
    "app-x86-release.apk",
    "app-x86_64-release.apk",
    "app-universal-release.apk",
)

/**
 * A release built from a tag alone, for the Atom feed fallback: the notes and the file
 * sizes are unknown, but the tag and the standard download links are enough to offer
 * the update.
 */
fun syntheticReleaseFromTag(tag: String): GitHubRelease = GitHubRelease(
    tagName = tag,
    name = tag,
    assets = RELEASE_APK_NAMES.map {
        ReleaseAsset(
            name = it,
            downloadUrl = "https://github.com/$UPDATE_REPO/releases/download/$tag/$it",
        )
    },
)

/** One downloadable file attached to a GitHub release. */
@Serializable
data class ReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
    @SerialName("content_type") val contentType: String = "",
)

/** The parts of a GitHub release that the update system uses. Unknown fields are ignored. */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String = "",
    val body: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    @SerialName("published_at") val publishedAt: String = "",
    val assets: List<ReleaseAsset> = emptyList(),
)

/** The version a release tag refers to: the tag without its leading "v". */
fun releaseVersion(tagName: String): String = tagName.removePrefix("v").removePrefix("V")

/**
 * Compares two dot separated numeric versions ("1.2.3", "v1.2"). Missing parts count as
 * zero, so "1.2" equals "1.2.0"; non numeric suffixes ("-beta") are ignored. Returns a
 * negative number, zero or a positive number when [left] is older, equal or newer.
 */
fun compareVersions(left: String, right: String): Int {
    val a = versionParts(left)
    val b = versionParts(right)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return if (x > y) 1 else -1
    }
    return 0
}

private fun versionParts(version: String): List<Int> =
    releaseVersion(version)
        .split('.', '-', '+', ' ')
        .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }

/**
 * Chooses the APK asset that suits a device. The first supported ABI of the device that
 * has a matching APK wins (the ABI list is ordered from best to compatible), then the
 * universal APK, then any APK of the release.
 */
fun pickApkAsset(assets: List<ReleaseAsset>, supportedAbis: List<String>): ReleaseAsset? {
    val apks = assets.filter { it.name.endsWith(".apk") }
    if (apks.isEmpty()) return null
    for (abi in supportedAbis) {
        apks.firstOrNull { it.name.contains("-$abi-", ignoreCase = true) }?.let { return it }
    }
    return apks.firstOrNull { it.name.contains("universal", ignoreCase = true) } ?: apks.first()
}

/** An update the user can install: what is installed, the release and the APK to fetch. */
data class AppUpdate(
    val currentVersion: String,
    val release: GitHubRelease,
    val asset: ReleaseAsset,
) {
    /** The version of the release, ready to show to the user. */
    val newVersion: String = releaseVersion(release.tagName)
}
