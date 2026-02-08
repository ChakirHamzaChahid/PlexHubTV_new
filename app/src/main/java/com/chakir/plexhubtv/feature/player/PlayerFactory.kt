package com.chakir.plexhubtv.feature.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import com.chakir.plexhubtv.feature.player.mpv.MpvPlayer
import com.chakir.plexhubtv.feature.player.mpv.MpvPlayerWrapper
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

interface PlayerFactory {
    fun createExoPlayer(context: Context): ExoPlayer

    fun createMediaItem(
        uri: android.net.Uri,
        mediaId: String,
        isM3u8: Boolean,
    ): MediaItem

    fun createMpvPlayer(
        context: Context,
        scope: CoroutineScope,
    ): MpvPlayer
}

class ExoPlayerFactory
    @Inject
    constructor() : PlayerFactory {
        override fun createExoPlayer(context: Context): ExoPlayer {
            return ExoPlayer.Builder(context).build()
        }

        override fun createMediaItem(
            uri: android.net.Uri,
            mediaId: String,
            isM3u8: Boolean,
        ): MediaItem {
            val builder =
                MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(mediaId)

            if (isM3u8) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
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
