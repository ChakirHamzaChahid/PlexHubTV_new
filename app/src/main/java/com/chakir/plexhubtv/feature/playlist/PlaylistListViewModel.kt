package com.chakir.plexhubtv.feature.playlist

import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.model.Playlist
import com.chakir.plexhubtv.feature.common.BaseViewModel
import com.chakir.plexhubtv.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class PlaylistListUiState(
    val isLoading: Boolean = true,
    val playlists: List<Playlist> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) : BaseViewModel() {

    val uiState: StateFlow<PlaylistListUiState> = playlistRepository.getPlaylists()
        .map { playlists ->
            PlaylistListUiState(
                isLoading = false,
                playlists = playlists,
            )
        }
        .catch { e ->
            Timber.e(e, "PlaylistListViewModel: getPlaylists failed")
            emit(PlaylistListUiState(isLoading = false, error = e.message ?: "Failed to load playlists"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlaylistListUiState(),
        )

    init {
        Timber.d("SCREEN [Playlists]: Opened")
        refreshPlaylists()
    }

    fun refreshPlaylists() {
        viewModelScope.launch {
            try {
                playlistRepository.refreshPlaylists()
            } catch (e: Exception) {
                Timber.e(e, "PlaylistListViewModel: refreshPlaylists failed")
            }
        }
    }
}
