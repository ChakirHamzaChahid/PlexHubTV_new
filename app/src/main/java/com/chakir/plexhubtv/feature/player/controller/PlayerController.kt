package com.chakir.plexhubtv.feature.player.controller

import android.app.Application
import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
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
import com.chakir.plexhubtv.feature.player.url.PlaybackUrlBuilder
import com.chakir.plexhubtv.core.common.util.FormatUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.chakir.plexhubtv.feature.player.mpv.MpvConfig
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
    private val urlBuilders: Set<@JvmSuppressWildcards PlaybackUrlBuilder>,
    private val performanceTracker: com.chakir.plexhubtv.core.common.PerformanceTracker,
    private val connectionManager: ConnectionManager,
    private val mediaSourceResolver: com.chakir.plexhubtv.data.source.MediaSourceResolver,
    private val mediaSessionManager: MediaSessionManager,
    val refreshRateManager: RefreshRateManager,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    private val globalHandler: GlobalCoroutineExceptionHandler,
) {
    // S-04: Child of applicationScope for structured concurrency — cancelled/recreated per session
    private var sessionJob = SupervisorJob(applicationScope.coroutineContext[Job])
    private var scope = CoroutineScope(sessionJob + mainDispatcher + globalHandler)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    @Volatile
    var player: ExoPlayer? = null
        private set
    @Volatile
    var mpvPlayer: com.chakir.plexhubtv.feature.player.mpv.MpvPlayer? = null
        private set

    private var positionTrackerJob: Job? = null
    /** Tracks markers that were already auto-skipped in this session to avoid re-skipping */
    private val autoSkippedMarkers = mutableSetOf<String>()
    @Volatile
    private var isMpvMode = false
    @Volatile
    private var isDirectPlay = false
    /** PLY-19: Prevents resume toast from re-appearing on quality/track changes */
    @Volatile
    private var hasShownResumeToast = false

    @Volatile
    private var ratingKey: String? = null
    @Volatile
    private var serverId: String? = null
    @Volatile
    private var directUrl: String? = null
    @Volatile
    private var startOffset: Long = 0L

    // --- MPV AudioFocus management ---
    // ExoPlayer handles AudioFocus automatically via setAudioAttributes(handleAudioFocus=true).
    // MPV needs manual AudioFocus since it's a standalone player with no Android framework integration.
    private val audioManager = application.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (!isMpvMode) return@OnAudioFocusChangeListener
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Timber.d("PlayerController: AudioFocus LOSS (MPV) → pausing")
                mpvPlayer?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Timber.d("PlayerController: AudioFocus LOSS_TRANSIENT (MPV) → pausing")
                mpvPlayer?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // MPV doesn't support volume ducking — pause instead
                Timber.d("PlayerController: AudioFocus LOSS_TRANSIENT_CAN_DUCK (MPV) → pausing")
                mpvPlayer?.pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Timber.d("PlayerController: AudioFocus GAIN (MPV) → resuming")
                mpvPlayer?.resume()
            }
        }
    }

    private val mpvAudioFocusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AndroidAudioAttributes.Builder()
                .setUsage(AndroidAudioAttributes.USAGE_MEDIA)
                .setContentType(AndroidAudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
        )
        .setOnAudioFocusChangeListener(audioFocusListener)
        .build()

    private fun requestMpvAudioFocus() {
        val result = audioManager.requestAudioFocus(mpvAudioFocusRequest)
        Timber.d("PlayerController: MPV AudioFocus request result=$result")
    }

    private fun abandonMpvAudioFocus() {
        audioManager.abandonAudioFocusRequest(mpvAudioFocusRequest)
        Timber.d("PlayerController: MPV AudioFocus abandoned")
    }

    fun initialize(startRatingKey: String?, startServerId: String?, startDirectUrl: String?, offset: Long) {
        // Safety: if a previous session left a stale player (e.g., rapid navigation), clean up first
        if (player != null || mpvPlayer != null) {
            Timber.w("PlayerController: Releasing stale player before re-init")
            release()
        }

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
            isMpvModeProvider = { isMpvMode },
            exoMetadata = {
                player?.let { p ->
                    val exo = p as? androidx.media3.exoplayer.ExoPlayer
                    ExoStreamMetadata(
                        format = p.videoFormat,
                        videoSize = p.videoSize,
                        audioFormat = exo?.audioFormat,
                    )
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

    /**
     * Switch to next/previous episode by reusing the existing player instance.
     * Resets per-episode state (startOffset, resume toast, auto-next) without recreating ExoPlayer.
     */
    fun playNext(nextRatingKey: String, nextServerId: String) {
        Timber.d("PlayerController.playNext: Switching to rk=$nextRatingKey, sid=$nextServerId (isMpvMode=$isMpvMode, player=${player != null}, mpvPlayer=${mpvPlayer != null})")
        this.ratingKey = nextRatingKey
        this.serverId = nextServerId
        this.startOffset = 0L
        this.directUrl = null
        hasShownResumeToast = false
        autoSkippedMarkers.clear()
        _uiState.update { it.copy(showAutoNextPopup = false, resumeMessage = null) }
        loadMedia(nextRatingKey, nextServerId)
    }

    /**
     * Switch to next/previous episode for direct-stream sources (Xtream/Backend).
     * Resets per-episode state then delegates to playDirectStream().
     */
    fun playNextDirectStream(url: String, item: MediaItem) {
        this.startOffset = 0L
        hasShownResumeToast = false
        autoSkippedMarkers.clear()
        _uiState.update { it.copy(showAutoNextPopup = false, resumeMessage = null) }
        playDirectStream(url, item)
    }

    fun release() {
        positionTrackerJob?.cancel()
        positionTrackerJob = null

        playerStatsTracker.stopTracking()
        // Capture current state BEFORE reset — needed for stopped-timeline
        val stoppedItem = _uiState.value.currentItem
        val stoppedPosition = _uiState.value.currentPosition
        playerScrobbler.stop(stoppedItem, stoppedPosition)
        mediaSessionManager.release()

        // Release MPV audio focus if in MPV mode
        if (isMpvMode) {
            abandonMpvAudioFocus()
        }

        // Capture references and null immediately to prevent further use
        val exo = player
        val mpv = mpvPlayer
        player = null
        mpvPlayer = null

        // Fire-and-forget release on main thread via applicationScope
        // Avoids blocking onCleared() if ExoPlayer.release() hangs
        applicationScope.launch(mainDispatcher) {
            try { exo?.release() } catch (e: Exception) { Timber.w(e, "ExoPlayer release failed") }
            try { mpv?.release() } catch (e: Exception) { Timber.w(e, "MPV release failed") }
        }

        // Cancel ALL coroutines (position tracker, scrobbler, stats, collectors)
        sessionJob.cancel()
        // Recreate a fresh child Job + scope for the next initialize() cycle
        sessionJob = SupervisorJob(applicationScope.coroutineContext[Job])
        scope = CoroutineScope(sessionJob + mainDispatcher + globalHandler)

        // Reset state so a fresh session doesn't inherit stale values
        _uiState.value = PlayerUiState()
        isMpvMode = false
        isDirectPlay = false
        hasShownResumeToast = false
        autoSkippedMarkers.clear()
        chapterMarkerManager.clear()
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

        // Start perf tracking so ExoPlayer listener callbacks can checkpoint/end it
        val opId = "player_load_${item?.ratingKey ?: "direct"}"
        performanceTracker.startOperation(
            opId,
            com.chakir.plexhubtv.core.common.PerfCategory.PLAYBACK,
            "Direct Stream Play",
            mapOf("url" to url.take(80))
        )

        val mediaItem = item ?: MediaItem(
            id = "iptv-$url",
            ratingKey = "iptv",
            serverId = "iptv",
            title = "Live Stream",
            type = MediaType.Movie,
            mediaParts = emptyList()
        )

        // Populate tracks from mediaParts (Backend/Xtream sources have pre-loaded streams)
        val (audios, subtitles) = if (mediaItem.mediaParts.isNotEmpty()) {
            playerTrackController.populateTracks(mediaItem)
        } else {
            Pair(emptyList(), emptyList())
        }

        playerScrobbler.resetAutoNext()
        val next = playbackManager.getNextMedia()

        _uiState.update {
            it.copy(
                currentItem = mediaItem,
                nextItem = next,
                showAutoNextPopup = false,
                isPlaying = true,
                isBuffering = true,
                currentPosition = if (it.currentItem?.id != mediaItem.id) 0L else it.currentPosition,
                audioTracks = audios,
                subtitleTracks = subtitles,
                selectedAudio = audios.find { t -> t.isSelected },
                selectedSubtitle = subtitles.find { t -> t.isSelected } ?: SubtitleTrack.OFF,
            )
        }

        scope.launch {
            // Resolve initial track selection from preferences (VOSTFR defaults)
            if (audios.isNotEmpty()) {
                val part = mediaItem.mediaParts.firstOrNull()
                val (finalAudioStreamId, finalSubtitleStreamId) = playerTrackController.resolveInitialTracks(
                    mediaItem.ratingKey, mediaItem.serverId, part, null, null
                )
                val resolvedAudio = _uiState.value.audioTracks.find { it.streamId == finalAudioStreamId }
                    ?: _uiState.value.audioTracks.firstOrNull()
                val resolvedSubtitle = _uiState.value.subtitleTracks.find { it.streamId == finalSubtitleStreamId }
                    ?: SubtitleTrack.OFF
                _uiState.update { it.copy(selectedAudio = resolvedAudio, selectedSubtitle = resolvedSubtitle) }
            }

            val streamUri = Uri.parse(url)
            player?.apply {
                val metadata = androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(mediaItem.title)
                    .setArtist(mediaItem.grandparentTitle ?: mediaItem.studio)
                    .setSubtitle(mediaItem.tagline ?: mediaItem.parentTitle)
                    .build()
                val exoItem = ExoMediaItem.Builder()
                    .setUri(streamUri)
                    .setMediaId(mediaItem.ratingKey)
                    .setMediaMetadata(metadata)
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

        /** Bitrate threshold (kbps) above which MPV is preferred for its FFmpeg demuxer */
        private const val HIGH_BITRATE_THRESHOLD_KBPS = 60_000

        /** File size threshold (bytes) above which MPV is preferred regardless of reported bitrate.
         *  ExoPlayer's seek index and demuxer can OOM on very large containers. */
        private const val LARGE_FILE_THRESHOLD_BYTES = 20L * 1024 * 1024 * 1024 // 20 GB

        /** Audio codecs known to crash ExoPlayer on most Android TV devices */
        private val ALWAYS_PROBLEMATIC_CODECS = setOf("truehd", "dts-hd ma", "dts-hd", "dtshd")

        /** Video codecs/profiles known to crash ExoPlayer on most Android TV devices */
        private val PROBLEMATIC_VIDEO_CODECS = setOf(
            "hevc dolbyvision",
            "hevc dv",
            "dolby vision",
            "hdr10+",
            "av1 hdr",
        )

        private fun audioCodecToMime(codec: String): String? = when (codec) {
            "truehd" -> "audio/true-hd"
            "dts-hd ma", "dts-hd", "dtshd" -> "audio/vnd.dts.hd"
            "dts" -> "audio/vnd.dts"
            "eac3" -> "audio/eac3"
            "ac3" -> "audio/ac3"
            else -> null
        }

        fun isProblematicAudioCodec(codec: String): Boolean {
            if (codec in ALWAYS_PROBLEMATIC_CODECS) return true
            val mimeType = audioCodecToMime(codec) ?: return false
            return !hasHardwareAudioDecoder(mimeType)
        }

        // Cached codec lists — MediaCodecList queries take 100-300ms on low-end TV devices.
        // The list never changes at runtime, so we cache it once on first access.
        private val cachedAllCodecs: List<android.media.MediaCodecInfo> by lazy {
            try {
                android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS).codecInfos.toList()
            } catch (e: Exception) { emptyList() }
        }
        private val cachedRegularCodecs: List<android.media.MediaCodecInfo> by lazy {
            try {
                android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.toList()
            } catch (e: Exception) { emptyList() }
        }

        private fun hasHardwareAudioDecoder(mimeType: String): Boolean {
            return cachedAllCodecs.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
        }

        fun isProblematicVideoCodec(codec: String, profile: String? = null): Boolean {
            val fullCodec = if (profile != null) "$codec $profile".trim().lowercase() else codec.lowercase()

            // Check exact match in problematic list
            if (PROBLEMATIC_VIDEO_CODECS.any { fullCodec.contains(it) }) {
                return true
            }

            // Check if device supports HDR/DolbyVision via MediaCodec
            val mimeType = when {
                fullCodec.contains("dolby") || fullCodec.contains("dv") -> "video/dolby-vision"
                fullCodec.contains("hdr10+") -> "video/hevc"  // HDR10+ is HEVC variant
                fullCodec.contains("av1") -> "video/av01"
                else -> return false
            }

            return !hasHardwareVideoDecoder(mimeType)
        }

        private fun hasHardwareVideoDecoder(mimeType: String): Boolean {
            return cachedRegularCodecs.any { codecInfo ->
                !codecInfo.isEncoder && codecInfo.supportedTypes.contains(mimeType)
            }
        }
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
                        Player.STATE_ENDED -> {
                            _uiState.update { it.copy(isPlaying = false, isBuffering = false) }
                            // Safety net: scrobble if the 1s check hasn't fired yet (e.g. short video)
                            val endedItem = _uiState.value.currentItem
                            val endedDur = _uiState.value.duration
                            if (endedItem != null && endedDur > 0 && !playerScrobbler.isScrobbled) {
                                scope.launch {
                                    runCatching { playbackRepository.toggleWatchStatus(endedItem, isWatched = true) }
                                }
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
        // Attach MediaSession for system-level controls (notification, Bluetooth, Assistant)
        player?.let { mediaSessionManager.initialize(it) }
    }

    fun switchToMpv(config: MpvConfig = MpvConfig()) {
        Timber.d("SCREEN [Player] switchToMpv() called (deinterlace=${config.deinterlace})")
        if (isMpvMode) return
        isMpvMode = true
        FirebaseCrashlytics.getInstance().setCustomKey("player_engine", "MPV")

        player?.release()
        player = null

        mpvPlayer = playerFactory.createMpvPlayer(application, scope, config)
        requestMpvAudioFocus()

        // S-05: Observe MPV init/runtime errors to avoid stuck screen
        scope.launch {
            mpvPlayer?.error?.filterNotNull()?.collect { errorMsg ->
                Timber.e("PlayerController: MPV error: $errorMsg")

                // Detect codec-related errors and provide user-friendly message
                val userMessage = when {
                    errorMsg.contains("codec", ignoreCase = true) ||
                    errorMsg.contains("format not supported", ignoreCase = true) ||
                    errorMsg.contains("decoder", ignoreCase = true) ->
                        "Format vidéo non supporté par votre appareil. Essayez un autre fichier ou serveur."
                    else -> errorMsg
                }

                _uiState.update {
                    it.copy(
                        error = userMessage,
                        errorType = com.chakir.plexhubtv.feature.player.PlayerErrorType.Generic,
                        isBuffering = false
                    )
                }
            }
        }

        // Observe MPV end-of-file to trigger auto-scrobble (mirrors ExoPlayer STATE_ENDED)
        scope.launch {
            mpvPlayer?.endOfFile?.collect { eof ->
                if (eof) {
                    _uiState.update { it.copy(isPlaying = false, isBuffering = false) }
                    val endedItem = _uiState.value.currentItem
                    val endedDur = _uiState.value.duration
                    if (endedItem != null && endedDur > 0 && !playerScrobbler.isScrobbled) {
                        runCatching { playbackRepository.toggleWatchStatus(endedItem, isWatched = true) }
                    }
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
        Timber.d("PlayerController.loadMedia: rk=$rKey, sid=$sId (isMpvMode=$isMpvMode, scopeActive=${sessionJob.isActive})")
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

            // Always fetch full detail from API to get chapters/markers.
            // PlaybackManager cache (queue items) comes from /children endpoint
            // which doesn't include markers — skip intro/credits would be missing.
            val mediaFetchStart = System.currentTimeMillis()
            val result = getMediaDetailUseCase(rKey, sId).first()
            val fetchDuration = System.currentTimeMillis() - mediaFetchStart
            performanceTracker.addCheckpoint(
                opId,
                "Media Detail Fetched",
                mapOf("duration" to fetchDuration, "success" to (result.isSuccess))
            )
            val media: MediaItem? = result.getOrNull()?.item

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
            Timber.d("PlayerController: Loaded ${media.chapters.size} chapters, ${media.markers.size} markers for '${media.title}'")

            val part = media.mediaParts.firstOrNull()

            // Extras (Clip) are CDN-hosted — must go through server transcode/proxy, not direct play
            isDirectPlay = bitrate >= 200000 && part?.key != null && media.type != com.chakir.plexhubtv.core.model.MediaType.Clip

            // Audio codec pre-flight: proactively switch to MPV for codecs ExoPlayer can't handle
            if (isDirectPlay && !isMpvMode) {
                val audioStream = part?.streams?.filterIsInstance<com.chakir.plexhubtv.core.model.AudioStream>()?.firstOrNull()
                val audioCodec = audioStream?.codec?.lowercase()
                if (audioCodec != null && isProblematicAudioCodec(audioCodec)) {
                    Timber.d("PlayerController: Audio codec '$audioCodec' not supported by ExoPlayer, switching to MPV")
                    performanceTracker.addCheckpoint(opId, "Audio Codec Preflight → MPV", mapOf("codec" to audioCodec))
                    switchToMpv()
                    return@launch
                }

                // Video codec pre-flight: proactively switch to MPV for codecs ExoPlayer can't handle (HDR/DV/AV1)
                val videoStream = part?.streams?.filterIsInstance<com.chakir.plexhubtv.core.model.VideoStream>()?.firstOrNull()
                val videoCodec = videoStream?.codec?.lowercase()
                val videoProfile = videoStream?.displayTitle?.lowercase() ?: ""
                if (videoCodec != null && isProblematicVideoCodec(videoCodec, videoProfile)) {
                    val fullCodec = "$videoCodec $videoProfile".trim()
                    Timber.d("PlayerController: Video codec '$fullCodec' not supported by ExoPlayer, switching to MPV")
                    performanceTracker.addCheckpoint(opId, "Video Codec Preflight → MPV", mapOf("codec" to fullCodec))
                    switchToMpv()
                    return@launch
                }
            }

            // High-bitrate / interlace / large-file pre-flight: route to MPV
            if (isDirectPlay && !isMpvMode) {
                val videoStream = part?.streams?.filterIsInstance<com.chakir.plexhubtv.core.model.VideoStream>()?.firstOrNull()
                val videoBitrateKbps = videoStream?.bitrate ?: 0
                val deinterlaceMode = settingsRepository.deinterlaceMode.first()
                val isInterlaced = videoStream?.scanType?.equals("interlaced", ignoreCase = true) == true
                val needsDeinterlace = isInterlaced && deinterlaceMode == "auto"

                // Estimate bitrate from file size when Plex reports 0 (common for remux/recently-added)
                val fileSizeBytes = part?.size ?: 0L
                val mediaDurationMs = part?.duration ?: media.durationMs ?: 0L
                val estimatedBitrateKbps = if (videoBitrateKbps == 0 && mediaDurationMs > 0 && fileSizeBytes > 0) {
                    ((fileSizeBytes * 8) / (mediaDurationMs / 1000) / 1000).toInt()
                } else videoBitrateKbps
                val effectiveBitrateKbps = maxOf(videoBitrateKbps, estimatedBitrateKbps)
                val isLargeFile = fileSizeBytes > LARGE_FILE_THRESHOLD_BYTES

                if (effectiveBitrateKbps > HIGH_BITRATE_THRESHOLD_KBPS || needsDeinterlace || isLargeFile) {
                    val reason = when {
                        isLargeFile && needsDeinterlace ->
                            "large file (${fileSizeBytes / (1024*1024*1024)}GB) + interlaced"
                        isLargeFile ->
                            "large file (${fileSizeBytes / (1024*1024*1024)}GB > 20GB threshold)"
                        needsDeinterlace && effectiveBitrateKbps > HIGH_BITRATE_THRESHOLD_KBPS ->
                            "interlaced + high-bitrate (${effectiveBitrateKbps}kbps)"
                        needsDeinterlace -> "interlaced content (scanType=${videoStream?.scanType})"
                        else -> "high-bitrate (${effectiveBitrateKbps}kbps > ${HIGH_BITRATE_THRESHOLD_KBPS}kbps${if (videoBitrateKbps == 0) ", estimated from file size" else ""})"
                    }
                    Timber.d("PlayerController: $reason → switching to MPV")
                    performanceTracker.addCheckpoint(opId, "Auto-route → MPV", mapOf("reason" to reason))
                    switchToMpv(MpvConfig(deinterlace = needsDeinterlace))
                    return@launch
                }
            }

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
                 urlBuilders.find { it.matches(sId) }?.buildUrl(
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
                    currentPos > 0 -> {
                        Timber.d("PlayerController: Resume via currentPos=$currentPos (MPV)")
                        currentPos
                    }
                    startOffset > 0 -> {
                        Timber.d("PlayerController: Resume via startOffset=$startOffset (MPV)")
                        startOffset
                    }
                    media.viewOffset > 0 -> {
                        Timber.d("PlayerController: Resume via Plex viewOffset=${media.viewOffset} (MPV)")
                        media.viewOffset
                    }
                    else -> {
                        Timber.d("PlayerController: No resume position available, starting at 0 (MPV)")
                        0L
                    }
                }

                if (seekTarget > 0) {
                    Timber.d("PlayerController: Seeking to $seekTarget ms (MPV)")
                    mpvPlayer?.seekTo(seekTarget)
                    performanceTracker.addCheckpoint(opId, "MPV Seek Applied", mapOf("position" to seekTarget))

                    // Verify seek success after a delay (on background thread — no main thread needed)
                    scope.launch(defaultDispatcher) {
                        kotlinx.coroutines.delay(500)
                        val actualPos = mpvPlayer?.position?.value ?: 0
                        if (actualPos < seekTarget - 2000) {  // Tolerance 2s
                            Timber.e("PlayerController: MPV Seek FAILED - target=$seekTarget, actual=$actualPos")
                        } else {
                            Timber.d("PlayerController: MPV Seek SUCCESS - target=$seekTarget, actual=$actualPos")
                        }
                    }

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
                        // MPV only sees embedded (non-external) subtitle tracks in the container.
                        val embeddedTracks = _uiState.value.subtitleTracks.filter { it.id != "no" && !it.isExternal }
                        val subIndex = embeddedTracks.indexOf(resolvedSubtitle) + 1
                        if (subIndex > 0) {
                            mpvPlayer?.setSubtitleId(subIndex.toString())
                            Timber.d("PlayerController: MPV subtitle track set to index $subIndex (${resolvedSubtitle.title})")
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
                val metadata = androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(media.title)
                    .setArtist(media.grandparentTitle ?: media.studio)
                    .setSubtitle(media.tagline ?: media.parentTitle)
                    .build()
                val mediaItem = playerFactory.createMediaItem(streamUri, rKey, !isDirectPlay, subtitleConfigs, metadata)
                val mediaItemDuration = System.currentTimeMillis() - mediaItemStart
                performanceTracker.addCheckpoint(opId, "ExoPlayer MediaItem Created", mapOf("duration" to mediaItemDuration))

                player?.apply {
                    // Apply track preferences BEFORE prepare() so ExoPlayer selects the right tracks
                    if (isDirectPlay) {
                        val builder = trackSelectionParameters.buildUpon()
                        (resolvedAudio?.languageCode ?: resolvedAudio?.language)?.let { lang ->
                            builder.setPreferredAudioLanguage(lang)
                            Timber.d("PlayerController: ExoPlayer preferred audio language set to '$lang' (code=${resolvedAudio?.languageCode})")
                        }
                        if (resolvedSubtitle != null && resolvedSubtitle.id != "no") {
                            builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                            (resolvedSubtitle.languageCode ?: resolvedSubtitle.language)?.let { lang ->
                                builder.setPreferredTextLanguage(lang)
                                Timber.d("PlayerController: ExoPlayer preferred text language set to '$lang' (code=${resolvedSubtitle.languageCode})")
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
                        currentPos > 0 -> {
                            Timber.d("PlayerController: Resume via currentPos=$currentPos (ExoPlayer)")
                            currentPos
                        }
                        startOffset > 0 -> {
                            Timber.d("PlayerController: Resume via startOffset=$startOffset (ExoPlayer)")
                            startOffset
                        }
                        media.viewOffset > 0 -> {
                            Timber.d("PlayerController: Resume via Plex viewOffset=${media.viewOffset} (ExoPlayer)")
                            media.viewOffset
                        }
                        else -> {
                            Timber.d("PlayerController: No resume position available, starting at 0 (ExoPlayer)")
                            0L
                        }
                    }

                    if (seekTarget > 0) {
                        Timber.d("PlayerController: Seeking to $seekTarget ms (ExoPlayer)")
                        seekTo(seekTarget)
                        performanceTracker.addCheckpoint(opId, "ExoPlayer Seek Applied", mapOf("position" to seekTarget))

                        // Verify seek success after a delay (ExoPlayer needs time to prepare)
                        scope.launch {
                            kotlinx.coroutines.delay(500)
                            val actualPos = player?.currentPosition ?: 0
                            if (actualPos < seekTarget - 2000) {  // Tolerance 2s
                                Timber.e("PlayerController: ExoPlayer Seek FAILED - target=$seekTarget, actual=$actualPos")
                            } else {
                                Timber.d("PlayerController: ExoPlayer Seek SUCCESS - target=$seekTarget, actual=$actualPos")
                            }
                        }

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
            // Cache skip modes to avoid collecting on every tick — re-read every 10s is fine
            var skipIntroMode = "ask"
            var skipCreditsMode = "ask"
            var modeRefreshCounter = 0

            while (isActive) {
                // Refresh skip modes periodically (every ~10s)
                if (modeRefreshCounter % 10 == 0) {
                    skipIntroMode = settingsRepository.skipIntroMode.first()
                    skipCreditsMode = settingsRepository.skipCreditsMode.first()
                }
                modeRefreshCounter++

                try {
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
                            chapterMarkerManager.updatePlaybackPosition(pos)
                            checkAutoSkipMarkers(pos, skipIntroMode, skipCreditsMode)
                            playerScrobbler.checkAutoNext(
                                position = pos,
                                duration = dur,
                                hasNextItem = _uiState.value.nextItem != null,
                                isPopupAlreadyShown = _uiState.value.showAutoNextPopup
                            )
                            _uiState.value.currentItem?.let { item ->
                                playerScrobbler.checkAutoScrobble(pos, dur, item)
                            }
                        }
                    } else {
                        player?.let { p ->
                            val pos = p.currentPosition
                            val dur = p.duration.coerceAtLeast(0)
                            if (p.isPlaying) {
                                _uiState.update {
                                    it.copy(
                                        currentPosition = pos,
                                        bufferedPosition = p.bufferedPosition,
                                        duration = dur
                                    )
                                }
                                chapterMarkerManager.updatePlaybackPosition(pos)
                                checkAutoSkipMarkers(pos, skipIntroMode, skipCreditsMode)
                            }
                            // Check auto-next and auto-scrobble even during buffering near end of stream —
                            // ExoPlayer may enter BUFFERING at 95%+ which previously blocked the popup
                            if (pos > 0 && dur > 0) {
                                playerScrobbler.checkAutoNext(
                                    position = pos,
                                    duration = dur,
                                    hasNextItem = _uiState.value.nextItem != null,
                                    isPopupAlreadyShown = _uiState.value.showAutoNextPopup
                                )
                                _uiState.value.currentItem?.let { item ->
                                    playerScrobbler.checkAutoScrobble(pos, dur, item)
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Position tracking tick failed (player may be released)")
                }
                delay(1000)
            }
        }
    }

    /**
     * Auto-skip markers when the corresponding mode is "auto".
     * Each marker is skipped at most once per session via [autoSkippedMarkers].
     */
    private fun checkAutoSkipMarkers(positionMs: Long, skipIntroMode: String, skipCreditsMode: String) {
        val visible = chapterMarkerManager.visibleMarkers.value
        for (marker in visible) {
            val mode = when (marker.type) {
                "intro" -> skipIntroMode
                "credits" -> skipCreditsMode
                else -> "ask"
            }
            if (mode != "auto") continue

            val markerKey = "${marker.type}-${marker.startTime}"
            if (markerKey in autoSkippedMarkers) continue

            autoSkippedMarkers.add(markerKey)
            Timber.d("PlayerController: Auto-skipping ${marker.type} marker to ${marker.endTime}ms")
            seekTo(marker.endTime)
            return // Skip one marker per tick to avoid seeking twice
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

    fun applyExternalSubtitle(filePath: String) {
        val uri = android.net.Uri.fromFile(java.io.File(filePath))
        val mimeType = when {
            filePath.endsWith(".srt", ignoreCase = true) -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
            filePath.endsWith(".ass", ignoreCase = true) || filePath.endsWith(".ssa", ignoreCase = true) -> androidx.media3.common.MimeTypes.TEXT_SSA
            filePath.endsWith(".vtt", ignoreCase = true) -> androidx.media3.common.MimeTypes.TEXT_VTT
            else -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
        }

        if (_uiState.value.isMpvMode) {
            mpvPlayer?.loadExternalSubtitle(filePath)
            timber.log.Timber.d("Applied external subtitle via MPV: $filePath")
            return
        }

        val p = player ?: return
        val currentItem = p.currentMediaItem ?: return
        val currentPos = p.currentPosition

        val subtitleConfig = androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mimeType)
            .setLabel("Downloaded")
            .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
            .build()

        val newMediaItem = currentItem.buildUpon()
            .setSubtitleConfigurations(
                currentItem.localConfiguration?.subtitleConfigurations.orEmpty() + subtitleConfig,
            )
            .build()

        p.setMediaItem(newMediaItem, currentPos)
        p.prepare()

        // Select the new subtitle track after a short delay
        scope.launch {
            kotlinx.coroutines.delay(500)
            val builder = p.trackSelectionParameters.buildUpon()
            builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
            builder.setPreferredTextLanguage("und")
            p.trackSelectionParameters = builder.build()
        }

        timber.log.Timber.d("Applied external subtitle via ExoPlayer: $filePath")
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
