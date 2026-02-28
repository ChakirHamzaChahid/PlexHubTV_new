package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.UnifiedSeason
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import javax.inject.Inject

class GetUnifiedSeasonsUseCase @Inject constructor(
    private val mediaDetailRepository: MediaDetailRepository,
    private val getEnabledServerIdsUseCase: GetEnabledServerIdsUseCase,
) {
    suspend operator fun invoke(
        showTitle: String,
        fallbackServerId: String,
        fallbackRatingKey: String,
    ): Result<List<UnifiedSeason>> = runCatching {
        val enabledServerIds = getEnabledServerIdsUseCase()
        if (enabledServerIds.isEmpty()) return@runCatching emptyList()
        mediaDetailRepository.getUnifiedSeasons(showTitle, enabledServerIds)
    }
}
