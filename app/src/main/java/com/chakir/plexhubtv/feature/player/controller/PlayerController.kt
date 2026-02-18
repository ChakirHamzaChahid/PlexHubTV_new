package com.chakir.plexhubtv.feature.player.controller

import android.app.Application
import android.net.Uri
import android.util.Log
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
import com.chakir.plexhubtv.feature.player.PlayerUiState
import com.chakir.plexhubtv.feature.player.url.TranscodeUrlBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class PlayerController @Inject constructor(
    private val application: Application,
    private val playerFactory: PlayerFactory,
    private val getMediaDetailUseCase: GetMediaDetailUseCase,
    private val playbackRepository: PlaybackRepository,
    private val playbackManager: com.chakir.plexhubtv.domain.service.PlaybackManager,
    private val settingsRepository: SettingsRepository,
    private val trackPreferenceDao: com.chakir.plexhubtv.core.database.TrackPreferenceDao,
    private val chapterMarkerManager: ChapterMarkerManager,
    private val playerTrackController: PlayerTrackController,
    private val playerScrobbler: PlayerScrobbler,
    private val playerStatsTracker: PlayerStatsTracker,
    private val transcodeUrlBuilder: TranscodeUrlBuilder,
    private val performanceTracker: com.chakir.plexhubtv.core.common.PerformanceTracker,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    var player: ExoPlayer? = null
        private set
    var mpvPlayer: com.chakir.plexhubtv.feature.player.mpv.MpvPlayer? = null
        private set

    private var positionTrackerJob: Job? = null
    private var isMpvMode = false
    private var isDirectPlay = false

    private var ratingKey: String? = null
    private var serverId: String? = null
    private var directUrl: String? = null
    private var startOffset: Long = 0L

    fun initialize(startRatingKey: String?, startServerId: String?, startDirectUrl: String?, offset: Long) {
        this.ratingKey = startRatingKey
        this.serverId = startServerId
        this.directUrl = startDirectUrl
        this.startOffset = offset

        initializePlayer(application)

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

        if (directUrl != null) {
            playDirectUrl(directUrl!!)
        } else if (ratingKey != null && serverId != null) {
            loadMedia(ratingKey!!, serverId!!)
        }
        startPositionTracking()
    }

    fun release() {
        playerStatsTracker.stopTracking()
        playerScrobbler.stop()
        player?.release()
        player = null
        mpvPlayer?.release()
        positionTrackerJob?.cancel()
        // scope.cancel() // Don't cancel scope if Singleton, or recreate it on initialize
        // Actually for Singleton, we should keep scope alive or manage jobs carefully
        positionTrackerJob?.cancel()
    }
    
    // ... Copy methods from PlayerViewModel ...
    
    private fun playDirectUrl(url: String) {
        // ... (Logic from PlayerViewModel)
         val dummyItem = MediaItem(
            id = "iptv-$url",
            ratingKey = "iptv",
            serverId = "iptv",
            title = "Live Stream", // Title handling needs improvement if passed from VM
            type = MediaType.Movie, 
            mediaParts = emptyList()
        )

        _uiState.update {
            it.copy(
                currentItem = dummyItem,
                isPlaying = true,
                isBuffering = true
            )
        }
        
        scope.launch {
            val streamUri = Uri.parse(url)
            player?.apply {
                val mediaItem = ExoMediaItem.Builder()
                    .setUri(streamUri)
                    .setMediaId("iptv")
                    .build()
                    
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
        }
    }

    private fun initializePlayer(context: Application) {
        player = playerFactory.createExoPlayer(context).apply {
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
                    
                    if (isFormatError && !isMpvMode) {
                        switchToMpv()
                    } else {
                        _uiState.update { it.copy(error = error.localizedMessage) }
                    }
                }
            })
        }
    }
    
    private fun switchToMpv() {
         Log.d("METRICS", "SCREEN [Player] switchToMpv() called")
        if (isMpvMode) return
        isMpvMode = true
        
        player?.release()
        player = null
        
        mpvPlayer = playerFactory.createMpvPlayer(application, scope)
        _uiState.update { it.copy(isMpvMode = true, error = null) }
        
        if (directUrl != null) {
             scope.launch { mpvPlayer?.play(directUrl!!) }
        } else if (ratingKey != null && serverId != null) {
            loadMedia(ratingKey!!, serverId!!)
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

            val qualityObj = _uiState.value.availableQualities.find { it.bitrate == bitrate }
                ?: if (bitrate >= 200000) _uiState.value.availableQualities.first() else _uiState.value.availableQualities.last()

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

            isDirectPlay = bitrate >= 200000 && part?.key != null

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
                }

                // Track/Sub selection for MPV
                 if (isDirectPlay && resolvedAudio != null) {
                     val index = _uiState.value.audioTracks.indexOf(resolvedAudio) + 1
                     if (index > 0) mpvPlayer?.setAudioId(index.toString())
                }

                performanceTracker.endOperation(opId, success = true, additionalMeta = mapOf("engine" to "MPV", "streamUrl" to streamUri.toString()))
            } else {
                performanceTracker.addCheckpoint(opId, "ExoPlayer Mode")
                val mediaItemStart = System.currentTimeMillis()
                val mediaItem = playerFactory.createMediaItem(streamUri, rKey, !isDirectPlay)
                val mediaItemDuration = System.currentTimeMillis() - mediaItemStart
                performanceTracker.addCheckpoint(opId, "ExoPlayer MediaItem Created", mapOf("duration" to mediaItemDuration))

                player?.apply {
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
