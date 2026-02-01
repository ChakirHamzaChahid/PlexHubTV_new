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
import com.chakir.plexhubtv.domain.model.*
import com.chakir.plexhubtv.domain.repository.*
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import com.chakir.plexhubtv.domain.usecase.GetMediaDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
/**
 * ViewModel principal pour le lecteur vidéo.
 * Gère ExoPlayer et MPV, la sélection des pistes (Audio/Sous-titres), la qualité vidéo, et le suivi de progression (Scrobbling).
 */
class PlayerViewModel @Inject constructor(
    application: Application,
    private val getMediaDetailUseCase: GetMediaDetailUseCase,
    private val mediaRepository: MediaRepository,
    private val playbackManager: com.chakir.plexhubtv.core.playback.PlaybackManager,
    private val settingsRepository: SettingsRepository,
    private val watchNextHelper: com.chakir.plexhubtv.core.util.WatchNextHelper,
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
        android.util.Log.d("METRICS", "SCREEN [Player] switchToMpv() called")
        if (isMpvMode) return
        isMpvMode = true
        
        // Release ExoPlayer
        player?.release()
        player = null
        
        // Init MPV
        mpvPlayer = com.chakir.plexhubtv.feature.player.mpv.MpvPlayerWrapper(getApplication(), viewModelScope)
        // Note: UI must call mpvPlayer.initialize(viewGroup) or attach surface
        
        _uiState.update { it.copy(isMpvMode = true, error = null) }
        
        // Reload current media in MPV (Ensures loadMedia continues after transition)
        loadMedia(ratingKey, serverId)
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

    private fun loadMedia(rKey: String, sId: String, bitrateOverride: Int? = null, audioIndex: Int? = null, subtitleIndex: Int? = null) {
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
                    
                    // Populate tracks from metadata if state is empty or item changed
                    if (_uiState.value.currentItem?.id != detail.item.id) {
                        populateTracksFromMetadata(detail.item)
                    }

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
                        
                        // Use provided indices or fall back to metadata selection
                        // Note: subtitleIndex = -1 means "None"
                        val sIndex = subtitleIndex ?: media.mediaParts.firstOrNull()?.streams?.find { it is SubtitleStream && it.selected }?.index
                        val aIndex = audioIndex ?: media.mediaParts.firstOrNull()?.streams?.find { it is AudioStream && it.selected }?.index

                        // Universal Transcode URL with full Plex headers to avoid 400 error
                        val transcodeUrlBuilder = StringBuilder("$cleanBase/video/:/transcode/universal/start.m3u8?")
                        transcodeUrlBuilder.append("path=$encodedPath")
                        transcodeUrlBuilder.append("&mediaIndex=0")
                        transcodeUrlBuilder.append("&partIndex=0")
                        transcodeUrlBuilder.append("&protocol=hls")
                        transcodeUrlBuilder.append("&fastSeek=1")
                        transcodeUrlBuilder.append("&directPlay=0")
                        transcodeUrlBuilder.append("&directStream=1")
                        transcodeUrlBuilder.append("&subtitleSize=100")
                        transcodeUrlBuilder.append("&audioBoost=100")
                        transcodeUrlBuilder.append("&location=lan")
                        transcodeUrlBuilder.append("&addDebugOverlay=0")
                        transcodeUrlBuilder.append("&autoAdjustQuality=0")
                        transcodeUrlBuilder.append("&videoQuality=100")
                        transcodeUrlBuilder.append("&maxVideoBitrate=$bitrate")
                        
                        if (aIndex != null) {
                            transcodeUrlBuilder.append("&audioIndex=$aIndex")
                        }
                        if (sIndex != null) {
                            // If index is -1, we omit or set to null? Plex usually uses negative or omission for none.
                            if (sIndex >= 0) {
                                transcodeUrlBuilder.append("&subtitleIndex=$sIndex")
                            }
                        }

                        transcodeUrlBuilder.append("&X-Plex-Token=$token")
                        transcodeUrlBuilder.append("&X-Plex-Client-Identifier=$clientId")
                        transcodeUrlBuilder.append("&X-Plex-Platform=Android")
                        transcodeUrlBuilder.append("&X-Plex-Platform-Version=${android.os.Build.VERSION.RELEASE}")
                        transcodeUrlBuilder.append("&X-Plex-Product=PlexHubTV")
                        transcodeUrlBuilder.append("&X-Plex-Device=${java.net.URLEncoder.encode(android.os.Build.MODEL, "UTF-8")}")
                                
                        Uri.parse(transcodeUrlBuilder.toString())
                    } else {
                        null
                    }
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
                                seekTo(currentPos) // Resume from current playback pos if changing quality or tracks
                            } else if (startOffset > 0) {
                                seekTo(startOffset)
                            } else if (media.viewOffset > 0) {
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

    private fun populateTracksFromMetadata(item: MediaItem) {
        val part = item.mediaParts.firstOrNull() ?: return
        val audios = part.streams.filterIsInstance<com.chakir.plexhubtv.domain.model.AudioStream>().map { stream ->
            AudioTrack(
                id = "plex-${stream.index}",
                title = stream.displayTitle ?: stream.title ?: "Audio",
                language = stream.language,
                codec = stream.codec,
                channels = stream.channels,
                index = stream.index,
                isSelected = stream.selected
            )
        }
        val subtitles = mutableListOf<SubtitleTrack>()
        subtitles.add(SubtitleTrack.OFF) // Add Off option
        
        subtitles.addAll(part.streams.filterIsInstance<com.chakir.plexhubtv.domain.model.SubtitleStream>().map { stream ->
            SubtitleTrack(
                id = "plex-${stream.index}",
                title = stream.displayTitle ?: stream.title ?: "Subtitle",
                language = stream.language,
                codec = stream.codec,
                index = stream.index,
                isSelected = stream.selected,
                isExternal = stream.isExternal
            )
        })

        _uiState.update { state ->
            state.copy(
                audioTracks = audios,
                subtitleTracks = subtitles,
                selectedAudio = audios.find { it.isSelected },
                selectedSubtitle = subtitles.find { it.isSelected } ?: SubtitleTrack.OFF
            )
        }
    }

    private fun updateTracks() {
        val exoPlayer = player ?: return
        val currentTracks = exoPlayer.currentTracks
        
        val audios = mutableListOf<AudioTrack>()
        val subtitles = mutableListOf<SubtitleTrack>()
        
        android.util.Log.d("PlayerViewModel", "Updating tracks - Total groups: ${currentTracks.groups.size}")
        
        currentTracks.groups.forEach { group ->
            val type = group.type
            android.util.Log.d("PlayerViewModel", "Group type: $type, length: ${group.length}")
            
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val isSelected = group.isTrackSelected(i)
                
                // Deterministic ID: "groupIndex:trackIndex"
                val groupIndex = currentTracks.groups.indexOf(group)
                val id = "$groupIndex:$i"
                
                // Improved language display logic
                val trackLang = format.language
                val language = trackLang ?: "und"
                val displayTitle = when {
                    !format.label.isNullOrEmpty() -> format.label
                    !trackLang.isNullOrEmpty() && trackLang != "und" -> {
                        try {
                            val locale = java.util.Locale.forLanguageTag(trackLang)
                            locale.displayLanguage.takeIf { it.isNotEmpty() } ?: trackLang.uppercase()
                        } catch (e: Exception) {
                            trackLang.uppercase()
                        }
                    }
                    else -> "Track ${i + 1}" // Numbered fallback for undefined tracks
                }
                
                android.util.Log.d("PlayerViewModel", "Track $i (Group $groupIndex): id=$id, lang=$language, title=$displayTitle, selected=$isSelected")
                
                // If using ExoPlayer native tracks, we map them too. 
                // But for Transcoding, we prioritize Plex Metadata tracks populated in loadMedia.
                // We only update track selection state here if they match.
                
                if (isSelected) {
                    _uiState.update { state ->
                        when (type) {
                            androidx.media3.common.C.TRACK_TYPE_AUDIO -> {
                                val plexTrack = state.audioTracks.find { it.index != null && id.endsWith(":${it.index}") } // Heuristic match if needed
                                state.copy(selectedAudio = state.audioTracks.find { it.id == id } ?: state.selectedAudio)
                            }
                            androidx.media3.common.C.TRACK_TYPE_TEXT -> {
                                state.copy(selectedSubtitle = state.subtitleTracks.find { it.id == id } ?: state.selectedSubtitle)
                            }
                            else -> state
                        }
                    }
                }
            }
        }
        
        android.util.Log.d("PlayerViewModel", "Found ${audios.size} audio tracks, ${subtitles.size} subtitle tracks from ExoPlayer")
        
        // Note: For transcoding, we typically don't want to OVERWRITE the tracks from metadata 
        // because ExoPlayer only sees the currently muxed tracks.
        // We only want to update the 'isSelected' status of our existing tracks if possible.
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
                    // Wrap in try-catch to prevent crashes from network/API errors
                    try {
                        mediaRepository.updatePlaybackProgress(item, state.currentPosition)
                    } catch (e: Exception) {
                        android.util.Log.w("Player", "Scrobble failed: ${e.message}")
                    }
                    
                    try {
                        watchNextHelper.updateWatchNext(item, state.currentPosition, state.duration)
                    } catch (e: Exception) {
                        android.util.Log.w("Player", "WatchNext update failed: ${e.message}")
                    }
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
            is PlayerAction.DismissDialog -> {
                // Close any open dialog without stopping playback
                _uiState.update { it.copy(showSettings = false, showAudioSelection = false, showSubtitleSelection = false, showAutoNextPopup = false) }
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
        }
    }
    
    private fun skipMarker(marker: com.chakir.plexhubtv.domain.model.Marker) {
         val position = marker.endTime
         if (isMpvMode) mpvPlayer?.seekTo(position) else player?.seekTo(position)
    }

    private fun selectTrack(track: AudioTrack) {
        // If it's a plex track (has index), we reload to let the server change audio
        val current = _uiState.value.currentItem
        if (current != null) {
            _uiState.update { it.copy(selectedAudio = track) }
            // Use current quality, current subtitle index, and NEW audio index
            val sIndex = _uiState.value.selectedSubtitle?.index
            loadMedia(current.ratingKey, current.serverId, _uiState.value.selectedQuality.bitrate, track.index, sIndex)
            return
        }

        if (isMpvMode) return
        
        player?.let { p ->
             try {
                 val parts = track.id.split(":")
                 if (parts.size == 2) {
                     val groupIndex = parts[0].toInt()
                     val trackIndex = parts[1].toInt()
                     
                     val groups = p.currentTracks.groups
                     if (groupIndex < groups.size) {
                         val group = groups[groupIndex]
                         val builder = p.trackSelectionParameters.buildUpon()
                         builder.setOverrideForType(
                             androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                         )
                         p.trackSelectionParameters = builder.build()
                         android.util.Log.i("PlayerViewModel", "Selected audio track: $groupIndex:$trackIndex")
                     }
                 }
             } catch (e: Exception) {
                 android.util.Log.e("PlayerViewModel", "Failed to select audio track", e)
             }
        }
    }

    private fun selectSubtitle(track: SubtitleTrack) {
        // If it's a plex track (has index), we reload to let the server burn/mux different subtitles
        val current = _uiState.value.currentItem
        if (current != null) {
            _uiState.update { it.copy(selectedSubtitle = track) }
            // Use current quality, NEW subtitle index, and current audio index
            val aIndex = _uiState.value.selectedAudio?.index
            loadMedia(current.ratingKey, current.serverId, _uiState.value.selectedQuality.bitrate, aIndex, track.index)
            return
        }

        player?.let { p ->
             val builder = p.trackSelectionParameters.buildUpon()
             
             if (track.id == "no") {
                 builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                 android.util.Log.i("PlayerViewModel", "Subtitles disabled")
             } else {
                 try {
                     builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                     val parts = track.id.split(":")
                     if (parts.size == 2) {
                         val groupIndex = parts[0].toInt()
                         val trackIndex = parts[1].toInt()
                         
                         val groups = p.currentTracks.groups
                         if (groupIndex < groups.size) {
                             val group = groups[groupIndex]
                             builder.setOverrideForType(
                                 androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                             )
                             android.util.Log.i("PlayerViewModel", "Selected subtitle track: $groupIndex:$trackIndex")
                         }
                     }
                 } catch (e: Exception) {
                     android.util.Log.e("PlayerViewModel", "Failed to select subtitle track", e)
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
