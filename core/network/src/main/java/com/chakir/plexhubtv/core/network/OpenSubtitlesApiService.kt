package com.chakir.plexhubtv.core.network

import com.chakir.plexhubtv.core.network.model.OpenSubtitlesDownloadRequest
import com.chakir.plexhubtv.core.network.model.OpenSubtitlesDownloadResponse
import com.chakir.plexhubtv.core.network.model.OpenSubtitlesSearchResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface OpenSubtitlesApiService {
    @GET("subtitles")
    suspend fun search(
        @Header("Api-Key") apiKey: String,
        @Query("query") query: String,
        @Query("languages") languages: String? = null,
        @Query("season_number") seasonNumber: Int? = null,
        @Query("episode_number") episodeNumber: Int? = null,
        @Query("type") type: String? = null,
        @Query("order_by") orderBy: String = "download_count",
        @Query("order_direction") orderDirection: String = "desc",
    ): OpenSubtitlesSearchResponse

    @POST("download")
    suspend fun download(
        @Header("Api-Key") apiKey: String,
        @Body request: OpenSubtitlesDownloadRequest,
    ): OpenSubtitlesDownloadResponse
}
