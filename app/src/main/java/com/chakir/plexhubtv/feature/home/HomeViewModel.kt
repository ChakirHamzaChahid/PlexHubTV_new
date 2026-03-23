package com.chakir.plexhubtv.feature.home

import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.common.safeCollectIn
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.toAppError
import com.chakir.plexhubtv.domain.repository.FavoritesRepository
import com.chakir.plexhubtv.domain.repository.ProfileRepository
import com.chakir.plexhubtv.domain.repository.WatchlistRepository
import com.chakir.plexhubtv.domain.usecase.FilterContentByAgeUseCase
import com.chakir.plexhubtv.domain.usecase.GetSuggestionsUseCase
import com.chakir.plexhubtv.domain.usecase.GetUnifiedHomeContentUseCase
import com.chakir.plexhubtv.feature.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.toImmutableList
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel pour l'écran d'accueil (Hero Billboard uniquement).
 * Charge le contenu On Deck pour alimenter le carrousel Hero.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val getUnifiedHomeContentUseCase: GetUnifiedHomeContentUseCase,
        private val profileRepository: ProfileRepository,
        private val filterContentByAgeUseCase: FilterContentByAgeUseCase,
        private val favoritesRepository: FavoritesRepository,
        private val watchlistRepository: WatchlistRepository,
        private val getSuggestionsUseCase: GetSuggestionsUseCase,
        private val workManager: androidx.work.WorkManager,
        private val settingsDataStore: com.chakir.plexhubtv.core.datastore.SettingsDataStore,
    ) : BaseViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        private val _navigationEvents = Channel<HomeNavigationEvent>(Channel.BUFFERED)
        val navigationEvents = _navigationEvents.receiveAsFlow()

        init {
            collectSharedContent()
            collectMyList()
            loadSuggestions()
            checkInitialSync()
            observeHomeRowPreferences()
        }

        private fun checkInitialSync() {
            viewModelScope.launch {
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
                                val phase = initialSyncWork.progress.getString("phase") ?: "discovering"
                                val libraryName = initialSyncWork.progress.getString("libraryName") ?: ""
                                val completedLibs = initialSyncWork.progress.getInt("completedLibs", 0)
                                val totalLibs = initialSyncWork.progress.getInt("totalLibs", 0)
                                val state = initialSyncWork.state

                                _uiState.update {
                                    it.copy(
                                        syncProgress = progress,
                                        syncMessage = message,
                                        syncPhase = phase,
                                        syncLibraryName = libraryName,
                                        syncCompletedLibraries = completedLibs,
                                        syncTotalLibraries = totalLibs,
                                    )
                                }

                                if (state == androidx.work.WorkInfo.State.SUCCEEDED ||
                                    state == androidx.work.WorkInfo.State.FAILED ||
                                    state == androidx.work.WorkInfo.State.CANCELLED
                                ) {
                                    _uiState.update { it.copy(isInitialSync = false) }
                                }
                            }
                        }
                }
            }
        }

        fun onAction(action: HomeAction) {
            when (action) {
                is HomeAction.Refresh -> {
                    _uiState.update { it.copy(isLoading = it.onDeck.isEmpty()) }
                    getUnifiedHomeContentUseCase.refresh()
                    loadSuggestions()
                }
                is HomeAction.OpenMedia -> {
                    if (action.media.serverId == "watchlist") {
                        _errorEvents.trySend(
                            AppError.Media.NotFound(),
                        )
                    } else {
                        viewModelScope.launch {
                            _navigationEvents.send(
                                HomeNavigationEvent.NavigateToDetails(action.media.ratingKey, action.media.serverId),
                            )
                        }
                    }
                }
                is HomeAction.PlayMedia -> {
                    if (action.media.serverId == "watchlist") {
                        _errorEvents.trySend(
                            AppError.Media.NotFound(),
                        )
                    } else {
                        viewModelScope.launch {
                            _navigationEvents.send(
                                HomeNavigationEvent.NavigateToPlayer(action.media.ratingKey, action.media.serverId),
                            )
                        }
                    }
                }
                is HomeAction.FocusMedia -> {
                    _uiState.update { it.copy(focusedItem = action.media) }
                }
            }
        }

        private fun collectSharedContent() {
            getUnifiedHomeContentUseCase.sharedContent
                .filterNotNull()
                .onEach { result ->
                    result.fold(
                        onSuccess = { content ->
                            val activeProfile = profileRepository.getActiveProfile()
                            val filtered = if (activeProfile != null) {
                                filterContentByAgeUseCase(content.onDeck, activeProfile)
                            } else {
                                content.onDeck
                            }
                            _uiState.update { state ->
                                val initialFocused = if (state.focusedItem == null) {
                                    filtered.firstOrNull()
                                        ?: content.hubs.firstOrNull()?.items?.firstOrNull()
                                } else {
                                    state.focusedItem
                                }
                                state.copy(
                                    isLoading = false,
                                    onDeck = filtered.toImmutableList(),
                                    hubs = content.hubs.toImmutableList(),
                                    focusedItem = initialFocused,
                                )
                            }
                        },
                        onFailure = { error ->
                            Timber.e("SCREEN [Home] FAILED: error=${error.message}")
                            _errorEvents.trySend(error.toAppError())
                            _uiState.update { it.copy(isLoading = false) }
                        },
                    )
                }
                .catch { e -> Timber.e(e, "HomeViewModel: sharedContent flow failed") }
                .launchIn(viewModelScope)
        }

        /**
         * Combines cloud watchlist items with local-only favorites for the "My List" row.
         * Watchlist items take priority; local favorites not in the watchlist are appended.
         */
        private fun collectMyList() {
            combine(
                watchlistRepository.getWatchlistItems(),
                favoritesRepository.getFavorites(),
            ) { watchlistItems, localFavorites ->
                val watchlistGuids = watchlistItems.mapNotNull { it.guid }.toSet()
                val localOnly = localFavorites.filter { fav ->
                    fav.guid == null || fav.guid !in watchlistGuids
                }
                watchlistItems + localOnly
            }
                .catch { e -> Timber.e(e, "HomeViewModel: my list collection failed") }
                .onEach { items ->
                    _uiState.update { it.copy(favorites = items.toImmutableList()) }
                }
                .launchIn(viewModelScope)
        }

        private fun loadSuggestions() {
            viewModelScope.launch {
                val suggestions = getSuggestionsUseCase()
                _uiState.update { it.copy(suggestions = suggestions.toImmutableList()) }
            }
        }

        private fun observeHomeRowPreferences() {
            combine(
                settingsDataStore.showContinueWatching,
                settingsDataStore.showMyList,
                settingsDataStore.showSuggestions,
                settingsDataStore.homeRowOrder,
            ) { showContinueWatching, showMyList, showSuggestions, homeRowOrder ->
                _uiState.update {
                    it.copy(
                        showContinueWatching = showContinueWatching,
                        showMyList = showMyList,
                        showSuggestions = showSuggestions,
                        homeRowOrder = homeRowOrder.toImmutableList(),
                    )
                }
            }.launchIn(viewModelScope)
        }
    }

sealed interface HomeNavigationEvent {
    data class NavigateToDetails(val ratingKey: String, val serverId: String) : HomeNavigationEvent

    data class NavigateToPlayer(val ratingKey: String, val serverId: String) : HomeNavigationEvent
}
