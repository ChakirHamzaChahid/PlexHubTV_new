package com.chakir.plexhubtv.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.common.safeCollectIn
import com.chakir.plexhubtv.feature.common.BaseViewModel
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.usecase.GetMediaDetailUseCase
import com.chakir.plexhubtv.feature.common.launchLoading
import com.chakir.plexhubtv.domain.usecase.ResolveEpisodeSourcesUseCase
import com.chakir.plexhubtv.domain.usecase.ToggleWatchStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
        private val enrichMediaItemUseCase: com.chakir.plexhubtv.domain.usecase.EnrichMediaItemUseCase,
        private val preparePlaybackUseCase: com.chakir.plexhubtv.domain.usecase.PreparePlaybackUseCase,
        private val getPlayQueueUseCase: com.chakir.plexhubtv.domain.usecase.GetPlayQueueUseCase,
        private val playbackManager: com.chakir.plexhubtv.domain.service.PlaybackManager,
        private val toggleFavoriteUseCase: com.chakir.plexhubtv.domain.usecase.ToggleFavoriteUseCase,
        private val isFavoriteUseCase: com.chakir.plexhubtv.domain.usecase.IsFavoriteUseCase,
        private val performanceTracker: com.chakir.plexhubtv.core.common.PerformanceTracker,
        private val mediaSourceResolver: com.chakir.plexhubtv.data.source.MediaSourceResolver,
        savedStateHandle: SavedStateHandle,
    ) : BaseViewModel() {
        private val ratingKey: String? = savedStateHandle["ratingKey"]
        private val serverId: String? = savedStateHandle["serverId"]

        private val _uiState = MutableStateFlow(SeasonDetailUiState(isLoading = true))
        val uiState: StateFlow<SeasonDetailUiState> = _uiState.asStateFlow()

        private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
        val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

        private val _isOfflineMode = MutableStateFlow(false)
        val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

        private val _navigationEvents = Channel<SeasonDetailNavigationEvent>(Channel.BUFFERED)
        val navigationEvents = _navigationEvents.receiveAsFlow()

        init {
            if (ratingKey == null || serverId == null) {
                Timber.e("SeasonDetailViewModel: missing required navigation args (ratingKey=$ratingKey, serverId=$serverId)")
                _uiState.update { it.copy(isLoading = false, error = "Invalid navigation arguments") }
            } else {
                loadSeason()
                observeOfflineMode()
                checkFavoriteStatus()
            }
        }

        private fun checkFavoriteStatus() {
            val rk = ratingKey ?: return
            val sid = serverId ?: return
            isFavoriteUseCase(rk, sid).safeCollectIn(
                scope = viewModelScope,
                onError = { e ->
                    Timber.e(e, "SeasonDetailViewModel: checkFavoriteStatus failed")
                }
            ) { isFav ->
                val current = _uiState.value.season
                if (current != null) {
                    _uiState.update { it.copy(season = current.copy(isFavorite = isFav)) }
                }
            }
        }

        private fun observeOfflineMode() {
            isOfflineMode.safeCollectIn(
                scope = viewModelScope,
                onError = { e ->
                    Timber.e(e, "SeasonDetailViewModel: observeOfflineMode failed")
                }
            ) { offline ->
                _uiState.update { it.copy(isOfflineMode = offline) }
            }
        }
        // Mock download states or integrate with DownloadManager

        fun onAction(event: SeasonDetailEvent) = onEvent(event)

        fun onEvent(event: SeasonDetailEvent) {
            when (event) {
                is SeasonDetailEvent.PlayEpisode -> {
                    viewModelScope.launch {
                        val opId = "playback_episode_${event.episode.ratingKey}_${System.currentTimeMillis()}"
                        performanceTracker.startOperation(
                            opId,
                            com.chakir.plexhubtv.core.common.PerfCategory.PLAYBACK,
                            "Episode PlayClicked → Player",
                            mapOf(
                                "title" to event.episode.title,
                                "ratingKey" to event.episode.ratingKey,
                                "serverId" to event.episode.serverId,
                                "viewOffset" to event.episode.viewOffset.toString()
                            )
                        )

                        _uiState.update { it.copy(isResolvingSources = true) }
                        performanceTracker.addCheckpoint(opId, "UI Loading State Shown")

                        try {
                            // 1. Prepare playback: enrichment + source determination
                            val enrichStart = System.currentTimeMillis()
                            val playbackResult = preparePlaybackUseCase(event.episode)
                            val enrichDuration = System.currentTimeMillis() - enrichStart
                            val enrichedEpisode = when (playbackResult) {
                                is com.chakir.plexhubtv.domain.usecase.PreparePlaybackUseCase.Result.ReadyToPlay -> playbackResult.item
                                is com.chakir.plexhubtv.domain.usecase.PreparePlaybackUseCase.Result.NeedsSourceSelection -> playbackResult.item
                            }
                            performanceTracker.addCheckpoint(opId, "PreparePlayback", mapOf(
                                "duration" to enrichDuration,
                                "sources" to enrichedEpisode.remoteSources.size,
                                "result" to playbackResult.javaClass.simpleName
                            ))

                            // 2. Build queue + init playback manager
                            val queueStart = System.currentTimeMillis()
                            val queue = getPlayQueueUseCase(enrichedEpisode).getOrElse { listOf(enrichedEpisode) }
                            val queueDuration = System.currentTimeMillis() - queueStart
                            performanceTracker.addCheckpoint(opId, "Queue Built", mapOf("duration" to queueDuration, "items" to queue.size))

                            playbackManager.play(enrichedEpisode, queue)
                            performanceTracker.addCheckpoint(opId, "PlaybackManager Initialized")

                            _uiState.update { it.copy(isResolvingSources = false) }

                            // 3. Act on result
                            when (playbackResult) {
                                is com.chakir.plexhubtv.domain.usecase.PreparePlaybackUseCase.Result.NeedsSourceSelection -> {
                                    performanceTracker.addCheckpoint(opId, "Source Selection Dialog Shown")
                                    _uiState.update {
                                        it.copy(
                                            showSourceSelection = true,
                                            selectedEpisodeForSources = enrichedEpisode,
                                        )
                                    }
                                    lazyFetchSourceDetails(enrichedEpisode)
                                    // Will end when user selects source
                                }
                                is com.chakir.plexhubtv.domain.usecase.PreparePlaybackUseCase.Result.ReadyToPlay -> {
                                    performanceTracker.addCheckpoint(opId, "Single Source - Direct Navigation")
                                    performanceTracker.endOperation(
                                        opId,
                                        success = true,
                                        additionalMeta = mapOf(
                                            "finalRatingKey" to enrichedEpisode.ratingKey,
                                            "finalServerId" to enrichedEpisode.serverId
                                        )
                                    )
                                    _navigationEvents.send(
                                        SeasonDetailNavigationEvent.NavigateToPlayer(
                                            enrichedEpisode.ratingKey,
                                            enrichedEpisode.serverId,
                                            enrichedEpisode.viewOffset,
                                        ),
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            performanceTracker.endOperation(opId, success = false, errorMessage = e.message)
                            Timber.e(e, "PlayEpisode failed for ${event.episode.title}")
                            _uiState.update { it.copy(isResolvingSources = false) }
                        }
                    }
                }
                is SeasonDetailEvent.PlaySource -> {
                    val viewOffset = _uiState.value.selectedEpisodeForSources?.viewOffset ?: 0L
                    _uiState.update { it.copy(showSourceSelection = false, selectedEpisodeForSources = null) }
                    viewModelScope.launch {
                        // Continue tracking from PlayEpisode
                        val opId = performanceTracker.getSummary(com.chakir.plexhubtv.core.common.PerfCategory.PLAYBACK, 1)
                            .firstOrNull()?.id
                        opId?.let {
                            performanceTracker.addCheckpoint(it, "User Selected Source", mapOf("serverId" to event.source.serverId))
                            performanceTracker.endOperation(
                                it,
                                success = true,
                                additionalMeta = mapOf(
                                    "finalRatingKey" to event.source.ratingKey,
                                    "finalServerId" to event.source.serverId
                                )
                            )
                        }
                        _navigationEvents.send(SeasonDetailNavigationEvent.NavigateToPlayer(event.source.ratingKey, event.source.serverId, viewOffset))
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

        private fun lazyFetchSourceDetails(episode: MediaItem) {
            val sourcesNeedingDetails = episode.remoteSources.filter { source ->
                // Only Plex sources (needing URL resolution) require lazy detail fetch
                mediaSourceResolver.resolve(source.serverId).needsUrlResolution() &&
                    source.resolution == null
            }
            if (sourcesNeedingDetails.isEmpty()) return

            for (source in sourcesNeedingDetails) {
                viewModelScope.launch {
                    try {
                        val detail = getMediaDetailUseCase(source.ratingKey, source.serverId).first()
                        val detailItem = detail.getOrNull()?.item ?: return@launch
                        val part = detailItem.mediaParts?.firstOrNull()
                        val videoStream = part?.streams?.filterIsInstance<com.chakir.plexhubtv.core.model.VideoStream>()?.firstOrNull()
                        val audioStream = part?.streams?.filterIsInstance<com.chakir.plexhubtv.core.model.AudioStream>()?.firstOrNull()
                        val enrichedSource = source.copy(
                            resolution = videoStream?.displayTitle,
                            videoCodec = videoStream?.codec,
                            audioCodec = audioStream?.codec,
                            audioChannels = audioStream?.channels,
                            hasHDR = videoStream?.hasHDR ?: false,
                            container = part?.container,
                            fileSize = part?.size,
                        )
                        // Update the source in UI state progressively
                        _uiState.update { state ->
                            val currentEp = state.selectedEpisodeForSources ?: return@update state
                            val updatedSources = currentEp.remoteSources.map { s ->
                                if (s.serverId == source.serverId && s.ratingKey == source.ratingKey) enrichedSource else s
                            }
                            state.copy(selectedEpisodeForSources = currentEp.copy(remoteSources = updatedSources))
                        }
                        Timber.d("Lazy-fetched stream details for source ${source.serverName}")
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to lazy-fetch source details for ${source.serverName}")
                    }
                }
            }
        }

        private fun loadSeason() {
            val rk = ratingKey ?: return
            val sid = serverId ?: return
            val startTime = System.currentTimeMillis()
            Timber.d("SCREEN [SeasonDetail]: Loading start for $rk on $sid")
            launchLoading(
                onStart = { _uiState.update { it.copy(isLoading = true, error = null) } },
                block = { getMediaDetailUseCase(rk, sid).first() },
                onSuccess = { detail ->
                    val duration = System.currentTimeMillis() - startTime
                    Timber.i("SCREEN [SeasonDetail] SUCCESS: Load Duration=${duration}ms | Episodes=${detail.children.size}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            season = detail.item,
                            episodes = detail.children,
                        )
                    }
                    val unwatched = detail.children.filter { it.viewedStatus != "watched" }
                    val toPrefetch = (unwatched.take(3).ifEmpty { detail.children.take(3) })
                        .filter { it.remoteSources.size <= 1 }
                    if (toPrefetch.isNotEmpty()) {
                        viewModelScope.launch {
                            for (episode in toPrefetch) {
                                try {
                                    enrichMediaItemUseCase(episode)
                                } catch (e: Exception) {
                                    Timber.d(e, "SeasonDetail: Prefetch enrichment failed for ${episode.title}")
                                }
                            }
                            Timber.d("SCREEN [SeasonDetail]: Prefetch enrichment done for ${toPrefetch.size} episodes")
                        }
                    }
                },
                onFailure = { error ->
                    val duration = System.currentTimeMillis() - startTime
                    Timber.e("SCREEN [SeasonDetail] FAILED: duration=${duration}ms error=${error.message}")
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                },
            )
        }
    }

sealed interface SeasonDetailNavigationEvent {
    data class NavigateToPlayer(val ratingKey: String, val serverId: String, val startOffset: Long = 0L) : SeasonDetailNavigationEvent

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
