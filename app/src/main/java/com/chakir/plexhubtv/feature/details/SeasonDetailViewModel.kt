package com.chakir.plexhubtv.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.usecase.GetMediaDetailUseCase
import com.chakir.plexhubtv.domain.usecase.ResolveEpisodeSourcesUseCase
import com.chakir.plexhubtv.domain.usecase.ToggleWatchStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SeasonDetailUiState(
    val isLoading: Boolean = false,
    val season: MediaItem? = null,
    val episodes: List<MediaItem> = emptyList(),
    val isOfflineMode: Boolean = false,
    val showSourceSelection: Boolean = false,
    val selectedEpisodeForSources: MediaItem? = null,
    val isResolvingSources: Boolean = false,
    val error: String? = null,
)

sealed interface SeasonDetailEvent {
    data class PlayEpisode(val episode: MediaItem) : SeasonDetailEvent

    data class PlaySource(val source: com.chakir.plexhubtv.core.model.MediaSource) : SeasonDetailEvent

    data object DismissSourceSelection : SeasonDetailEvent

    data object MarkSeasonWatched : SeasonDetailEvent

    data object ToggleFavorite : SeasonDetailEvent

    data object Back : SeasonDetailEvent
}

/**
 * ViewModel pour le détail d'une Saison.
 * Gère le chargement des épisodes.
 */
@HiltViewModel
class SeasonDetailViewModel
    @Inject
    constructor(
        private val getMediaDetailUseCase: GetMediaDetailUseCase,
        private val toggleWatchStatusUseCase: ToggleWatchStatusUseCase,
        private val resolveEpisodeSourcesUseCase: ResolveEpisodeSourcesUseCase,
        private val getPlayQueueUseCase: com.chakir.plexhubtv.domain.usecase.GetPlayQueueUseCase,
        private val playbackManager: com.chakir.plexhubtv.domain.service.PlaybackManager,
        private val toggleFavoriteUseCase: com.chakir.plexhubtv.domain.usecase.ToggleFavoriteUseCase,
        private val isFavoriteUseCase: com.chakir.plexhubtv.domain.usecase.IsFavoriteUseCase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val ratingKey: String = checkNotNull(savedStateHandle["ratingKey"])
        private val serverId: String = checkNotNull(savedStateHandle["serverId"])

        private val _uiState = MutableStateFlow(SeasonDetailUiState(isLoading = true))
        val uiState: StateFlow<SeasonDetailUiState> = _uiState.asStateFlow()

        val downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
        val isOfflineMode = MutableStateFlow(false)

        private val _navigationEvents = Channel<SeasonDetailNavigationEvent>()
        val navigationEvents = _navigationEvents.receiveAsFlow()

        init {
            loadSeason()
            observeOfflineMode()
            checkFavoriteStatus()
        }

        private fun checkFavoriteStatus() {
            viewModelScope.launch {
                isFavoriteUseCase(ratingKey, serverId).collect { isFav ->
                    val current = _uiState.value.season
                    if (current != null) {
                        _uiState.update { it.copy(season = current.copy(isFavorite = isFav)) }
                    }
                }
            }
        }

        private fun observeOfflineMode() {
            viewModelScope.launch {
                isOfflineMode.collect { offline ->
                    _uiState.update { it.copy(isOfflineMode = offline) }
                }
            }
        }
        // Mock download states or integrate with DownloadManager

        fun onAction(event: SeasonDetailEvent) = onEvent(event)

        fun onEvent(event: SeasonDetailEvent) {
            when (event) {
                is SeasonDetailEvent.PlayEpisode -> {
                    viewModelScope.launch {
                        _uiState.update { it.copy(isResolvingSources = true) }

                        // Populate Queue
                        val queue = getPlayQueueUseCase(event.episode).getOrElse { listOf(event.episode) }
                        playbackManager.play(event.episode, queue)

                        val sources = resolveEpisodeSourcesUseCase(event.episode)
                        _uiState.update { it.copy(isResolvingSources = false) }

                        if (sources.size > 1) {
                            // Update episode with sources and show dialog
                            val enrichedEpisode = event.episode.copy(remoteSources = sources)
                            _uiState.update {
                                it.copy(
                                    showSourceSelection = true,
                                    selectedEpisodeForSources = enrichedEpisode,
                                )
                            }
                        } else {
                            // Play directly
                            _navigationEvents.send(
                                SeasonDetailNavigationEvent.NavigateToPlayer(event.episode.ratingKey, event.episode.serverId),
                            )
                        }
                    }
                }
                is SeasonDetailEvent.PlaySource -> {
                    _uiState.update { it.copy(showSourceSelection = false, selectedEpisodeForSources = null) }
                    viewModelScope.launch {
                        _navigationEvents.send(SeasonDetailNavigationEvent.NavigateToPlayer(event.source.ratingKey, event.source.serverId))
                    }
                }
                is SeasonDetailEvent.DismissSourceSelection -> {
                    _uiState.update { it.copy(showSourceSelection = false, selectedEpisodeForSources = null) }
                }
                is SeasonDetailEvent.Back -> {
                    viewModelScope.launch { _navigationEvents.send(SeasonDetailNavigationEvent.NavigateBack) }
                }
                is SeasonDetailEvent.ToggleFavorite -> {
                    val season = _uiState.value.season ?: return
                    viewModelScope.launch {
                        toggleFavoriteUseCase(season)
                    }
                }
                is SeasonDetailEvent.MarkSeasonWatched -> {
                    val season = _uiState.value.season ?: return
                    viewModelScope.launch {
                        // Optimistic update logic would be complex for whole season, just call use case
                        toggleWatchStatusUseCase(season, true)
                        // Then reload or update state manually
                        loadSeason()
                    }
                }
            }
        }

        private fun loadSeason() {
            viewModelScope.launch {
                val startTime = System.currentTimeMillis()
                Timber.d("SCREEN [SeasonDetail]: Loading start for $ratingKey on $serverId")
                _uiState.update { it.copy(isLoading = true, error = null) }
                getMediaDetailUseCase(ratingKey, serverId).collect { result ->
                    val duration = System.currentTimeMillis() - startTime
                    result.fold(
                        onSuccess = { detail ->
                            Timber.i("SCREEN [SeasonDetail] SUCCESS: Load Duration=${duration}ms | Episodes=${detail.children.size}")
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    season = detail.item,
                                    episodes = detail.children,
                                )
                            }
                        },
                        onFailure = { error ->
                            Timber.e("SCREEN [SeasonDetail] FAILED: duration=${duration}ms error=${error.message}")
                            _uiState.update { it.copy(isLoading = false, error = error.message) }
                        },
                    )
                }
            }
        }
    }

sealed interface SeasonDetailNavigationEvent {
    data class NavigateToPlayer(val ratingKey: String, val serverId: String) : SeasonDetailNavigationEvent

    data object NavigateBack : SeasonDetailNavigationEvent
}

// Download state sealed class
sealed class DownloadState {
    object Queued : DownloadState()

    data class Downloading(val progress: Float) : DownloadState()

    object Paused : DownloadState()

    object Completed : DownloadState()

    object Failed : DownloadState()

    object Cancelled : DownloadState()
}
