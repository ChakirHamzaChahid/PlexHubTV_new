package com.chakir.plexhubtv.feature.hub

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.repository.HubsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class ViewMode {
    GRID,
    LIST,
}

data class HubDetailUiState(
    val hub: Hub? = null,
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed interface HubDetailEvent {
    object Back : HubDetailEvent

    data class ToggleViewMode(val mode: ViewMode) : HubDetailEvent
}

/**
 * ViewModel pour l'écran détail d'un Hub spécifique (ex: "Récemment ajoutés").
 * Charge la liste complète des éléments du Hub et gère les modes d'affichage (Grid/List).
 */
@HiltViewModel
class HubDetailViewModel
    @Inject
    constructor(
        private val hubsRepository: HubsRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val hubKey: String = checkNotNull(savedStateHandle["hubKey"])
        private val serverId: String = checkNotNull(savedStateHandle["serverId"])

        private val _uiState = MutableStateFlow(HubDetailUiState())
        val uiState = _uiState.asStateFlow()

        init {
            loadHubDetail()
        }

        private fun loadHubDetail() {
            viewModelScope.launch {
                val startTime = System.currentTimeMillis()
                Timber.d("SCREEN [HubDetail]: Loading start for $hubKey on $serverId")
                _uiState.update { it.copy(isLoading = true) }
                // In a real app, we'd fetch specific hub content.
                // For now, we reuse the detail or fetch from repository if implemented.
                // Since unified hubs were already fetched, we might just filter them.
                hubsRepository.getUnifiedHubs().collect { hubs ->
                    val duration = System.currentTimeMillis() - startTime
                    val hub = hubs.find { it.serverId == serverId && it.key == hubKey }
                    if (hub != null) {
                        Timber.i("SCREEN [HubDetail] PROGRESS: Duration=${duration}ms | HubTitle=${hub.title} | Items=${hub.items.size}")
                        _uiState.update { it.copy(hub = hub, items = hub.items, isLoading = false, error = null) }
                    } else if (hubs.isNotEmpty()) {
                        Timber.w("SCREEN [HubDetail] NOT FOUND: hub $hubKey not in current batch after ${duration}ms")
                        // If we have hubs but not this one, it might still be loading or missing.
                        // We stay in whatever state we are, hoping for the next emission.
                    }
                }
            }
        }

        fun onEvent(event: HubDetailEvent) {
            when (event) {
                is HubDetailEvent.ToggleViewMode -> {
                    // View mode handled in UI state internally for simplicity in screen
                }
                HubDetailEvent.Back -> { /* Handled by navigation */ }
            }
        }
    }
