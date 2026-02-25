package com.chakir.plexhubtv.feature.hub

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.common.safeCollectIn
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
        private val hubKey: String? = savedStateHandle["hubKey"]
        private val serverId: String? = savedStateHandle["serverId"]

        private val _uiState = MutableStateFlow(HubDetailUiState())
        val uiState = _uiState.asStateFlow()

        init {
            if (hubKey == null || serverId == null) {
                Timber.e("HubDetailViewModel: missing required navigation args (hubKey=$hubKey, serverId=$serverId)")
                _uiState.update { it.copy(error = "Invalid navigation arguments") }
            } else {
                loadHubDetail()
            }
        }

        private fun loadHubDetail() {
            val hk = hubKey ?: return
            val sid = serverId ?: return
            val startTime = System.currentTimeMillis()
            Timber.d("SCREEN [HubDetail]: Loading start for $hk on $sid")
            _uiState.update { it.copy(isLoading = true) }
            // In a real app, we'd fetch specific hub content.
            // For now, we reuse the detail or fetch from repository if implemented.
            // Since unified hubs were already fetched, we might just filter them.
            hubsRepository.getUnifiedHubs().safeCollectIn(
                scope = viewModelScope,
                onError = { e ->
                    val duration = System.currentTimeMillis() - startTime
                    Timber.e(e, "HubDetailViewModel: loadHubDetail failed")
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load hub") }
                }
            ) { hubs ->
                val duration = System.currentTimeMillis() - startTime
                val hub = hubs.find { it.serverId == sid && it.key == hk }
                if (hub != null) {
                    Timber.i("SCREEN [HubDetail] PROGRESS: Duration=${duration}ms | HubTitle=${hub.title} | Items=${hub.items.size}")
                    _uiState.update { it.copy(hub = hub, items = hub.items, isLoading = false, error = null) }
                } else if (hubs.isNotEmpty()) {
                    Timber.w("SCREEN [HubDetail] NOT FOUND: hub $hk not in current batch after ${duration}ms")
                    // If we have hubs but not this one, it might still be loading or missing.
                    // We stay in whatever state we are, hoping for the next emission.
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
