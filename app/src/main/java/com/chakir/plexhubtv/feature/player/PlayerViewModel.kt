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
import com.chakir.plexhubtv.domain.model.AudioTrack
import com.chakir.plexhubtv.domain.model.SubtitleTrack
import com.chakir.plexhubtv.domain.repository.*
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import com.chakir.plexhubtv.domain.usecase.GetMediaDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application,
    private val getMediaDetailUseCase: GetMediaDetailUseCase,
    private val mediaRepository: MediaRepository,
    private val playbackManager: com.chakir.plexhubtv.core.playback.PlaybackManager,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val ratingKey: String = checkNotNull(savedStateHandle["ratingKey"])
    private val serverId: String = checkNotNull(savedStateHandle["serverId"])
    private val startOffset: Long = savedStateHandle.get<Long>("startOffset") ?: 0L

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var player: ExoPlayer? = null
    var mpvPlayer: com.chakir.plexhubtv.feature.player.mpv.MpvPlayerWrapper? = null
        private set
        
    val chapterMarkerManager = ChapterMarkerManager()

    private var positionTrackerJob: Job? = null
    private var scrobbleJob: Job? = null
    private var isMpvMode = false

    init {
        initializePlayer(application)
        loadMedia(ratingKey, serverId)
        startPositionTracking()
        startScrobbling()
    }
    
    private fun switchToMpv() {
        if (isMpvMode) return
        isMpvMode = true
        
        // Release ExoPlayer
        player?.release()
        player = null
        
        // Init MPV
        mpvPlayer = com.chakir.plexhubtv.feature.player.mpv.MpvPlayerWrapper(getApplication(), viewModelScope)
        // Note: UI must call mpvPlayer.initialize(viewGroup) or attach surface
        
        _uiState.update { it.copy(isMpvFallback = true, error = null) }
        
        // Reload current media in MPV
        val currentItem = uiState.value.currentItem
        if (currentItem != null) {
            loadMedia(ratingKey, serverId) // Reuse logic, logic will adapt
        }
    }

    private fun initializePlayer(context: Application) {
        player = ExoPlayer.Builder(context).build().apply {
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
                            updateTracks()
                        }
                        Player.STATE_ENDED -> { /* Handle completion */ }
                        else -> {}
                    }
                }

                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    updateTracks()
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

    private fun loadMedia(rKey: String, sId: String, bitrateOverride: Int? = null) {
        viewModelScope.launch {
            // Get Settings
            val qualityPref = settingsRepository.getVideoQuality().first()
            val engine = settingsRepository.playerEngine.first()
            
            // Determine Bitrate: Override > Pref > Default
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
                    
                    autoNextTriggered = false // Reset trigger
                    val next = playbackManager.getNextMedia()
                    _uiState.update {
                        it.copy(
                            currentItem = detail.item,
                            nextItem = next,
                            showAutoNextPopup = false,
                            currentPosition = if (it.currentItem?.id != detail.item.id) 0L else it.currentPosition
                        )
                    }
                    
                    val media = detail.item
                    chapterMarkerManager.setChapters(media.chapters)
                    chapterMarkerManager.setMarkers(media.markers)
                    
                    val token = media.accessToken ?: ""
                    val clientId = settingsRepository.clientId.first() ?: "PlexHubTV-Client"
                    
                    val uriStartTime = System.currentTimeMillis()
                    val streamUri = if (media.baseUrl != null) {
                        val baseUrl = media.baseUrl!!
                        val cleanBase = baseUrl.trimEnd('/')
                        val path = "/library/metadata/$rKey"
                        val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
                        
                        // Universal Transcode URL with full Plex headers to avoid 400 error
                        val transcodeUrl = "$cleanBase/video/:/transcode/universal/start.m3u8?" +
                                "path=$encodedPath" +
                                "&mediaIndex=0" +
                                "&partIndex=0" +
                                "&protocol=hls" +
                                "&fastSeek=1" +
                                "&directPlay=0" +
                                "&directStream=1" +
                                "&subtitleSize=100" +
                                "&audioBoost=100" +
                                "&location=lan" +
                                "&addDebugOverlay=0" +
                                "&autoAdjustQuality=0" +
                                "&videoQuality=100" +
                                "&maxVideoBitrate=$bitrate" +
                                "&X-Plex-Token=$token" +
                                "&X-Plex-Client-Identifier=$clientId" +
                                "&X-Plex-Platform=Android" +
                                "&X-Plex-Platform-Version=${android.os.Build.VERSION.RELEASE}" +
                                "&X-Plex-Product=PlexHubTV" +
                                "&X-Plex-Device=${java.net.URLEncoder.encode(android.os.Build.MODEL, "UTF-8")}"
                                
                        Uri.parse(transcodeUrl)
                    } else {
                        null
                    }
                    val uriDuration = System.currentTimeMillis() - uriStartTime
                    android.util.Log.i("METRICS", "SCREEN [Player] URI Generated: duration=${uriDuration}ms")
                    
                    if (streamUri == null) {
                        _uiState.update { it.copy(error = "Unable to play media: Invalid URL") }
                        return@collect
                    }

                    val playerStartTime = System.currentTimeMillis()
                    if (isMpvMode) {
                        // MPV Logic
                        mpvPlayer?.play(streamUri.toString())
                        android.util.Log.i("METRICS", "SCREEN [Player] SUCCESS: MPV Start duration=${System.currentTimeMillis() - playerStartTime}ms")
                    } else {
                        // ExoPlayer Logic
                        val mediaItem = ExoMediaItem.Builder()
                            .setUri(streamUri)
                            .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                            .setMediaId(rKey)
                            .build()
    
                        player?.apply {
                            setMediaItem(mediaItem)
                            prepare()
                            val currentPos = _uiState.value.currentPosition
                            if (currentPos > 0) {
                                seekTo(currentPos) // Resume from current playback pos if changing quality
                            } else if (startOffset > 0) {
                                seekTo(startOffset)
                            } else if (media.viewOffset != null) {
                                seekTo(media.viewOffset)
                            }
                            playWhenReady = true
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

    private fun updateTracks() {
        val exoPlayer = player ?: return
        val currentTracks = exoPlayer.currentTracks
        
        val audios = mutableListOf<AudioTrack>()
        val subtitles = mutableListOf<SubtitleTrack>()
        
        currentTracks.groups.forEach { group ->
            val type = group.type
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val isSelected = group.isTrackSelected(i)
                val id = format.id ?: "$type-$i"
                
                // Fallback for Title/Language
                val language = format.language ?: "und"
                val displayTitle = format.label ?: java.util.Locale(language).displayLanguage.takeIf { it.isNotEmpty() } ?: "Unknown"
                
                when (type) {
                    androidx.media3.common.C.TRACK_TYPE_AUDIO -> {
                        audios.add(AudioTrack(
                            id = id,
                            title = displayTitle,
                            language = language,
                            codec = format.sampleMimeType,
                            channels = format.channelCount,
                            isSelected = isSelected
                        ))
                    }
                    androidx.media3.common.C.TRACK_TYPE_VIDEO -> {} // Handle video tracks if needed
                    androidx.media3.common.C.TRACK_TYPE_TEXT -> {
                        subtitles.add(SubtitleTrack(
                            id = id,
                            title = displayTitle,
                            language = language,
                            codec = format.sampleMimeType,
                            isSelected = isSelected
                        ))
                    }
                }
            }
        }
        
        _uiState.update { state ->
            state.copy(
                audioTracks = audios,
                subtitleTracks = subtitles,
                selectedAudio = audios.find { it.isSelected },
                selectedSubtitle = subtitles.find { it.isSelected } ?: SubtitleTrack("no", "Off", "", "", false, true)
            )
        }
    }

    private fun startPositionTracking() {
        positionTrackerJob = viewModelScope.launch {
            while (isActive) {
                if (isMpvMode) {
                    // Update from MPV State
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
                        checkAutoNext(pos, dur)
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
                            checkAutoNext(pos, dur)
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    private var autoNextTriggered = false 

    private fun checkAutoNext(position: Long, duration: Long) {
        if (duration <= 1000) return // Avoid triggering on empty/loading state
        val progress = position.toFloat() / duration.toFloat()
        val state = _uiState.value
        
        // Show popup at 90% if not triggered and next item exists
        if (progress >= 0.9f && !autoNextTriggered && state.nextItem != null && !state.showAutoNextPopup) {
            autoNextTriggered = true
            _uiState.update { it.copy(showAutoNextPopup = true) }
        }
        
        // Optional: Auto-dismiss or auto-play at 99% if we want, but user only asked for 90% popup.
    }

    private fun startScrobbling() {
        scrobbleJob = viewModelScope.launch {
            while (isActive) {
                delay(10000) // Scrobble every 10 seconds
                val state = uiState.value
                val item = state.currentItem ?: continue
                if (state.isPlaying) {
                    mediaRepository.updatePlaybackProgress(item, state.currentPosition)
                }
            }
        }
    }

    fun onAction(action: PlayerAction) {
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
                selectTrack(action.track)
            }
            is PlayerAction.SelectSubtitleTrack -> {
                _uiState.update { it.copy(showSubtitleSelection = false) } // Close dialog
                selectSubtitle(action.track)
            }
            is PlayerAction.ShowAudioSelector -> {
                _uiState.update { it.copy(showAudioSelection = true, showSettings = false, showSubtitleSelection = false) }
            }
            is PlayerAction.ShowSubtitleSelector -> {
                _uiState.update { it.copy(showSubtitleSelection = true, showSettings = false, showAudioSelection = false) }
            }
            is PlayerAction.Close -> {
                // If any dialog is open, close it first. Otherwise, navigate back.
                val state = _uiState.value
                if (state.showSettings || state.showAudioSelection || state.showSubtitleSelection || state.showAutoNextPopup) {
                    _uiState.update { it.copy(showSettings = false, showAudioSelection = false, showSubtitleSelection = false, showAutoNextPopup = false) }
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
            else -> { /* Other actions */ }
        }
    }
    
    private fun skipMarker(marker: com.chakir.plexhubtv.domain.model.Marker) {
         val position = marker.endTime
         if (isMpvMode) mpvPlayer?.seekTo(position) else player?.seekTo(position)
    }

    private fun selectTrack(track: AudioTrack) {
        if (isMpvMode) return
        
        player?.let { p ->
             val trackGroups = p.currentTracks.groups
             // Find group index and track index
             // TrackSelectionOverrides needs TrackGroup
             // This is complex with just ID string.
             // Ideally we should store TrackGroup info in AudioTrack or re-iterate.
             
             // Simplification: We iterate groups again to find matching ID
             var found = false
             val builder = p.trackSelectionParameters.buildUpon()
             
             for (group in trackGroups) {
                 if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                     for (i in 0 until group.length) {
                         val format = group.getTrackFormat(i)
                         val id = format.id ?: "audio-$i"
                         if (id == track.id) {
                             builder.setOverrideForType(
                                 androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i)
                             )
                             found = true
                             break
                         }
                     }
                 }
                 if (found) break
             }
             p.trackSelectionParameters = builder.build()
        }
    }

    private fun selectSubtitle(track: SubtitleTrack) {
        player?.let { p ->
             val builder = p.trackSelectionParameters.buildUpon()
             
             if (track.id == "no") {
                 builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
             } else {
                 builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                 
                 val trackGroups = p.currentTracks.groups
                 var found = false
                 for (group in trackGroups) {
                     if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                         for (i in 0 until group.length) {
                             val format = group.getTrackFormat(i)
                             val id = format.id ?: "text-$i"
                             if (id == track.id) {
                                 builder.setOverrideForType(
                                     androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i)
                                 )
                                 found = true
                                 break
                             }
                         }
                     }
                     if (found) break
                 }
             }
             p.trackSelectionParameters = builder.build()
        }
    }
    
    fun getExoPlayer(): ExoPlayer? = player

    override fun onCleared() {
        super.onCleared()
        player?.release()
        player = null
        positionTrackerJob?.cancel()
        scrobbleJob?.cancel()
    }
}
