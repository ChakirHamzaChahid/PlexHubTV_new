package com.chakir.plexhubtv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.domain.usecase.GetUnifiedHomeContentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.work.WorkInfo

/**
 * ViewModel pour l'écran d'accueil.
 * Récupère le contenu unifié (On Deck + Hubs) via [GetUnifiedHomeContentUseCase].
 * Gère également le suivi de la synchronisation initiale via WorkManager.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getUnifiedHomeContentUseCase: GetUnifiedHomeContentUseCase,
    private val workManager: androidx.work.WorkManager,
    private val settingsDataStore: com.chakir.plexhubtv.core.datastore.SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _navigationEvents = Channel<HomeNavigationEvent>()
    val navigationEvents = _navigationEvents.receiveAsFlow()

    init {
        checkInitialSync()
    }

    private fun checkInitialSync() {
        viewModelScope.launch {
            val isFirstSyncComplete = settingsDataStore.isFirstSyncComplete.firstOrNull() ?: false
            if (!isFirstSyncComplete) {
                _uiState.update { it.copy(isInitialSync = true, isLoading = false) }
                
                // Observe WorkManager for "LibrarySync_Initial"
                // We use getWorkInfosForUniqueWorkFlow to react to changes
                workManager.getWorkInfosForUniqueWorkFlow("LibrarySync_Initial")
                    .collect { workInfos ->
                        val initialSyncWork = workInfos.firstOrNull()
                        if (initialSyncWork != null) {
                            val progress = initialSyncWork.progress.getFloat("progress", 0f)
                            val message = initialSyncWork.progress.getString("message") ?: "Initializing..."
                            val state = initialSyncWork.state
                            
                            _uiState.update { 
                                it.copy(
                                    syncProgress = progress,
                                    syncMessage = message
                                )
                            }
                            
                            if (state == WorkInfo.State.SUCCEEDED || state == WorkInfo.State.FAILED || state == WorkInfo.State.CANCELLED) {
                                // Sync done (Success or Failure)
                                // We trust the worker to update "isFirstSyncComplete" on success.
                                // If failed, we might want to retry or just let the user in.
                                // For now, let's assume if it finished, we proceed.
                                _uiState.update { it.copy(isInitialSync = false, isLoading = true) }
                                loadContent()
                            }
                        }
                    }
            } else {
                loadContent()
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

    private fun loadContent() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            android.util.Log.d("METRICS", "SCREEN [Home]: Loading start")
            
            // Avoid showing full-screen loading if we already have some data (e.g., from previous session or quick refresh)
            _uiState.update { it.copy(isLoading = it.onDeck.isEmpty() && it.hubs.isEmpty(), error = null) }
            
            getUnifiedHomeContentUseCase().collect { result ->
                val duration = System.currentTimeMillis() - startTime
                result.fold(
                    onSuccess = { content ->
                        android.util.Log.i("METRICS", "SCREEN [Home] PROGRESS: Duration=${duration}ms | OnDeck=${content.onDeck.size} | Hubs=${content.hubs.size}")
                        
                        // FIX: Filter empty hubs and deduplicate by title to reduce UI clutter
                        val filteredHubs = content.hubs
                            .filter { it.items.isNotEmpty() }
                            .distinctBy { it.title }
                            
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                onDeck = content.onDeck,
                                hubs = filteredHubs
                            )
                        }
                    },
                    onFailure = { error ->
                        android.util.Log.e("METRICS", "SCREEN [Home] FAILED: duration=${duration}ms error=${error.message}")
                        // Only show error message if no content is displayed
                        _uiState.update {
                            if (it.onDeck.isEmpty() && it.hubs.isEmpty()) {
                                it.copy(isLoading = false, error = error.message ?: "Unknown error occurred")
                            } else {
                                it.copy(isLoading = false) // Silently fail update if we have cache
                            }
                        }
                    }
                )
            }
        }
    }
}

sealed interface HomeNavigationEvent {
    data class NavigateToDetails(val ratingKey: String, val serverId: String) : HomeNavigationEvent
    data class NavigateToPlayer(val ratingKey: String, val serverId: String) : HomeNavigationEvent
}
