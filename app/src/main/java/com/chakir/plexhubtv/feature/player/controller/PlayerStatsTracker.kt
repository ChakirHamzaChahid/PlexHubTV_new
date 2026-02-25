package com.chakir.plexhubtv.feature.player.controller

import com.chakir.plexhubtv.feature.player.PlayerStats
import com.chakir.plexhubtv.feature.player.StreamMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@javax.inject.Singleton
class PlayerStatsTracker
    @Inject
    constructor() {
        private val _stats = MutableStateFlow<PlayerStats?>(null)
        val stats: StateFlow<PlayerStats?> = _stats.asStateFlow()

        private var trackingJob: Job? = null

        /**
         * Starts periodically updating the [stats] flow.
         */
        fun startTracking(
            scope: CoroutineScope,
            isMpvMode: () -> Boolean,
            exoMetadata: () -> StreamMetadata?,
            exoPosition: () -> Long,
            exoBuffered: () -> Long,
            mpvStats: () -> PlayerStats?,
        ) {
            stopTracking()
            trackingJob =
                scope.launch {
                    while (true) {
                        if (isMpvMode()) {
                            _stats.value = mpvStats()
                        } else {
                            val metadata = exoMetadata()
                            val rawBitrate = metadata?.bitrate ?: -1
                            val bitrateKbps = if (rawBitrate > 0) (rawBitrate / 1000) else -1

                            // Extract codec name from MIME type (e.g., "video/hevc" -> "HEVC")
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

                            _stats.update {
                                PlayerStats(
                                    bitrate = if (bitrateKbps > 0) "$bitrateKbps kbps" else "N/A",
                                    videoCodec = videoCodec,
                                    audioCodec = "Unknown", // TODO: Extract from ExoPlayer audio format
                                    resolution = "${metadata?.width ?: 0}x${metadata?.height ?: 0}",
                                    fps = if (metadata?.frameRate != null && metadata.frameRate > 0) metadata.frameRate.toDouble() else -1.0,
                                    cacheDuration = (exoBuffered() - exoPosition()).coerceAtLeast(0),
                                )
                            }
                        }
                        delay(1000)
                    }
                }
        }

        fun stopTracking() {
            trackingJob?.cancel()
            trackingJob = null
        }
    }
