package com.chakir.plexhubtv.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.domain.usecase.GetMediaDetailUseCase
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

/**
 * ViewModel gérant les détails d'un média.
 * Charge les métadonnées, gère les favoris, le statut vu/non-vu, et initie la lecture.
 * Gère une logique complexe de "Smart Start" pour reprendre la lecture ou lancer le prochain épisode.
 */
@HiltViewModel
class MediaDetailViewModel
    @Inject
    constructor(
        private val getMediaDetailUseCase: GetMediaDetailUseCase,
        private val toggleWatchStatusUseCase: ToggleWatchStatusUseCase,
        private val getNextEpisodeUseCase: com.chakir.plexhubtv.domain.usecase.GetNextEpisodeUseCase,
        private val getPlayQueueUseCase: com.chakir.plexhubtv.domain.usecase.GetPlayQueueUseCase,
        private val playbackManager: com.chakir.plexhubtv.domain.service.PlaybackManager,
        private val toggleFavoriteUseCase: com.chakir.plexhubtv.domain.usecase.ToggleFavoriteUseCase,
        private val isFavoriteUseCase: com.chakir.plexhubtv.domain.usecase.IsFavoriteUseCase,
        private val enrichMediaItemUseCase: com.chakir.plexhubtv.domain.usecase.EnrichMediaItemUseCase,
        private val getSimilarMediaUseCase: com.chakir.plexhubtv.domain.usecase.GetSimilarMediaUseCase,
        private val getMediaCollectionsUseCase: com.chakir.plexhubtv.domain.usecase.GetMediaCollectionsUseCase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val ratingKey: String = checkNotNull(savedStateHandle["ratingKey"])
        private val serverId: String = checkNotNull(savedStateHandle["serverId"])

        private val _uiState = MutableStateFlow(MediaDetailUiState(isLoading = true))
        val uiState: StateFlow<MediaDetailUiState> = _uiState.asStateFlow()

        private val _navigationEvents = Channel<MediaDetailNavigationEvent>()
        val navigationEvents = _navigationEvents.receiveAsFlow()

        init {
            loadDetail()
            checkFavoriteStatus()
        }

        private fun checkFavoriteStatus() {
            viewModelScope.launch {
                isFavoriteUseCase(ratingKey, serverId).collect { isFav ->
                    val current = _uiState.value.media
                    if (current != null) {
                        _uiState.update { it.copy(media = current.copy(isFavorite = isFav)) }
                    }
                }
            }
        }

        fun onEvent(event: MediaDetailEvent) {
            Timber.d("ACTION [Detail] Event=${event.javaClass.simpleName}")
            when (event) {
                is MediaDetailEvent.PlayClicked -> {
                    val media = _uiState.value.media ?: return
                    viewModelScope.launch {
                        try {
                            // 1. Resolve Smart Start Element
                            val resolvedItem = getNextEpisodeUseCase(media).getOrNull()

                            val startItem =
                                if (resolvedItem != null) {
                                    resolvedItem
                                } else {
                                    if (media.type == MediaType.Movie || media.type == MediaType.Episode) {
                                        media
                                    } else {
                                        _uiState.update { it.copy(error = "No playable episode found.") }
                                        return@launch
                                    }
                                }

                            // 2. CHECK: If we are playing the MAIN media (Movie) and we already have sources enriched, reuse them.
                            // Optimization: Avoid re-running EnrichMediaItemUseCase if loadAvailableServers already did it.
                            val enrichedItem =
                                if (startItem.ratingKey == media.ratingKey && media.remoteSources.size > 1) {
                                    media // Already enriched by background job
                                } else {
                                    // Either a different item (Next Episode) or background job not finished yet.
                                    enrichMediaItemUseCase(startItem)
                                }

                            _uiState.update { it.copy(selectedPlaybackItem = enrichedItem) }

                            // 3. Check for multiple sources on the RESOLVED item
                            if (enrichedItem.remoteSources.size > 1) {
                                _uiState.update { it.copy(showSourceSelection = true) }
                            } else {
                                // Single source or no remoteSources (fallback to direct)
                                playItem(enrichedItem)
                            }
                        } catch (e: Exception) {
                            _uiState.update { it.copy(error = "Playback error: ${e.message}") }
                        }
                    }
                }
                is MediaDetailEvent.OpenSeason -> {
                    viewModelScope.launch {
                        _navigationEvents.send(MediaDetailNavigationEvent.NavigateToSeason(event.season.ratingKey, event.season.serverId))
                    }
                }
                is MediaDetailEvent.OpenMediaDetail -> {
                    viewModelScope.launch {
                        _navigationEvents.send(
                            MediaDetailNavigationEvent.NavigateToMediaDetail(event.media.ratingKey, event.media.serverId),
                        )
                    }
                }
                is MediaDetailEvent.Back -> {
                    viewModelScope.launch { _navigationEvents.send(MediaDetailNavigationEvent.NavigateBack) }
                }
                is MediaDetailEvent.ToggleWatchStatus -> {
                    val media = _uiState.value.media ?: return
                    val newStatus = !media.isWatched
                    viewModelScope.launch {
                        // Optimistic update
                        _uiState.update { it.copy(media = media.copy(isWatched = newStatus)) }
                        toggleWatchStatusUseCase(media, newStatus)
                            .onFailure {
                                // Revert on failure
                                _uiState.update { it.copy(media = media.copy(isWatched = !newStatus)) }
                            }
                    }
                }
                is MediaDetailEvent.DownloadClicked -> {
                    // TODO: Implement Download logic
                }
                is MediaDetailEvent.ToggleFavorite -> {
                    val media = _uiState.value.media ?: return
                    viewModelScope.launch {
                        toggleFavoriteUseCase(media)
                        // UI update handled by flow collection
                    }
                }
                is MediaDetailEvent.ShowSourceSelection -> {
                    // If manual click on source button, use main media
                    _uiState.update { it.copy(selectedPlaybackItem = _uiState.value.media, showSourceSelection = true) }
                }
                is MediaDetailEvent.DismissSourceSelection -> {
                    _uiState.update { it.copy(showSourceSelection = false, selectedPlaybackItem = null) }
                }
                is MediaDetailEvent.PlaySource -> {
                    _uiState.update { it.copy(showSourceSelection = false) }
                    val startItem = _uiState.value.selectedPlaybackItem ?: _uiState.value.media ?: return
                    viewModelScope.launch {
                        // Start playback with specific server
                        playItem(startItem, event.source.serverId)
                    }
                }
                is MediaDetailEvent.Retry -> {
                    loadDetail()
                    checkFavoriteStatus()
                }
            }
        }

        private suspend fun playItem(
            item: MediaItem,
            forcedServerId: String? = null,
        ) {
            val targetServerId = forcedServerId ?: item.serverId
            val finalItem =
                if (forcedServerId != null && forcedServerId != item.serverId) {
                    // Find the source matching forcedServerId and use its ratingKey
                    val source = item.remoteSources.find { it.serverId == forcedServerId }
                    if (source != null) {
                        // We might need to fetch the full detail for this source?
                        // For now, let's assume ratingKey is enough for PlayerViewModel to load it.
                        item.copy(serverId = source.serverId, ratingKey = source.ratingKey)
                    } else {
                        item
                    }
                } else {
                    item
                }

            // 2. Build Playback Queue
            val queue = getPlayQueueUseCase(finalItem).getOrElse { listOf(finalItem) }

            // 3. Initialize PlaybackManager
            playbackManager.play(finalItem, queue)

            // 4. Navigate
            _navigationEvents.send(MediaDetailNavigationEvent.NavigateToPlayer(finalItem.ratingKey, finalItem.serverId))
        }

        fun onCollectionClicked(
            collectionId: String,
            serverId: String,
        ) {
            viewModelScope.launch {
                _navigationEvents.send(MediaDetailNavigationEvent.NavigateToCollection(collectionId, serverId))
            }
        }

        private fun loadDetail() {
            viewModelScope.launch {
                val startTime = System.currentTimeMillis()
                Timber.d("SCREEN [Detail]: Loading start for $ratingKey on $serverId")
                _uiState.update { it.copy(isLoading = true, error = null) }
                getMediaDetailUseCase(ratingKey, serverId).collect { result ->
                    val duration = System.currentTimeMillis() - startTime
                    result.fold(
                        onSuccess = { detail ->
                            Timber.i(
                                "SCREEN [Detail] SUCCESS: Load Duration=${duration}ms | Title=${detail.item.title} | Seasons=${detail.children.size}",
                            )
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    media = detail.item,
                                    seasons = detail.children,
                                    isEnriching = true, // Start enriching (looking for other servers)
                                )
                            }
                            // Secondary fetch for similar items
                            loadSimilarItems()

                            // Secondary fetch for collection
                            loadCollection()

                            // Secondary fetch for other servers (Available Sources)
                            loadAvailableServers(detail.item)
                        },
                        onFailure = { error ->
                            Timber.e("SCREEN [Detail] FAILED: duration=${duration}ms error=${error.message}")
                            _uiState.update { it.copy(isLoading = false, error = error.message) }
                        },
                    )
                }
            }
        }

        private fun loadAvailableServers(item: MediaItem) {
            viewModelScope.launch {
                try {
                    // Determine remote sources (other servers having this media)
                    val enriched = enrichMediaItemUseCase(item)

                    // Update UI state if new sources found (and if we are still looking at the same media)
                    _uiState.update { currentState ->
                        if (currentState.media?.ratingKey == item.ratingKey) {
                            currentState.copy(media = enriched, isEnriching = false)
                        } else {
                            currentState.copy(isEnriching = false)
                        }
                    }
                } catch (e: Exception) {
                    Timber.w("Failed to enrich media: ${e.message}")
                    _uiState.update { it.copy(isEnriching = false) }
                }
            }
        }

        private fun loadSimilarItems() {
            viewModelScope.launch {
                getSimilarMediaUseCase(ratingKey, serverId).onSuccess { items ->
                    _uiState.update { it.copy(similarItems = items) }
                }
            }
        }

        private fun loadCollection() {
            Timber.d("VM: Loading ALL collections for ratingKey=$ratingKey serverId=$serverId")
            viewModelScope.launch {
                getMediaCollectionsUseCase(ratingKey, serverId).collect { collections ->
                    if (collections.isNotEmpty()) {
                        Timber.d("VM: Received ${collections.size} collection(s)")
                        collections.forEach { col ->
                            Timber.d("   - '${col.title}' (${col.items.size} items, server=${col.serverId.take(8)})")
                        }
                    } else {
                        Timber.d("VM: No collections received for $ratingKey")
                    }
                    _uiState.update { it.copy(collections = collections) }
                }
            }
        }
    }

sealed interface MediaDetailNavigationEvent {
    data class NavigateToPlayer(val ratingKey: String, val serverId: String) : MediaDetailNavigationEvent

    data class NavigateToMediaDetail(val ratingKey: String, val serverId: String) : MediaDetailNavigationEvent

    data class NavigateToSeason(val ratingKey: String, val serverId: String) : MediaDetailNavigationEvent

    data class NavigateToCollection(val collectionId: String, val serverId: String) : MediaDetailNavigationEvent

    data object NavigateBack : MediaDetailNavigationEvent
}
