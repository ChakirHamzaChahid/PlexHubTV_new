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
}
