package com.chakir.plexhubtv.core.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OMDb API service for fetching IMDb ratings.
 * Used for movies and as fallback for series without TMDb ID.
 * 
 * API Documentation: http://www.omdbapi.com/
 * Free tier: 1000 requests/day
 */
interface OmdbApiService {
    
    /**
     * Get movie/series rating by IMDb ID.
     * 
     * Example: getRating("tt0416449")
     * Returns: { "imdbRating": "7.6", "Type": "movie", ... }
     */
    @GET("/")
    suspend fun getRating(
        @Query("i") imdbId: String,
        @Query("apikey") apiKey: String,
    ): OmdbResponse
}

/**
 * OMDb API response.
 */
data class OmdbResponse(
    @SerializedName("imdbID")
    val imdbId: String,
    
    @SerializedName("imdbRating")
    val imdbRating: String?, // "7.6" or "N/A"
    
    @SerializedName("Type")
    val type: String, // "movie" or "series"
    
    @SerializedName("Response")
    val response: String, // "True" or "False"
    
    @SerializedName("Error")
    val error: String? = null, // Error message if Response = "False"
)
