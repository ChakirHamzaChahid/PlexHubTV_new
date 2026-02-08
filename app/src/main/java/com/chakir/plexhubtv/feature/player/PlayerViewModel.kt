package com.chakir.plexhubtv.feature.player

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import android.net.Uri
import android.media.MediaCodecList
import com.chakir.plexhubtv.core.model.*
import com.chakir.plexhubtv.domain.repository.*
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import com.chakir.plexhubtv.domain.usecase.GetMediaDetailUseCase
import com.chakir.plexhubtv.feature.player.controller.ChapterMarkerManager
import com.chakir.plexhubtv.feature.player.controller.PlayerScrobbler
import com.chakir.plexhubtv.feature.player.controller.PlayerStatsTracker
import com.chakir.plexhubtv.feature.player.controller.PlayerTrackController
import com.chakir.plexhubtv.feature.player.url.TranscodeUrlBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
@OptIn(UnstableApi::class)
/**
 * ViewModel principal pour le lecteur vidéo.
 * Gère ExoPlayer et MPV, la sélection des pistes (Audio/Sous-titres), la qualité vidéo, et le suivi de progression (Scrobbling).
 */
class PlayerViewModel @Inject constructor(
    application: Application,
    private val getMediaDetailUseCase: GetMediaDetailUseCase,
    private val playbackRepository: PlaybackRepository,
    private val playbackManager: com.chakir.plexhubtv.domain.service.PlaybackManager,
    private val settingsRepository: SettingsRepository,
    private val trackPreferenceDao: com.chakir.plexhubtv.core.database.TrackPreferenceDao,
    private val watchNextHelper: com.chakir.plexhubtv.core.util.WatchNextHelper,
    private val savedStateHandle: SavedStateHandle,
    // Controllers
    val chapterMarkerManager: ChapterMarkerManager,
    private val playerTrackController: PlayerTrackController,
    private val playerScrobbler: PlayerScrobbler,
    private val playerStatsTracker: PlayerStatsTracker,
    private val transcodeUrlBuilder: TranscodeUrlBuilder,
    private val playerFactory: PlayerFactory
) : AndroidViewModel(application) {

    private val ratingKey: String? = savedStateHandle["ratingKey"]
    private val serverId: String? = savedStateHandle["serverId"]
    private val directUrl: String? = savedStateHandle["url"]
    private val startOffset: Long = savedStateHandle.get<Long>("startOffset") ?: 0L

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    var player: ExoPlayer? = null
        private set
    var mpvPlayer: com.chakir.plexhubtv.feature.player.mpv.MpvPlayer? = null
        private set
        
    // chapterMarkerManager is now injected

    private var positionTrackerJob: Job? = null
    // scrobbleJob handled by controller
    // statsJob handled by controller
    private var isMpvMode = false
    private var isDirectPlay = false

    init {
        initializePlayer(application)
        
        playerScrobbler.start(
            scope = viewModelScope,
            currentItemProvider = { _uiState.value.currentItem },
            isPlayingProvider = { _uiState.value.isPlaying },
            currentPositionProvider = { _uiState.value.currentPosition },
            durationProvider = { _uiState.value.duration }
        )

        playerStatsTracker.startTracking(
            scope = viewModelScope,
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
        
        viewModelScope.launch {
            playerScrobbler.showAutoNextPopup.collect { show ->
                _uiState.update { it.copy(showAutoNextPopup = show) }
            }
        }

        if (directUrl != null) {
            playDirectUrl(directUrl)
        } else if (ratingKey != null && serverId != null) {
            loadMedia(ratingKey, serverId)
        }
        startPositionTracking()
    }

    private fun playDirectUrl(url: String) {
        val title = savedStateHandle.get<String>("title") ?: "Live Stream"
        
        // Create dummy MediaItem
        val dummyItem = MediaItem(
            id = "iptv-$url",
            ratingKey = "iptv",
            serverId = "iptv",
            title = title,
            type = MediaType.Movie, // Treated as movie for player controls
            mediaParts = emptyList() // No Plex parts
        )

        _uiState.update {
            it.copy(
                currentItem = dummyItem,
                isPlaying = true,
                isBuffering = true
            )
        }
        
        // Play directly via ExoPlayer (or MPV if preferred later)
        viewModelScope.launch {
            val streamUri = Uri.parse(url)
            player?.apply {
                // DON'T force MimeType - let ExoPlayer auto-detect the stream format
                // This is critical because IPTV streams can be HLS, MPEG-TS, RTSP, etc.
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
    
    override fun onCleared() {
        super.onCleared()
        playerStatsTracker.stopTracking()
        playerScrobbler.stop()
        player?.release()
        player = null
        mpvPlayer?.release()
        positionTrackerJob?.cancel()
    }
    
    private fun switchToMpv() {
        android.util.Log.d("METRICS", "SCREEN [Player] switchToMpv() called")
        if (isMpvMode) return
        isMpvMode = true
        
        // Release ExoPlayer
        player?.release()
        player = null
        
        // Init MPV
        mpvPlayer = playerFactory.createMpvPlayer(getApplication(), viewModelScope)
        // Note: UI must call mpvPlayer.initialize(viewGroup) or attach surface
        
        _uiState.update { it.copy(isMpvMode = true, error = null) }
        
        // Reload current media in MPV (Ensures loadMedia continues after transition)
        if (directUrl != null) {
            // Re-trigger direct play logic for MPV
            // MPV wrapper needs to be told to play
             viewModelScope.launch {
                 mpvPlayer?.play(directUrl)
             }
        } else if (ratingKey != null && serverId != null) {
            loadMedia(ratingKey, serverId)
        }
    }

    private fun initializePlayer(context: Application) {
        player = playerFactory.createExoPlayer(context).apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> _uiState.update { it.copy(isBuffering = true, error = null) }
                        Player.STATE_READY -> {
                            _uiState.update { 
                                it.copy(
                                    isBuffering = false,
                                    duration = duration.coerceAtLeast(0),
                                    error = null // Clear any transient errors on recovery
                                ) 
                            }
                            // Sync tracks
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
                        Player.STATE_ENDED -> { /* Handle completion */ }
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

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val isFormatError = when (error.errorCode) {
                        androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED,
                        androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> true
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

    private fun loadMedia(rKey: String, sId: String, bitrateOverride: Int? = null, audioIndex: Int? = null, subtitleIndex: Int? = null, audioStreamId: String? = null, subtitleStreamId: String? = null) {
        viewModelScope.launch {
            // Get Settings
            val qualityPref = settingsRepository.getVideoQuality().first()
            val engine = settingsRepository.playerEngine.first()
            
            // Determine Bitrate
            val bitrate = bitrateOverride ?: when {
                qualityPref.startsWith("20 Mbps") -> 20000
                qualityPref.startsWith("12 Mbps") -> 12000
                qualityPref.startsWith("8 Mbps") -> 8000
                qualityPref.startsWith("4 Mbps") -> 4000
                qualityPref.startsWith("3 Mbps") -> 3000
                else -> 200000 // Original / Max
            }

            // Update Selected Quality in UI
            val qualityObj = _uiState.value.availableQualities.find { it.bitrate == bitrate } 
                ?: if (bitrate >= 200000) _uiState.value.availableQualities.first() else _uiState.value.availableQualities.last()
            
            _uiState.update { it.copy(selectedQuality = qualityObj) }

            // Initialize Mode based on Preference
            if (engine == "MPV" && !isMpvMode) {
                switchToMpv()
                return@launch
            }
            
            val loadStartTime = System.currentTimeMillis()
            android.util.Log.d("METRICS", "SCREEN [Player]: Loading details start for $rKey")
            getMediaDetailUseCase(rKey, sId).collect { result ->
                result.onSuccess { detail ->
                    val detailDuration = System.currentTimeMillis() - loadStartTime
                    android.util.Log.i("METRICS", "SCREEN [Player] Detail SUCCESS: duration=${detailDuration}ms")
                    
                    playerScrobbler.resetAutoNext()
                    val next = playbackManager.getNextMedia()
                    
                    // Populate tracks from metadata implementation via controller
                    val (audios, subtitles) = playerTrackController.populateTracks(detail.item)

                    _uiState.update {
                        it.copy(
                            currentItem = detail.item,
                            nextItem = next,
                            showAutoNextPopup = false,
                            currentPosition = if (it.currentItem?.id != detail.item.id) 0L else it.currentPosition,
                            audioTracks = audios,
                            subtitleTracks = subtitles,
                            selectedAudio = audios.find { t -> t.isSelected },
                            selectedSubtitle = subtitles.find { t -> t.isSelected } ?: SubtitleTrack.OFF
                        )
                    }
                    
                    val media = detail.item
                    chapterMarkerManager.setChapters(media.chapters)
                    chapterMarkerManager.setMarkers(media.markers)
                    
                    val clientId = settingsRepository.clientId.first() ?: "PlexHubTV-Client"
                    
                    val uriStartTime = System.currentTimeMillis()
                    val part = media.mediaParts.firstOrNull()
                    
                    // HEVC Detection & Fallback
                    // Check if the video is HEVC and if we lack hardware support
                    val videoStream = part?.streams?.filterIsInstance<com.chakir.plexhubtv.core.model.VideoStream>()?.firstOrNull()
                    val isHevc = videoStream?.codec?.equals("hevc", ignoreCase = true) == true || 
                                 videoStream?.codec?.equals("h265", ignoreCase = true) == true
                                 
                    if (isHevc && !hasHardwareHEVCDecoder() && !isMpvMode) {
                        android.util.Log.w("METRICS", "PLAYER: HEVC detected without hardware support. Switching to MPV.")
                        switchToMpv()
                        // switchToMpv re-triggered loadMedia, so we stop here
                        return@onSuccess
                    }
                    
                    this@PlayerViewModel.isDirectPlay = bitrate >= 200000 && part?.key != null
                    val isDirectPlay = this@PlayerViewModel.isDirectPlay
                    
                    // --- TRACK SELECTION LOGIC ---
                    val (finalAudioStreamId, finalSubtitleStreamId) = playerTrackController.resolveInitialTracks(
                         rKey, sId, part, audioStreamId, subtitleStreamId
                    )
                    
                    // Resolve Stream IDs to Indices/Objects if needed
                    val aIndex = part?.streams?.filterIsInstance<AudioStream>()?.find { it.id == finalAudioStreamId }?.index
                    val sIndex = part?.streams?.filterIsInstance<SubtitleStream>()?.find { it.id == finalSubtitleStreamId }?.index
                    
                     // Update UI selection based on resolved IDs
                    val resolvedAudio = _uiState.value.audioTracks.find { it.streamId == finalAudioStreamId } 
                        ?: _uiState.value.audioTracks.firstOrNull()
                    val resolvedSubtitle = _uiState.value.subtitleTracks.find { it.streamId == finalSubtitleStreamId } 
                        ?: SubtitleTrack.OFF
                        
                    _uiState.update { it.copy(selectedAudio = resolvedAudio, selectedSubtitle = resolvedSubtitle) }

                    // Generate URL using Controller
                    val streamUri = if (part != null) {
                         transcodeUrlBuilder.buildUrl(
                             media, part, rKey, isDirectPlay, bitrate, clientId,
                             finalAudioStreamId, finalSubtitleStreamId, aIndex, sIndex
                         )
                    } else null

                    val uriDuration = System.currentTimeMillis() - uriStartTime
                    android.util.Log.i("METRICS", "SCREEN [Player] URI Generated: duration=${uriDuration}ms")
                    
                    if (streamUri == null) {
                        android.util.Log.e("METRICS", "SCREEN [Player] URI is NULL for $rKey")
                        _uiState.update { it.copy(error = "Unable to play media: Invalid URL") }
                        return@collect
                    }

                    android.util.Log.d("METRICS", "SCREEN [Player] Stream URI: $streamUri")
                    val playerStartTime = System.currentTimeMillis()
                    if (isMpvMode) {
                        android.util.Log.d("METRICS", "SCREEN [Player] Calling mpvPlayer?.play()")
                        // MPV Logic
                        mpvPlayer?.play(streamUri.toString())
                        
                        // Handle Resume
                        val currentPos = _uiState.value.currentPosition
                        if (currentPos > 0) {
                            mpvPlayer?.seekTo(currentPos)
                        } else if (startOffset > 0) {
                            mpvPlayer?.seekTo(startOffset)
                        } else if (media.viewOffset > 0) {
                            mpvPlayer?.seekTo(media.viewOffset)
                        }
                        
                         // Apply track selection to MPV if Direct Playing
                        if (isDirectPlay && resolvedAudio != null) {
                             val index = _uiState.value.audioTracks.indexOf(resolvedAudio) + 1
                             if (index > 0) mpvPlayer?.setAudioId(index.toString())
                        }
                        if (isDirectPlay) {
                             if (resolvedSubtitle.id == "no") {
                                 mpvPlayer?.setSubtitleId("no")
                             } else {
                                 val validTracks = _uiState.value.subtitleTracks.filter { it.id != "no" }
                                 val index = validTracks.indexOf(resolvedSubtitle) + 1
                                 if (index > 0) mpvPlayer?.setSubtitleId(index.toString())
                             }
                        }

                        android.util.Log.i("METRICS", "SCREEN [Player] SUCCESS: MPV Start duration=${System.currentTimeMillis() - playerStartTime}ms")
                    } else {
                        // ExoPlayer Logic
                        val mediaItem = playerFactory.createMediaItem(streamUri, rKey, !isDirectPlay)
    
                        player?.apply {
                            setMediaItem(mediaItem)
                            prepare()
                            val currentPos = _uiState.value.currentPosition
                            if (currentPos > 0) {
                                seekTo(currentPos) // Resume from current playback pos if changing quality or tracks
                            } else if (startOffset > 0) {
                                seekTo(startOffset)
                            } else if (media.viewOffset > 0) {
                                seekTo(media.viewOffset)
                            }
                            playWhenReady = true
                            
                             // Apply track selection for Direct Play
                            if (isDirectPlay) {
                                // Defer to onTracksChanged or force selection here if needed
                                // Given we might not have tracks immediately, we rely on updateTracks/sync logic later
                                // But we can try setting params if we know indices
                            }
                        }
                        android.util.Log.i("METRICS", "SCREEN [Player] SUCCESS: ExoPlayer Start duration=${System.currentTimeMillis() - playerStartTime}ms")
                    }
                    android.util.Log.i("METRICS", "SCREEN [Player] TOTAL PREPARATION: duration=${System.currentTimeMillis() - loadStartTime}ms")
                }.onFailure { e ->
                    android.util.Log.e("METRICS", "SCREEN [Player] FAILED: error=${e.message}")
                    _uiState.update { it.copy(error = e.message) }
                }
            }
        }
    }



    private fun startPositionTracking() {
        positionTrackerJob = viewModelScope.launch {
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

    private var autoNextTriggered = false 



    fun onAction(action: PlayerAction) {
        android.util.Log.d("METRICS", "ACTION [Player] Action=${action.javaClass.simpleName}")
        when (action) {
            is PlayerAction.Play -> if (isMpvMode) mpvPlayer?.resume() else player?.play()
            is PlayerAction.Pause -> if (isMpvMode) mpvPlayer?.pause() else player?.pause()
            is PlayerAction.SeekTo -> if (isMpvMode) mpvPlayer?.seekTo(action.position) else player?.seekTo(action.position)
            is PlayerAction.Next -> {
                playbackManager.next()
                playbackManager.currentMedia.value?.let { loadMedia(it.ratingKey, it.serverId) }
            }
            is PlayerAction.Previous -> {
                playbackManager.previous()
                playbackManager.currentMedia.value?.let { loadMedia(it.ratingKey, it.serverId) }
            }
            is PlayerAction.SelectAudioTrack -> {
                _uiState.update { it.copy(showAudioSelection = false) } // Close dialog
                val state = _uiState.value
                val current = state.currentItem
                if (current != null) {
                    playerTrackController.selectAudioTrack(
                        track = action.track,
                        currentItem = current,
                        currentSubtitleStreamId = state.selectedSubtitle?.streamId,
                        scope = viewModelScope,
                        exoPlayer = player,
                        mpvPlayer = mpvPlayer,
                        isMpvMode = isMpvMode,
                        isDirectPlay = isDirectPlay,
                        audioTracksInUi = state.audioTracks,
                        onReloadRequired = { aId, sId ->
                             loadMedia(current.ratingKey, current.serverId, state.selectedQuality.bitrate, action.track.index, state.selectedSubtitle?.index, aId, sId)
                        }
                    )
                }
            }
            is PlayerAction.SelectSubtitleTrack -> {
                _uiState.update { it.copy(showSubtitleSelection = false) } // Close dialog
                val state = _uiState.value
                val current = state.currentItem
                if (current != null) {
                    playerTrackController.selectSubtitleTrack(
                        track = action.track,
                        currentItem = current,
                        currentAudioStreamId = state.selectedAudio?.streamId,
                        scope = viewModelScope,
                        exoPlayer = player,
                        mpvPlayer = mpvPlayer,
                        isMpvMode = isMpvMode,
                        isDirectPlay = isDirectPlay,
                        subtitleTracksInUi = state.subtitleTracks,
                        onReloadRequired = { aId, sId ->
                             loadMedia(current.ratingKey, current.serverId, state.selectedQuality.bitrate, state.selectedAudio?.index, action.track.index, aId, sId)
                        }
                    )
                }
            }
            is PlayerAction.ShowAudioSelector -> {
                _uiState.update { it.copy(showAudioSelection = true, showSettings = false, showSubtitleSelection = false) }
            }
            is PlayerAction.ShowSubtitleSelector -> {
                _uiState.update { it.copy(showSubtitleSelection = true, showSettings = false, showAudioSelection = false) }
            }
            is PlayerAction.DismissDialog -> {
                // Close any open dialog without stopping playback
                _uiState.update { it.copy(showSettings = false, showAudioSelection = false, showSubtitleSelection = false, showAutoNextPopup = false, showAudioSyncDialog = false, showSubtitleSyncDialog = false, showSpeedSelection = false) }
            }
            is PlayerAction.Close -> {
                // If any dialog is open, close it first. Otherwise, navigate back.
                val state = _uiState.value
                if (state.showSettings || state.showAudioSelection || state.showSubtitleSelection || state.showAutoNextPopup || state.showAudioSyncDialog || state.showSubtitleSyncDialog || state.showSpeedSelection) {
                    _uiState.update { it.copy(showSettings = false, showAudioSelection = false, showSubtitleSelection = false, showAutoNextPopup = false, showAudioSyncDialog = false, showSubtitleSyncDialog = false, showSpeedSelection = false) }
                } else {
                   // This action is typically handled by the UI (onClose callback), but we can clear state here too.
                }
            }
            is PlayerAction.SkipMarker -> skipMarker(action.marker)
            is PlayerAction.PlayNext -> {
                _uiState.update { it.copy(showAutoNextPopup = false) }
                playbackManager.next()
                playbackManager.currentMedia.value?.let { loadMedia(it.ratingKey, it.serverId) }
            }
            is PlayerAction.CancelAutoNext -> {
                _uiState.update { it.copy(showAutoNextPopup = false) }
            }
            is PlayerAction.SelectQuality -> {
                _uiState.update { it.copy(showSettings = false) }
                val current = _uiState.value.currentItem
                if (current != null) {
                    loadMedia(current.ratingKey, current.serverId, action.quality.bitrate)
                }
            }
            is PlayerAction.ToggleSettings -> {
                _uiState.update { it.copy(showSettings = !it.showSettings, showAudioSelection = false, showSubtitleSelection = false) }
            }
            is PlayerAction.SeekToNextChapter -> {
                val currentPos = _uiState.value.currentPosition
                val chapters = chapterMarkerManager.chapters.value
                val nextChapter = chapters.firstOrNull { it.startTime > currentPos + 1000 }
                if (nextChapter != null) {
                    onAction(PlayerAction.SeekTo(nextChapter.startTime))
                } else {
                    onAction(PlayerAction.Next) // Fallback to next episode if no chapters left
                }
            }
            is PlayerAction.SeekToPreviousChapter -> {
                val currentPos = _uiState.value.currentPosition
                val chapters = chapterMarkerManager.chapters.value
                
                // Find current chapter
                val currentChapter = chapters.find { currentPos >= it.startTime && currentPos < it.endTime }
                
                if (currentChapter != null) {
                    // If we are within the first 3 seconds of the chapter, go to previous chapter
                    if (currentPos - currentChapter.startTime < 3000) {
                        val prevChapterIndex = chapters.indexOf(currentChapter) - 1
                        if (prevChapterIndex >= 0) {
                            onAction(PlayerAction.SeekTo(chapters[prevChapterIndex].startTime))
                        } else {
                             // Start of file
                             onAction(PlayerAction.SeekTo(0))
                        }
                    } else {
                        // Go to start of current chapter
                        onAction(PlayerAction.SeekTo(currentChapter.startTime))
                    }
                } else {
                    // Fallback using simple binary search logic or just find generic previous
                    val prevChapter = chapters.filter { it.startTime < currentPos - 3000 }.maxByOrNull { it.startTime }
                    if (prevChapter != null) {
                        onAction(PlayerAction.SeekTo(prevChapter.startTime))
                    } else {
                        onAction(PlayerAction.SeekTo(0))
                    }
                }
            }
            is PlayerAction.SetPlaybackSpeed -> {
                val speed = action.speed
                _uiState.update { it.copy(playbackSpeed = speed, showSpeedSelection = false) }
                mpvPlayer?.setSpeed(speed.toDouble())
                player?.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speed))
            }
            is PlayerAction.ToggleSpeedSelection -> {
                 _uiState.update { it.copy(showSpeedSelection = !it.showSpeedSelection, showSettings = false, showAudioSelection = false, showSubtitleSelection = false, showAudioSyncDialog = false, showSubtitleSyncDialog = false) }
            }
            is PlayerAction.SetAudioDelay -> {
                val delay = action.delayMs
                _uiState.update { it.copy(audioDelay = delay) }
                mpvPlayer?.setAudioDelay(delay)
            }
            is PlayerAction.SetSubtitleDelay -> {
                val delay = action.delayMs
                _uiState.update { it.copy(subtitleDelay = delay) }
                mpvPlayer?.setSubtitleDelay(delay)
            }
            is PlayerAction.ShowAudioSyncSelector -> {
                 _uiState.update { it.copy(showAudioSyncDialog = true, showSettings = false, showAudioSelection = false, showSubtitleSelection = false, showSpeedSelection = false, showSubtitleSyncDialog = false) }
            }
            is PlayerAction.ShowSubtitleSyncSelector -> {
                 _uiState.update { it.copy(showSubtitleSyncDialog = true, showSettings = false, showAudioSelection = false, showSubtitleSelection = false, showSpeedSelection = false, showAudioSyncDialog = false) }
            }
            is PlayerAction.TogglePerformanceOverlay -> {
                val newState = !_uiState.value.showPerformanceOverlay
                _uiState.update { it.copy(showPerformanceOverlay = newState) }
                if (newState) {
                     viewModelScope.launch {
                         playerStatsTracker.stats.collect { stats ->
                             _uiState.update { it.copy(playerStats = stats) }
                         }
                     }
                } else {
                    _uiState.update { it.copy(playerStats = null) }
                }
            }
        }
    }
    
    private var statsJob: Job? = null


    
    private fun skipMarker(marker: com.chakir.plexhubtv.core.model.Marker) {
         val position = marker.endTime
         if (isMpvMode) mpvPlayer?.seekTo(position) else player?.seekTo(position)
    }

    private fun hasHardwareHEVCDecoder(): Boolean {
        return false // Temporary disabling for test debugging
        /*
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        return codecList.codecInfos.any { info ->
            if (info.isEncoder) return@any false
            
            val isHevc = info.supportedTypes.any { it.equals("video/hevc", ignoreCase = true) }
            if (!isHevc) return@any false

            if (android.os.Build.VERSION.SDK_INT >= 29) {
                info.isHardwareAccelerated
            } else {
                // Heuristic for API < 29 (Android 9 and below)
                // Filter out known software decoders
                val name = info.name.lowercase()
                !name.startsWith("omx.google.") && !name.startsWith("c2.android.")
            }
        }*/
    }

    fun getExoPlayer(): ExoPlayer? = player
}
