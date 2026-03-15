package com.chakir.plexhubtv.feature.player.controller

import com.chakir.plexhubtv.core.di.ApplicationScope
import com.chakir.plexhubtv.core.di.IoDispatcher
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.util.WatchNextHelper
import com.chakir.plexhubtv.domain.service.TvChannelManager
import com.chakir.plexhubtv.domain.repository.PlaybackRepository
import com.chakir.plexhubtv.domain.usecase.GetUnifiedHomeContentUseCase
import com.chakir.plexhubtv.domain.usecase.PrefetchNextEpisodeUseCase
import kotlinx.coroutines.CoroutineDispatcher
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
        private val tvChannelManager: TvChannelManager,
        private val prefetchNextEpisodeUseCase: PrefetchNextEpisodeUseCase,
        private val getUnifiedHomeContentUseCase: GetUnifiedHomeContentUseCase,
        @ApplicationScope private val applicationScope: CoroutineScope,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        private var scrobbleJob: Job? = null
        private var autoNextTriggered = false
        private var prefetchTriggered = false
        private var scrobbleTriggered = false

        /** Whether auto-scrobble has already fired for the current item. */
        val isScrobbled: Boolean get() = scrobbleTriggered

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
                        delay(30000) // Scrobble every 30 seconds (reduced CPU/network on low-end TV devices)
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

        fun stop(currentItem: MediaItem? = null, currentPosition: Long = 0L) {
            scrobbleJob?.cancel()
            scrobbleJob = null
            autoNextTriggered = false
            prefetchTriggered = false
            scrobbleTriggered = false
            _showAutoNextPopup.update { false }
            prefetchNextEpisodeUseCase.reset()

            // Fire-and-forget on IO: send stopped timeline, flush progress, update TV channel, refresh On Deck
            applicationScope.launch(ioDispatcher) {
                // 1. Notify Plex server that playback has stopped
                if (currentItem != null) {
                    try {
                        playbackRepository.sendStoppedTimeline(currentItem, currentPosition)
                        Timber.d("Sent 'stopped' timeline for ${currentItem.ratingKey} at ${currentPosition}ms")
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to send stopped timeline")
                    }
                }

                // 2. Flush cached progress to DB
                try {
                    playbackRepository.flushLocalProgress()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to flush progress cache on stop")
                }

                // 3. Update Android TV channel
                try {
                    tvChannelManager.updateContinueWatching()
                } catch (e: Exception) {
                    Timber.e(e, "TV Channel: Post-playback update failed")
                }

                // 4. Refresh On Deck so home screen reflects updated Continue Watching
                try {
                    getUnifiedHomeContentUseCase.refresh()
                    Timber.d("On Deck refresh triggered after playback stop")
                } catch (e: Exception) {
                    Timber.w(e, "On Deck refresh failed")
                }
            }
        }

        /**
         * Called every ~1 second from the position tracker.
         * Auto-scrobbles (marks as watched) at 95% progress, matching official Plex behavior.
         */
        fun checkAutoScrobble(position: Long, duration: Long, item: MediaItem) {
            if (scrobbleTriggered || duration <= 1000) return
            val progress = position.toFloat() / duration.toFloat()
            if (progress >= 0.95f) {
                scrobbleTriggered = true
                applicationScope.launch(ioDispatcher) {
                    try {
                        playbackRepository.toggleWatchStatus(item, isWatched = true)
                        Timber.d("Auto-scrobble at ${(progress * 100).toInt()}% for ${item.ratingKey}")
                    } catch (e: Exception) {
                        Timber.w("Auto-scrobble failed: ${e.message}")
                    }
                }
            }
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
            scrobbleTriggered = false
            _showAutoNextPopup.update { false }
            prefetchNextEpisodeUseCase.reset()
        }

        fun dismissAutoNext() {
            _showAutoNextPopup.update { false }
        }
    }
