package com.chakir.plexhubtv.feature.player.controller

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.chakir.plexhubtv.feature.player.PlayerFactory
import com.chakir.plexhubtv.feature.player.mpv.MpvPlayer
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class PlayerInitializer
    @Inject
    constructor(
        private val playerFactory: PlayerFactory,
    ) {
        @OptIn(UnstableApi::class)
        fun createExoPlayer(
            context: Context,
            isRelay: Boolean = false,
            onPlayingChanged: (Boolean) -> Unit,
            onStateChanged: (Int) -> Unit,
            onTracksChanged: (Tracks) -> Unit,
            onError: (androidx.media3.common.PlaybackException) -> Unit,
        ): ExoPlayer {
            return playerFactory.createExoPlayer(context, isRelay).apply {
                addListener(
                    object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            onPlayingChanged(isPlaying)
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            onStateChanged(state)
                        }

                        override fun onTracksChanged(tracks: Tracks) {
                            onTracksChanged(tracks)
                        }

                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            onError(error)
                        }
                    },
                )
            }
        }

        fun createMpvPlayer(
            context: Context,
            scope: CoroutineScope,
        ): MpvPlayer {
            return playerFactory.createMpvPlayer(context, scope)
        }
    }
