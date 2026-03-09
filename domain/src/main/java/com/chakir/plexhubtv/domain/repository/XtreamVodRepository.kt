package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.XtreamCategory
import kotlinx.coroutines.flow.Flow

interface XtreamVodRepository {
    suspend fun getCategories(accountId: String): Result<List<XtreamCategory>>

    suspend fun syncMovies(accountId: String, categoryId: Int? = null): Result<Int>

    fun getMovies(accountId: String, categoryId: Int? = null): Flow<List<MediaItem>>

    suspend fun buildStreamUrl(accountId: String, streamId: Int, extension: String): String

    /** Fetch detailed VOD info (plot, cast, genre, tmdb_id) and persist to Room. */
    suspend fun enrichMovieDetail(accountId: String, vodId: Int, ratingKey: String): Result<Unit>
}
