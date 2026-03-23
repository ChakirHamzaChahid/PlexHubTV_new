package com.chakir.plexhubtv.feature.player.controller

import com.chakir.plexhubtv.feature.player.PlayerStats
import com.chakir.plexhubtv.feature.player.StreamMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject

@javax.inject.Singleton
class PlayerStatsTracker
    @Inject
    constructor() {
        private val _stats = MutableStateFlow<PlayerStats?>(null)
        val stats: StateFlow<PlayerStats?> = _stats.asStateFlow()

        private var trackingJob: Job? = null
        private var peakBitrateKbps: Long = 0
        private var bitrateSum: Long = 0
        private var bitrateSamples: Long = 0

        /**
         * Starts periodically updating the [stats] flow.
         */
        fun startTracking(
            scope: CoroutineScope,
            isMpvMode: () -> Boolean,
            @Suppress("UNUSED_PARAMETER") isMpvModeProvider: () -> Boolean = isMpvMode,
            exoMetadata: () -> StreamMetadata?,
            exoPosition: () -> Long,
            exoBuffered: () -> Long,
            mpvStats: () -> PlayerStats?,
        ) {
            stopTracking()
            peakBitrateKbps = 0
            bitrateSum = 0
            bitrateSamples = 0

            trackingJob =
                scope.launch {
                    while (true) {
                        try {
                            if (isMpvMode()) {
                                val mpv = mpvStats()
                                _stats.value = mpv?.copy(playerBackend = "MPV")
                            } else {
                                val metadata = exoMetadata()
                                val rawBitrate = metadata?.bitrate ?: -1
                                val bitrateKbps = if (rawBitrate > 0) (rawBitrate / 1000).toLong() else -1L

                                // Track peak and average bitrate
                                if (bitrateKbps > 0) {
                                    if (bitrateKbps > peakBitrateKbps) peakBitrateKbps = bitrateKbps
                                    bitrateSum += bitrateKbps
                                    bitrateSamples++
                                }
                                val avgKbps = if (bitrateSamples > 0) bitrateSum / bitrateSamples else 0L

                                // Extract video codec from MIME type
                                val videoCodec = metadata?.sampleMimeType?.let { mimeType ->
                                    when {
                                        mimeType.contains("hevc", ignoreCase = true) -> "HEVC (H.265)"
                                        mimeType.contains("avc", ignoreCase = true) -> "AVC (H.264)"
                                        mimeType.contains("vp9", ignoreCase = true) -> "VP9"
                                        mimeType.contains("av1", ignoreCase = true) -> "AV1"
                                        mimeType.contains("mpeg", ignoreCase = true) -> "MPEG"
                                        else -> mimeType.substringAfter("/").uppercase()
                                    }
                                } ?: "Unknown"

                                // Extract audio codec from MIME type
                                val audioCodec = metadata?.audioMimeType?.let { mimeType ->
                                    when {
                                        mimeType.contains("eac3", ignoreCase = true) || mimeType.contains("e-ac3", ignoreCase = true) -> "EAC3 (Dolby Digital+)"
                                        mimeType.contains("ac3", ignoreCase = true) -> "AC3 (Dolby Digital)"
                                        mimeType.contains("aac", ignoreCase = true) -> "AAC"
                                        mimeType.contains("opus", ignoreCase = true) -> "Opus"
                                        mimeType.contains("vorbis", ignoreCase = true) -> "Vorbis"
                                        mimeType.contains("flac", ignoreCase = true) -> "FLAC"
                                        mimeType.contains("truehd", ignoreCase = true) -> "TrueHD (Atmos)"
                                        mimeType.contains("dts", ignoreCase = true) -> "DTS"
                                        mimeType.contains("mp3", ignoreCase = true) -> "MP3"
                                        mimeType.contains("pcm", ignoreCase = true) -> "PCM"
                                        else -> mimeType.substringAfter("/").uppercase()
                                    }
                                } ?: "Unknown"

                                // Audio channels layout
                                val channels = when (metadata?.audioChannelCount ?: 0) {
                                    1 -> "Mono (1.0)"
                                    2 -> "Stereo (2.0)"
                                    6 -> "5.1"
                                    8 -> "7.1"
                                    else -> if ((metadata?.audioChannelCount ?: 0) > 0) "${metadata?.audioChannelCount}ch" else "Unknown"
                                }

                                // Decoder type (HW vs SW)
                                val decoderType = metadata?.decoderName?.let { name ->
                                    when {
                                        name.startsWith("c2.", ignoreCase = true) -> "HW (C2)"
                                        name.startsWith("OMX.", ignoreCase = true) -> "HW (OMX)"
                                        name.contains("MediaCodec", ignoreCase = true) -> "HW"
                                        else -> "SW"
                                    }
                                } ?: "Unknown"

                                val cacheDuration = (exoBuffered() - exoPosition()).coerceAtLeast(0)

                                // Audio bitrate
                                val audioBitrateKbps = metadata?.audioBitrate ?: 0
                                val audioBitrateStr = if (audioBitrateKbps > 0) "${audioBitrateKbps / 1000} kbps" else "N/A"

                                _stats.update {
                                    PlayerStats(
                                        bitrate = formatBitrate(bitrateKbps),
                                        videoCodec = videoCodec,
                                        audioCodec = audioCodec,
                                        resolution = "${metadata?.width ?: 0}x${metadata?.height ?: 0}",
                                        fps = if (metadata?.frameRate != null && metadata.frameRate > 0) metadata.frameRate.toDouble() else -1.0,
                                        cacheDuration = cacheDuration,
                                        decoderType = decoderType,
                                        peakBitrateKbps = peakBitrateKbps,
                                        avgBitrateKbps = avgKbps,
                                        bufferBytes = cacheDuration,
                                        playerBackend = "ExoPlayer",
                                        audioChannels = channels,
                                        audioBitrate = audioBitrateStr,
                                    )
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.w(e, "Stats tracking tick failed (player may be released)")
                        }
                        delay(1000)
                    }
                }
        }

        fun stopTracking() {
            trackingJob?.cancel()
            trackingJob = null
        }

        private fun formatBitrate(kbps: Long): String = when {
            kbps < 0 -> "N/A"
            kbps < 1000 -> "$kbps kbps"
            else -> String.format("%.1f Mbps", kbps / 1000.0)
        }
    }
