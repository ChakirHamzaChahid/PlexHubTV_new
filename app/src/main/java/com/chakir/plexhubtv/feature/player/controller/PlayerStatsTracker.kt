package com.chakir.plexhubtv.feature.player.controller

import com.chakir.plexhubtv.feature.player.PlayerStats
import com.chakir.plexhubtv.feature.player.StreamMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

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
                            _stats.update {
                                PlayerStats(
                                    bitrate = "${metadata?.bitrate ?: 0} kbps",
                                    videoCodec = metadata?.sampleMimeType ?: "Unknown",
                                    audioCodec = "Unknown", // Simplified for now
                                    resolution = "${metadata?.width ?: 0}x${metadata?.height ?: 0}",
                                    fps = metadata?.frameRate?.toDouble() ?: 0.0,
                                    cacheDuration = exoBuffered() - exoPosition(),
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
