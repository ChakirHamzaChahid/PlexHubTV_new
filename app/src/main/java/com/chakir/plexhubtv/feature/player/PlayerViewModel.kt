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
import com.chakir.plexhubtv.domain.model.*
import com.chakir.plexhubtv.domain.repository.*
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import com.chakir.plexhubtv.domain.usecase.GetMediaDetailUseCase
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
    private val mediaRepository: MediaRepository,
    private val playbackManager: com.chakir.plexhubtv.core.playback.PlaybackManager,
    private val settingsRepository: SettingsRepository,
    private val trackPreferenceDao: com.chakir.plexhubtv.core.database.TrackPreferenceDao,
    private val watchNextHelper: com.chakir.plexhubtv.core.util.WatchNextHelper,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val ratingKey: String? = savedStateHandle["ratingKey"]
    private val serverId: String? = savedStateHandle["serverId"]
    private val directUrl: String? = savedStateHandle["url"]
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
    private var isDirectPlay = false

    init {
        initializePlayer(application)
        if (directUrl != null) {
            playDirectUrl(directUrl)
        } else if (ratingKey != null && serverId != null) {
            loadMedia(ratingKey, serverId)
            startScrobbling()
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
        stopStatsTracking()
        player?.release()
        player = null
        mpvPlayer?.release()
        positionTrackerJob?.cancel()
        scrobbleJob?.cancel()
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

    private fun loadMedia(rKey: String, sId: String, bitrateOverride: Int? = null, audioIndex: Int? = null, subtitleIndex: Int? = null, audioStreamId: String? = null, subtitleStreamId: String? = null) {
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
                    val part = media.mediaParts.firstOrNull()
                    
                    // HEVC Detection & Fallback
                    // Check if the video is HEVC and if we lack hardware support
                    val videoStream = part?.streams?.filterIsInstance<com.chakir.plexhubtv.domain.model.VideoStream>()?.firstOrNull()
                    val isHevc = videoStream?.codec?.equals("hevc", ignoreCase = true) == true || 
                                 videoStream?.codec?.equals("h265", ignoreCase = true) == true
                                 
                    if (isHevc && !hasHardwareHEVCDecoder() && !isMpvMode) {
                        android.util.Log.w("METRICS", "PLAYER: HEVC detected without hardware support. Switching to MPV.")
                        switchToMpv()
                        // switchToMpv re-triggered loadMedia, so we stop here to avoid double processing or race conditions
                        // However, we are in a coroutine collect block. 
                        // Returning from onSuccess blocks the rest of this specific collection emission processing.
                        return@onSuccess
                    }
                    
                    this@PlayerViewModel.isDirectPlay = bitrate >= 200000 && part?.key != null
                    val isDirectPlay = this@PlayerViewModel.isDirectPlay
                    
                    // --- TRACK SELECTION LOGIC ---
                    // Priority 1: Navigation/Arguments (passed as args to loadMedia)
                    // Priority 2: Per-Media Preference (Local DB)
                    // Priority 3: Plex Metadata Selection (Server)
                    // Priority 4: Global Language Profile (Settings)
                    // Priority 5: Default (First Track)
                    
                    var finalAudioStreamId: String? = audioStreamId // Level 1
                    var finalSubtitleStreamId: String? = subtitleStreamId // Level 1
                    
                    if (finalAudioStreamId == null || finalSubtitleStreamId == null) {
                        // Level 2: DB
                        val dbPref = trackPreferenceDao.getPreferenceSync(rKey, sId)
                        
                        // Audio
                        if (finalAudioStreamId == null) {
                            finalAudioStreamId = dbPref?.audioStreamId
                        }
                        
                        // Subtitle
                        if (finalSubtitleStreamId == null) {
                            finalSubtitleStreamId = dbPref?.subtitleStreamId
                        }
                        
                        // Level 3 & 4: Metadata / Profile
                        if (finalAudioStreamId == null) {
                            val preferredAudioLang = settingsRepository.preferredAudioLanguage.first()
                            
                            val bestAudio = if (preferredAudioLang != null) {
                                // Level 4: Profile Match
                                part?.streams?.filterIsInstance<AudioStream>()?.find { 
                                     areLanguagesEqual(it.language, preferredAudioLang)
                                } ?: part?.streams?.filterIsInstance<AudioStream>()?.find { it.selected } // Fallback to Level 3
                            } else {
                                // Level 3: Plex Default
                                part?.streams?.filterIsInstance<AudioStream>()?.find { it.selected }
                            }
                            finalAudioStreamId = bestAudio?.id
                        }
                        
                        if (finalSubtitleStreamId == null) {
                             val preferredSubLang = settingsRepository.preferredSubtitleLanguage.first()
                             
                             val bestSub = if (preferredSubLang != null) {
                                 // Level 4: Profile Match
                                 part?.streams?.filterIsInstance<SubtitleStream>()?.find { 
                                      areLanguagesEqual(it.language, preferredSubLang)
                                 } ?: part?.streams?.filterIsInstance<SubtitleStream>()?.find { it.selected } // Fallback Level 3
                             } else {
                                 // Level 3: Plex Default
                                 part?.streams?.filterIsInstance<SubtitleStream>()?.find { it.selected }
                             }
                             finalSubtitleStreamId = bestSub?.id
                        }
                    }

                    // Resolve Stream IDs to Indices/Objects if needed
                    val aIndex = part?.streams?.filterIsInstance<AudioStream>()?.find { it.id == finalAudioStreamId }?.index
                    val sIndex = part?.streams?.filterIsInstance<SubtitleStream>()?.find { it.id == finalSubtitleStreamId }?.index
                    
                    val streamUri = if (media.baseUrl != null) {
                        val baseUrl = media.baseUrl!!
                        val cleanBase = baseUrl.trimEnd('/')
                        val token = media.accessToken ?: ""
                        
                        if (isDirectPlay) {
                             // Direct Play Strategy: Use the file key directly
                             val partKey = part!!.key
                             Uri.parse("$cleanBase$partKey?X-Plex-Token=$token")
                        } else {
                            // Transcoding Strategy
                            val path = "/library/metadata/$rKey"
                            val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
                            
                            // Use resolved IDs
                            val aStreamId = finalAudioStreamId
                            val sStreamId = finalSubtitleStreamId

                            val transcodeUrlBuilder = StringBuilder("$cleanBase/video/:/transcode/universal/start.m3u8?")
                            transcodeUrlBuilder.append("path=$encodedPath")
                            transcodeUrlBuilder.append("&mediaIndex=0")
                            transcodeUrlBuilder.append("&partIndex=0")
                            transcodeUrlBuilder.append("&protocol=hls")
                            transcodeUrlBuilder.append("&fastSeek=1")
                            transcodeUrlBuilder.append("&directPlay=0")
                            // Force FULL transcoding (directStream=0) to ensure the server burns subtitles 
                            // and sends ONLY the selected audio track. This fixes multi-track HLS issues.
                            transcodeUrlBuilder.append("&directStream=0")
                            transcodeUrlBuilder.append("&subtitleSize=100")
                            transcodeUrlBuilder.append("&audioBoost=100")
                            transcodeUrlBuilder.append("&location=lan")
                            transcodeUrlBuilder.append("&addDebugOverlay=0")
                            transcodeUrlBuilder.append("&autoAdjustQuality=0")
                            transcodeUrlBuilder.append("&videoQuality=100")
                            transcodeUrlBuilder.append("&maxVideoBitrate=$bitrate")
                            
                            // Send BOTH ID and Index to ensure Plex respects the selection
                            if (aStreamId != null) {
                                transcodeUrlBuilder.append("&audioStreamID=$aStreamId")
                            }
                            if (aIndex != null) {
                                transcodeUrlBuilder.append("&audioIndex=$aIndex")
                            }
                            
                            if (sStreamId != null) {
                                transcodeUrlBuilder.append("&subtitleStreamID=$sStreamId")
                            }
                            if (sIndex != null) {
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
                            
                            // Important: Generate a unique session ID for this playback request
                            // This forces Plex to start a new transcoding session with the selected audio/subtitles
                            // instead of reusing an existing session where the old audio might be stuck.
                            val session = java.util.UUID.randomUUID().toString()
                            transcodeUrlBuilder.append("&session=$session")
                                    
                            val finalUri = Uri.parse(transcodeUrlBuilder.toString())
                            android.util.Log.d("PlayerViewModel", "loadMedia (Transcoding): Generated URL = $finalUri")
                            finalUri
                        }
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
                        val mediaItemBuilder = ExoMediaItem.Builder()
                            .setUri(streamUri)
                            .setMediaId(rKey)
                            
                        // ONLY force M3U8 for Transcoding
                        if (!isDirectPlay) {
                            mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                        }
                            
                        val mediaItem = mediaItemBuilder.build()
    
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
                isSelected = stream.selected,
                streamId = stream.id
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
                isExternal = stream.isExternal,
                streamId = stream.id
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
                // Match based on Language and/or Codec/MimeType since IDs don't match
                val isSelected = group.isTrackSelected(i)
                val language = format.language ?: "und"
                
                if (isSelected) {
                    _uiState.update { state ->
                        when (type) {
                            androidx.media3.common.C.TRACK_TYPE_AUDIO -> {
                                // Find matching UI track by language and attempt codec/channel match
                                val matchingTrack = state.audioTracks.find { uiTrack -> 
                                     areLanguagesEqual(uiTrack.language, language)
                                     // Optional: Check MimeType/Channels if available and reliable
                                } ?: state.selectedAudio
                                
                                state.copy(selectedAudio = matchingTrack)
                            }
                            androidx.media3.common.C.TRACK_TYPE_TEXT -> {
                                val matchingTrack = state.subtitleTracks.find { uiTrack -> 
                                     areLanguagesEqual(uiTrack.language, language)
                                } ?: state.selectedSubtitle
                                state.copy(selectedSubtitle = matchingTrack)
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
                    startStatsTracking()
                } else {
                    stopStatsTracking()
                }
            }
        }
    }
    
    private var statsJob: Job? = null

    private fun startStatsTracking() {
        if (statsJob?.isActive == true) return
        statsJob = viewModelScope.launch {
            while (isActive) {
                if (!_uiState.value.showPerformanceOverlay) {
                    break
                }
                
                val stats = if (isMpvMode) {
                    val mpv = mpvPlayer
                    if (mpv != null) {
                        PlayerStats(
                            bitrate = "${(mpv.videoBitrate.value / 1000).toInt()} kbps",
                            resolution = "${mpv.videoWidth.value}x${mpv.videoHeight.value}",
                            videoCodec = mpv.videoCodec.value,
                            audioCodec = mpv.audioCodec.value,
                            droppedFrames = mpv.droppedFrames.value,
                            fps = mpv.fps.value,
                            cacheDuration = mpv.cacheDuration.value.toLong()
                        )
                    } else null
                } else {
                    val p = player
                    if (p != null) {
                        val videoSize = p.videoSize
                        
                        var vFormat: androidx.media3.common.Format? = null
                        var aFormat: androidx.media3.common.Format? = null
                        
                        // Find currently selected formats
                        val groups = p.currentTracks.groups
                        for (i in 0 until groups.size) {
                            val group = groups[i]
                            if (group.isSelected) {
                                for (j in 0 until group.length) {
                                    if (group.isTrackSelected(j)) {
                                        val format = group.getTrackFormat(j)
                                        if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                                            vFormat = format
                                        } else if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                                            aFormat = format
                                        }
                                    }
                                }
                            }
                        }

                        // DecoderCounters not guaranteed to be accessible on all ExoPlayer versions/interfaces directly
                        val dropped = 0L 
                        
                         PlayerStats(
                            bitrate = "${(vFormat?.bitrate ?: 0) / 1000} kbps",
                            resolution = "${videoSize.width}x${videoSize.height}",
                            videoCodec = vFormat?.sampleMimeType ?: "Unknown",
                            audioCodec = aFormat?.sampleMimeType ?: "Unknown",
                            droppedFrames = dropped,
                            fps = vFormat?.frameRate?.toDouble() ?: 0.0,
                            cacheDuration = (p.bufferedPosition - p.currentPosition).coerceAtLeast(0) / 1000
                        )
                    } else null
                }
                
                if (stats != null) {
                    _uiState.update { it.copy(playerStats = stats) }
                }
                delay(1000)
            }
        }
    }
    
    private fun stopStatsTracking() {
        statsJob?.cancel()
        statsJob = null
        _uiState.update { it.copy(playerStats = null) }
    }
    
    private fun skipMarker(marker: com.chakir.plexhubtv.domain.model.Marker) {
         val position = marker.endTime
         if (isMpvMode) mpvPlayer?.seekTo(position) else player?.seekTo(position)
    }

    private fun hasHardwareHEVCDecoder(): Boolean {
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
        }
    }

    private fun selectTrack(track: AudioTrack) {
        _uiState.update { it.copy(selectedAudio = track) }
        
        // 1. Persist to Local DB (Priority 2: Per-Media)
        viewModelScope.launch {
            try {
                val currentFn = _uiState.value.currentItem
                if (currentFn != null) {
                   var pref = trackPreferenceDao.getPreferenceSync(currentFn.ratingKey, currentFn.serverId)
                   if (pref == null) {
                       pref = com.chakir.plexhubtv.core.database.TrackPreferenceEntity(
                           currentFn.ratingKey, 
                           currentFn.serverId, 
                           audioStreamId = track.streamId, 
                           subtitleStreamId = _uiState.value.selectedSubtitle?.streamId
                       )
                   } else {
                       pref = pref.copy(audioStreamId = track.streamId, lastUpdated = System.currentTimeMillis())
                   }
                   trackPreferenceDao.upsertPreference(pref)
                   android.util.Log.i("PlayerViewModel", "Persisted Audio Preference: ${track.streamId}")
                }
            } catch (e: Exception) {
               android.util.Log.e("PlayerViewModel", "Failed to persist audio preference", e)
            }
        }
        
        // 2. Sync Selection to Plex Server (Priority 3: Plex Metadata)
        viewModelScope.launch {
            val item = _uiState.value.currentItem
            val part = item?.mediaParts?.firstOrNull()
            if (item != null && part != null) {
                try {
                    mediaRepository.updateStreamSelection(
                        serverId = item.serverId,
                        partId = part.id,
                        audioStreamId = track.streamId
                    )
                } catch (e: Exception) {
                    android.util.Log.w("PlayerViewModel", "Failed to sync audio selection: ${e.message}")
                }
            }
        }
        
        if (isMpvMode && isDirectPlay) {
            // MPV uses 1-based relative index for "aid" property
            // We find the index of the track in our UI list + 1
            val index = _uiState.value.audioTracks.indexOf(track) + 1
            if (index > 0) {
                 mpvPlayer?.setAudioId(index.toString())
                 android.util.Log.i("PlayerViewModel", "MPV: Switched to Audio Track $index")
            }
            return
        }

        if (isDirectPlay) {
            val p = player ?: return
            
            // Try to find matching group in ExoPlayer
            val groups = p.currentTracks.groups
            var selectedGroupIndex = -1
            var selectedTrackIndex = -1
            
            // Strategy 1: Match loosely by Language and Type
            for (i in 0 until groups.size) {
                 val group = groups[i]
                 if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                     for (j in 0 until group.length) {
                         val format = group.getTrackFormat(j)
                         if (areLanguagesEqual(format.language, track.language)) {
                             selectedGroupIndex = i
                             selectedTrackIndex = j
                             break
                         }
                     }
                 }
                 if (selectedGroupIndex != -1) break
            }
            
            // Strategy 2: Fallback to Order Match
            if (selectedGroupIndex == -1) {
                 val audioTracksInUI = _uiState.value.audioTracks
                 val uiIndex = audioTracksInUI.indexOf(track)
                 
                 var audioGroupCounter = 0
                 for (i in 0 until groups.size) {
                     if (groups[i].type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                         if (audioGroupCounter == uiIndex) {
                             selectedGroupIndex = i
                             selectedTrackIndex = 0 
                             break
                         }
                         audioGroupCounter++
                     }
                 }
            }

            if (selectedGroupIndex != -1) {
                val builder = p.trackSelectionParameters.buildUpon()
                builder.setOverrideForType(
                    androidx.media3.common.TrackSelectionOverride(groups[selectedGroupIndex].mediaTrackGroup, selectedTrackIndex)
                )
                p.trackSelectionParameters = builder.build()
                android.util.Log.i("PlayerViewModel", "Direct Play: Switched Audio to Group=$selectedGroupIndex Track=$selectedTrackIndex")
            } else {
                 android.util.Log.w("PlayerViewModel", "Direct Play: Could not find matching audio track for ${track.language}")
            }
            
        } else {
            // Transcoding: Reload with new index
            val current = _uiState.value.currentItem
            android.util.Log.d("PlayerViewModel", "selectTrack (Transcoding): CurrentItem=${current?.ratingKey}, TrackIndex=${track.index}, StreamId=${track.streamId}")
            
            // CRITICAL: Update ExoPlayer preferences even for Transcoding.
            // If directStream=1 is active, Plex sends an HLS with multiple audio tracks.
            // We must tell ExoPlayer which one to pick to avoid it reverting to default.
            player?.let { p ->
                val builder = p.trackSelectionParameters.buildUpon()
                if (track.language != null) {
                    builder.setPreferredAudioLanguage(track.language)
                }
                p.trackSelectionParameters = builder.build()
            }
            
            if (current != null) {
                val sIndex = _uiState.value.selectedSubtitle?.index
                val sStreamId = _uiState.value.selectedSubtitle?.streamId
                android.util.Log.d("PlayerViewModel", "selectTrack (Transcoding): Calling loadMedia with audioStreamId=${track.streamId}, subtitleStreamId=$sStreamId")
                loadMedia(current.ratingKey, current.serverId, _uiState.value.selectedQuality.bitrate, track.index, sIndex, track.streamId, sStreamId)
            } else {
                android.util.Log.e("PlayerViewModel", "selectTrack (Transcoding): CurrentItem is NULL")
            }
        }
    }

    private fun selectSubtitle(track: SubtitleTrack) {
         _uiState.update { it.copy(selectedSubtitle = track) }
         
        // 1. Persist to Local DB (Priority 2: Per-Media)
        viewModelScope.launch {
            try {
                val currentFn = _uiState.value.currentItem
                if (currentFn != null) {
                   var pref = trackPreferenceDao.getPreferenceSync(currentFn.ratingKey, currentFn.serverId)
                   // Use "0" as sentinel for "Subtitle Off" because streamId is null for OFF track
                   val subStreamIdToSave = if (track.id == "no") "0" else track.streamId
                   
                   if (pref == null) {
                       pref = com.chakir.plexhubtv.core.database.TrackPreferenceEntity(
                           currentFn.ratingKey, 
                           currentFn.serverId, 
                           audioStreamId = _uiState.value.selectedAudio?.streamId,
                           subtitleStreamId = subStreamIdToSave
                       )
                   } else {
                       pref = pref.copy(subtitleStreamId = subStreamIdToSave, lastUpdated = System.currentTimeMillis())
                   }
                   trackPreferenceDao.upsertPreference(pref)
                   android.util.Log.i("PlayerViewModel", "Persisted Subtitle Preference: $subStreamIdToSave")
                }
            } catch (e: Exception) {
               android.util.Log.e("PlayerViewModel", "Failed to persist subtitle preference", e)
            }
        }
         
        // 2. Sync Selection to Plex Server
        viewModelScope.launch {
            val item = _uiState.value.currentItem
            val part = item?.mediaParts?.firstOrNull()
            if (item != null && part != null) {
                try {
                    // Send "0" if disabling subtitles
                    val streamId = if (track.id == "no" || track.streamId == null) "0" else track.streamId
                    mediaRepository.updateStreamSelection(
                        serverId = item.serverId,
                        partId = part.id,
                        subtitleStreamId = streamId
                    )
                } catch (e: Exception) {
                    android.util.Log.w("PlayerViewModel", "Failed to sync subtitle selection: ${e.message}")
                }
            }
        }
         
         if (isMpvMode && isDirectPlay) {
             if (track.id == "no") {
                 mpvPlayer?.setSubtitleId("no")
                 android.util.Log.i("PlayerViewModel", "MPV: Subtitles Disabled")
             } else {
                 // Filter out 'OFF' option to get correct index
                 val validTracks = _uiState.value.subtitleTracks.filter { it.id != "no" }
                 val index = validTracks.indexOf(track) + 1
                 if (index > 0) {
                     mpvPlayer?.setSubtitleId(index.toString())
                     android.util.Log.i("PlayerViewModel", "MPV: Switched to Subtitle Track $index")
                 }
             }
             return
         }
         
         if (isDirectPlay) {
             val p = player ?: return
             val builder = p.trackSelectionParameters.buildUpon()

             if (track.id == "no") {
                 builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                 android.util.Log.i("PlayerViewModel", "Direct Play: Subtitles Disabled")
             } else {
                 builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                 
                val groups = p.currentTracks.groups
                var selectedGroupIndex = -1
                var selectedTrackIndex = -1
                
                // Strategy 1: Match by Language
                for (i in 0 until groups.size) {
                     val group = groups[i]
                     if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                         for (j in 0 until group.length) {
                             val format = group.getTrackFormat(j)
                             if (areLanguagesEqual(format.language, track.language)) {
                                 selectedGroupIndex = i
                                 selectedTrackIndex = j
                                 break
                             }
                         }
                     }
                     if (selectedGroupIndex != -1) break
                }
                
                // Strategy 2: Order Match
                if (selectedGroupIndex == -1) {
                     val subtitleTracksInUI = _uiState.value.subtitleTracks.filter { it.id != "no" }
                     val uiIndex = subtitleTracksInUI.indexOf(track)
                     
                     var textGroupCounter = 0
                     for (i in 0 until groups.size) {
                         if (groups[i].type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                             if (textGroupCounter == uiIndex) {
                                 selectedGroupIndex = i
                                 selectedTrackIndex = 0
                                 break
                             }
                             textGroupCounter++
                         }
                     }
                }

                if (selectedGroupIndex != -1) {
                     builder.setOverrideForType(
                         androidx.media3.common.TrackSelectionOverride(groups[selectedGroupIndex].mediaTrackGroup, selectedTrackIndex)
                     )
                     android.util.Log.i("PlayerViewModel", "Direct Play: Switched Subtitle to Group=$selectedGroupIndex Track=$selectedTrackIndex")
                } else {
                    android.util.Log.w("PlayerViewModel", "Direct Play: Could not find matching subtitle track for ${track.language}")
                }
             }
             p.trackSelectionParameters = builder.build()
             
         } else {
             // Transcoding: Reload
            val current = _uiState.value.currentItem
            android.util.Log.d("PlayerViewModel", "selectSubtitle (Transcoding): CurrentItem=${current?.ratingKey}, TrackIndex=${track.index}, StreamId=${track.streamId}")
            
            // CRITICAL: Update ExoPlayer preferences even for Transcoding.
            player?.let { p ->
                val builder = p.trackSelectionParameters.buildUpon()
                if (track.id == "no") {
                     builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                } else {
                     builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                     if (track.language != null) {
                         builder.setPreferredTextLanguage(track.language)
                     }
                }
                p.trackSelectionParameters = builder.build()
            }
            
            if (current != null) {
                val aIndex = _uiState.value.selectedAudio?.index
                val aStreamId = _uiState.value.selectedAudio?.streamId
                 android.util.Log.d("PlayerViewModel", "selectSubtitle (Transcoding): Calling loadMedia with audioStreamId=$aStreamId, subtitleStreamId=${track.streamId}")
                loadMedia(current.ratingKey, current.serverId, _uiState.value.selectedQuality.bitrate, aIndex, track.index, aStreamId, track.streamId)
            } else {
                 android.util.Log.e("PlayerViewModel", "selectSubtitle (Transcoding): CurrentItem is NULL")
            }
         }
    }
    
    fun getExoPlayer(): ExoPlayer? = player



    private fun areLanguagesEqual(lang1: String?, lang2: String?): Boolean {
        if (lang1 == lang2) return true
        if (lang1.isNullOrEmpty() || lang1 == "und") {
            return lang2.isNullOrEmpty() || lang2 == "und"
        }
        if (lang2.isNullOrEmpty() || lang2 == "und") return false
        
        val normalized1 = lang1.lowercase().trim()
        val normalized2 = lang2.lowercase().trim()
        
        if (normalized1 == normalized2) return true
        
        return try {
            val l1 = if (normalized1.length == 3) java.util.Locale.forLanguageTag(normalized1) else java.util.Locale(normalized1)
            val l2 = if (normalized2.length == 3) java.util.Locale.forLanguageTag(normalized2) else java.util.Locale(normalized2)
            
            // Compare ISO3 language codes (e.g. fre == fra, eng == eng)
            l1.isO3Language.equals(l2.isO3Language, ignoreCase = true)
        } catch (e: Exception) {
            // Fallback for names vs codes (e.g. "French" vs "fra")
            // This is approximate but helps if legacy data exists
             try {
                val l1 = java.util.Locale(normalized1)
                val l2 = java.util.Locale(normalized2)
                l1.isO3Language.equals(l2.isO3Language, ignoreCase = true)
            } catch (e2: Exception) {
                 normalized1 == normalized2
            }
        }
    }
}
