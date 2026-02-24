package com.chakir.plexhubtv.feature.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.chakir.plexhubtv.feature.player.mpv.MpvPlayer
import com.chakir.plexhubtv.feature.player.mpv.MpvPlayerWrapper
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

interface PlayerFactory {
    fun createExoPlayer(context: Context, isRelay: Boolean = false): ExoPlayer

    fun createMediaItem(
        uri: android.net.Uri,
        mediaId: String,
        isM3u8: Boolean,
        subtitleConfigurations: List<MediaItem.SubtitleConfiguration> = emptyList(),
    ): MediaItem

    fun createMpvPlayer(
        context: Context,
        scope: CoroutineScope,
    ): MpvPlayer
}

class ExoPlayerFactory
    @Inject
    constructor() : PlayerFactory {
        override fun createExoPlayer(context: Context, isRelay: Boolean): ExoPlayer {
            val loadControl = createLoadControl(isRelay)

            return ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()
        }

        private fun createLoadControl(isRelay: Boolean): DefaultLoadControl {
            val builder = DefaultLoadControl.Builder()

            if (isRelay) {
                // Relay/transcode: larger buffer for unstable connections (capped at ~2 Mbps)
                builder.setBufferDurationsMs(
                    10_000,  // minBufferMs (10s)
                    30_000,  // maxBufferMs (30s)
                    2_500,   // bufferForPlaybackMs
                    5_000,   // bufferForPlaybackAfterRebufferMs
                )
            } else {
                // LAN direct: aggressive buffers — fast network, low memory footprint
                builder.setBufferDurationsMs(
                    5_000,   // minBufferMs (5s — plenty on gigabit LAN)
                    15_000,  // maxBufferMs (15s — saves ~70% memory vs default 50s on 4K)
                    1_500,   // bufferForPlaybackMs
                    2_500,   // bufferForPlaybackAfterRebufferMs
                )
            }

            return builder.build()
        }

        override fun createMediaItem(
            uri: android.net.Uri,
            mediaId: String,
            isM3u8: Boolean,
            subtitleConfigurations: List<MediaItem.SubtitleConfiguration>,
        ): MediaItem {
            val builder =
                MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(mediaId)

            if (isM3u8) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }

            if (subtitleConfigurations.isNotEmpty()) {
                builder.setSubtitleConfigurations(subtitleConfigurations)
            }

            return builder.build()
        }

        override fun createMpvPlayer(
            context: Context,
            scope: CoroutineScope,
        ): MpvPlayer {
            return MpvPlayerWrapper(context, scope)
        }
    }
