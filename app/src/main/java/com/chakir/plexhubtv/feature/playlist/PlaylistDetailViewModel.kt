package com.chakir.plexhubtv.feature.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.Playlist
import com.chakir.plexhubtv.feature.common.BaseViewModel
import com.chakir.plexhubtv.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class PlaylistDetailUiState(
    val isLoading: Boolean = false,
    val playlist: Playlist? = null,
    val error: String? = null,
    val showDeleteConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
)

sealed interface PlaylistDetailEvent {
    data class OpenMediaDetail(val media: MediaItem) : PlaylistDetailEvent
    data object DeletePlaylistClicked : PlaylistDetailEvent
    data object ConfirmDeletePlaylist : PlaylistDetailEvent
    data object DismissDeleteDialog : PlaylistDetailEvent
    data object Back : PlaylistDetailEvent
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel() {

    private val playlistId: String? = savedStateHandle["playlistId"]
    private val serverId: String? = savedStateHandle["serverId"]

    private val _uiState = MutableStateFlow(PlaylistDetailUiState(isLoading = true))
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    init {
        if (playlistId == null || serverId == null) {
            Timber.e("PlaylistDetailViewModel: missing required navigation args (playlistId=$playlistId, serverId=$serverId)")
            _uiState.update { it.copy(isLoading = false, error = "Invalid navigation arguments") }
        } else {
            loadPlaylist()
        }
    }

    private fun loadPlaylist() {
        val pid = playlistId ?: return
        val sid = serverId ?: return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val playlist = playlistRepository.getPlaylistDetail(pid, sid)
                if (playlist != null) {
                    _uiState.update { it.copy(isLoading = false, playlist = playlist) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Playlist not found") }
                }
            } catch (e: Exception) {
                Timber.e(e, "PlaylistDetailViewModel: loadPlaylist failed")
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load playlist") }
            }
        }
    }

    fun onEvent(event: PlaylistDetailEvent) {
        when (event) {
            is PlaylistDetailEvent.DeletePlaylistClicked -> {
                _uiState.update { it.copy(showDeleteConfirmation = true) }
            }
            is PlaylistDetailEvent.ConfirmDeletePlaylist -> {
                val pid = playlistId ?: return
                val sid = serverId ?: return
                _uiState.update { it.copy(showDeleteConfirmation = false, isDeleting = true) }
                viewModelScope.launch {
                    playlistRepository.deletePlaylist(pid, sid)
                        .onSuccess {
                            // Navigation back will be handled by the UI
                            _uiState.update { it.copy(isDeleting = false, playlist = null) }
                        }
                        .onFailure { error ->
                            Timber.e(error, "PlaylistDetailViewModel: deletePlaylist failed")
                            _uiState.update { it.copy(isDeleting = false, error = error.message) }
                        }
                }
            }
            is PlaylistDetailEvent.DismissDeleteDialog -> {
                _uiState.update { it.copy(showDeleteConfirmation = false) }
            }
            else -> { /* OpenMediaDetail and Back handled at Route level */ }
        }
    }
}
