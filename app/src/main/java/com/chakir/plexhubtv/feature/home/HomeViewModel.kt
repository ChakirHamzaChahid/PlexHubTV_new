package com.chakir.plexhubtv.feature.home

import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.common.safeCollectIn
import com.chakir.plexhubtv.core.model.toAppError
import com.chakir.plexhubtv.domain.usecase.GetUnifiedHomeContentUseCase
import com.chakir.plexhubtv.feature.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        private val workManager: androidx.work.WorkManager,
        private val settingsDataStore: com.chakir.plexhubtv.core.datastore.SettingsDataStore,
    ) : BaseViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        private val _navigationEvents = Channel<HomeNavigationEvent>(Channel.BUFFERED)
        val navigationEvents = _navigationEvents.receiveAsFlow()

        init {
            collectSharedContent()
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
                is HomeAction.Refresh -> {
                    _uiState.update { it.copy(isLoading = it.onDeck.isEmpty()) }
                    getUnifiedHomeContentUseCase.refresh()
                }
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

        private fun collectSharedContent() {
            getUnifiedHomeContentUseCase.sharedContent
                .filterNotNull()
                .onEach { result ->
                    result.fold(
                        onSuccess = { content ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    onDeck = content.onDeck.toImmutableList(),
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
                .launchIn(viewModelScope)
        }
    }

sealed interface HomeNavigationEvent {
    data class NavigateToDetails(val ratingKey: String, val serverId: String) : HomeNavigationEvent

    data class NavigateToPlayer(val ratingKey: String, val serverId: String) : HomeNavigationEvent
}
