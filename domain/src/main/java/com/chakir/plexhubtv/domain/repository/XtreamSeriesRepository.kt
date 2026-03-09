package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.XtreamCategory
import com.chakir.plexhubtv.domain.usecase.MediaDetail
import kotlinx.coroutines.flow.Flow

interface XtreamSeriesRepository {
    suspend fun getCategories(accountId: String): Result<List<XtreamCategory>>

    suspend fun syncSeries(accountId: String, categoryId: Int? = null): Result<Int>

    fun getSeries(accountId: String, categoryId: Int? = null): Flow<List<MediaItem>>

    suspend fun getSeriesDetail(accountId: String, seriesId: Int): Result<MediaDetail>

    suspend fun buildEpisodeUrl(accountId: String, episodeId: String, extension: String): String
}
