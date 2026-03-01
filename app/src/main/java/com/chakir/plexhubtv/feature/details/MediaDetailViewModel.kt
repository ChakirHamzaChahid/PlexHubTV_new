package com.chakir.plexhubtv.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.common.safeCollectIn
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.feature.common.BaseViewModel
import com.chakir.plexhubtv.feature.common.launchLoading
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.toAppError
import com.chakir.plexhubtv.domain.usecase.GetMediaDetailUseCase
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
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent

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
        private val preparePlaybackUseCase: com.chakir.plexhubtv.domain.usecase.PreparePlaybackUseCase,
        private val getSimilarMediaUseCase: com.chakir.plexhubtv.domain.usecase.GetSimilarMediaUseCase,
        private val getMediaCollectionsUseCase: com.chakir.plexhubtv.domain.usecase.GetMediaCollectionsUseCase,
        private val getUnifiedSeasonsUseCase: com.chakir.plexhubtv.domain.usecase.GetUnifiedSeasonsUseCase,
        private val performanceTracker: com.chakir.plexhubtv.core.common.PerformanceTracker,
        private val mediaSourceResolver: com.chakir.plexhubtv.data.source.MediaSourceResolver,
        savedStateHandle: SavedStateHandle,
    ) : BaseViewModel() {
        private val ratingKey: String? = savedStateHandle["ratingKey"]
        private val serverId: String? = savedStateHandle["serverId"]

        private val _uiState = MutableStateFlow(MediaDetailUiState(isLoading = true))
        val uiState: StateFlow<MediaDetailUiState> = _uiState.asStateFlow()

        private val _navigationEvents = Channel<MediaDetailNavigationEvent>()
        val navigationEvents = _navigationEvents.receiveAsFlow()

        init {
            if (ratingKey == null || serverId == null) {
                Timber.e("MediaDetailViewModel: missing required navigation args (ratingKey=$ratingKey, serverId=$serverId)")
                _uiState.update { it.copy(isLoading = false, error = "Invalid navigation arguments") }
            } else {
                loadDetail()
                checkFavoriteStatus()
            }
        }

        private var favoriteCheckJob: kotlinx.coroutines.Job? = null

        private fun checkFavoriteStatus(allRatingKeys: List<String>? = null) {
            favoriteCheckJob?.cancel()
            val keys = allRatingKeys ?: listOfNotNull(ratingKey)
            if (keys.isEmpty()) return
            favoriteCheckJob = isFavoriteUseCase.anyOf(keys).safeCollectIn(
                scope = viewModelScope,
                onError = { e ->
                    Timber.e(e, "MediaDetailViewModel: checkFavoriteStatus failed")
                }
            ) { isFav ->
                val current = _uiState.value.media
                if (current != null) {
                    _uiState.update { it.copy(media = current.copy(isFavorite = isFav)) }
                }
            }
        }

        fun onEvent(event: MediaDetailEvent) {
            Timber.d("ACTION [Detail] Event=${event.javaClass.simpleName}")
            when (event) {
                is MediaDetailEvent.PlayClicked -> {
                    val media = _uiState.value.media ?: return
                    Firebase.analytics.logEvent("video_play") {
                        param("media_type", media.type.name)
                        param("title", media.title.take(100))
                        param("server_id", media.serverId)
                    }
                    viewModelScope.launch {
                        val opId = "playback_movie_${media.ratingKey}_${System.currentTimeMillis()}"
                        performanceTracker.startOperation(
                            opId,
                            com.chakir.plexhubtv.core.common.PerfCategory.PLAYBACK,
                            "Movie/Show PlayClicked → Player",
                            mapOf(
                                "title" to media.title,
                                "type" to media.type.name,
                                "ratingKey" to media.ratingKey,
                                "serverId" to media.serverId
                            )
                        )

                        try {
                            // 1. Resolve Smart Start Element
                            val resolvedItem = getNextEpisodeUseCase(media).getOrNull()
                            performanceTracker.addCheckpoint(opId, "Smart Start Resolved", mapOf("resolved" to (resolvedItem != null)))

                            val startItem =
                                if (resolvedItem != null) {
                                    resolvedItem
                                } else {
                                    if (media.type == MediaType.Movie || media.type == MediaType.Episode) {
                                        media
                                    } else {
                                        performanceTracker.endOperation(opId, success = false, errorMessage = "No playable content")
                                        emitError(AppError.Media.NoPlayableContent("No playable episode found for this media."))
                                        return@launch
                                    }
                                }

                            // 2. Prepare playback: enrichment + source determination
                            // If playing the main media and background enrichment already ran, reuse it
                            val itemForPlayback = if (startItem.ratingKey == media.ratingKey && media.remoteSources.size > 1) {
                                performanceTracker.addCheckpoint(opId, "Enrichment (Cache Hit)", mapOf("sources" to media.remoteSources.size))
                                media
                            } else {
                                startItem
                            }

                            val enrichStart = System.currentTimeMillis()
                            val playbackResult = preparePlaybackUseCase(itemForPlayback)
                            val enrichDuration = System.currentTimeMillis() - enrichStart
                            val enrichedItem = when (playbackResult) {
                                is com.chakir.plexhubtv.domain.usecase.PreparePlaybackUseCase.Result.ReadyToPlay -> playbackResult.item
                                is com.chakir.plexhubtv.domain.usecase.PreparePlaybackUseCase.Result.NeedsSourceSelection -> playbackResult.item
                            }
                            performanceTracker.addCheckpoint(opId, "PreparePlayback", mapOf(
                                "duration" to enrichDuration,
                                "sources" to enrichedItem.remoteSources.size,
                                "result" to playbackResult.javaClass.simpleName
                            ))

                            _uiState.update { it.copy(selectedPlaybackItem = enrichedItem) }

                            // 3. Act on result
                            when (playbackResult) {
                                is com.chakir.plexhubtv.domain.usecase.PreparePlaybackUseCase.Result.NeedsSourceSelection -> {
                                    performanceTracker.addCheckpoint(opId, "Source Selection Dialog Shown")
                                    _uiState.update { it.copy(showSourceSelection = true) }
                                    // Will end operation when user selects source (in PlaySource event)
                                }
                                is com.chakir.plexhubtv.domain.usecase.PreparePlaybackUseCase.Result.ReadyToPlay -> {
                                    performanceTracker.addCheckpoint(opId, "Single Source - Direct Play")
                                    playItem(playbackResult.item, opId)
                                }
                            }
                        } catch (e: Exception) {
                            performanceTracker.endOperation(opId, success = false, errorMessage = e.message)
                            emitError(AppError.Playback.InitializationFailed(e.message, e))
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
                    // Download feature not implemented
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
                        // Continue tracking from PlayClicked (find active operation)
                        val opId = performanceTracker.getSummary(com.chakir.plexhubtv.core.common.PerfCategory.PLAYBACK, 1)
                            .firstOrNull()?.id
                        opId?.let { performanceTracker.addCheckpoint(it, "User Selected Source", mapOf("serverId" to event.source.serverId)) }

                        // Start playback with specific server
                        playItem(startItem, opId, event.source.serverId)
                    }
                }
                is MediaDetailEvent.PlayExtra -> {
                    val extra = event.extra
                    val media = _uiState.value.media ?: return
                    viewModelScope.launch {
                        val extraItem = MediaItem(
                            id = "extra_${extra.ratingKey}",
                            ratingKey = extra.ratingKey,
                            serverId = media.serverId,
                            title = extra.title,
                            type = MediaType.Clip,
                            thumbUrl = extra.thumbUrl,
                            durationMs = extra.durationMs,
                            mediaParts = extra.mediaParts,
                            baseUrl = extra.baseUrl,
                            accessToken = extra.accessToken,
                        )
                        playbackManager.play(extraItem, listOf(extraItem))
                        _navigationEvents.send(MediaDetailNavigationEvent.NavigateToPlayer(extraItem.ratingKey, extraItem.serverId))
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
            opId: String? = null,
            forcedServerId: String? = null,
        ) {
            // Direct-stream sources: URL built in PlayerControlViewModel, skip detail fetch
            val targetServerId = forcedServerId ?: item.serverId
            if (!mediaSourceResolver.resolve(targetServerId).needsUrlResolution()) {
                opId?.let {
                    performanceTracker.addCheckpoint(it, "Direct Stream Navigation")
                    performanceTracker.endOperation(it, success = true, additionalMeta = mapOf("finalRatingKey" to item.ratingKey))
                }
                // Still set up PlaybackManager for queue (Next/Previous support)
                val queue = getPlayQueueUseCase(item).getOrElse { listOf(item) }
                playbackManager.play(item, queue)
                _navigationEvents.send(MediaDetailNavigationEvent.NavigateToPlayer(item.ratingKey, targetServerId))
                return
            }

            val finalItem =
                if (forcedServerId != null && forcedServerId != item.serverId) {
                    // Find the source matching forcedServerId to get its ratingKey
                    val source = item.remoteSources.find { it.serverId == forcedServerId }
                    if (source != null) {
                        opId?.let { performanceTracker.addCheckpoint(it, "Server Switch", mapOf("from" to item.serverId, "to" to source.serverId)) }
                        // Fetch full media detail from the target server to get correct
                        // mediaParts, stream IDs, baseUrl, accessToken, and id
                        val detailResult = getMediaDetailUseCase(source.ratingKey, source.serverId).first()
                        detailResult.getOrNull()?.item ?: run {
                            Timber.w("playItem: Failed to fetch detail for ${source.ratingKey} on ${source.serverId}, falling back to shallow copy")
                            item.copy(
                                id = "${source.serverId}:${source.ratingKey}",
                                serverId = source.serverId,
                                ratingKey = source.ratingKey,
                            )
                        }
                    } else {
                        Timber.w("playItem: Source not found for serverId=$forcedServerId in remoteSources")
                        item
                    }
                } else {
                    item
                }

            // 2. Build Playback Queue
            val queueStart = System.currentTimeMillis()
            val queue = getPlayQueueUseCase(finalItem).getOrElse { listOf(finalItem) }
            val queueDuration = System.currentTimeMillis() - queueStart
            opId?.let { performanceTracker.addCheckpoint(it, "Queue Built", mapOf("duration" to queueDuration, "items" to queue.size)) }

            // 3. Initialize PlaybackManager
            playbackManager.play(finalItem, queue)
            opId?.let { performanceTracker.addCheckpoint(it, "PlaybackManager Initialized") }

            // 4. Navigate
            opId?.let {
                performanceTracker.addCheckpoint(it, "Navigation to Player Triggered")
                performanceTracker.endOperation(it, success = true, additionalMeta = mapOf("finalRatingKey" to finalItem.ratingKey))
            }
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
            val rk = ratingKey ?: return
            val sid = serverId ?: return
            val startTime = System.currentTimeMillis()
            Timber.d("SCREEN [Detail]: Loading start for $rk on $sid")
            launchLoading(
                onStart = { _uiState.update { it.copy(isLoading = true, error = null) } },
                block = { getMediaDetailUseCase(rk, sid).first() },
                onSuccess = { detail ->
                    val duration = System.currentTimeMillis() - startTime
                    Timber.i(
                        "SCREEN [Detail] SUCCESS: Load Duration=${duration}ms | Title=${detail.item.title} | Seasons=${detail.children.size}",
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            media = detail.item,
                            seasons = detail.children,
                            isEnriching = true,
                        )
                    }
                    loadSimilarItems()
                    if (detail.item.type == MediaType.Show) {
                        loadUnifiedSeasons(detail.item)
                    }
                    if (mediaSourceResolver.resolve(detail.item.serverId).needsEnrichment()) {
                        loadAvailableServers(detail.item)
                    } else {
                        _uiState.update { it.copy(isEnriching = false) }
                    }
                    loadCollection()
                },
                onFailure = { error ->
                    val duration = System.currentTimeMillis() - startTime
                    Timber.e("SCREEN [Detail] FAILED: duration=${duration}ms error=${error.message}")
                    viewModelScope.launch {
                        emitError(AppError.Media.LoadFailed(error.message, error))
                    }
                    _uiState.update { it.copy(isLoading = false) }
                },
            )
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
                    Timber.d("VM: Enrichment complete. remoteSources count: ${enriched.remoteSources.size}")

                    // Re-check favorite status with all server ratingKeys
                    if (enriched.remoteSources.isNotEmpty()) {
                        val allKeys = enriched.remoteSources.map { it.ratingKey }
                        checkFavoriteStatus(allKeys)
                    }

                    // For shows with remote sources: proactively fetch episodes from remote servers
                    // so that unified seasons can find them in Room
                    if (item.type == MediaType.Show && enriched.remoteSources.size > 1) {
                        prefetchRemoteEpisodes(enriched)
                    }
                } catch (e: Exception) {
                    Timber.w("Failed to enrich media: ${e.message}")
                    _uiState.update { it.copy(isEnriching = false) }
                }
            }
        }

        /**
         * After enrichment finds remote sources for a show, fetch episodes from those servers
         * to populate Room, then re-run unified seasons.
         */
        private fun prefetchRemoteEpisodes(show: MediaItem) {
            viewModelScope.launch {
                val remoteSources = show.remoteSources.filter { it.serverId != show.serverId }
                Timber.d("VM: Prefetching episodes from ${remoteSources.size} remote server(s)")
                for (source in remoteSources) {
                    try {
                        // Fetch show detail → returns seasons as children
                        val showDetail = getMediaDetailUseCase(source.ratingKey, source.serverId).first()
                        val seasons = showDetail.getOrNull()?.children ?: continue
                        // For each season, fetch episodes (triggers Room persistence via Fix 1)
                        for (season in seasons) {
                            getMediaDetailUseCase(season.ratingKey, source.serverId).first()
                        }
                        Timber.d("VM: Prefetched ${seasons.size} seasons from ${source.serverName}")
                    } catch (e: Exception) {
                        Timber.w(e, "VM: Failed to prefetch episodes from ${source.serverName}")
                    }
                }
                // Re-run unified seasons now that remote episodes are in Room
                loadUnifiedSeasons(show)
            }
        }

        private fun loadSimilarItems() {
            val rk = ratingKey ?: return
            val sid = serverId ?: return
            viewModelScope.launch {
                getSimilarMediaUseCase(rk, sid)
                    .onSuccess { items ->
                        Timber.d("VM: Loaded ${items.size} similar items for $ratingKey")
                        _uiState.update { it.copy(similarItems = items) }
                    }
                    .onFailure { error ->
                        Timber.w(error, "VM: Failed to load similar items for $ratingKey")
                    }
            }
        }

        private fun loadUnifiedSeasons(show: MediaItem) {
            viewModelScope.launch {
                val startTime = System.currentTimeMillis()
                getUnifiedSeasonsUseCase(
                    showUnificationId = show.unificationId ?: return@launch,
                    fallbackServerId = show.serverId,
                    fallbackRatingKey = show.ratingKey,
                ).onSuccess { unifiedSeasons ->
                    val duration = System.currentTimeMillis() - startTime
                    Timber.i("VM: Loaded ${unifiedSeasons.size} unified seasons in ${duration}ms")
                    _uiState.update { it.copy(unifiedSeasons = unifiedSeasons) }
                }.onFailure { error ->
                    Timber.w(error, "VM: Failed to load unified seasons")
                }
            }
        }

        private fun loadCollection() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingCollections = true) }
                try {
                    val media = _uiState.value.media ?: run {
                        Timber.w("VM: Cannot load collections - media is null")
                        _uiState.update { it.copy(isLoadingCollections = false) }
                        return@launch
                    }

                    // Collections are BDD-local (fast) — query primary server only
                    val result = getMediaCollectionsUseCase(media.ratingKey, media.serverId).first()
                    Timber.d("VM: Got ${result.size} collection(s) from primary server")

                    _uiState.update { it.copy(collections = result, isLoadingCollections = false) }
                } catch (e: Exception) {
                    Timber.e(e, "VM: Exception loading collections")
                    _uiState.update { it.copy(isLoadingCollections = false) }
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
