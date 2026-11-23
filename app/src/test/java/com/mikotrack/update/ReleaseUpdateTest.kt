package com.mikotrack.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Unit tests of the pure parts of the self update system. */
class ReleaseUpdateTest {

    @Test
    fun `versions compare numerically part by part`() {
        assertEquals(-1, compareVersions("1.0.0", "1.0.1"))
        assertEquals(1, compareVersions("1.1.0", "1.0.9"))
        assertEquals(-1, compareVersions("1.9.0", "1.10.0"))
        assertEquals(0, compareVersions("1.2", "1.2.0"))
        assertEquals(1, compareVersions("2.0", "1.99.99"))
    }

    @Test
    fun `release tags with a leading v compare with plain versions`() {
        assertEquals(1, compareVersions(releaseVersion("v1.1.0"), "1.0.0"))
        assertEquals(0, compareVersions(releaseVersion("v1.0"), "1.0.0"))
        assertEquals(-1, compareVersions(releaseVersion("v1.0.0"), "v1.0.1"))
    }

    @Test
    fun `prerelease suffixes are ignored`() {
        assertEquals(0, compareVersions("1.2.0-beta", "1.2"))
        assertEquals(1, compareVersions("1.2.0-beta", "1.1.9"))
    }

    @Test
    fun `the asset of the first supported abi wins`() {
        val assets = apkAssets()
        val x86 = pickApkAsset(assets, listOf("x86_64", "arm64-v8a"))
        assertEquals("app-x86_64-release.apk", x86?.name)
        val arm32 = pickApkAsset(assets, listOf("armeabi-v7a"))
        assertEquals("app-armeabi-v7a-release.apk", arm32?.name)
    }

    @Test
    fun `an unknown abi falls back to the universal apk`() {
        val picked = pickApkAsset(apkAssets(), listOf("riscv64"))
        assertEquals("app-universal-release.apk", picked?.name)
    }

    @Test
    fun `a release without apks has nothing to install`() {
        val notes = listOf(ReleaseAsset("notes.txt", "https://x/notes.txt"))
        assertNull(pickApkAsset(notes, listOf("arm64-v8a")))
    }

    @Test
    fun `the first apk is the last resort`() {
        val only = listOf(ReleaseAsset("custom-build.apk", "https://x/custom.apk"))
        assertSame(only.first(), pickApkAsset(only, listOf("mips")))
    }

    @Test
    fun `the GitHub release json is parsed with its unknown fields`() {
        val json = Json { ignoreUnknownKeys = true }
        val release = json.decodeFromString(
            GitHubRelease.serializer(),
            """
            {
              "url": "https://api.github.com/repos/elricdog/MycoTrack/releases/1",
              "tag_name": "v1.1.0",
              "name": "MycoTrack v1.1.0",
              "body": "## Changes\n- In-app self update",
              "prerelease": false,
              "draft": false,
              "published_at": "2026-11-17T10:00:00Z",
              "assets": [
                {
                  "name": "app-arm64-v8a-release.apk",
                  "browser_download_url": "https://release.example/arm64.apk",
                  "size": 33554432,
                  "content_type": "application/vnd.android.package-archive",
                  "id": 1,
                  "node_id": "RA_x",
                  "uploader": {"login": "elricdog"}
                }
              ],
              "author": {"login": "elricdog"}
            }
            """.trimIndent(),
        )
        assertEquals("v1.1.0", release.tagName)
        assertEquals("1.1.0", releaseVersion(release.tagName))
        assertEquals(1, release.assets.size)
        assertEquals(33554432L, release.assets.first().size)
        assertEquals(
            "https://release.example/arm64.apk",
            pickApkAsset(release.assets, listOf("arm64-v8a"))?.downloadUrl,
        )
    }

    @Test
    fun `the Atom feed of releases yields the newest tag`() {
        val feed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <id>tag:github.com,2008:https://github.com/elricdog/MycoTrack/releases/tag/v1.9.9</id>
                <link type="text/html" rel="edit" href="https://github.com/elricdog/MycoTrack/releases/tag/v1.9.9"/>
                <title>MycoTrack v1.9.9 — test</title>
                <updated>2026-11-21T17:31:31Z</updated>
              </entry>
              <entry>
                <id>tag:github.com,2008:https://github.com/elricdog/MycoTrack/releases/tag/v1.0.0</id>
                <title>older</title>
              </entry>
            </feed>
        """.trimIndent()
        assertEquals("v1.9.9", parseLatestTagFromFeed(feed))
        assertNull(parseLatestTagFromFeed("<feed>no releases</feed>"))
    }

    @Test
    fun `a synthetic release from a tag offers the standard apks`() {
        val release = syntheticReleaseFromTag("v1.9.9")
        assertEquals("1.9.9", releaseVersion(release.tagName))
        val arm = pickApkAsset(release.assets, listOf("arm64-v8a"))
        assertEquals("app-arm64-v8a-release.apk", arm?.name)
        assertEquals(
            "https://github.com/elricdog/MycoTrack/releases/download/v1.9.9/app-arm64-v8a-release.apk",
            arm?.downloadUrl,
        )
        val universal = pickApkAsset(release.assets, listOf("riscv64"))
        assertEquals("app-universal-release.apk", universal?.name)
    }

    private fun apkAssets() = listOf(
        "arm64-v8a",
        "armeabi-v7a",
        "x86",
        "x86_64",
        "universal",
    ).map { ReleaseAsset("app-$it-release.apk", "https://x/app-$it-release.apk", 1_000) }
}
