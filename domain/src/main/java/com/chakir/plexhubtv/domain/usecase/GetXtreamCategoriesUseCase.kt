package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.XtreamCategory
import com.chakir.plexhubtv.domain.repository.XtreamSeriesRepository
import com.chakir.plexhubtv.domain.repository.XtreamVodRepository
import javax.inject.Inject

class GetXtreamCategoriesUseCase @Inject constructor(
    private val vodRepo: XtreamVodRepository,
    private val seriesRepo: XtreamSeriesRepository,
) {
    suspend fun getVodCategories(accountId: String): Result<List<XtreamCategory>> =
        vodRepo.getCategories(accountId)

    suspend fun getSeriesCategories(accountId: String): Result<List<XtreamCategory>> =
        seriesRepo.getCategories(accountId)
}
