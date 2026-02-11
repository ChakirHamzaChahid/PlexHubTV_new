package com.chakir.plexhubtv.feature.player.controller

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.util.WatchNextHelper
import com.chakir.plexhubtv.domain.repository.PlaybackRepository
import com.chakir.plexhubtv.domain.usecase.PrefetchNextEpisodeUseCase
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
 * Gère le scrobbling (mise à jour de la progression sur le serveur), la détection "Watch Next",
 * et le préchargement du prochain épisode.
 */
@javax.inject.Singleton
class PlayerScrobbler
    @Inject
    constructor(
        private val playbackRepository: PlaybackRepository,
        private val watchNextHelper: WatchNextHelper,
        private val prefetchNextEpisodeUseCase: PrefetchNextEpisodeUseCase,
    ) {
        private var scrobbleJob: Job? = null
        private var autoNextTriggered = false
        private var prefetchTriggered = false

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

                            // Prefetch next episode at 80% progress
                            if (item.type == MediaType.Episode && !prefetchTriggered && duration > 1000) {
                                val progress = position.toFloat() / duration.toFloat()
                                if (progress >= 0.8f) {
                                    prefetchTriggered = true
                                    launch {
                                        try {
                                            prefetchNextEpisodeUseCase(item)
                                            Timber.d("Prefetch triggered at ${(progress * 100).toInt()}%")
                                        } catch (e: Exception) {
                                            Timber.w("Prefetch failed: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        }

        fun stop() {
            scrobbleJob?.cancel()
            scrobbleJob = null
            autoNextTriggered = false
            prefetchTriggered = false
            _showAutoNextPopup.update { false }
            prefetchNextEpisodeUseCase.reset()
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
            prefetchTriggered = false
            _showAutoNextPopup.update { false }
            prefetchNextEpisodeUseCase.reset()
        }

        fun dismissAutoNext() {
            _showAutoNextPopup.update { false }
        }
    }
