package com.chakir.plexhubtv.feature.hub

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.chakir.plexhubtv.domain.model.Hub
import com.chakir.plexhubtv.domain.model.MediaItem
import javax.inject.Inject

enum class ViewMode {
    GRID, LIST
}

data class HubDetailUiState(
    val hub: Hub? = null,
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface HubDetailEvent {
    object Back : HubDetailEvent
    data class ToggleViewMode(val mode: ViewMode) : HubDetailEvent
}

@HiltViewModel
class HubDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    savedStateHandle: SavedStateHandle
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
            android.util.Log.d("METRICS", "SCREEN [HubDetail]: Loading start for $hubKey on $serverId")
            _uiState.update { it.copy(isLoading = true) }
            // In a real app, we'd fetch specific hub content.
            // For now, we reuse the detail or fetch from repository if implemented.
            // Since unified hubs were already fetched, we might just filter them.
            mediaRepository.getUnifiedHubs().collect { hubs ->
                val duration = System.currentTimeMillis() - startTime
                val hub = hubs.find { it.serverId == serverId && it.key == hubKey }
                if (hub != null) {
                    android.util.Log.i("METRICS", "SCREEN [HubDetail] PROGRESS: Duration=${duration}ms | HubTitle=${hub.title} | Items=${hub.items.size}")
                    _uiState.update { it.copy(hub = hub, items = hub.items, isLoading = false, error = null) }
                } else if (hubs.isNotEmpty()) {
                    android.util.Log.w("METRICS", "SCREEN [HubDetail] NOT FOUND: hub $hubKey not in current batch after ${duration}ms")
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
