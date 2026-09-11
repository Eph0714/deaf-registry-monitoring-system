package com.deafregistry.app.util

import com.deafregistry.app.data.remote.dto.AppVersionDto
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private data class GitHubReleaseAsset(
    val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String
)

private data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String?,
    val name: String?,
    val body: String?,
    val assets: List<GitHubReleaseAsset>?
)

/**
 * This app isn't distributed through Google Play, so there's no automatic update channel from a
 * store - previously an admin had to remember to publish the new version by hand in Control
 * Panel > App Update after every release (easy to forget - see git history), and only devices
 * that happened to check would ever see it. Checking GitHub's Releases API directly removes that
 * manual step entirely: every release just needs a `vX.Y` tag, an APK asset, and a release title
 * containing "(versionCode N)" - the same convention every release already uses.
 */
object GitHubUpdateChecker {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/Eph0714/deaf-registry-monitoring-system/releases/latest"
    private val versionCodePattern = Regex("""versionCode\s*(\d+)""", RegexOption.IGNORE_CASE)

    /** Returns null on any failure (offline, rate-limited, malformed release) - callers treat a
     * failed check the same as "no update available" rather than surfacing it as an error. */
    suspend fun fetchLatest(): AppVersionDto? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val release = Gson().fromJson(body, GitHubRelease::class.java) ?: return@withContext null
            val tag = release.tagName?.takeIf { it.isNotBlank() } ?: return@withContext null
            val versionCode = versionCodePattern.find(release.name.orEmpty())
                ?.groupValues?.get(1)?.toIntOrNull() ?: return@withContext null
            val apkUrl = release.assets.orEmpty()
                .firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?.browserDownloadUrl ?: return@withContext null

            AppVersionDto(
                versionCode = versionCode,
                versionName = tag.removePrefix("v"),
                apkUrl = apkUrl,
                releaseNotes = release.body
            )
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
