package com.chakir.plexhubtv.core.network

import com.chakir.plexhubtv.core.network.model.GenericPlexResponse
import com.chakir.plexhubtv.core.network.model.PinResponse
import com.chakir.plexhubtv.core.network.model.PlexHomeUserDto
import com.chakir.plexhubtv.core.network.model.PlexResource
import com.chakir.plexhubtv.core.network.model.PlexResponse
import com.chakir.plexhubtv.core.network.model.UserSwitchResponseDto
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Interface Retrofit définissant l'API brute de Plex.
 *
 * Couvre deux types d'endpoints :
 * 1. **Plex.tv (Cloud)** : Authentification, découverte de ressources, synchronisation Watchlist.
 *    URL de base : `https://plex.tv/`
 * 2. **Plex Media Server (Local/Remote)** : Métadonnées, streaming, recherche.
 *    URL dynamique (injectée via `@Url` dans chaque appel).
 */
interface PlexApiService {
    // --- Authentication (plex.tv) ---

    @POST("https://plex.tv/api/v2/pins")
    suspend fun getPin(
        @Query("strong") strong: Boolean = true,
        @Header("X-Plex-Client-Identifier") clientId: String,
    ): Response<PinResponse>

    @GET("https://plex.tv/api/v2/pins/{id}")
    suspend fun getPinStatus(
        @Path("id") id: String,
        @Header("X-Plex-Client-Identifier") clientId: String,
    ): Response<PinResponse>

    @GET("https://plex.tv/api/v2/user")
    suspend fun getUser(
        @Header("X-Plex-Token") token: String,
        @Header("X-Plex-Client-Identifier") clientId: String,
    ): Response<GenericPlexResponse> // Replace with UserResponse

    @GET("https://plex.tv/api/v2/resources")
    suspend fun getResources(
        @Query("includeHttps") includeHttps: Int = 1,
        @Query("includeRelay") includeRelay: Int = 1,
        @Query("includeIPv6") includeIPv6: Int = 1,
        @Header("X-Plex-Token") token: String,
        @Header("X-Plex-Client-Identifier") clientId: String,
    ): Response<List<PlexResource>>

    @GET("https://discover.provider.plex.tv/library/sections/watchlist/all")
    suspend fun getWatchlist(
        @Header("X-Plex-Token") token: String,
        @Header("X-Plex-Client-Identifier") clientId: String,
        @Query("sort") sort: String = "addedAt:desc",
        @Query("includeExternalMedia") includeExternalMedia: Int = 1,
        @Query("includeCollections") includeCollections: Int = 1,
        @Query("X-Plex-Container-Start") start: Int = 0,
        @Query("X-Plex-Container-Size") size: Int = 100,
    ): Response<GenericPlexResponse>

    @PUT("https://metadata.provider.plex.tv/actions/addToWatchlist")
    suspend fun addToWatchlist(
        @Query("ratingKey") ratingKey: String,
        @Header("X-Plex-Token") token: String,
        @Header("X-Plex-Client-Identifier") clientId: String,
    ): Response<Unit>

    @DELETE("https://metadata.provider.plex.tv/actions/removeFromWatchlist")
    suspend fun removeFromWatchlist(
        @Query("ratingKey") ratingKey: String,
        @Header("X-Plex-Token") token: String,
        @Header("X-Plex-Client-Identifier") clientId: String,
    ): Response<Unit>

    // --- Media / Library (Dynamic Server URL) ---

    @GET
    suspend fun getHubs(
        @Url url: String,
        @Query("count") count: Int = 50,
    ): Response<GenericPlexResponse> // Replace with HubResponse

    @GET
    suspend fun getMetadata(
        @Url url: String,
        // Add specific query params if needed
    ): Response<PlexResponse> // Replace with MetadataResponse

    @GET
    suspend fun search(
        @Url url: String,
        @Query("query") query: String,
        @Query("year") year: Int? = null,
        @Query("type") type: String? = null, // movie, show, episode, person
        @Query("unwatched") unwatched: Int? = null, // 1 = true, 0 = false
    ): Response<GenericPlexResponse> // SearchResponse

    @GET
    suspend fun getSections(
        @Url url: String,
    ): Response<GenericPlexResponse>

    @GET
    suspend fun getLibraryContents(
        @Url url: String,
        @Query("X-Plex-Container-Start") start: Int,
        @Query("X-Plex-Container-Size") size: Int,
        @Query("type") type: String? = null,
        @Query("sort") sort: String? = null,
    ): Response<PlexResponse>

    @GET
    suspend fun getCollections(
        @Url url: String,
        @Query("type") type: Int = 18, // 18 = Collections
        @Query("X-Plex-Container-Start") start: Int = 0,
        @Query("X-Plex-Container-Size") size: Int = 1000,
    ): Response<PlexResponse>

    @GET
    suspend fun getCollectionItems(
        @Url url: String,
        @Query("includeGuids") includeGuids: Int = 1,
    ): Response<PlexResponse>

    // --- Playback Tracking ---

    @GET
    suspend fun updateTimeline(
        @Url url: String,
        @Query("ratingKey") ratingKey: String,
        @Query("state") state: String,
        @Query("time") time: Long,
        @Query("duration") duration: Long,
        @Query("X-Plex-Token") token: String,
    ): Response<Unit>

    @GET
    suspend fun scrobble(
        @Url url: String,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library",
        @Query("key") ratingKey: String,
        @Query("X-Plex-Token") token: String,
    ): Response<Unit>

    @GET
    suspend fun unscrobble(
        @Url url: String,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library",
        @Query("key") ratingKey: String,
        @Query("X-Plex-Token") token: String,
    ): Response<Unit>

    // --- Stream Selection ---

    @PUT
    suspend fun putStreamSelection(
        @Url url: String,
        @Query("audioStreamID") audioStreamID: String? = null,
        @Query("subtitleStreamID") subtitleStreamID: String? = null,
        @Query("allParts") allParts: Int = 1,
        @Header("X-Plex-Token") token: String,
    ): Response<Unit>

    // --- Plex Home / Users ---

    @GET("https://plex.tv/api/v2/home/users")
    suspend fun getHomeUsers(
        @Header("X-Plex-Token") token: String,
        @Header("X-Plex-Client-Identifier") clientId: String,
    ): Response<List<PlexHomeUserDto>>

    @POST("https://plex.tv/api/v2/home/users/{uuid}/switch")
    suspend fun switchUser(
        @Path("uuid") uuid: String,
        @Query("pin") pin: String?,
        @Header("X-Plex-Token") token: String,
        @Header("X-Plex-Client-Identifier") clientId: String,
    ): Response<UserSwitchResponseDto>
}
