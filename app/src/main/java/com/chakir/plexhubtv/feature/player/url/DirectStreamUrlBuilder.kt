package com.chakir.plexhubtv.feature.player.url

import com.chakir.plexhubtv.data.source.MediaSourceResolver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified URL builder for direct-stream sources (Xtream, Backend).
 * Delegates to source-specific builders and uses [MediaSourceResolver]
 * to determine if a given serverId is a direct-stream source.
 */
@Singleton
class DirectStreamUrlBuilder @Inject constructor(
    private val xtreamUrlBuilder: XtreamUrlBuilder,
    private val backendUrlBuilder: BackendUrlBuilder,
    private val mediaSourceResolver: MediaSourceResolver,
) {
    /**
     * Returns true if this serverId represents a direct-stream source
     * (i.e., URL must be built locally rather than using Plex transcode).
     */
    fun isDirectStream(serverId: String): Boolean =
        !mediaSourceResolver.resolve(serverId).needsUrlResolution()

    /**
     * Build the direct stream URL for non-Plex sources.
     * Tries each builder in turn; they return null for non-matching serverIds.
     */
    suspend fun buildUrl(ratingKey: String, serverId: String): String? =
        xtreamUrlBuilder.buildUrl(ratingKey, serverId)
            ?: backendUrlBuilder.buildUrl(ratingKey, serverId)
}
