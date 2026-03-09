package com.chakir.plexhubtv.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.feature.player.controller.PlayerController
import com.chakir.plexhubtv.feature.player.controller.ChapterMarkerManager
import com.chakir.plexhubtv.feature.player.url.DirectStreamUrlBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PlayerControlViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val savedStateHandle: SavedStateHandle,
    val chapterMarkerManager: ChapterMarkerManager,
    private val playbackManager: com.chakir.plexhubtv.domain.service.PlaybackManager,
    private val directStreamUrlBuilder: DirectStreamUrlBuilder,
) : ViewModel() {

    val uiState = playerController.uiState

    init {
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
                if (nextMedia != null) {
                    playbackManager.next()
                    loadOrPlayMedia(nextMedia)
                } else {
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
                if (nextMedia != null) {
                    playbackManager.next()
                    loadOrPlayMedia(nextMedia)
                } else {
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
            is PlayerAction.DismissDialog -> {
                playerController.updateState { it.copy(showSettings = false, showAudioSelection = false, showSubtitleSelection = false, showAutoNextPopup = false, showAudioSyncDialog = false, showSubtitleSyncDialog = false, showSpeedSelection = false) }
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
            is PlayerAction.TogglePerformanceOverlay -> {
                playerController.updateState { it.copy(showPerformanceOverlay = !it.showPerformanceOverlay) }
            }
            is PlayerAction.Close -> {
                val state = uiState.value
                if (state.showSettings || state.showAudioSelection || state.showSubtitleSelection || state.showAutoNextPopup || state.showAudioSyncDialog || state.showSubtitleSyncDialog || state.showSpeedSelection) {
                    playerController.updateState { it.copy(showSettings = false, showAudioSelection = false, showSubtitleSelection = false, showAutoNextPopup = false, showAudioSyncDialog = false, showSubtitleSyncDialog = false, showSpeedSelection = false) }
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
        if (directStreamUrlBuilder.isDirectStream(media.serverId)) {
            viewModelScope.launch {
                val url = directStreamUrlBuilder.buildUrl(media.ratingKey, media.serverId)
                if (url != null) {
                    playerController.playNextDirectStream(url, media)
                } else {
                    Timber.e("[Player] Failed to resolve stream URL for ${media.ratingKey} on ${media.serverId}")
                    playerController.updateState {
                        it.copy(error = "Failed to get stream URL", isBuffering = false)
                    }
                }
            }
        } else {
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
        playerController.release()
    }

    // Helper accessors for UI
    val mpvPlayer get() = playerController.mpvPlayer
    val player get() = playerController.player
}
