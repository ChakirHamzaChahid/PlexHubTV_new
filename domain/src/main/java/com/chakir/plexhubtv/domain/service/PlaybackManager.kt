package com.chakir.plexhubtv.domain.service

import com.chakir.plexhubtv.core.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified playback state — all fields updated atomically via [MutableStateFlow.update].
 */
data class PlaybackState(
    val currentMedia: MediaItem? = null,
    val playQueue: List<MediaItem> = emptyList(),
    val currentIndex: Int = -1,
    val isShuffled: Boolean = false,
)

@Singleton
class PlaybackManager
    @Inject
    constructor() {
        private val _state = MutableStateFlow(PlaybackState())
        val state: StateFlow<PlaybackState> = _state.asStateFlow()

        fun play(
            media: MediaItem,
            queue: List<MediaItem> = emptyList(),
        ) {
            _state.update { current ->
                val effectiveQueue = if (queue.isEmpty()) listOf(media) else queue
                // Use ratingKey (canonical Plex identifier) instead of synthetic id
                // which can differ between enriched items and queue items
                // Fallback to ID or just ratingKey for unified items
                val index = effectiveQueue.indexOfFirst {
                    it.ratingKey == media.ratingKey && it.serverId == media.serverId
                }.takeIf { it != -1 } ?: effectiveQueue.indexOfFirst {
                    it.ratingKey == media.ratingKey || it.id == media.id
                }.let { if (it == -1) 0 else it }

                current.copy(
                    currentMedia = media,
                    playQueue = effectiveQueue,
                    currentIndex = index,
                )
            }
        }

        fun next() {
            _state.update { current ->
                val nextIndex = current.currentIndex + 1
                if (nextIndex < current.playQueue.size) {
                    current.copy(
                        currentIndex = nextIndex,
                        currentMedia = current.playQueue[nextIndex],
                    )
                } else {
                    current
                }
            }
        }

        fun previous() {
            _state.update { current ->
                val prevIndex = current.currentIndex - 1
                if (prevIndex >= 0) {
                    current.copy(
                        currentIndex = prevIndex,
                        currentMedia = current.playQueue[prevIndex],
                    )
                } else {
                    current
                }
            }
        }

        fun toggleShuffle() {
            // TODO: actually shuffle playQueue when enabling
            _state.update { current ->
                current.copy(isShuffled = !current.isShuffled)
            }
        }

        fun getNextMedia(): MediaItem? {
            val s = _state.value
            val nextIndex = s.currentIndex + 1
            return if (nextIndex >= 0 && nextIndex < s.playQueue.size) {
                s.playQueue[nextIndex]
            } else {
                null
            }
        }

        fun getPreviousMedia(): MediaItem? {
            val s = _state.value
            val prevIndex = s.currentIndex - 1
            return if (prevIndex >= 0 && prevIndex < s.playQueue.size) {
                s.playQueue[prevIndex]
            } else {
                null
            }
        }

        fun clear() {
            _state.update { PlaybackState() }
        }
    }
