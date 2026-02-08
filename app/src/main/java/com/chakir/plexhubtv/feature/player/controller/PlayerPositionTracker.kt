package com.chakir.plexhubtv.feature.player.controller

import androidx.media3.exoplayer.ExoPlayer
import com.chakir.plexhubtv.feature.player.PlayerUiState
import com.chakir.plexhubtv.feature.player.mpv.MpvPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

class PlayerPositionTracker
    @Inject
    constructor(
        private val playerScrobbler: PlayerScrobbler,
    ) {
        private var trackingJob: Job? = null

        fun startTracking(
            scope: CoroutineScope,
            isMpvMode: () -> Boolean,
            mpvPlayer: () -> MpvPlayer?,
            exoPlayer: () -> ExoPlayer?,
            onUpdate: (PlayerUiState.() -> PlayerUiState) -> Unit,
            hasNextItem: () -> Boolean,
            isPopupShown: () -> Boolean,
        ) {
            trackingJob?.cancel()
            trackingJob =
                scope.launch {
                    while (isActive) {
                        if (isMpvMode()) {
                            val mpv = mpvPlayer()
                            if (mpv != null) {
                                val pos = mpv.position.value
                                val dur = mpv.duration.value
                                onUpdate {
                                    copy(
                                        currentPosition = pos,
                                        duration = dur,
                                        isPlaying = mpv.isPlaying.value,
                                        isBuffering = mpv.isBuffering.value,
                                    )
                                }
                                playerScrobbler.checkAutoNext(pos, dur, hasNextItem(), isPopupShown())
                            }
                        } else {
                            val exo = exoPlayer()
                            if (exo != null) {
                                val pos = exo.currentPosition
                                val dur = exo.duration.coerceAtLeast(0L)
                                onUpdate {
                                    copy(
                                        currentPosition = pos,
                                        duration = dur,
                                        isPlaying = exo.isPlaying,
                                        isBuffering = exo.playbackState == androidx.media3.common.Player.STATE_BUFFERING,
                                    )
                                }
                                playerScrobbler.checkAutoNext(pos, dur, hasNextItem(), isPopupShown())
                            }
                        }
                        delay(1000L)
                    }
                }
        }

        fun stopTracking() {
            trackingJob?.cancel()
            trackingJob = null
        }
    }
