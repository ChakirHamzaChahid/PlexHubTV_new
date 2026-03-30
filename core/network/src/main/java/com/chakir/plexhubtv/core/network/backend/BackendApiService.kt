package com.chakir.plexhubtv.core.network.backend

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface BackendApiService {
    @GET("api/health")
    suspend fun getHealth(): BackendHealthResponse

    @GET("api/accounts")
    suspend fun getAccounts(): List<BackendAccountResponse>

    @GET("api/media/movies")
    suspend fun getMovies(
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String = "added_desc",
    ): BackendMediaListResponse

    @GET("api/media/shows")
    suspend fun getShows(
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int = 0,
    ): BackendMediaListResponse

    @GET("api/media/episodes")
    suspend fun getEpisodes(
        @Query("parent_rating_key") parentRatingKey: String,
        @Query("server_id") serverId: String? = null,
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int = 0,
    ): BackendMediaListResponse

    @GET("api/media/{ratingKey}")
    suspend fun getMediaDetail(
        @Path("ratingKey") ratingKey: String,
        @Query("server_id") serverId: String,
    ): BackendMediaItemDto

    @GET("api/stream/{ratingKey}")
    suspend fun getStreamUrl(
        @Path("ratingKey") ratingKey: String,
        @Query("server_id") serverId: String,
    ): BackendStreamResponse

    @POST("api/sync/xtream")
    suspend fun triggerSync(
        @Body request: BackendSyncRequest,
    ): BackendSyncJobResponse

    @GET("api/sync/status/{jobId}")
    suspend fun getSyncStatus(
        @Path("jobId") jobId: String,
    ): BackendSyncStatusResponse

    // Account CRUD
    @POST("api/accounts")
    suspend fun createAccount(@Body account: BackendAccountCreate): BackendAccountResponse

    @PUT("api/accounts/{accountId}")
    suspend fun updateAccount(
        @Path("accountId") accountId: String,
        @Body update: BackendAccountUpdate,
    ): BackendAccountResponse

    @DELETE("api/accounts/{accountId}")
    suspend fun deleteAccount(@Path("accountId") accountId: String)

    @POST("api/accounts/{accountId}/test")
    suspend fun testAccount(
        @Path("accountId") accountId: String,
    ): BackendAccountTestResponse

    // Categories
    @GET("api/accounts/{accountId}/categories")
    suspend fun getCategories(
        @Path("accountId") accountId: String,
    ): BackendCategoryListResponse

    @PUT("api/accounts/{accountId}/categories")
    suspend fun updateCategories(
        @Path("accountId") accountId: String,
        @Body request: BackendCategoryUpdateRequest,
    )

    @POST("api/accounts/{accountId}/categories/refresh")
    suspend fun refreshCategories(
        @Path("accountId") accountId: String,
    ): BackendCategoryRefreshResponse

    // Sync all
    @POST("api/sync/xtream/all")
    suspend fun triggerSyncAll(): BackendSyncJobResponse

    // Live TV
    @GET("api/live/channels")
    suspend fun getLiveChannels(
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String = "name_asc",
        @Query("server_id") serverId: String? = null,
        @Query("category_id") categoryId: String? = null,
        @Query("search") search: String? = null,
    ): BackendLiveChannelListResponse

    @GET("api/live/channels/{stream_id}")
    suspend fun getLiveChannel(
        @Path("stream_id") streamId: Int,
        @Query("server_id") serverId: String,
    ): BackendLiveChannelDto

    @GET("api/live/channels/{stream_id}/stream")
    suspend fun getLiveStream(
        @Path("stream_id") streamId: Int,
        @Query("server_id") serverId: String,
    ): BackendStreamResponse

    @GET("api/live/channels/{stream_id}/epg")
    suspend fun getChannelEpg(
        @Path("stream_id") streamId: Int,
        @Query("server_id") serverId: String,
    ): BackendEpgListResponse

    @GET("api/live/epg")
    suspend fun getCurrentEpg(
        @Query("server_id") serverId: String,
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int = 0,
    ): BackendEpgListResponse
}
