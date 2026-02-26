package com.chakir.plexhubtv.feature.player.url

import com.chakir.plexhubtv.core.model.XtreamAccount
import com.chakir.plexhubtv.core.network.xtream.XtreamApiClient
import com.chakir.plexhubtv.domain.repository.XtreamAccountRepository
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
     * @param ratingKey The media identifier (e.g. "vod_12345" or "ep_67890")
     * @param serverId The server identifier (e.g. "xtream_abc12345")
     * @return The direct stream URL, or null if account/credentials not found
     */
    suspend fun buildUrl(ratingKey: String, serverId: String): String? {
        val accountId = serverId.removePrefix("xtream_")
        val account = accountRepo.getAccount(accountId) ?: return null
        val password = accountRepo.getDecryptedPassword(accountId) ?: return null
        val ext = preferredExtension(account)

        return when {
            ratingKey.startsWith("vod_") -> {
                val streamId = ratingKey.removePrefix("vod_").toIntOrNull() ?: return null
                apiClient.buildMovieUrl(account, account.username, password, streamId, ext)
            }
            ratingKey.startsWith("ep_") -> {
                val episodeId = ratingKey.removePrefix("ep_")
                apiClient.buildEpisodeUrl(account, account.username, password, episodeId, ext)
            }
            else -> null
        }
    }

    private fun preferredExtension(account: XtreamAccount): String = when {
        account.allowedFormats.contains("m3u8") -> "m3u8"
        account.allowedFormats.contains("ts") -> "ts"
        account.allowedFormats.isNotEmpty() -> account.allowedFormats.first()
        else -> "m3u8"
    }
}
