package com.chakir.plexhubtv.feature.player.controller

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages MediaSession lifecycle for system-level media controls
 * (notifications, Bluetooth remote, Google Assistant on Android TV).
 *
 * Metadata is provided via ExoPlayer's MediaItem.mediaMetadata — the session
 * automatically reflects whatever the player currently has.
 */
@Singleton
class MediaSessionManager @Inject constructor(
    private val application: Application,
) {
    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    fun initialize(player: ExoPlayer) {
        release()
        mediaSession = MediaSession.Builder(application, player)
            .setId("PlexHubTV")
            .build()
        Timber.d("MediaSession initialized")
    }

    fun release() {
        mediaSession?.release()
        mediaSession = null
        Timber.d("MediaSession released")
    }
}
