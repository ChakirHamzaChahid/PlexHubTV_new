package com.chakir.plexhubtv.domain.service

import com.chakir.plexhubtv.core.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackManager
    @Inject
    constructor() {
        private val _currentMedia = MutableStateFlow<MediaItem?>(null)
        val currentMedia: StateFlow<MediaItem?> = _currentMedia.asStateFlow()

        private val _playQueue = MutableStateFlow<List<MediaItem>>(emptyList())
        val playQueue: StateFlow<List<MediaItem>> = _playQueue.asStateFlow()

        private val _isShuffled = MutableStateFlow(false)
        val isShuffled: StateFlow<Boolean> = _isShuffled.asStateFlow()

        private val _currentIndex = MutableStateFlow(-1)
        val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

        fun play(
            media: MediaItem,
            queue: List<MediaItem> = emptyList(),
        ) {
            _currentMedia.value = media
            _playQueue.value = if (queue.isEmpty()) listOf(media) else queue
            _currentIndex.value = _playQueue.value.indexOfFirst { it.id == media.id }
        }

        fun next() {
            val nextIndex = _currentIndex.value + 1
            if (nextIndex < _playQueue.value.size) {
                _currentIndex.value = nextIndex
                _currentMedia.value = _playQueue.value[nextIndex]
            }
        }

        fun previous() {
            val prevIndex = _currentIndex.value - 1
            if (prevIndex >= 0) {
                _currentIndex.value = prevIndex
                _currentMedia.value = _playQueue.value[prevIndex]
            }
        }

        fun toggleShuffle() {
            _isShuffled.value = !_isShuffled.value
            if (_isShuffled.value) {
                // Logic to shuffle _playQueue but keep current item at start or whatever Plezy does
            }
        }

        fun getNextMedia(): MediaItem? {
            val nextIndex = _currentIndex.value + 1
            return if (nextIndex >= 0 && nextIndex < _playQueue.value.size) {
                _playQueue.value[nextIndex]
            } else {
                null
            }
        }

        fun getPreviousMedia(): MediaItem? {
            val prevIndex = _currentIndex.value - 1
            return if (prevIndex >= 0 && prevIndex < _playQueue.value.size) {
                _playQueue.value[prevIndex]
            } else {
                null
            }
        }

        fun clear() {
            _currentMedia.value = null
            _playQueue.value = emptyList()
            _currentIndex.value = -1
        }
    }
