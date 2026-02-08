package com.chakir.plexhubtv.feature.player.controller

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.util.WatchNextHelper
import com.chakir.plexhubtv.domain.repository.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Gère le scrobbling (mise à jour de la progression sur le serveur) et la détection "Watch Next".
 */
class PlayerScrobbler
    @Inject
    constructor(
        private val playbackRepository: PlaybackRepository,
        private val watchNextHelper: WatchNextHelper,
    ) {
        private var scrobbleJob: Job? = null
        private var autoNextTriggered = false

        private val _showAutoNextPopup = MutableStateFlow(false)
        val showAutoNextPopup: StateFlow<Boolean> = _showAutoNextPopup.asStateFlow()

        fun start(
            scope: CoroutineScope,
            currentItemProvider: () -> MediaItem?,
            isPlayingProvider: () -> Boolean,
            currentPositionProvider: () -> Long,
            durationProvider: () -> Long,
        ) {
            if (scrobbleJob?.isActive == true) return

            scrobbleJob =
                scope.launch {
                    while (isActive) {
                        delay(10000) // Scrobble every 10 seconds
                        val item = currentItemProvider() ?: continue
                        val isPlaying = isPlayingProvider()
                        val position = currentPositionProvider()
                        val duration = durationProvider()

                        if (isPlaying) {
                            try {
                                playbackRepository.updatePlaybackProgress(item, position)
                            } catch (e: Exception) {
                                Timber.w("Scrobble failed: ${e.message}")
                            }

                            try {
                                watchNextHelper.updateWatchNext(item, position, duration)
                            } catch (e: Exception) {
                                Timber.w("WatchNext update failed: ${e.message}")
                            }
                        }
                    }
                }
        }

        fun stop() {
            scrobbleJob?.cancel()
            scrobbleJob = null
            autoNextTriggered = false
            _showAutoNextPopup.update { false }
        }

        fun checkAutoNext(
            position: Long,
            duration: Long,
            hasNextItem: Boolean,
            isPopupAlreadyShown: Boolean,
        ) {
            if (duration <= 1000) return
            val progress = position.toFloat() / duration.toFloat()

            // Show popup at 90% if not triggered and next item exists
            if (progress >= 0.9f && !autoNextTriggered && hasNextItem && !isPopupAlreadyShown) {
                autoNextTriggered = true
                _showAutoNextPopup.update { true }
            }
        }

        fun resetAutoNext() {
            autoNextTriggered = false
            _showAutoNextPopup.update { false }
        }

        fun dismissAutoNext() {
            _showAutoNextPopup.update { false }
        }
    }
