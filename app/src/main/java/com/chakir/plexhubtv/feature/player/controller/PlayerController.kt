package com.chakir.plexhubtv.feature.player.controller

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.SubtitleTrack
import com.chakir.plexhubtv.domain.repository.PlaybackRepository
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import com.chakir.plexhubtv.domain.usecase.GetMediaDetailUseCase
import com.chakir.plexhubtv.feature.player.ExoStreamMetadata
import com.chakir.plexhubtv.feature.player.PlayerFactory
import com.chakir.plexhubtv.handler.GlobalCoroutineExceptionHandler
import com.chakir.plexhubtv.core.di.ApplicationScope
import com.chakir.plexhubtv.core.di.DefaultDispatcher
import com.chakir.plexhubtv.core.di.MainDispatcher
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.feature.player.PlayerUiState
import com.chakir.plexhubtv.feature.player.url.TranscodeUrlBuilder
import com.chakir.plexhubtv.core.common.util.FormatUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.google.firebase.crashlytics.FirebaseCrashlytics

@Singleton
@OptIn(UnstableApi::class)
class PlayerController @Inject constructor(
    private val application: Application,
    private val playerFactory: PlayerFactory,
    private val getMediaDetailUseCase: GetMediaDetailUseCase,
    private val playbackRepository: PlaybackRepository,
    private val playbackManager: com.chakir.plexhubtv.domain.service.PlaybackManager,
    private val settingsRepository: SettingsRepository,
    private val chapterMarkerManager: ChapterMarkerManager,
    private val playerTrackController: PlayerTrackController,
    private val playerScrobbler: PlayerScrobbler,
    private val playerStatsTracker: PlayerStatsTracker,
    private val transcodeUrlBuilder: TranscodeUrlBuilder,
    private val performanceTracker: com.chakir.plexhubtv.core.common.PerformanceTracker,
    private val connectionManager: ConnectionManager,
    private val mediaSourceResolver: com.chakir.plexhubtv.data.source.MediaSourceResolver,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    private val globalHandler: GlobalCoroutineExceptionHandler,
) {
    // S-04: Child of applicationScope for structured concurrency — cancelled/recreated per session
    private var sessionJob = SupervisorJob(applicationScope.coroutineContext[Job])
    private val scope: CoroutineScope
        get() = CoroutineScope(sessionJob + mainDispatcher + globalHandler)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    var player: ExoPlayer? = null
        private set
    var mpvPlayer: com.chakir.plexhubtv.feature.player.mpv.MpvPlayer? = null
        private set

    private var positionTrackerJob: Job? = null
    private var isMpvMode = false
    private var isDirectPlay = false
    /** PLY-19: Prevents resume toast from re-appearing on quality/track changes */
    private var hasShownResumeToast = false

    private var ratingKey: String? = null
    private var serverId: String? = null
    private var directUrl: String? = null
    private var startOffset: Long = 0L

    fun initialize(startRatingKey: String?, startServerId: String?, startDirectUrl: String?, offset: Long) {
        this.ratingKey = startRatingKey
        this.serverId = startServerId
        this.directUrl = startDirectUrl
        this.startOffset = offset

        // Set Crashlytics context for crash diagnostics
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("player_engine", "ExoPlayer")
            setCustomKey("media_rating_key", startRatingKey ?: "unknown")
            setCustomKey("server_id", startServerId ?: "unknown")
            setCustomKey("is_direct_url", (startDirectUrl != null).toString())
        }

        val isRelay = isLikelyRelay(startServerId, startDirectUrl)
        initializePlayer(application, isRelay)

        playerScrobbler.start(
            scope = scope,
            currentItemProvider = { _uiState.value.currentItem },
            isPlayingProvider = { _uiState.value.isPlaying },
            currentPositionProvider = { _uiState.value.currentPosition },
            durationProvider = { _uiState.value.duration }
        )

        playerStatsTracker.startTracking(
            scope = scope,
            isMpvMode = { isMpvMode },
            exoMetadata = {
                player?.let { p ->
                    ExoStreamMetadata(p.videoFormat, p.videoSize)
                }
            },
            exoPosition = { player?.currentPosition ?: 0L },
            exoBuffered = { player?.bufferedPosition ?: 0L },
            mpvStats = { mpvPlayer?.getStats() }
        )

        scope.launch {
            playerScrobbler.showAutoNextPopup.collect { show ->
                _uiState.update { it.copy(showAutoNextPopup = show) }
            }
        }

        val initUrl = directUrl
        val initRKey = ratingKey
        val initSId = serverId
        if (initUrl != null) {
            playDirectUrl(initUrl)
        } else if (initRKey != null && initSId != null && mediaSourceResolver.resolve(initSId).needsUrlResolution()) {
            // Direct-stream sources: URL is built asynchronously by PlayerControlViewModel → playDirectStream()
            loadMedia(initRKey, initSId)
        }
        startPositionTracking()
    }

    fun release() {
        positionTrackerJob?.cancel()
        positionTrackerJob = null

        playerStatsTracker.stopTracking()
        playerScrobbler.stop()

        player?.release()
        player = null
        mpvPlayer?.release()
        mpvPlayer = null

        // Cancel ALL coroutines (position tracker, scrobbler, stats, collectors)
        sessionJob.cancel()
        // Recreate a fresh child Job for the next initialize() cycle
        sessionJob = SupervisorJob(applicationScope.coroutineContext[Job])

        // Reset state so a fresh session doesn't inherit stale values
        _uiState.value = PlayerUiState()
        isMpvMode = false
        isDirectPlay = false
        hasShownResumeToast = false
    }

    /**
     * Play a direct stream URL with optional metadata from a real MediaItem.
     * Used for Xtream IPTV content and plain URL streams.
     *
     * @param url The direct stream URL (http/https/rtsp/rtp)
     * @param item Optional MediaItem for metadata (title, thumbnail, etc.).
     *             If null, a placeholder item is created.
     */
    fun playDirectStream(url: String, item: MediaItem? = null) {
        this.directUrl = url
        if (item != null) {
            this.ratingKey = item.ratingKey
            this.serverId = item.serverId
        }

        playDirectUrlInternal(url, item)
    }

    private fun playDirectUrl(url: String) {
        playDirectUrlInternal(url, null)
    }

    private fun playDirectUrlInternal(url: String, item: MediaItem?) {
        // Defense-in-depth: only allow safe streaming schemes
        val scheme = Uri.parse(url).scheme?.lowercase()
        if (scheme == null || scheme !in ALLOWED_DIRECT_SCHEMES) {
            Timber.e("Rejected direct URL with disallowed scheme '$scheme': ${url.take(80)}")
            _uiState.update { it.copy(error = "Invalid stream URL") }
            return
        }

        val mediaItem = item ?: MediaItem(
            id = "iptv-$url",
            ratingKey = "iptv",
            serverId = "iptv",
            title = "Live Stream",
            type = MediaType.Movie,
            mediaParts = emptyList()
        )

        _uiState.update {
            it.copy(
                currentItem = mediaItem,
                isPlaying = true,
                isBuffering = true,
                currentPosition = if (it.currentItem?.id != mediaItem.id) 0L else it.currentPosition,
            )
        }

        scope.launch {
            val streamUri = Uri.parse(url)
            player?.apply {
                val exoItem = ExoMediaItem.Builder()
                    .setUri(streamUri)
                    .setMediaId(mediaItem.ratingKey)
                    .build()

                setMediaItem(exoItem)
                prepare()
                playWhenReady = true
            }
        }
    }

    companion object {
        private val ALLOWED_DIRECT_SCHEMES = setOf("http", "https", "rtsp", "rtp")
        /** PLY-19: Only show resume indicator for positions > 30s to avoid false positives */
        private const val RESUME_THRESHOLD_MS = 30_000L
    }

    /** PLY-19: Dismiss the resume playback indicator */
    fun clearResumeMessage() {
        _uiState.update { it.copy(resumeMessage = null) }
    }

    /**
     * Determines if the connection for this server is likely relay/remote.
     * Uses ConnectionManager's cached URL: private IPs → LAN, everything else → relay.
     */
    private fun isLikelyRelay(serverId: String?, directUrl: String?): Boolean {
        // External URLs (IPTV, etc.) — unknown network, use conservative buffers
        if (directUrl != null) return true
        if (serverId == null) return true

        val cachedUrl = connectionManager.getCachedUrl(serverId) ?: return true
        val host = android.net.Uri.parse(cachedUrl).host ?: return true

        // RFC1918 private IPs → LAN
        return !(host.startsWith("192.168.") ||
                host.startsWith("10.") ||
                host.startsWith("172.") ||
                host == "localhost" ||
                host == "127.0.0.1")
    }

    private fun initializePlayer(context: Application, isRelay: Boolean = false) {
        player = playerFactory.createExoPlayer(context, isRelay).apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                    if (isPlaying) {
                        val opId = "player_load_${_uiState.value.currentItem?.ratingKey}"
                        performanceTracker.addCheckpoint(opId, "🎬 PLAYBACK STARTED (isPlaying=true)")
                        // End the performance tracking operation when playback actually starts
                        performanceTracker.endOperation(
                            opId,
                            success = true,
                            additionalMeta = mapOf(
                                "title" to (_uiState.value.currentItem?.title ?: "unknown"),
                                "position" to _uiState.value.currentPosition,
                                "duration" to _uiState.value.duration
                            )
                        )
                        // Reset retry count on successful playback
                        _uiState.update { it.copy(networkRetryCount = 0, error = null, errorType = com.chakir.plexhubtv.feature.player.PlayerErrorType.None) }
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            _uiState.update { it.copy(isBuffering = true, error = null) }
                            val opId = "player_load_${_uiState.value.currentItem?.ratingKey}"
                            performanceTracker.addCheckpoint(opId, "ExoPlayer STATE_BUFFERING")
                        }
                        Player.STATE_READY -> {
                            val opId = "player_load_${_uiState.value.currentItem?.ratingKey}"
                            performanceTracker.addCheckpoint(opId, "ExoPlayer STATE_READY (Buffered)")
                            _uiState.update {
                                it.copy(
                                    isBuffering = false,
                                    duration = duration.coerceAtLeast(0),
                                    error = null
                                )
                            }
                            player?.let { p ->
                                val (newAudio, newSub) = playerTrackController.syncTracksWithExoPlayer(
                                    exoPlayer = p,
                                    currentAudioTracks = _uiState.value.audioTracks,
                                    currentSubtitleTracks = _uiState.value.subtitleTracks,
                                    currentSelectedAudio = _uiState.value.selectedAudio,
                                    currentSelectedSubtitle = _uiState.value.selectedSubtitle
                                )
                                _uiState.update { it.copy(selectedAudio = newAudio, selectedSubtitle = newSub) }
                            }
                        }
                        else -> {}
                    }
                }

                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    player?.let { p ->
                        val (newAudio, newSub) = playerTrackController.syncTracksWithExoPlayer(
                            exoPlayer = p,
                            currentAudioTracks = _uiState.value.audioTracks,
                            currentSubtitleTracks = _uiState.value.subtitleTracks,
                            currentSelectedAudio = _uiState.value.selectedAudio,
                            currentSelectedSubtitle = _uiState.value.selectedSubtitle
                        )
                        _uiState.update { it.copy(selectedAudio = newAudio, selectedSubtitle = newSub) }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val isFormatError = when (error.errorCode) {
                        PlaybackException.ERROR_CODE_DECODING_FAILED,
                        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> true
                        else -> error.cause?.message?.contains("MediaCodec") == true
                    }

                    val isNetError = isNetworkError(error)

                    if (isFormatError && !isMpvMode) {
                        Timber.d("PlayerController: Codec error detected, switching to MPV")
                        _uiState.update { it.copy(errorType = com.chakir.plexhubtv.feature.player.PlayerErrorType.Codec) }
                        switchToMpv()
                    } else if (isNetError) {
                        Timber.e(error, "PlayerController: Network error detected")
                        handleNetworkError(error)
                    } else {
                        Timber.e(error, "PlayerController: Generic player error")
                        _uiState.update {
                            it.copy(
                                error = error.localizedMessage,
                                errorType = com.chakir.plexhubtv.feature.player.PlayerErrorType.Generic
                            )
                        }
                    }
                }
            })
        }
    }
    
    fun switchToMpv() {
        Timber.d("SCREEN [Player] switchToMpv() called")
        if (isMpvMode) return
        isMpvMode = true
        FirebaseCrashlytics.getInstance().setCustomKey("player_engine", "MPV")

        player?.release()
        player = null

        mpvPlayer = playerFactory.createMpvPlayer(application, scope)

        // S-05: Observe MPV init/runtime errors to avoid stuck screen
        scope.launch {
            mpvPlayer?.error?.filterNotNull()?.collect { errorMsg ->
                Timber.e("PlayerController: MPV error: $errorMsg")
                _uiState.update {
                    it.copy(
                        error = errorMsg,
                        errorType = com.chakir.plexhubtv.feature.player.PlayerErrorType.Generic,
                        isBuffering = false
                    )
                }
            }
        }

        _uiState.update {
            it.copy(
                isMpvMode = true,
                error = null,
                errorType = com.chakir.plexhubtv.feature.player.PlayerErrorType.None,
                networkRetryCount = 0,
                isBuffering = true
            )
        }

        val mpvUrl = directUrl
        val mpvRKey = ratingKey
        val mpvSId = serverId
        if (mpvUrl != null) {
            scope.launch { mpvPlayer?.play(mpvUrl) }
        } else if (mpvRKey != null && mpvSId != null) {
            loadMedia(mpvRKey, mpvSId)
        }
    }

    /**
     * Détecte si une erreur ExoPlayer est d'origine réseau
     */
    private fun isNetworkError(error: PlaybackException): Boolean {
        val cause = error.cause
        return when {
            // Erreurs réseau standard
            cause is java.net.UnknownHostException -> true
            cause is java.net.SocketTimeoutException -> true
            cause is java.net.ConnectException -> true
            // ExoPlayer HttpDataSource errors
            cause is androidx.media3.datasource.HttpDataSource.HttpDataSourceException -> true
            // Check error code
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> true
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> true
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> true
            // Check message for network-related keywords
            error.message?.contains("UnknownHost", ignoreCase = true) == true -> true
            error.message?.contains("timeout", ignoreCase = true) == true -> true
            error.message?.contains("network", ignoreCase = true) == true -> true
            else -> false
        }
    }

    /**
     * Gère une erreur réseau : affiche l'erreur et permet le retry
     */
    private fun handleNetworkError(error: PlaybackException) {
        val currentRetryCount = _uiState.value.networkRetryCount
        val maxRetries = 3

        Timber.w("Network error (retry $currentRetryCount/$maxRetries): ${error.message}")

        _uiState.update {
            it.copy(
                error = error.localizedMessage ?: "Network error during playback",
                errorType = com.chakir.plexhubtv.feature.player.PlayerErrorType.Network,
                networkRetryCount = currentRetryCount,
                isBuffering = false,
                isPlaying = false
            )
        }

        // Si on a dépassé le nombre max de retry et qu'on n'est pas déjà en MPV, proposer MPV
        if (currentRetryCount >= maxRetries && !isMpvMode) {
            Timber.w("Max network retries reached, suggesting MPV fallback")
            // L'UI affichera l'option de basculer vers MPV
        }
    }

    /**
     * Retry playback après une erreur réseau
     */
    fun retryPlayback() {
        val currentRetryCount = _uiState.value.networkRetryCount
        Timber.d("Retrying playback (attempt ${currentRetryCount + 1})")

        // Reset error state
        _uiState.update {
            it.copy(
                error = null,
                errorType = com.chakir.plexhubtv.feature.player.PlayerErrorType.None,
                networkRetryCount = currentRetryCount + 1,
                isBuffering = true
            )
        }

        // Reload current media
        val rKey = ratingKey
        val sId = serverId
        val dUrl = directUrl

        if (dUrl != null) {
            playDirectUrl(dUrl)
        } else if (rKey != null && sId != null) {
            loadMedia(rKey, sId)
        } else {
            Timber.e("Cannot retry: no media loaded")
            _uiState.update {
                it.copy(
                    error = "Cannot retry: no media information",
                    errorType = com.chakir.plexhubtv.feature.player.PlayerErrorType.Generic,
                    isBuffering = false
                )
            }
        }
    }

    fun loadMedia(rKey: String, sId: String, bitrateOverride: Int? = null, audioIndex: Int? = null, subtitleIndex: Int? = null, audioStreamId: String? = null, subtitleStreamId: String? = null) {
        scope.launch {
            val opId = "player_load_$rKey"
            performanceTracker.startOperation(
                opId,
                com.chakir.plexhubtv.core.common.PerfCategory.PLAYBACK,
                "PlayerController.loadMedia → Stream Ready",
                mapOf("ratingKey" to rKey, "serverId" to sId)
            )

            // Parallel settings queries (P1.3)
            val settingsStart = System.currentTimeMillis()
            val (qualityPref, engine, clientId) = coroutineScope {
                val q = async { settingsRepository.getVideoQuality().first() }
                val e = async { settingsRepository.playerEngine.first() }
                val c = async { settingsRepository.clientId.first() ?: "PlexHubTV-Client" }
                Triple(q.await(), e.await(), c.await())
            }
            val settingsDuration = System.currentTimeMillis() - settingsStart
            performanceTracker.addCheckpoint(opId, "Settings Loaded (Parallel)", mapOf("duration" to settingsDuration))

            val bitrate = bitrateOverride ?: when {
                qualityPref.startsWith("20") -> 20000
                qualityPref.startsWith("12") -> 12000
                qualityPref.startsWith("8") -> 8000
                qualityPref.startsWith("4") -> 4000
                qualityPref.startsWith("3") -> 3000
                else -> 200000
            }

            val qualities = _uiState.value.availableQualities
            val qualityObj = qualities.find { it.bitrate == bitrate }
                ?: (if (bitrate >= 200000) qualities.firstOrNull() else qualities.lastOrNull())
                ?: return@launch

            _uiState.update { it.copy(selectedQuality = qualityObj) }

            if (engine == "MPV" && !isMpvMode) {
                switchToMpv()
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null) }

            // P1.1: Try PlaybackManager cache first to avoid double fetch
            val cachedMedia = playbackManager.currentMedia.value
            val mediaFetchStart = System.currentTimeMillis()
            val media: MediaItem? = if (cachedMedia != null
                && cachedMedia.ratingKey == rKey
                && cachedMedia.serverId == sId
                && cachedMedia.mediaParts.isNotEmpty()
            ) {
                val cacheDuration = System.currentTimeMillis() - mediaFetchStart
                performanceTracker.addCheckpoint(opId, "Media Detail (Cache Hit)", mapOf("duration" to cacheDuration))
                Timber.d("PlayerController: Using cached media from PlaybackManager for $rKey")
                cachedMedia
            } else {
                // P1.2: Use .first() instead of .collect to avoid infinite flow collection
                val result = getMediaDetailUseCase(rKey, sId).first()
                val fetchDuration = System.currentTimeMillis() - mediaFetchStart
                performanceTracker.addCheckpoint(
                    opId,
                    "Media Detail (Network Fetch)",
                    mapOf("duration" to fetchDuration, "success" to (result.isSuccess))
                )
                result.getOrNull()?.item
            }

            if (media == null) {
                performanceTracker.endOperation(opId, success = false, errorMessage = "Unable to load media")
                _uiState.update { it.copy(isLoading = false, error = "Unable to load media") }
                return@launch
            }

            performanceTracker.addCheckpoint(opId, "Media Loaded", mapOf("title" to media.title, "parts" to media.mediaParts.size))

            playerScrobbler.resetAutoNext()
            val next = playbackManager.getNextMedia()

            val tracksStart = System.currentTimeMillis()
            val (audios, subtitles) = playerTrackController.populateTracks(media)
            val tracksDuration = System.currentTimeMillis() - tracksStart
            performanceTracker.addCheckpoint(opId, "Tracks Populated", mapOf("duration" to tracksDuration, "audioTracks" to audios.size, "subtitles" to subtitles.size))

            _uiState.update {
                it.copy(
                    currentItem = media,
                    nextItem = next,
                    showAutoNextPopup = false,
                    currentPosition = if (it.currentItem?.id != media.id) 0L else it.currentPosition,
                    audioTracks = audios,
                    subtitleTracks = subtitles,
                    selectedAudio = audios.find { t -> t.isSelected },
                    selectedSubtitle = subtitles.find { t -> t.isSelected } ?: SubtitleTrack.OFF
                )
            }

            chapterMarkerManager.setChapters(media.chapters)
            chapterMarkerManager.setMarkers(media.markers)

            val part = media.mediaParts.firstOrNull()

            // Extras (Clip) are CDN-hosted — must go through server transcode/proxy, not direct play
            isDirectPlay = bitrate >= 200000 && part?.key != null && media.type != com.chakir.plexhubtv.core.model.MediaType.Clip

            val (finalAudioStreamId, finalSubtitleStreamId) = playerTrackController.resolveInitialTracks(
                 rKey, sId, part, audioStreamId, subtitleStreamId
            )

            val aIndex = part?.streams?.filterIsInstance<com.chakir.plexhubtv.core.model.AudioStream>()?.find { it.id == finalAudioStreamId }?.index
            val sIndex = part?.streams?.filterIsInstance<com.chakir.plexhubtv.core.model.SubtitleStream>()?.find { it.id == finalSubtitleStreamId }?.index

            val resolvedAudio = _uiState.value.audioTracks.find { it.streamId == finalAudioStreamId }
                ?: _uiState.value.audioTracks.firstOrNull()
            val resolvedSubtitle = _uiState.value.subtitleTracks.find { it.streamId == finalSubtitleStreamId }
                ?: SubtitleTrack.OFF

            _uiState.update { it.copy(selectedAudio = resolvedAudio, selectedSubtitle = resolvedSubtitle) }

            val urlBuildStart = System.currentTimeMillis()
            val streamUri = if (part != null) {
                 transcodeUrlBuilder.buildUrl(
                     media, part, rKey, isDirectPlay, bitrate, clientId,
                     finalAudioStreamId, finalSubtitleStreamId, aIndex, sIndex
                 )
            } else null
            val urlBuildDuration = System.currentTimeMillis() - urlBuildStart
            performanceTracker.addCheckpoint(
                opId,
                "Stream URL Built",
                mapOf("duration" to urlBuildDuration, "directPlay" to isDirectPlay, "bitrate" to bitrate)
            )

            if (streamUri == null) {
                performanceTracker.endOperation(opId, success = false, errorMessage = "Invalid stream URL")
                _uiState.update { it.copy(isLoading = false, error = "Unable to play media: Invalid URL") }
                return@launch
            }

            Timber.d("PlayerController: Track resolution → audio=${resolvedAudio?.language}(${finalAudioStreamId}), sub=${resolvedSubtitle?.language}(${finalSubtitleStreamId}), directPlay=$isDirectPlay")

            if (isMpvMode) {
                performanceTracker.addCheckpoint(opId, "MPV Player Mode")
                mpvPlayer?.play(streamUri.toString())

                val currentPos = _uiState.value.currentPosition
                val seekTarget = when {
                    currentPos > 0 -> currentPos
                    startOffset > 0 -> startOffset
                    media.viewOffset > 0 -> media.viewOffset
                    else -> 0L
                }
                if (seekTarget > 0) {
                    mpvPlayer?.seekTo(seekTarget)
                    performanceTracker.addCheckpoint(opId, "MPV Seek Applied", mapOf("position" to seekTarget))
                    // PLY-19: Show resume indicator for significant positions (once per session)
                    if (seekTarget > RESUME_THRESHOLD_MS && !hasShownResumeToast) {
                        hasShownResumeToast = true
                        _uiState.update { it.copy(resumeMessage = FormatUtils.formatDurationTimestamp(seekTarget)) }
                    }
                }

                // Track/Sub selection for MPV in Direct Play
                if (isDirectPlay) {
                    if (resolvedAudio != null) {
                        val index = _uiState.value.audioTracks.indexOf(resolvedAudio) + 1
                        if (index > 0) {
                            mpvPlayer?.setAudioId(index.toString())
                            Timber.d("PlayerController: MPV audio track set to index $index (${resolvedAudio.language})")
                        }
                    }
                    if (resolvedSubtitle != null && resolvedSubtitle.id != "no") {
                        val validTracks = _uiState.value.subtitleTracks.filter { it.id != "no" }
                        val subIndex = validTracks.indexOf(resolvedSubtitle) + 1
                        if (subIndex > 0) {
                            mpvPlayer?.setSubtitleId(subIndex.toString())
                            Timber.d("PlayerController: MPV subtitle track set to index $subIndex (${resolvedSubtitle.language})")
                        }
                    } else {
                        mpvPlayer?.setSubtitleId("no")
                        Timber.d("PlayerController: MPV subtitles disabled")
                    }
                }

                performanceTracker.endOperation(opId, success = true, additionalMeta = mapOf("engine" to "MPV", "streamUrl" to streamUri.toString()))
            } else {
                performanceTracker.addCheckpoint(opId, "ExoPlayer Mode")

                // Build subtitle configurations for external SRT/ASS sideloading in Direct Play
                val subtitleConfigs = if (isDirectPlay && part != null) {
                    val baseUrl = media.baseUrl?.trimEnd('/') ?: ""
                    val token = media.accessToken ?: ""
                    part.streams.filterIsInstance<com.chakir.plexhubtv.core.model.SubtitleStream>()
                        .filter { it.isExternal && !it.key.isNullOrEmpty() }
                        .mapNotNull { stream ->
                            // S-12: Validate subtitle URI scheme before passing to ExoPlayer
                            val subtitleUri = android.net.Uri.parse("$baseUrl${stream.key}?X-Plex-Token=$token")
                            val scheme = subtitleUri.scheme?.lowercase()
                            if (scheme == null || scheme !in setOf("http", "https")) {
                                Timber.w("PlayerController: Rejected subtitle URI with scheme '$scheme'")
                                return@mapNotNull null
                            }
                            val mimeType = when (stream.codec?.lowercase()) {
                                "srt" -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                                "ass", "ssa" -> androidx.media3.common.MimeTypes.TEXT_SSA
                                "vtt", "webvtt" -> androidx.media3.common.MimeTypes.TEXT_VTT
                                else -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP // default
                            }
                            Timber.d("PlayerController: Sideloading external subtitle '${stream.displayTitle}' (${stream.language}, ${stream.codec}) from ${stream.key}")
                            androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                                .setMimeType(mimeType)
                                .setLanguage(stream.languageCode ?: stream.language)
                                .setLabel(stream.displayTitle ?: stream.title ?: stream.language ?: "Subtitle")
                                .setId(stream.id)
                                .build()
                        }
                } else emptyList()

                val mediaItemStart = System.currentTimeMillis()
                val mediaItem = playerFactory.createMediaItem(streamUri, rKey, !isDirectPlay, subtitleConfigs)
                val mediaItemDuration = System.currentTimeMillis() - mediaItemStart
                performanceTracker.addCheckpoint(opId, "ExoPlayer MediaItem Created", mapOf("duration" to mediaItemDuration))

                player?.apply {
                    // Apply track preferences BEFORE prepare() so ExoPlayer selects the right tracks
                    if (isDirectPlay) {
                        val builder = trackSelectionParameters.buildUpon()
                        resolvedAudio?.language?.let { lang ->
                            builder.setPreferredAudioLanguage(lang)
                            Timber.d("PlayerController: ExoPlayer preferred audio language set to '$lang'")
                        }
                        if (resolvedSubtitle != null && resolvedSubtitle.id != "no") {
                            builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                            resolvedSubtitle.language?.let { lang ->
                                builder.setPreferredTextLanguage(lang)
                                Timber.d("PlayerController: ExoPlayer preferred text language set to '$lang'")
                            }
                        } else {
                            builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                            Timber.d("PlayerController: ExoPlayer subtitles disabled")
                        }
                        trackSelectionParameters = builder.build()
                    }

                    val prepareStart = System.currentTimeMillis()
                    setMediaItem(mediaItem)
                    prepare()
                    val prepareDuration = System.currentTimeMillis() - prepareStart
                    performanceTracker.addCheckpoint(opId, "ExoPlayer Prepared", mapOf("duration" to prepareDuration))

                    val currentPos = _uiState.value.currentPosition
                    val seekTarget = when {
                        currentPos > 0 -> currentPos
                        startOffset > 0 -> startOffset
                        media.viewOffset > 0 -> media.viewOffset
                        else -> 0L
                    }
                    if (seekTarget > 0) {
                        seekTo(seekTarget)
                        performanceTracker.addCheckpoint(opId, "ExoPlayer Seek Applied", mapOf("position" to seekTarget))
                        // PLY-19: Show resume indicator for significant positions
                        if (seekTarget > RESUME_THRESHOLD_MS) {
                            _uiState.update { it.copy(resumeMessage = FormatUtils.formatDurationTimestamp(seekTarget)) }
                        }
                    }

                    playWhenReady = true
                    performanceTracker.addCheckpoint(opId, "ExoPlayer PlayWhenReady=true")
                    // Will end operation when buffering complete (see Player.Listener below)
                }
            }
        }
    }
    
    private fun startPositionTracking() {
        positionTrackerJob = scope.launch {
            while (isActive) {
                if (isMpvMode) {
                    val mpv = mpvPlayer
                    if (mpv != null) {
                        val pos = mpv.position.value
                        val dur = mpv.duration.value
                        _uiState.update { 
                            it.copy(
                                currentPosition = pos,
                                duration = dur,
                                isPlaying = mpv.isPlaying.value,
                                isBuffering = mpv.isBuffering.value
                            )
                        }
                        playerScrobbler.checkAutoNext(
                            position = pos, 
                            duration = dur,
                            hasNextItem = _uiState.value.nextItem != null,
                            isPopupAlreadyShown = _uiState.value.showAutoNextPopup
                        )
                    }
                } else {
                    player?.let { p ->
                        if (p.isPlaying) {
                            val pos = p.currentPosition
                            val dur = p.duration.coerceAtLeast(0)
                            _uiState.update { 
                                it.copy(
                                    currentPosition = pos,
                                    bufferedPosition = p.bufferedPosition,
                                    duration = dur
                                ) 
                            }
                            chapterMarkerManager.updatePlaybackPosition(pos)
                            playerScrobbler.checkAutoNext(
                                position = pos, 
                                duration = dur,
                                hasNextItem = _uiState.value.nextItem != null,
                                isPopupAlreadyShown = _uiState.value.showAutoNextPopup
                            )
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    fun play() {
        if (isMpvMode) mpvPlayer?.resume() else player?.play()
    }

    fun pause() {
        if (isMpvMode) mpvPlayer?.pause() else player?.pause()
    }

    fun seekTo(position: Long) {
        if (isMpvMode) mpvPlayer?.seekTo(position) else player?.seekTo(position)
    }
    
    fun setPlaybackSpeed(speed: Float) {
        mpvPlayer?.setSpeed(speed.toDouble())
        player?.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speed))
        _uiState.update { it.copy(playbackSpeed = speed) }
    }
    
    fun setAudioDelay(delay: Long) {
        mpvPlayer?.setAudioDelay(delay)
        _uiState.update { it.copy(audioDelay = delay) }
    }
    
    fun setSubtitleDelay(delay: Long) {
        mpvPlayer?.setSubtitleDelay(delay)
        _uiState.update { it.copy(subtitleDelay = delay) }
    }
    
    fun updateState(update: (PlayerUiState) -> PlayerUiState) {
        _uiState.update(update)
    }
    
    fun selectAudioTrack(track: com.chakir.plexhubtv.core.model.AudioTrack) {
        val current = _uiState.value.currentItem ?: return
        // Immediately update UI state so the dialog reflects the selection
        _uiState.update { it.copy(selectedAudio = track) }
        playerTrackController.selectAudioTrack(
            track = track,
            currentItem = current,
            currentSubtitleStreamId = _uiState.value.selectedSubtitle?.streamId,
            scope = scope,
            exoPlayer = player,
            mpvPlayer = mpvPlayer,
            isMpvMode = isMpvMode,
            isDirectPlay = isDirectPlay,
            audioTracksInUi = _uiState.value.audioTracks,
            onReloadRequired = { aId, sId ->
                 loadMedia(current.ratingKey, current.serverId, _uiState.value.selectedQuality.bitrate, track.index, _uiState.value.selectedSubtitle?.index, aId, sId)
            }
        )
    }

    fun selectSubtitleTrack(track: SubtitleTrack) {
        val current = _uiState.value.currentItem ?: return
        // Immediately update UI state so the dialog reflects the selection
        _uiState.update { it.copy(selectedSubtitle = track) }
        playerTrackController.selectSubtitleTrack(
            track = track,
            currentItem = current,
            currentAudioStreamId = _uiState.value.selectedAudio?.streamId,
            scope = scope,
            exoPlayer = player,
            mpvPlayer = mpvPlayer,
            isMpvMode = isMpvMode,
            isDirectPlay = isDirectPlay,
            subtitleTracksInUi = _uiState.value.subtitleTracks,
            onReloadRequired = { aId, sId ->
                 loadMedia(current.ratingKey, current.serverId, _uiState.value.selectedQuality.bitrate, _uiState.value.selectedAudio?.index, track.index, aId, sId)
            }
        )
    }
}
