package com.chakir.plexhubtv.feature.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.chakir.plexhubtv.feature.player.mpv.MpvConfig
import com.chakir.plexhubtv.feature.player.mpv.MpvPlayer
import com.chakir.plexhubtv.feature.player.mpv.MpvPlayerWrapper
import com.chakir.plexhubtv.feature.player.net.CrlfFixSocketFactory
import com.chakir.plexhubtv.feature.player.net.RangeRetryInterceptor
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import javax.inject.Inject

interface PlayerFactory {
    fun createExoPlayer(context: Context, isRelay: Boolean = false): ExoPlayer

    fun createMediaItem(
        uri: android.net.Uri,
        mediaId: String,
        isM3u8: Boolean,
        subtitleConfigurations: List<MediaItem.SubtitleConfiguration> = emptyList(),
        mediaMetadata: MediaMetadata? = null,
    ): MediaItem

    fun createMpvPlayer(
        context: Context,
        scope: CoroutineScope,
        config: MpvConfig = MpvConfig(),
    ): MpvPlayer
}

class ExoPlayerFactory
    @Inject
    constructor() : PlayerFactory {
        // Shared OkHttpClient: reuses connection pool and thread pool across player sessions.
        // Previously created per createExoPlayer() call, leaking pools on each session.
        // Uses CrlfFixSocketFactory for Xtream/IPTV servers that send bare \r in headers.
        private val playerOkHttpClient: OkHttpClient = OkHttpClient.Builder()
            .socketFactory(CrlfFixSocketFactory())
            .addInterceptor(RangeRetryInterceptor())
            .build()

        override fun createExoPlayer(context: Context, isRelay: Boolean): ExoPlayer {
            val loadControl = createLoadControl(isRelay)
            val dataSourceFactory = OkHttpDataSource.Factory(playerOkHttpClient)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            val trackSelector = DefaultTrackSelector(context).apply {
                parameters = buildUponParameters()
                    .setTunnelingEnabled(true)
                    .build()
            }

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

        return ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
                .build()
        }

        private fun createLoadControl(isRelay: Boolean): DefaultLoadControl {
            val builder = DefaultLoadControl.Builder()

            if (isRelay) {
                // Remote/Xtream: tight min-max gap (2s) keeps the TCP connection alive.
                // With a wide gap (old: 10s-30s = 20s idle) the loader pauses when
                // the buffer fills, the socket goes idle, and Xtream servers close
                // the connection within seconds. On reconnect ExoPlayer sends
                // Range: bytes=X- → 416 → RangeRetryInterceptor retries from 0 →
                // bytesToSkip has to discard hundreds of MB through a 4KB buffer → stuck.
                // A 2s gap means the loader resumes before the server gives up.
                builder.setBufferDurationsMs(
                    28_000,  // minBufferMs (28s — loader resumes quickly)
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
            mediaMetadata: MediaMetadata?,
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

            if (mediaMetadata != null) {
                builder.setMediaMetadata(mediaMetadata)
            }

            return builder.build()
        }

        override fun createMpvPlayer(
            context: Context,
            scope: CoroutineScope,
            config: MpvConfig,
        ): MpvPlayer {
            return MpvPlayerWrapper(context, scope, config)
        }
    }
