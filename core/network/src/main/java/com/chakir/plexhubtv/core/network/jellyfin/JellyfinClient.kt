package com.chakir.plexhubtv.core.network.jellyfin

import retrofit2.Response

/**
 * High-level facade for a single Jellyfin server.
 *
 * Mirrors [com.chakir.plexhubtv.core.network.PlexClient] — fixes the base URL,
 * auth header, and userId for all calls so callers don't repeat boilerplate.
 *
 * Constructed by [com.chakir.plexhubtv.data.repository.JellyfinClientResolver].
 */
class JellyfinClient(
    val serverId: String,
    val serverName: String,
    val baseUrl: String,
    private val userId: String,
    val accessToken: String,
    private val api: JellyfinApiService,
) {
    /** MediaBrowser authorization header required by Jellyfin API. */
    private val authHeader: String =
        "MediaBrowser Token=\"$accessToken\""

    // ========================================
    // Items
    // ========================================

    suspend fun getUserViews(): Response<JellyfinViewsResponse> =
        api.getUserViews("$baseUrl/Users/$userId/Views", authHeader)

    suspend fun getItems(
        parentId: String? = null,
        includeItemTypes: String? = null,
        startIndex: Int = 0,
        limit: Int = 200,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
        recursive: Boolean = true,
        fields: String = JellyfinApiService.ITEM_FIELDS,
    ): Response<JellyfinItemsResponse> =
        api.getItems(
            url = "$baseUrl/Users/$userId/Items",
            authHeader = authHeader,
            parentId = parentId,
            includeItemTypes = includeItemTypes,
            recursive = recursive,
            fields = fields,
            startIndex = startIndex,
            limit = limit,
            sortBy = sortBy,
            sortOrder = sortOrder,
        )

    suspend fun getItem(itemId: String): Response<JellyfinItem> =
        api.getItem("$baseUrl/Users/$userId/Items/$itemId", authHeader)

    suspend fun getSeasons(seriesId: String): Response<JellyfinItemsResponse> =
        api.getSeasons("$baseUrl/Shows/$seriesId/Seasons", authHeader, userId)

    suspend fun getEpisodes(
        seriesId: String,
        seasonId: String,
    ): Response<JellyfinItemsResponse> =
        api.getEpisodes(
            "$baseUrl/Shows/$seriesId/Episodes",
            authHeader,
            userId,
            seasonId,
        )

    suspend fun getSimilar(itemId: String): Response<JellyfinSimilarResponse> =
        api.getSimilar("$baseUrl/Items/$itemId/Similar", authHeader, userId)

    // ========================================
    // Search
    // ========================================

    suspend fun search(query: String, limit: Int = 30): Response<JellyfinSearchHintResponse> =
        api.search("$baseUrl/Search/Hints", authHeader, query, userId, limit)

    // ========================================
    // Playback Reporting
    // ========================================

    suspend fun reportPlaybackStart(info: JellyfinPlaybackStartInfo): Response<Unit> =
        api.reportPlaybackStart("$baseUrl/Sessions/Playing", authHeader, info)

    suspend fun reportPlaybackProgress(info: JellyfinPlaybackProgressInfo): Response<Unit> =
        api.reportPlaybackProgress("$baseUrl/Sessions/Playing/Progress", authHeader, info)

    suspend fun reportPlaybackStopped(info: JellyfinPlaybackStopInfo): Response<Unit> =
        api.reportPlaybackStopped("$baseUrl/Sessions/Playing/Stopped", authHeader, info)

    // ========================================
    // Watch Status
    // ========================================

    suspend fun markPlayed(itemId: String): Response<JellyfinUserData> =
        api.markPlayed("$baseUrl/Users/$userId/PlayedItems/$itemId", authHeader)

    suspend fun markUnplayed(itemId: String): Response<JellyfinUserData> =
        api.markUnplayed("$baseUrl/Users/$userId/PlayedItems/$itemId", authHeader)

    // ========================================
    // Favorites
    // ========================================

    suspend fun addFavorite(itemId: String): Response<JellyfinUserData> =
        api.addFavorite("$baseUrl/Users/$userId/FavoriteItems/$itemId", authHeader)

    suspend fun removeFavorite(itemId: String): Response<JellyfinUserData> =
        api.removeFavorite("$baseUrl/Users/$userId/FavoriteItems/$itemId", authHeader)

    // ========================================
    // Image URL Helpers (relative paths — no token)
    // ========================================

    /**
     * Returns the relative image path for a given item.
     * Token is NOT embedded — resolved at display time by [JellyfinSourceHandler].
     */
    fun primaryImagePath(itemId: String, tag: String?, maxWidth: Int = 300): String {
        val tagParam = if (tag != null) "&tag=$tag" else ""
        return "/Items/$itemId/Images/Primary?maxWidth=$maxWidth$tagParam"
    }

    fun backdropImagePath(itemId: String, tag: String?, index: Int = 0): String {
        val tagParam = if (tag != null) "&tag=$tag" else ""
        return "/Items/$itemId/Images/Backdrop/$index?maxWidth=1920$tagParam"
    }

    // ========================================
    // Direct Stream URL
    // ========================================

    /**
     * Builds a direct stream URL for a given item.
     * Includes api_key for authentication (required for media streams).
     */
    fun directStreamUrl(itemId: String, container: String): String =
        "$baseUrl/Videos/$itemId/stream.$container?static=true&api_key=$accessToken"

    fun hlsTranscodeUrl(
        itemId: String,
        bitrate: Int,
        audioCodec: String = "aac",
        videoCodec: String = "h264",
    ): String =
        "$baseUrl/Videos/$itemId/master.m3u8" +
            "?api_key=$accessToken" +
            "&VideoBitRate=$bitrate" +
            "&VideoCodec=$videoCodec" +
            "&AudioCodec=$audioCodec" +
            "&TranscodingMaxAudioChannels=6"
}
