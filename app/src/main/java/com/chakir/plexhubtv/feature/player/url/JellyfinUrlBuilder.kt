package com.chakir.plexhubtv.feature.player.url

import android.net.Uri
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaPart
import com.chakir.plexhubtv.core.model.SourcePrefix
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jellyfin-specific playback URL builder.
 *
 * Handles Direct Stream and HLS transcoding URLs for Jellyfin servers.
 * Uses `api_key` query parameter for authentication (vs Plex's `X-Plex-Token`).
 */
@Singleton
class JellyfinUrlBuilder @Inject constructor() : PlaybackUrlBuilder {

    override fun matches(serverId: String): Boolean =
        serverId.startsWith(SourcePrefix.JELLYFIN)

    override fun buildUrl(
        media: MediaItem,
        part: MediaPart,
        rKey: String,
        isDirectPlay: Boolean,
        bitrate: Int,
        clientId: String,
        audioStreamId: String?,
        subtitleStreamId: String?,
        audioIndex: Int?,
        subtitleIndex: Int?,
    ): Uri? {
        val baseUrl = media.baseUrl?.trimEnd('/') ?: return null
        val token = media.accessToken ?: return null
        val container = part.container ?: "mkv"

        val uri = if (isDirectPlay) {
            buildDirectStreamUrl(baseUrl, rKey, container, token)
        } else {
            buildTranscodeUrl(baseUrl, rKey, bitrate, token, audioIndex, subtitleIndex)
        }

        Timber.d("JellyfinUrlBuilder: %s → %s", if (isDirectPlay) "DirectStream" else "Transcode", uri)
        return uri
    }

    private fun buildDirectStreamUrl(
        baseUrl: String,
        itemId: String,
        container: String,
        token: String,
    ): Uri = Uri.parse("$baseUrl/Videos/$itemId/stream.$container?static=true&api_key=$token")

    private fun buildTranscodeUrl(
        baseUrl: String,
        itemId: String,
        bitrate: Int,
        token: String,
        audioIndex: Int?,
        subtitleIndex: Int?,
    ): Uri {
        val sb = StringBuilder("$baseUrl/Videos/$itemId/master.m3u8?")
        sb.append("api_key=$token")
        sb.append("&VideoBitRate=$bitrate")
        sb.append("&VideoCodec=h264")
        sb.append("&AudioCodec=aac")
        sb.append("&TranscodingMaxAudioChannels=6")
        sb.append("&SegmentContainer=ts")
        sb.append("&MinSegments=1")
        sb.append("&BreakOnNonKeyFrames=true")

        if (audioIndex != null) {
            sb.append("&AudioStreamIndex=$audioIndex")
        }
        if (subtitleIndex != null && subtitleIndex >= 0) {
            sb.append("&SubtitleStreamIndex=$subtitleIndex")
        }

        return Uri.parse(sb.toString())
    }
}
