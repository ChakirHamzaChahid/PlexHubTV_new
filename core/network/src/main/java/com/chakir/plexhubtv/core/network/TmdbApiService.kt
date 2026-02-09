package com.chakir.plexhubtv.core.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TMDb (The Movie Database) API service for fetching series ratings.
 * Used as primary source for TV show ratings.
 * 
 * API Documentation: https://developers.themoviedb.org/3
 * Free tier: 10,000 requests/day
 */
interface TmdbApiService {
    
    /**
     * Get TV show details including rating by TMDb ID.
     * 
     * Example: getTvDetails("1396") // Breaking Bad
     * Returns: { "vote_average": 8.9, "name": "Breaking Bad", ... }
     */
    @GET("/3/tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id") tmdbId: String,
        @Query("api_key") apiKey: String,
    ): TmdbTvResponse
}

/**
 * TMDb TV response.
 */
data class TmdbTvResponse(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("vote_average")
    val voteAverage: Double?, // 8.9 (out of 10)
    
    @SerializedName("vote_count")
    val voteCount: Int?,
    
    @SerializedName("success")
    val success: Boolean? = null, // false if error
    
    @SerializedName("status_message")
    val statusMessage: String? = null, // Error message
)
