package com.chakir.plexhubtv.core.network

import com.chakir.plexhubtv.data.model.GenericPlexResponse
import com.chakir.plexhubtv.data.model.PlexResponse
import com.chakir.plexhubtv.domain.model.Server
import retrofit2.Response

/**
 * Android equivalent of Plezy's PlexClient.
 * Encapsulates a specific server's connection info and provides type-safe API access.
 */
class PlexClient(
    val server: Server,
    private val api: PlexApiService,
    val baseUrl: String
) {
    private val token: String = server.accessToken ?: ""

    private fun buildUrl(path: String): String {
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        // Ensure we don't double append query indicator
        val separator = if (path.contains("?")) "&" else "?"
        return "$cleanBase$cleanPath${separator}X-Plex-Token=$token"
    }

    fun getPlaybackUrl(partKey: String): String {
        return buildUrl(partKey)
    }

    fun getTranscodeUrl(ratingKey: String, clientId: String, quality: Int = 100, bitrate: Int = 200000): String {
        val path = "/library/metadata/$ratingKey"
        val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
        
        return buildUrl("/video/:/transcode/universal/start.m3u8") +
                "&path=$encodedPath" +
                "&mediaIndex=0" +
                "&partIndex=0" +
                "&protocol=hls" +
                "&fastSeek=1" +
                "&directPlay=0" +
                "&directStream=1" +
                "&subtitleSize=100" +
                "&audioBoost=100" +
                "&location=lan" +
                "&addDebugOverlay=0" +
                "&autoAdjustQuality=0" +
                "&videoQuality=$quality" +
                "&maxVideoBitrate=$bitrate" +
                "&X-Plex-Client-Identifier=$clientId" +
                "&X-Plex-Platform=Android" +
                "&X-Plex-Platform-Version=${android.os.Build.VERSION.RELEASE}" +
                "&X-Plex-Product=PlexHubTV" +
                "&X-Plex-Device=${java.net.URLEncoder.encode(android.os.Build.MODEL, "UTF-8")}"
    }

    suspend fun getHubs(count: Int = 50): Response<GenericPlexResponse> {
        return api.getHubs(buildUrl("/hubs?includeGuids=1&includeMeta=1"), count)
    }

    suspend fun search(query: String): Response<GenericPlexResponse> {
        return api.search(buildUrl("/search?includeGuids=1&includeMedia=1"), query)
    }

    suspend fun getMetadata(ratingKey: String, includeChildren: Boolean = false): Response<PlexResponse> {
        var path = "/library/metadata/$ratingKey?includeGuids=1&includeMeta=1&includeChapters=1&includeMarkers=1"
        if (includeChildren) {
            path += "&includeChildren=1"
        }
        return api.getMetadata(buildUrl(path))
    }
    
    suspend fun getOnDeck(): Response<PlexResponse> {
        return api.getMetadata(buildUrl("/library/onDeck?includeGuids=1&includeMeta=1"))
    }

    suspend fun getChildren(ratingKey: String): Response<PlexResponse> {
        return api.getMetadata(buildUrl("/library/metadata/$ratingKey/children?includeGuids=1&includeMeta=1"))
    }

    suspend fun getSections(): Response<GenericPlexResponse> {
        return api.getSections(buildUrl("/library/sections"))
    }

    suspend fun getLibraryContents(sectionId: String, start: Int, size: Int): Response<PlexResponse> {
        // "all" endpoint retrieves all items in the section
        // We can add sorting/filtering params later if needed
        return api.getLibraryContents(
            url = buildUrl("/library/sections/$sectionId/all?includeGuids=1&includeMeta=1&includeRatings=1"),
            start = start,
            size = size
        )
    }

    fun getThumbnailUrl(path: String?): String? {
        if (path == null) return null
        val cleanBase = baseUrl.removeSuffix("/")
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "$cleanBase$cleanPath?X-Plex-Token=$token"
    }

    /**
     * Reports playback progress to Plex (Scrobble).
     * @param ratingKey The item's rating key
     * @param state "playing", "paused", or "stopped"
     * @param timeMs Current playback position in milliseconds
     */
    suspend fun updateTimeline(
        ratingKey: String,
        state: String,
        timeMs: Long,
        durationMs: Long
    ): Response<Unit> {
        return api.updateTimeline(
            url = "$baseUrl/:/timeline",
            ratingKey = ratingKey,
            state = state,
            time = timeMs,
            duration = durationMs,
            token = token
        )
    }

    suspend fun scrobble(ratingKey: String): Response<Unit> {
        return api.scrobble(
            url = "$baseUrl/:/scrobble",
            ratingKey = ratingKey,
            token = token
        )
    }

    suspend fun unscrobble(ratingKey: String): Response<Unit> {
        return api.unscrobble(
            url = "$baseUrl/:/unscrobble",
            ratingKey = ratingKey,
            token = token
        )
    }
}
