package com.chakir.plexhubtv.feature.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.common.safeCollectIn
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.toAppError
import com.chakir.plexhubtv.domain.repository.FavoritesRepository
import com.chakir.plexhubtv.domain.usecase.GetUnifiedHomeContentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HubViewModel
    @Inject
    constructor(
        private val getUnifiedHomeContentUseCase: GetUnifiedHomeContentUseCase,
        private val favoritesRepository: FavoritesRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HubUiState(isLoading = true))
        val uiState: StateFlow<HubUiState> = _uiState.asStateFlow()

        private val _navigationEvents = Channel<HubNavigationEvent>()
        val navigationEvents = _navigationEvents.receiveAsFlow()

        private val _errorEvents = Channel<AppError>()
        val errorEvents = _errorEvents.receiveAsFlow()

        init {
            observeFavorites()
            loadContent()
        }

        private fun observeFavorites() {
            favoritesRepository.getFavorites().safeCollectIn(
                scope = viewModelScope,
                onError = { e ->
                    Timber.e(e, "HubViewModel: getFavorites failed")
                }
            ) { favorites ->
                _uiState.update { it.copy(favorites = favorites) }
            }
        }

        fun onAction(action: HubAction) {
            when (action) {
                is HubAction.Refresh -> loadContent()
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
            }
        }

        private var contentJob: Job? = null

        private fun loadContent() {
            contentJob?.cancel()
            contentJob = viewModelScope.launch {
                Timber.d("SCREEN [Hub]: Loading start")
                _uiState.update { it.copy(isLoading = it.onDeck.isEmpty() && it.hubs.isEmpty()) }

                getUnifiedHomeContentUseCase()
                    .catch { error ->
                        Timber.e(error, "HubViewModel: getUnifiedHomeContentUseCase failed")
                        viewModelScope.launch { _errorEvents.send(error.toAppError()) }
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    .collect { result ->
                        result.fold(
                            onSuccess = { content ->
                                val filteredHubs =
                                    content.hubs
                                        .filter { it.items.isNotEmpty() }
                                        .distinctBy { it.title }

                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        onDeck = content.onDeck,
                                        hubs = filteredHubs,
                                    )
                                }
                            },
                            onFailure = { error ->
                                Timber.e("SCREEN [Hub] FAILED: error=${error.message}")
                                viewModelScope.launch { _errorEvents.send(error.toAppError()) }
                                _uiState.update { it.copy(isLoading = false) }
                            },
                        )
                    }
            }
        }
    }

sealed interface HubNavigationEvent {
    data class NavigateToDetails(val ratingKey: String, val serverId: String) : HubNavigationEvent
    data class NavigateToPlayer(val ratingKey: String, val serverId: String) : HubNavigationEvent
}
