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
        @Query("language") language: String? = null,
    ): TmdbTvResponse

    /**
     * Get movie details including rating by TMDb ID.
     *
     * Example: getMovieDetails("550") // Fight Club
     * Returns: { "vote_average": 8.4, "title": "Fight Club", ... }
     */
    @GET("/3/movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") tmdbId: String,
        @Query("api_key") apiKey: String,
        @Query("language") language: String? = null,
    ): TmdbMovieResponse

    /**
     * Search for a person by name.
     *
     * Example: searchPerson("Bryan Cranston")
     */
    @GET("/3/search/person")
    suspend fun searchPerson(
        @Query("api_key") apiKey: String,
        @Query("query") name: String,
        @Query("language") language: String? = null,
    ): TmdbPersonSearchResponse

    /**
     * Get person details including combined credits (movies + TV).
     *
     * Example: getPersonDetails("17419") // Bryan Cranston
     */
    @GET("/3/person/{person_id}")
    suspend fun getPersonDetails(
        @Path("person_id") personId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "combined_credits",
        @Query("language") language: String? = null,
    ): TmdbPersonDetailResponse
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

    @SerializedName("overview")
    val overview: String? = null,

    @SerializedName("poster_path")
    val posterPath: String? = null, // e.g. "/abc123.jpg" → prepend TMDB image base URL

    @SerializedName("success")
    val success: Boolean? = null, // false if error

    @SerializedName("status_message")
    val statusMessage: String? = null, // Error message
)

/**
 * TMDb Movie response.
 */
data class TmdbMovieResponse(
    @SerializedName("id")
    val id: Int,

    @SerializedName("title")
    val title: String,

    @SerializedName("vote_average")
    val voteAverage: Double?, // 8.4 (out of 10)

    @SerializedName("vote_count")
    val voteCount: Int?,

    @SerializedName("overview")
    val overview: String? = null,

    @SerializedName("poster_path")
    val posterPath: String? = null,

    @SerializedName("success")
    val success: Boolean? = null, // false if error

    @SerializedName("status_message")
    val statusMessage: String? = null, // Error message
)

/**
 * TMDb person search response.
 */
data class TmdbPersonSearchResponse(
    @SerializedName("results")
    val results: List<TmdbPersonSearchResult>,
)

data class TmdbPersonSearchResult(
    @SerializedName("id")
    val id: Int,

    @SerializedName("name")
    val name: String,

    @SerializedName("profile_path")
    val profilePath: String?,

    @SerializedName("known_for_department")
    val knownForDepartment: String?,
)

/**
 * TMDb person detail response with combined credits.
 */
data class TmdbPersonDetailResponse(
    @SerializedName("id")
    val id: Int,

    @SerializedName("name")
    val name: String,

    @SerializedName("biography")
    val biography: String?,

    @SerializedName("birthday")
    val birthday: String?,

    @SerializedName("deathday")
    val deathday: String?,

    @SerializedName("place_of_birth")
    val placeOfBirth: String?,

    @SerializedName("profile_path")
    val profilePath: String?,

    @SerializedName("known_for_department")
    val knownForDepartment: String?,

    @SerializedName("combined_credits")
    val combinedCredits: TmdbCombinedCredits?,
)

data class TmdbCombinedCredits(
    @SerializedName("cast")
    val cast: List<TmdbPersonCredit>?,

    @SerializedName("crew")
    val crew: List<TmdbPersonCredit>?,
)

data class TmdbPersonCredit(
    @SerializedName("id")
    val id: Int,

    @SerializedName("title")
    val title: String?,

    @SerializedName("name")
    val name: String?,

    @SerializedName("media_type")
    val mediaType: String?, // "movie" or "tv"

    @SerializedName("character")
    val character: String?,

    @SerializedName("job")
    val job: String?,

    @SerializedName("poster_path")
    val posterPath: String?,

    @SerializedName("vote_average")
    val voteAverage: Double?,

    @SerializedName("release_date")
    val releaseDate: String?,

    @SerializedName("first_air_date")
    val firstAirDate: String?,
)
