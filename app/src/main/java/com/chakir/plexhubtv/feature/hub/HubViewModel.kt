package com.chakir.plexhubtv.feature.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.common.safeCollectIn
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.toAppError
import com.chakir.plexhubtv.domain.repository.FavoritesRepository
import com.chakir.plexhubtv.domain.usecase.GetUnifiedHomeContentUseCase
import com.chakir.plexhubtv.domain.usecase.ToggleWatchStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.toImmutableList
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HubViewModel
    @Inject
    constructor(
        private val getUnifiedHomeContentUseCase: GetUnifiedHomeContentUseCase,
        private val favoritesRepository: FavoritesRepository,
        private val toggleWatchStatusUseCase: ToggleWatchStatusUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HubUiState(isLoading = true))
        val uiState: StateFlow<HubUiState> = _uiState.asStateFlow()

        private val _navigationEvents = Channel<HubNavigationEvent>()
        val navigationEvents = _navigationEvents.receiveAsFlow()

        private val _errorEvents = Channel<AppError>()
        val errorEvents = _errorEvents.receiveAsFlow()

        init {
            observeFavorites()
            collectSharedContent()
        }

        private fun observeFavorites() {
            favoritesRepository.getFavorites().safeCollectIn(
                scope = viewModelScope,
                onError = { e ->
                    Timber.e(e, "HubViewModel: getFavorites failed")
                }
            ) { favorites ->
                _uiState.update { it.copy(favorites = favorites.toImmutableList()) }
            }
        }

        fun onAction(action: HubAction) {
            when (action) {
                is HubAction.Refresh -> {
                    _uiState.update { it.copy(isLoading = it.onDeck.isEmpty() && it.hubs.isEmpty()) }
                    getUnifiedHomeContentUseCase.refresh()
                }
                is HubAction.OpenMedia -> {
                    viewModelScope.launch {
                        _navigationEvents.send(HubNavigationEvent.NavigateToDetails(action.media.ratingKey, action.media.serverId))
                    }
                }
                is HubAction.PlayMedia -> {
                    viewModelScope.launch {
                        _navigationEvents.send(HubNavigationEvent.NavigateToPlayer(action.media.ratingKey, action.media.serverId))
                    }
                }
                is HubAction.ShowRemoveDialog -> {
                    _uiState.update { it.copy(pendingRemoval = action.media) }
                }
                is HubAction.DismissRemoveDialog -> {
                    _uiState.update { it.copy(pendingRemoval = null) }
                }
                is HubAction.ConfirmRemoveFromOnDeck -> {
                    val media = _uiState.value.pendingRemoval ?: return
                    // Optimistic UI: remove item immediately
                    _uiState.update { current ->
                        current.copy(
                            pendingRemoval = null,
                            onDeck = current.onDeck
                                .filter { it.ratingKey != media.ratingKey || it.serverId != media.serverId }
                                .toImmutableList(),
                        )
                    }
                    // Scrobble (mark as watched) to remove from Plex On Deck
                    viewModelScope.launch {
                        toggleWatchStatusUseCase(media, isWatched = true)
                        getUnifiedHomeContentUseCase.refresh()
                    }
                }
            }
        }

        private fun collectSharedContent() {
            getUnifiedHomeContentUseCase.sharedContent
                .filterNotNull()
                .onEach { result ->
                    result.fold(
                        onSuccess = { content ->
                            val filteredHubs =
                                content.hubs
                                    .filter { it.items.isNotEmpty() }
                                    .distinctBy { it.title }

                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    onDeck = content.onDeck.toImmutableList(),
                                    hubs = filteredHubs.toImmutableList(),
                                )
                            }
                        },
                        onFailure = { error ->
                            Timber.e("SCREEN [Hub] FAILED: error=${error.message}")
                            _errorEvents.send(error.toAppError())
                            _uiState.update { it.copy(isLoading = false) }
                        },
                    )
                }
                .launchIn(viewModelScope)
        }
    }

sealed interface HubNavigationEvent {
    data class NavigateToDetails(val ratingKey: String, val serverId: String) : HubNavigationEvent
    data class NavigateToPlayer(val ratingKey: String, val serverId: String) : HubNavigationEvent
}
