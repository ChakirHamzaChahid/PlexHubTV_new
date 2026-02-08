package com.chakir.plexhubtv.feature.iptv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.model.IptvChannel
import com.chakir.plexhubtv.domain.repository.IptvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IptvUiState(
    val isLoading: Boolean = false,
    val channels: List<IptvChannel> = emptyList(),
    val groupedChannels: Map<String?, List<IptvChannel>> = emptyMap(),
    val error: String? = null,
    val searchQuery: String = "",
    val showUrlDialog: Boolean = false,
)

sealed class IptvEvent {
    data class OnSearchQueryChange(val query: String) : IptvEvent()

    data class SaveUrl(val url: String) : IptvEvent()

    object Refresh : IptvEvent()

    object DismissUrlDialog : IptvEvent()
}

@HiltViewModel
class IptvViewModel
    @Inject
    constructor(
        private val repository: IptvRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(IptvUiState())
        val uiState: StateFlow<IptvUiState> = _uiState.asStateFlow()

        private var allChannels: List<IptvChannel> = emptyList()

        init {
            loadChannels()
        }

        private fun loadChannels() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }

                // Initial load from repository (which might be empty initially)
                repository.getChannels().collect { channels ->
                    if (channels.isEmpty()) {
                        // Trigger fetch if empty
                        val url = repository.getM3uUrl()
                        if (url != null) {
                            fetchChannels(url)
                        } else {
                            _uiState.update { it.copy(isLoading = false, showUrlDialog = true) }
                        }
                    } else {
                        allChannels = channels
                        updateFilteredList()
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
            }
        }

        fun saveIptvUrl(url: String) {
            viewModelScope.launch {
                if (url.isNotBlank()) {
                    repository.saveM3uUrl(url)
                    _uiState.update { it.copy(showUrlDialog = false, isLoading = true) }
                    fetchChannels(url)
                }
            }
        }

        private suspend fun fetchChannels(url: String) {
            val result = repository.refreshChannels(url)
            if (result.isFailure) {
                _uiState.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
            }
        }

        fun onEvent(event: IptvEvent) {
            when (event) {
                is IptvEvent.OnSearchQueryChange -> {
                    _uiState.update { it.copy(searchQuery = event.query) }
                    updateFilteredList()
                }
                is IptvEvent.Refresh -> {
                    viewModelScope.launch {
                        val url = repository.getM3uUrl()
                        if (url != null) {
                            _uiState.update { it.copy(isLoading = true) }
                            fetchChannels(url)
                        } else {
                            _uiState.update { it.copy(showUrlDialog = true) }
                        }
                    }
                }
                is IptvEvent.SaveUrl -> {
                    saveIptvUrl(event.url)
                }
                is IptvEvent.DismissUrlDialog -> {
                    _uiState.update { it.copy(showUrlDialog = false) }
                }
            }
        }

        private fun updateFilteredList() {
            val query = _uiState.value.searchQuery
            val filtered =
                if (query.isBlank()) {
                    allChannels
                } else {
                    allChannels.filter {
                        it.name.contains(query, ignoreCase = true) ||
                            (it.group?.contains(query, ignoreCase = true) == true)
                    }
                }

            val grouped = filtered.groupBy { it.group }
            _uiState.update { it.copy(channels = filtered, groupedChannels = grouped) }
        }
    }
