package com.chakir.plexhubtv.feature.player.controller

import androidx.media3.exoplayer.ExoPlayer
import com.chakir.plexhubtv.core.util.WatchNextHelper
import com.chakir.plexhubtv.domain.service.PlaybackManager
import com.chakir.plexhubtv.feature.player.PlayerAction
import com.chakir.plexhubtv.feature.player.PlayerUiState
import com.chakir.plexhubtv.feature.player.mpv.MpvPlayer
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class PlayerActionHandler
    @Inject
    constructor(
        private val playerTrackController: PlayerTrackController,
        private val chapterMarkerManager: ChapterMarkerManager,
        private val watchNextHelper: WatchNextHelper,
        private val playbackManager: PlaybackManager,
    ) {
        fun handleAction(
            action: PlayerAction,
            player: ExoPlayer?,
            mpvPlayer: MpvPlayer?,
            isMpvMode: Boolean,
            currentState: PlayerUiState,
            onStateUpdate: (PlayerUiState.() -> PlayerUiState) -> Unit,
            onLoadMediaRequested: (ratingKey: String, serverId: String) -> Unit,
            onToggleMpvRequested: () -> Unit,
            scope: CoroutineScope,
            exoPlayer: ExoPlayer?,
        ) {
            when (action) {
                is PlayerAction.Play -> {
                    if (isMpvMode) {
                        mpvPlayer?.resume()
                    } else {
                        player?.play()
                    }
                }
                is PlayerAction.Pause -> {
                    if (isMpvMode) {
                        mpvPlayer?.pause()
                    } else {
                        player?.pause()
                    }
                }
                is PlayerAction.SeekTo -> {
                    if (isMpvMode) {
                        mpvPlayer?.seekTo(action.position)
                    } else {
                        player?.seekTo(action.position)
                    }
                }
                // Other actions
                is PlayerAction.SelectAudioTrack -> {
                    playerTrackController.selectAudioTrack(
                        track = action.track,
                        currentItem = currentState.currentItem,
                        currentSubtitleStreamId = currentState.selectedSubtitle?.streamId,
                        scope = scope,
                        exoPlayer = exoPlayer,
                        mpvPlayer = mpvPlayer,
                        isMpvMode = isMpvMode,
                        isDirectPlay = true, // Simplified
                        audioTracksInUi = currentState.audioTracks,
                        onReloadRequired = { aId, sId ->
                            currentState.currentItem?.let { item -> onLoadMediaRequested(item.ratingKey, item.serverId) }
                        },
                    )
                }
                is PlayerAction.SelectSubtitleTrack -> {
                    playerTrackController.selectSubtitleTrack(
                        track = action.track,
                        currentItem = currentState.currentItem,
                        currentAudioStreamId = currentState.selectedAudio?.streamId,
                        scope = scope,
                        exoPlayer = exoPlayer,
                        mpvPlayer = mpvPlayer,
                        isMpvMode = isMpvMode,
                        isDirectPlay = true,
                        subtitleTracksInUi = currentState.subtitleTracks,
                        onReloadRequired = { aId, sId ->
                            currentState.currentItem?.let { item -> onLoadMediaRequested(item.ratingKey, item.serverId) }
                        },
                    )
                }
                is PlayerAction.Next -> {
                    currentState.nextItem?.let { next ->
                        onLoadMediaRequested(next.ratingKey, next.serverId)
                    }
                }
                is PlayerAction.Close -> {
                    // Handled in ViewModel onCleared usually, or UI closes
                }
                else -> {}
            }
        }
    }
