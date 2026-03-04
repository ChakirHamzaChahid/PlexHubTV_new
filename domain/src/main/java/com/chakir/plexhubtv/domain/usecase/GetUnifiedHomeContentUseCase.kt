package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.di.ApplicationScope
import com.chakir.plexhubtv.core.di.IoDispatcher
import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.repository.HubsRepository
import com.chakir.plexhubtv.domain.repository.OnDeckRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

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
 *
 * Exposes a single shared [StateFlow] so that both HomeViewModel and HubViewModel
 * consume the same upstream without duplicating network fan-outs.
 */
@Singleton
class GetUnifiedHomeContentUseCase
    @Inject
    constructor(
        private val onDeckRepository: OnDeckRepository,
        private val hubsRepository: HubsRepository,
        private val sortOnDeck: SortOnDeckUseCase,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        @ApplicationScope private val appScope: CoroutineScope,
    ) {
        private val refreshTrigger = MutableSharedFlow<Unit>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

        init {
            refreshTrigger.tryEmit(Unit)
        }

        /**
         * Shared home content consumed by both HomeViewModel and HubViewModel.
         * Uses [SharingStarted.WhileSubscribed] so the upstream stops 5s after
         * all collectors leave (e.g. app backgrounded) and restarts fresh on return.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        val sharedContent: StateFlow<Result<HomeContent>?> = refreshTrigger
            .flatMapLatest { createContentFlow() }
            .stateIn(
                scope = appScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

        /**
         * Force a refresh of home content. Both HomeViewModel and HubViewModel
         * will receive the updated data through [sharedContent].
         */
        fun refresh() {
            refreshTrigger.tryEmit(Unit)
        }

        private fun createContentFlow(): Flow<Result<HomeContent>> {
            Timber.i("HOME [UseCase] >>> Creating combined flow for home content")
            val onDeckFlow = onDeckRepository.getUnifiedOnDeck().catch { e ->
                Timber.e(e, "Error loading OnDeck")
                emit(emptyList())
            }
            val hubsFlow = hubsRepository.getUnifiedHubs().catch { e ->
                Timber.e(e, "Error loading Hubs")
                emit(emptyList())
            }
            return combine(onDeckFlow, hubsFlow) { onDeck, hubs ->
                val sorted = sortOnDeck(onDeck)
                Timber.i("HOME [UseCase] combine emission: OnDeck=${sorted.size} Hubs=${hubs.size}")
                Result.success(HomeContent(onDeck = sorted, hubs = hubs))
            }
                .flowOn(ioDispatcher)
                .catch { e -> emit(Result.failure(e)) }
        }
    }
