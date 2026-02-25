package com.chakir.plexhubtv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.common.safeCollectIn
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.toAppError
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
        private val workManager: androidx.work.WorkManager,
        private val settingsDataStore: com.chakir.plexhubtv.core.datastore.SettingsDataStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        private val _navigationEvents = Channel<HomeNavigationEvent>()
        val navigationEvents = _navigationEvents.receiveAsFlow()

        private val _errorEvents = Channel<AppError>()
        val errorEvents = _errorEvents.receiveAsFlow()

        init {
            loadContent()
            checkInitialSync()
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
                                val state = initialSyncWork.state

                                _uiState.update {
                                    it.copy(
                                        syncProgress = progress,
                                        syncMessage = message,
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
                Timber.d("SCREEN [Home]: Loading start")
                _uiState.update { it.copy(isLoading = it.onDeck.isEmpty()) }

                getUnifiedHomeContentUseCase()
                    .catch { error ->
                        Timber.e(error, "HomeViewModel: getUnifiedHomeContentUseCase failed")
                        viewModelScope.launch { _errorEvents.send(error.toAppError()) }
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    .collect { result ->
                        result.fold(
                            onSuccess = { content ->
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        onDeck = content.onDeck,
                                    )
                                }
                            },
                            onFailure = { error ->
                                Timber.e("SCREEN [Home] FAILED: error=${error.message}")
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
