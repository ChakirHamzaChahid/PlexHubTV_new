package com.chakir.plexhubtv.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.domain.repository.DownloadsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel pour gérer les contenus téléchargés.
 * Interagit avec [DownloadsRepository] pour récupérer et supprimer des téléchargements.
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadsRepository: DownloadsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState(isLoading = true))
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private val _navigationEvents = Channel<DownloadsNavigationEvent>()
    val navigationEvents = _navigationEvents.receiveAsFlow()

    init {
        loadDownloads()
    }

    fun onAction(action: DownloadsAction) {
        when (action) {
            is DownloadsAction.Refresh -> loadDownloads()
            is DownloadsAction.DeleteDownload -> {
                viewModelScope.launch {
                    downloadsRepository.deleteDownload(action.mediaId)
                        // Repository flow should update list automatically, but logic depends on impl
                        // If getAllDownloads() is a cold flow re-emitted on DB change, it will update.
                }
            }
            is DownloadsAction.PlayDownload -> {
                viewModelScope.launch {
                    _navigationEvents.send(
                        DownloadsNavigationEvent.NavigateToPlayer(
                            action.media.ratingKey,
                            action.media.serverId
                        )
                    )
                }
            }
            is DownloadsAction.RetryDownload -> {
                // Logic to restart download
                // downloadsRepository.retryDownload(action.mediaId) // If this existed
            }
        }
    }

    private fun loadDownloads() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            downloadsRepository.getAllDownloads().collect { items ->
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        downloads = items
                    ) 
                }
            }
        }
    }
}

sealed interface DownloadsNavigationEvent {
    data class NavigateToPlayer(val ratingKey: String, val serverId: String) : DownloadsNavigationEvent
}
