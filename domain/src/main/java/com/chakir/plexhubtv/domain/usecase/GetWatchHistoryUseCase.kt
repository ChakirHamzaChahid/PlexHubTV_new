package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Récupère l'historique de visionnage local.
 * Utile pour la section "Récemment regardés" ou pour reprendre une lecture.
 */
class GetWatchHistoryUseCase
    @Inject
    constructor(
        private val mediaRepository: MediaRepository,
    ) {
        operator fun invoke(
            limit: Int = 50,
            offset: Int = 0,
        ): Flow<List<MediaItem>> {
            return mediaRepository.getWatchHistory(limit, offset)
        }
    }
