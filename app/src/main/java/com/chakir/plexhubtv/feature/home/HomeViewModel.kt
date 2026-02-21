package com.chakir.plexhubtv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.chakir.plexhubtv.core.common.safeCollectIn
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.toAppError
import com.chakir.plexhubtv.domain.repository.FavoritesRepository
import com.chakir.plexhubtv.domain.usecase.GetUnifiedHomeContentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.chakir.plexhubtv.di.image.*
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel pour l'écran d'accueil.
 * Récupère le contenu unifié (On Deck + Hubs) via [GetUnifiedHomeContentUseCase].
 * Gère également le suivi de la synchronisation initiale via WorkManager.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val getUnifiedHomeContentUseCase: GetUnifiedHomeContentUseCase,
        private val favoritesRepository: FavoritesRepository,
        private val workManager: androidx.work.WorkManager,
        private val settingsDataStore: com.chakir.plexhubtv.core.datastore.SettingsDataStore,
        private val imagePrefetchManager: ImagePrefetchManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        private val _navigationEvents = Channel<HomeNavigationEvent>()
        val navigationEvents = _navigationEvents.receiveAsFlow()

        private val _errorEvents = Channel<AppError>()
        val errorEvents = _errorEvents.receiveAsFlow()

        init {
            // PERFORMANCE: Load content immediately to unblock UI (Cold Start Optimization)
            observeFavorites()
            loadContent()
            checkInitialSync()
        }

        private fun observeFavorites() {
            favoritesRepository.getFavorites().safeCollectIn(
                scope = viewModelScope,
                onError = { e ->
                    Timber.e(e, "HomeViewModel: getFavorites failed")
                }
            ) { favorites ->
                _uiState.update { it.copy(favorites = favorites) }
            }
        }

        private fun checkInitialSync() {
            viewModelScope.launch {
                // Monitor sync progress in background without blocking UI unless absolutely necessary (handled in UI layer)
                val isFirstSyncComplete = settingsDataStore.isFirstSyncComplete.firstOrNull() ?: false
                if (!isFirstSyncComplete) {
                    _uiState.update { it.copy(isInitialSync = true) }

                    workManager.getWorkInfosForUniqueWorkFlow("LibrarySync_Initial")
                        .safeCollectIn(
                            scope = viewModelScope,
                            onError = { e ->
                                Timber.e(e, "HomeViewModel: checkInitialSync failed")
                                _uiState.update { it.copy(isInitialSync = false) }
                            }
                        ) { workInfos ->
                            val initialSyncWork = workInfos.firstOrNull()
                            if (initialSyncWork != null) {
                                val progress = initialSyncWork.progress.getFloat("progress", 0f)
                                val message = initialSyncWork.progress.getString("message") ?: "Initializing..."
                                val state = initialSyncWork.state

                                _uiState.update {
                                    it.copy(
                                        syncProgress = progress,
                                        syncMessage = message,
                                    )
                                }

                                if (state == WorkInfo.State.SUCCEEDED || state == WorkInfo.State.FAILED || state == WorkInfo.State.CANCELLED) {
                                    _uiState.update { it.copy(isInitialSync = false) }
                                }
                            }
                        }
                }
            }
        }

        fun onAction(action: HomeAction) {
            when (action) {
                is HomeAction.Refresh -> loadContent()
                is HomeAction.OpenMedia -> {
                    viewModelScope.launch {
                        _navigationEvents.send(HomeNavigationEvent.NavigateToDetails(action.media.ratingKey, action.media.serverId))
                    }
                }
                is HomeAction.PlayMedia -> {
                    viewModelScope.launch {
                        _navigationEvents.send(HomeNavigationEvent.NavigateToPlayer(action.media.ratingKey, action.media.serverId))
                    }
                }
            }
        }

        private var contentJob: Job? = null

        private fun loadContent() {
            contentJob?.cancel()
            contentJob = viewModelScope.launch {
                val startTime = System.currentTimeMillis()
                Timber.d("SCREEN [Home]: Loading start")

                // Avoid showing full-screen loading if we already have some data (e.g., from previous session or quick refresh)
                _uiState.update { it.copy(isLoading = it.onDeck.isEmpty() && it.hubs.isEmpty()) }

                getUnifiedHomeContentUseCase()
                    .catch { error ->
                        val duration = System.currentTimeMillis() - startTime
                        Timber.e(error, "HomeViewModel: getUnifiedHomeContentUseCase failed")
                        viewModelScope.launch { _errorEvents.send(error.toAppError()) }
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    .collect { result ->
                        val duration = System.currentTimeMillis() - startTime
                        result.fold(
                            onSuccess = { content ->
                                Timber.i(
                                    "SCREEN [Home] PROGRESS: Duration=${duration}ms | OnDeck=${content.onDeck.size} | Hubs=${content.hubs.size}",
                                )

                                // FIX: Filter empty hubs and deduplicate by title to reduce UI clutter
                                val filteredHubs =
                                    content.hubs
                                        .filter { it.items.isNotEmpty() }
                                        .distinctBy { it.title }

                                // PERFORMANCE: Prefetch images for On Deck and the first few hubs
                                // REDUCED: Prefetch only immediate viewport items to prevent network saturation/UI jank
                                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val urlsToPrefetch =
                                        content.onDeck.take(5).flatMap { listOfNotNull(it.thumbUrl, it.artUrl) } +
                                            filteredHubs.take(1).flatMap { hub ->
                                                hub.items.take(5).flatMap { listOfNotNull(it.thumbUrl, it.artUrl) }
                                            }
                                    if (urlsToPrefetch.isNotEmpty()) {
                                        Timber.d("Prefetching ${urlsToPrefetch.size} images for Home")
                                        imagePrefetchManager.prefetchImages(urlsToPrefetch)
                                    }
                                }

                                val newState =
                                    _uiState.value.copy(
                                        isLoading = false,
                                        onDeck = content.onDeck,
                                        hubs = filteredHubs,
                                    )

                                _uiState.update { newState }
                            },
                            onFailure = { error ->
                                Timber.e("SCREEN [Home] FAILED: duration=${duration}ms error=${error.message}")
                                viewModelScope.launch { _errorEvents.send(error.toAppError()) }
                                _uiState.update { it.copy(isLoading = false) }
                            },
                        )
                    }
            }
        }
    }

sealed interface HomeNavigationEvent {
    data class NavigateToDetails(val ratingKey: String, val serverId: String) : HomeNavigationEvent

    data class NavigateToPlayer(val ratingKey: String, val serverId: String) : HomeNavigationEvent
}
