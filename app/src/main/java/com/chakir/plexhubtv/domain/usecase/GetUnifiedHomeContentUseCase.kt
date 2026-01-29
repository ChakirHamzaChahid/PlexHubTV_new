package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.model.Hub
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class HomeContent(
    val onDeck: List<MediaItem>,
    val hubs: List<Hub>
)

class GetUnifiedHomeContentUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(): Flow<Result<HomeContent>> = 
        combine(
            mediaRepository.getUnifiedOnDeck(),
            mediaRepository.getUnifiedHubs()
        ) { onDeck, hubs ->
            Result.success(HomeContent(onDeck = onDeck, hubs = hubs))
        }
}
