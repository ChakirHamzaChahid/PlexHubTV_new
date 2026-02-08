package com.chakir.plexhubtv.feature.player.url

import android.net.Uri
import android.os.Build
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaPart
import timber.log.Timber
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject

/**
 * Construit les URLs de lecture (Direct Play ou Transcoding) pour Plex.
 */
class TranscodeUrlBuilder
    @Inject
    constructor() {
        fun buildUrl(
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
            val token = media.accessToken ?: ""

            if (isDirectPlay) {
                // Direct Play Strategy: Use the file key directly
                val partKey = part.key
                return Uri.parse("$baseUrl$partKey?X-Plex-Token=$token")
            } else {
                // Transcoding Strategy
                return buildTranscodeUrl(
                    baseUrl,
                    rKey,
                    bitrate,
                    token,
                    clientId,
                    audioStreamId,
                    subtitleStreamId,
                    audioIndex,
                    subtitleIndex,
                )
            }
        }

        private fun buildTranscodeUrl(
            baseUrl: String,
            rKey: String,
            bitrate: Int,
            token: String,
            clientId: String,
            audioStreamId: String?,
            subtitleStreamId: String?,
            audioIndex: Int?,
            subtitleIndex: Int?,
        ): Uri {
            val path = "/library/metadata/$rKey"
            val encodedPath = URLEncoder.encode(path, "UTF-8")

            val transcodeUrlBuilder = StringBuilder("$baseUrl/video/:/transcode/universal/start.m3u8?")
            transcodeUrlBuilder.append("path=$encodedPath")
            transcodeUrlBuilder.append("&mediaIndex=0")
            transcodeUrlBuilder.append("&partIndex=0")
            transcodeUrlBuilder.append("&protocol=hls")
            transcodeUrlBuilder.append("&fastSeek=1")
            transcodeUrlBuilder.append("&directPlay=0")
            // Force FULL transcoding (directStream=0) to ensure the server burns subtitles
            // and sends ONLY the selected audio track. This fixes multi-track HLS issues.
            transcodeUrlBuilder.append("&directStream=0")
            transcodeUrlBuilder.append("&subtitleSize=100")
            transcodeUrlBuilder.append("&audioBoost=100")
            transcodeUrlBuilder.append("&location=lan")
            transcodeUrlBuilder.append("&addDebugOverlay=0")
            transcodeUrlBuilder.append("&autoAdjustQuality=0")
            transcodeUrlBuilder.append("&videoQuality=100")
            transcodeUrlBuilder.append("&maxVideoBitrate=$bitrate")

            // Send BOTH ID and Index to ensure Plex respects the selection
            if (audioStreamId != null) {
                transcodeUrlBuilder.append("&audioStreamID=$audioStreamId")
            }
            if (audioIndex != null) {
                transcodeUrlBuilder.append("&audioIndex=$audioIndex")
            }

            if (subtitleStreamId != null) {
                transcodeUrlBuilder.append("&subtitleStreamID=$subtitleStreamId")
            }
            if (subtitleIndex != null) {
                if (subtitleIndex >= 0) {
                    transcodeUrlBuilder.append("&subtitleIndex=$subtitleIndex")
                }
            }

            transcodeUrlBuilder.append("&X-Plex-Token=$token")
            transcodeUrlBuilder.append("&X-Plex-Client-Identifier=$clientId")
            transcodeUrlBuilder.append("&X-Plex-Platform=Android")
            transcodeUrlBuilder.append("&X-Plex-Platform-Version=${Build.VERSION.RELEASE}")
            transcodeUrlBuilder.append("&X-Plex-Product=PlexHubTV")
            transcodeUrlBuilder.append("&X-Plex-Device=${URLEncoder.encode(Build.MODEL, "UTF-8")}")

            // Important: Generate a unique session ID for this playback request
            // This forces Plex to start a new transcoding session with the selected audio/subtitles
            // instead of reusing an existing session where the old audio might be stuck.
            val session = UUID.randomUUID().toString()
            transcodeUrlBuilder.append("&session=$session")

            val finalUri = Uri.parse(transcodeUrlBuilder.toString())
            Timber.d("Generated URL: $finalUri")
            return finalUri
        }
    }
