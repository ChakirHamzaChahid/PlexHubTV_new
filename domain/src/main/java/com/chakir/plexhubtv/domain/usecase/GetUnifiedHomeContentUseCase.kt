package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.di.IoDispatcher
import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.repository.MediaRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject

data class HomeContent(
    val onDeck: List<MediaItem>,
    val hubs: List<Hub>,
)

/**
 * Agrégateur principal pour l'écran d'accueil (Home).
 *
 * Combine deux sources de données parallèles :
 * 1. "On Deck" Unifié (Reprise de lecture / Prochains épisodes).
 * 2. "Hubs" Unifiés (Récemment ajoutés, Trending).
 */

class GetUnifiedHomeContentUseCase
    @Inject
    constructor(
        private val mediaRepository: MediaRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        operator fun invoke(): Flow<Result<HomeContent>> {
            Timber.i("HOME [UseCase] >>> invoke() called - creating combined flow")
            val onDeckFlow = mediaRepository.getUnifiedOnDeck().catch { e ->
                Timber.e(e, "Error loading OnDeck")
                emit(emptyList())
            }
            val hubsFlow = mediaRepository.getUnifiedHubs().catch { e ->
                Timber.e(e, "Error loading Hubs")
                emit(emptyList())
            }
            Timber.i("HOME [UseCase] >>> Both flows created, combining...")
            return combine(onDeckFlow, hubsFlow) { onDeck, hubs ->
                Timber.i("HOME [UseCase] combine emission: OnDeck=${onDeck.size} Hubs=${hubs.size}")
                Result.success(HomeContent(onDeck = onDeck, hubs = hubs))
            }
                .flowOn(ioDispatcher)
                .catch { e -> emit(Result.failure(e)) }
        }
    }
