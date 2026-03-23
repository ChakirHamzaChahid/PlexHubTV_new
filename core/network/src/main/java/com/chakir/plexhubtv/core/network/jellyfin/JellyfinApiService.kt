package com.chakir.plexhubtv.core.network.jellyfin

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit interface for the Jellyfin REST API.
 *
 * All methods use [Url] for the full URL because different Jellyfin servers
 * have different base URLs (unlike a single cloud API).
 * Auth is passed via [Header] on each call, keeping the OkHttpClient clean
 * and avoiding cross-server token leakage.
 */
interface JellyfinApiService {

    // ========================================
    // Public (unauthenticated)
    // ========================================

    /** Ping server — used for connection testing (no auth required). */
    @GET
    suspend fun getPublicInfo(
        @Url url: String, // e.g. "http://192.168.1.50:8096/System/Info/Public"
    ): Response<JellyfinPublicInfo>

    // ========================================
    // Authentication
    // ========================================

    /** Authenticate by username + password. Returns access token. */
    @POST
    suspend fun authenticateByName(
        @Url url: String, // $baseUrl/Users/AuthenticateByName
        @Header("Authorization") authHeader: String,
        @Body body: JellyfinAuthRequest,
    ): Response<JellyfinAuthResponse>

    // ========================================
    // User Libraries (Views)
    // ========================================

    /** Get user's library views (Movies, Shows, Music, etc.). */
    @GET
    suspend fun getUserViews(
        @Url url: String, // $baseUrl/Users/$userId/Views
        @Header("Authorization") authHeader: String,
    ): Response<JellyfinViewsResponse>

    // ========================================
    // Items (Library Browsing)
    // ========================================

    /** Get items from a library with pagination. */
    @GET
    suspend fun getItems(
        @Url url: String, // $baseUrl/Users/$userId/Items
        @Header("Authorization") authHeader: String,
        @Query("ParentId") parentId: String? = null,
        @Query("IncludeItemTypes") includeItemTypes: String? = null,
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = ITEM_FIELDS,
        @Query("StartIndex") startIndex: Int = 0,
        @Query("Limit") limit: Int = 200,
        @Query("SortBy") sortBy: String = "SortName",
        @Query("SortOrder") sortOrder: String = "Ascending",
        @Query("ImageTypeLimit") imageTypeLimit: Int = 1,
        @Query("EnableImageTypes") enableImageTypes: String = "Primary,Backdrop",
    ): Response<JellyfinItemsResponse>

    /** Get a single item's full details. */
    @GET
    suspend fun getItem(
        @Url url: String, // $baseUrl/Users/$userId/Items/$itemId
        @Header("Authorization") authHeader: String,
        @Query("Fields") fields: String = ITEM_FIELDS_DETAIL,
    ): Response<JellyfinItem>

    /** Get seasons for a series. */
    @GET
    suspend fun getSeasons(
        @Url url: String, // $baseUrl/Shows/$seriesId/Seasons
        @Header("Authorization") authHeader: String,
        @Query("UserId") userId: String,
        @Query("Fields") fields: String = ITEM_FIELDS,
    ): Response<JellyfinItemsResponse>

    /** Get episodes for a season. */
    @GET
    suspend fun getEpisodes(
        @Url url: String, // $baseUrl/Shows/$seriesId/Episodes
        @Header("Authorization") authHeader: String,
        @Query("UserId") userId: String,
        @Query("SeasonId") seasonId: String,
        @Query("Fields") fields: String = ITEM_FIELDS_DETAIL,
    ): Response<JellyfinItemsResponse>

    /** Get similar items. */
    @GET
    suspend fun getSimilar(
        @Url url: String, // $baseUrl/Items/$itemId/Similar
        @Header("Authorization") authHeader: String,
        @Query("UserId") userId: String,
        @Query("Limit") limit: Int = 12,
        @Query("Fields") fields: String = ITEM_FIELDS,
    ): Response<JellyfinSimilarResponse>

    // ========================================
    // Search
    // ========================================

    @GET
    suspend fun search(
        @Url url: String, // $baseUrl/Search/Hints
        @Header("Authorization") authHeader: String,
        @Query("searchTerm") searchTerm: String,
        @Query("UserId") userId: String,
        @Query("Limit") limit: Int = 30,
        @Query("IncludeItemTypes") includeItemTypes: String = "Movie,Series,Episode",
    ): Response<JellyfinSearchHintResponse>

    // ========================================
    // Playback Reporting
    // ========================================

    @POST
    suspend fun reportPlaybackStart(
        @Url url: String, // $baseUrl/Sessions/Playing
        @Header("Authorization") authHeader: String,
        @Body body: JellyfinPlaybackStartInfo,
    ): Response<Unit>

    @POST
    suspend fun reportPlaybackProgress(
        @Url url: String, // $baseUrl/Sessions/Playing/Progress
        @Header("Authorization") authHeader: String,
        @Body body: JellyfinPlaybackProgressInfo,
    ): Response<Unit>

    @POST
    suspend fun reportPlaybackStopped(
        @Url url: String, // $baseUrl/Sessions/Playing/Stopped
        @Header("Authorization") authHeader: String,
        @Body body: JellyfinPlaybackStopInfo,
    ): Response<Unit>

    // ========================================
    // Watch Status
    // ========================================

    @POST
    suspend fun markPlayed(
        @Url url: String, // $baseUrl/Users/$userId/PlayedItems/$itemId
        @Header("Authorization") authHeader: String,
    ): Response<JellyfinUserData>

    @DELETE
    suspend fun markUnplayed(
        @Url url: String, // $baseUrl/Users/$userId/PlayedItems/$itemId
        @Header("Authorization") authHeader: String,
    ): Response<JellyfinUserData>

    // ========================================
    // Favorites
    // ========================================

    @POST
    suspend fun addFavorite(
        @Url url: String, // $baseUrl/Users/$userId/FavoriteItems/$itemId
        @Header("Authorization") authHeader: String,
    ): Response<JellyfinUserData>

    @DELETE
    suspend fun removeFavorite(
        @Url url: String, // $baseUrl/Users/$userId/FavoriteItems/$itemId
        @Header("Authorization") authHeader: String,
    ): Response<JellyfinUserData>

    companion object {
        /** Fields for list/browse views (lightweight). */
        const val ITEM_FIELDS = "ProviderIds,Overview,Genres,CommunityRating," +
            "OfficialRating,DateCreated,People"

        /** Fields for bulk sync — excludes People to reduce payload on large libraries. */
        const val ITEM_FIELDS_SYNC = "ProviderIds,Overview,Genres,CommunityRating," +
            "OfficialRating,DateCreated"

        /** Fields for detail views (heavier, includes streams). */
        const val ITEM_FIELDS_DETAIL = "ProviderIds,Overview,Genres,CommunityRating," +
            "OfficialRating,DateCreated,People,MediaSources,Chapters,Taglines"
    }
}
