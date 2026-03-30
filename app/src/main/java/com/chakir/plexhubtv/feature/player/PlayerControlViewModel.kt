package com.chakir.plexhubtv.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.feature.common.BaseViewModel
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import com.chakir.plexhubtv.feature.player.controller.PlayerController
import com.chakir.plexhubtv.feature.player.controller.TrickplayManager
import com.chakir.plexhubtv.feature.player.controller.ChapterMarkerManager
import com.chakir.plexhubtv.feature.player.url.DirectStreamUrlBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import com.chakir.plexhubtv.core.model.SubtitlePreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PlayerControlViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val savedStateHandle: SavedStateHandle,
    val chapterMarkerManager: ChapterMarkerManager,
    val trickplayManager: TrickplayManager,
    private val playbackManager: com.chakir.plexhubtv.domain.service.PlaybackManager,
    private val directStreamUrlBuilder: DirectStreamUrlBuilder,
    val subtitleSearchService: com.chakir.plexhubtv.feature.player.controller.SubtitleSearchService,
    val audioEqualizerManager: com.chakir.plexhubtv.feature.player.controller.AudioEqualizerManager,
    settingsRepository: SettingsRepository,
) : BaseViewModel() {

    val uiState = playerController.uiState

    val autoPlayNextEnabled: StateFlow<Boolean> = settingsRepository.autoPlayNextEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val skipIntroMode: StateFlow<String> = settingsRepository.skipIntroMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "ask")

    val skipCreditsMode: StateFlow<String> = settingsRepository.skipCreditsMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "ask")

    val subtitlePrefs: StateFlow<SubtitlePreferences> = combine(
        settingsRepository.subtitleFontSize,
        settingsRepository.subtitleFontColor,
        settingsRepository.subtitleBgColor,
        settingsRepository.subtitleEdgeType,
        settingsRepository.subtitleEdgeColor,
    ) { fontSize, fontColor, bgColor, edgeType, edgeColor ->
        SubtitlePreferences(fontSize, fontColor, bgColor, edgeType, edgeColor)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SubtitlePreferences())

    init {
        // Collect playback queue state into UI state
        viewModelScope.launch {
            playbackManager.state.collect { pbState ->
                playerController.updateState {
                    it.copy(
                        playQueue = pbState.playQueue.toImmutableList(),
                        currentQueueIndex = pbState.currentIndex,
                    )
                }
            }
        }

        val ratingKey: String? = savedStateHandle["ratingKey"]
        val serverId: String? = savedStateHandle["serverId"]
        val directUrl: String? = savedStateHandle["url"]
        val startOffset: Long = savedStateHandle.get<Long>("startOffset") ?: 0L

        if (directUrl != null) {
            // Already have direct URL (IPTV or pre-built Xtream URL)
            playerController.initialize(ratingKey, serverId, directUrl, startOffset)
        } else if (serverId != null && ratingKey != null && directStreamUrlBuilder.isDirectStream(serverId)) {
            // Direct-stream source (Xtream/Backend): resolve URL then play
            resolveAndPlayDirectStream(ratingKey, serverId, startOffset) {
                directStreamUrlBuilder.buildUrl(ratingKey, serverId)
            }
        } else {
            // Plex: normal flow
            playerController.initialize(ratingKey, serverId, directUrl, startOffset)
        }
    }

    fun onAction(action: PlayerAction) {
        when (action) {
            is PlayerAction.Play -> playerController.play()
            is PlayerAction.Pause -> playerController.pause()
            is PlayerAction.SeekTo -> playerController.seekTo(action.position)
            is PlayerAction.Next -> {
                val nextMedia = playbackManager.getNextMedia()
                Timber.d("[Player] Next pressed: nextMedia=${nextMedia?.title} (rk=${nextMedia?.ratingKey}, sid=${nextMedia?.serverId})")
                if (nextMedia != null) {
                    playbackManager.next()
                    loadOrPlayMedia(nextMedia)
                } else {
                    Timber.w("[Player] Next pressed but NO next media in queue → closing dialogs only")
                    onAction(PlayerAction.Close)
                }
            }
            is PlayerAction.Previous -> {
                val prevMedia = playbackManager.getPreviousMedia()
                if (prevMedia != null) {
                    playbackManager.previous()
                    loadOrPlayMedia(prevMedia)
                }
            }
            is PlayerAction.SkipMarker -> {
                 val position = action.marker.endTime
                 playerController.seekTo(position)
            }
            is PlayerAction.PlayNext -> {
                val nextMedia = playbackManager.getNextMedia()
                Timber.d("[Player] PlayNext (auto): nextMedia=${nextMedia?.title} (rk=${nextMedia?.ratingKey}, sid=${nextMedia?.serverId})")
                if (nextMedia != null) {
                    playbackManager.next()
                    loadOrPlayMedia(nextMedia)
                } else {
                    Timber.w("[Player] PlayNext (auto) but NO next media in queue")
                    onAction(PlayerAction.Close)
                }
            }
            is PlayerAction.CancelAutoNext -> {
                playerController.updateState { it.copy(showAutoNextPopup = false) }
            }
            is PlayerAction.SetPlaybackSpeed -> playerController.setPlaybackSpeed(action.speed)
            is PlayerAction.SetAudioDelay -> playerController.setAudioDelay(action.delayMs)
            is PlayerAction.SetSubtitleDelay -> playerController.setSubtitleDelay(action.delayMs)
            is PlayerAction.SelectQuality -> {
                playerController.updateState { it.copy(showSettings = false) }
                val current = uiState.value.currentItem
                if (current != null) {
                    if (directStreamUrlBuilder.isDirectStream(current.serverId)) {
                        // Quality selection not applicable for direct streams
                        return
                    }
                    playerController.loadMedia(current.ratingKey, current.serverId, action.quality.bitrate)
                }
            }
            is PlayerAction.ToggleSettings -> {
                playerController.updateState { it.copy(showSettings = !it.showSettings, showAudioSelection = false, showSubtitleSelection = false) }
            }
            is PlayerAction.ToggleSpeedSelection -> {
                 playerController.updateState { it.copy(showSpeedSelection = !it.showSpeedSelection, showSettings = false, showAudioSelection = false, showSubtitleSelection = false, showAudioSyncDialog = false, showSubtitleSyncDialog = false) }
            }
            is PlayerAction.ShowAudioSyncSelector -> {
                  playerController.updateState { it.copy(showAudioSyncDialog = true, showSettings = false, showAudioSelection = false, showSubtitleSelection = false, showSpeedSelection = false, showSubtitleSyncDialog = false) }
            }
            is PlayerAction.ShowSubtitleSyncSelector -> {
                  playerController.updateState { it.copy(showSubtitleSyncDialog = true, showSettings = false, showAudioSelection = false, showSubtitleSelection = false, showSpeedSelection = false, showAudioSyncDialog = false) }
            }
            is PlayerAction.SeekToNextChapter -> {
                val currentPos = uiState.value.currentPosition
                val chapters = chapterMarkerManager.chapters.value
                val nextChapter = chapters.firstOrNull { it.startTime > currentPos + 1000 }
                if (nextChapter != null) {
                    playerController.seekTo(nextChapter.startTime)
                } else {
                    onAction(PlayerAction.Next)
                }
            }
            is PlayerAction.SeekToPreviousChapter -> {
                val currentPos = uiState.value.currentPosition
                val chapters = chapterMarkerManager.chapters.value
                val currentChapter = chapters.find { currentPos >= it.startTime && currentPos < it.endTime }

                if (currentChapter != null) {
                    if (currentPos - currentChapter.startTime < 3000) {
                        val prevChapterIndex = chapters.indexOf(currentChapter) - 1
                        if (prevChapterIndex >= 0) {
                            playerController.seekTo(chapters[prevChapterIndex].startTime)
                        } else {
                             playerController.seekTo(0)
                        }
                    } else {
                        playerController.seekTo(currentChapter.startTime)
                    }
                } else {
                    val prevChapter = chapters.filter { it.startTime < currentPos - 3000 }.maxByOrNull { it.startTime }
                    if (prevChapter != null) {
                        playerController.seekTo(prevChapter.startTime)
                    } else {
                        playerController.seekTo(0)
                    }
                }
            }
            is PlayerAction.ToggleMoreMenu -> {
                playerController.updateState { it.copy(showMoreMenu = !it.showMoreMenu, showSettings = false, showAudioSelection = false, showSubtitleSelection = false, showSpeedSelection = false, showAudioSyncDialog = false, showSubtitleSyncDialog = false, showSubtitleDownload = false, showEqualizer = false, showChapterOverlay = false, showQueueOverlay = false) }
            }
            is PlayerAction.ShowChapterOverlay -> {
                playerController.updateState { it.copy(showChapterOverlay = true, showMoreMenu = false) }
            }
            is PlayerAction.ShowQueueOverlay -> {
                playerController.updateState { it.copy(showQueueOverlay = true, showMoreMenu = false) }
            }
            is PlayerAction.SeekToChapter -> {
                playerController.seekTo(action.chapter.startTime)
                playerController.updateState { it.copy(showChapterOverlay = false) }
            }
            is PlayerAction.PlayQueueItem -> {
                val state = playbackManager.state.value
                if (action.index in state.playQueue.indices && action.index != state.currentIndex) {
                    val target = state.playQueue[action.index]
                    playbackManager.play(target, state.playQueue)
                    loadOrPlayMedia(target)
                }
                playerController.updateState { it.copy(showQueueOverlay = false) }
            }
            is PlayerAction.DismissDialog -> {
                playerController.updateState { it.copy(showSettings = false, showAudioSelection = false, showSubtitleSelection = false, showAutoNextPopup = false, showAudioSyncDialog = false, showSubtitleSyncDialog = false, showSpeedSelection = false, showSubtitleDownload = false, showEqualizer = false, showMoreMenu = false, showChapterOverlay = false, showQueueOverlay = false) }
            }
            is PlayerAction.DismissCurrentOverlay -> {
                val s = uiState.value
                playerController.updateState {
                    when {
                        s.showSettings -> it.copy(showSettings = false)
                        s.showSpeedSelection -> it.copy(showSpeedSelection = false)
                        s.showAudioSyncDialog -> it.copy(showAudioSyncDialog = false)
                        s.showSubtitleSyncDialog -> it.copy(showSubtitleSyncDialog = false)
                        s.showSubtitleDownload -> it.copy(showSubtitleDownload = false)
                        s.showEqualizer -> it.copy(showEqualizer = false)
                        s.showAudioSelection -> it.copy(showAudioSelection = false)
                        s.showSubtitleSelection -> it.copy(showSubtitleSelection = false)
                        s.showChapterOverlay -> it.copy(showChapterOverlay = false)
                        s.showQueueOverlay -> it.copy(showQueueOverlay = false)
                        s.showMoreMenu -> it.copy(showMoreMenu = false)
                        else -> it
                    }
                }
            }
            is PlayerAction.RetryPlayback -> {
                playerController.retryPlayback()
            }
            is PlayerAction.SwitchToMpv -> {
                playerController.switchToMpv()
            }
            is PlayerAction.ClearResumeMessage -> {
                playerController.clearResumeMessage()
            }
            is PlayerAction.ShowSubtitleDownload -> {
                playerController.updateState { it.copy(showSubtitleDownload = true, showSettings = false) }
            }
            is PlayerAction.ApplyDownloadedSubtitle -> {
                playerController.updateState { it.copy(showSubtitleDownload = false) }
                playerController.applyExternalSubtitle(action.filePath)
            }
            is PlayerAction.ShowEqualizer -> {
                // Attach equalizer to ExoPlayer audio session if not MPV
                val exo = playerController.player as? androidx.media3.exoplayer.ExoPlayer
                if (exo != null) {
                    audioEqualizerManager.attachToAudioSession(exo.audioSessionId)
                }
                playerController.updateState { it.copy(showEqualizer = true, showSettings = false) }
            }
            is PlayerAction.SelectEqualizerPreset -> {
                audioEqualizerManager.selectPreset(action.presetIndex)
            }
            is PlayerAction.SetEqualizerBand -> {
                audioEqualizerManager.setBandLevel(action.bandIndex, action.level)
            }
            is PlayerAction.SetEqualizerEnabled -> {
                audioEqualizerManager.setEnabled(action.enabled)
            }
            is PlayerAction.TogglePerformanceOverlay -> {
                playerController.updateState { it.copy(showPerformanceOverlay = !it.showPerformanceOverlay) }
            }
            is PlayerAction.CycleAspectRatio -> {
                playerController.updateState { it.copy(aspectRatioMode = it.aspectRatioMode.next()) }
            }
            is PlayerAction.Close -> {
                val state = uiState.value
                if (state.showSettings || state.showAudioSelection || state.showSubtitleSelection || state.showAutoNextPopup || state.showAudioSyncDialog || state.showSubtitleSyncDialog || state.showSpeedSelection || state.showSubtitleDownload || state.showEqualizer || state.showMoreMenu || state.showChapterOverlay || state.showQueueOverlay) {
                    playerController.updateState { it.copy(showSettings = false, showAudioSelection = false, showSubtitleSelection = false, showAutoNextPopup = false, showAudioSyncDialog = false, showSubtitleSyncDialog = false, showSpeedSelection = false, showSubtitleDownload = false, showEqualizer = false, showMoreMenu = false, showChapterOverlay = false, showQueueOverlay = false) }
                }
                // Navigation is handled by UI callback usually
            }
            else -> {} // Handled by other VMs
        }
    }

    /**
     * Switch to next/previous episode, reusing the existing player instance.
     * Resets per-episode state (startOffset, resume toast) via PlayerController.playNext*().
     */
    private fun loadOrPlayMedia(media: MediaItem) {
        val isDirectStream = directStreamUrlBuilder.isDirectStream(media.serverId)
        Timber.d("[Player] loadOrPlayMedia: '${media.title}' (rk=${media.ratingKey}, sid=${media.serverId}, isDirectStream=$isDirectStream)")
        if (isDirectStream) {
            viewModelScope.launch {
                val url = directStreamUrlBuilder.buildUrl(media.ratingKey, media.serverId)
                if (url != null) {
                    Timber.d("[Player] loadOrPlayMedia: Direct stream URL resolved, calling playNextDirectStream")
                    playerController.playNextDirectStream(url, media)
                } else {
                    Timber.e("[Player] Failed to resolve stream URL for ${media.ratingKey} on ${media.serverId}")
                    playerController.updateState {
                        it.copy(error = "Failed to get stream URL", isBuffering = false)
                    }
                }
            }
        } else {
            Timber.d("[Player] loadOrPlayMedia: Plex path, calling playNext(${media.ratingKey}, ${media.serverId})")
            playerController.playNext(media.ratingKey, media.serverId)
        }
    }

    /**
     * Shared helper: resolves a stream URL via a suspend builder, then plays it as a direct stream.
     * Used by both Xtream and Backend paths to avoid duplicating launch + error handling.
     */
    private fun resolveAndPlayDirectStream(
        ratingKey: String,
        serverId: String,
        startOffset: Long,
        cachedMedia: MediaItem? = null,
        urlBuilder: suspend () -> String?,
    ) {
        playerController.initialize(ratingKey, serverId, null, startOffset)
        viewModelScope.launch {
            val url = urlBuilder()
            if (url != null) {
                val media = cachedMedia ?: playbackManager.state.value.currentMedia?.takeIf {
                    it.ratingKey == ratingKey && it.serverId == serverId
                }
                playerController.playDirectStream(url, media)
            } else {
                Timber.e("[Player] Failed to resolve stream URL for $ratingKey on $serverId")
                playerController.updateState {
                    it.copy(error = "Failed to get stream URL", isBuffering = false)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEqualizerManager.release()
        trickplayManager.clear()
        playerController.release()
    }

    // Helper accessors for UI
    val mpvPlayer get() = playerController.mpvPlayer
    val player get() = playerController.player
    val refreshRateManager get() = playerController.refreshRateManager
}
