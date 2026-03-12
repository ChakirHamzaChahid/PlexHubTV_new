package com.chakir.plexhubtv.core.update

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks GitHub Releases API for newer app versions.
 * Compares semantic version strings to determine if an update is available.
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) {
    companion object {
        private const val GITHUB_API_URL =
            "https://api.github.com/repos/chakir-elarram/PlexHubTV/releases/latest"
    }

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.w("[Update] GitHub API returned ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val release = gson.fromJson(body, GitHubRelease::class.java)

            val latestVersion = release.tagName.removePrefix("v")
            if (!isNewerVersion(latestVersion, currentVersion)) {
                Timber.d("[Update] Current $currentVersion is up to date (latest: $latestVersion)")
                return@withContext null
            }

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }

            UpdateInfo(
                versionName = latestVersion,
                downloadUrl = apkAsset?.browserDownloadUrl ?: release.htmlUrl,
                releaseNotes = release.body ?: "",
                apkSize = apkAsset?.size ?: 0L,
                htmlUrl = release.htmlUrl,
            ).also {
                Timber.i("[Update] New version available: $latestVersion (current: $currentVersion)")
            }
        } catch (e: Exception) {
            Timber.e(e, "[Update] Failed to check for updates")
            null
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private data class GitHubRelease(
        @SerializedName("tag_name") val tagName: String,
        @SerializedName("html_url") val htmlUrl: String,
        val body: String?,
        val assets: List<GitHubAsset> = emptyList(),
    )

    private data class GitHubAsset(
        val name: String,
        @SerializedName("browser_download_url") val browserDownloadUrl: String,
        val size: Long,
    )
}
