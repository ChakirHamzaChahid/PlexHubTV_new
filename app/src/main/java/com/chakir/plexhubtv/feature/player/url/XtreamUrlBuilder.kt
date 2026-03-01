package com.chakir.plexhubtv.feature.player.url

import com.chakir.plexhubtv.core.model.XtreamAccount
import com.chakir.plexhubtv.core.network.xtream.XtreamApiClient
import com.chakir.plexhubtv.domain.repository.XtreamAccountRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds direct-play stream URLs for Xtream content (VOD movies and series episodes).
 * Resolves account credentials from encrypted storage at playback time.
 */
@Singleton
class XtreamUrlBuilder @Inject constructor(
    private val accountRepo: XtreamAccountRepository,
    private val apiClient: XtreamApiClient,
) {
    /**
     * Build a direct-play URL for an Xtream media item.
     *
     * @param ratingKey The media identifier (e.g. "vod_12345.mkv" or "ep_67890.mp4")
     *                  The container extension is encoded after the last dot.
     *                  Legacy keys without extension (e.g. "vod_12345") fall back to account-level preferred format.
     * @param serverId The server identifier (e.g. "xtream_abc12345")
     * @return The direct stream URL, or null if account/credentials not found
     */
    suspend fun buildUrl(ratingKey: String, serverId: String): String? {
        val accountId = serverId.removePrefix("xtream_")
        val account = accountRepo.getAccount(accountId) ?: return null
        val password = accountRepo.getDecryptedPassword(accountId) ?: return null
        val fallbackExt = preferredExtension(account)

        val url = when {
            ratingKey.startsWith("vod_") -> {
                val body = ratingKey.removePrefix("vod_")
                val (idStr, ext) = splitIdAndExtension(body, fallbackExt)
                val streamId = idStr.toIntOrNull() ?: return null
                apiClient.buildMovieUrl(account, account.username, password, streamId, ext)
            }
            ratingKey.startsWith("ep_") -> {
                val body = ratingKey.removePrefix("ep_")
                val (episodeId, ext) = splitIdAndExtension(body, fallbackExt)
                apiClient.buildEpisodeUrl(account, account.username, password, episodeId, ext)
            }
            else -> null
        }
        Timber.d("XtreamUrlBuilder: ratingKey=$ratingKey → url=$url")
        return url
    }

    /**
     * Split "12345.mkv" into ("12345", "mkv").
     * If no dot is present (legacy format), returns (body, fallback).
     * Uses FIRST dot to avoid issues with IDs containing extension-like suffixes.
     */
    private fun splitIdAndExtension(body: String, fallback: String): Pair<String, String> {
        val dotIndex = body.indexOf('.')
        return if (dotIndex > 0) {
            body.substring(0, dotIndex) to body.substring(dotIndex + 1)
        } else {
            body to fallback
        }
    }

    private fun preferredExtension(account: XtreamAccount): String = when {
        // Prefer ts/mp4 over m3u8: Xtream servers return raw streams even for .m3u8 URLs,
        // which causes ExoPlayer's HLS parser to fail with "Input does not start with #EXTM3U"
        account.allowedFormats.contains("ts") -> "ts"
        account.allowedFormats.contains("mp4") -> "mp4"
        account.allowedFormats.contains("m3u8") -> "m3u8"
        account.allowedFormats.isNotEmpty() -> account.allowedFormats.first()
        else -> "ts"
    }
}
