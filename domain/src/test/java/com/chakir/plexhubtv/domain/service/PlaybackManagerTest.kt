package com.chakir.plexhubtv.domain.service

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class PlaybackManagerTest {

    private lateinit var manager: PlaybackManager

    private fun mediaItem(
        ratingKey: String,
        serverId: String = "server1",
        title: String = "Media $ratingKey",
    ) = MediaItem(
        id = "${serverId}_$ratingKey",
        ratingKey = ratingKey,
        serverId = serverId,
        title = title,
        type = MediaType.Movie,
    )

    @Before
    fun setup() {
        manager = PlaybackManager()
    }

    // --- play() ---

    @Test
    fun `play sets currentMedia and builds queue`() {
        val media = mediaItem("1")
        val queue = listOf(mediaItem("1"), mediaItem("2"), mediaItem("3"))

        manager.play(media, queue)

        val state = manager.state.value
        assertThat(state.currentMedia).isEqualTo(media)
        assertThat(state.playQueue).hasSize(3)
        assertThat(state.currentIndex).isEqualTo(0)
    }

    @Test
    fun `play with empty queue creates single-item queue`() {
        val media = mediaItem("1")

        manager.play(media)

        val state = manager.state.value
        assertThat(state.playQueue).hasSize(1)
        assertThat(state.playQueue.first()).isEqualTo(media)
        assertThat(state.currentIndex).isEqualTo(0)
    }

    @Test
    fun `play finds index by ratingKey and serverId match`() {
        val media = mediaItem("2")
        val queue = listOf(mediaItem("1"), mediaItem("2"), mediaItem("3"))

        manager.play(media, queue)

        assertThat(manager.state.value.currentIndex).isEqualTo(1)
    }

    @Test
    fun `play falls back to ratingKey-only match`() {
        val media = mediaItem("2", serverId = "other-server")
        val queue = listOf(mediaItem("1"), mediaItem("2"), mediaItem("3"))

        manager.play(media, queue)

        // Falls back to ratingKey match (index 1)
        assertThat(manager.state.value.currentIndex).isEqualTo(1)
    }

    @Test
    fun `play defaults to index 0 when no match found`() {
        val media = mediaItem("999", serverId = "unknown")
        val queue = listOf(mediaItem("1"), mediaItem("2"), mediaItem("3"))

        manager.play(media, queue)

        assertThat(manager.state.value.currentIndex).isEqualTo(0)
    }

    // --- next() ---

    @Test
    fun `next advances to next item`() {
        val queue = listOf(mediaItem("1"), mediaItem("2"), mediaItem("3"))
        manager.play(queue.first(), queue)

        manager.next()

        val state = manager.state.value
        assertThat(state.currentIndex).isEqualTo(1)
        assertThat(state.currentMedia).isEqualTo(queue[1])
    }

    @Test
    fun `next at end of queue does nothing`() {
        val queue = listOf(mediaItem("1"), mediaItem("2"))
        manager.play(queue.last(), queue)
        assertThat(manager.state.value.currentIndex).isEqualTo(1)

        manager.next()

        val state = manager.state.value
        assertThat(state.currentIndex).isEqualTo(1)
        assertThat(state.currentMedia).isEqualTo(queue[1])
    }

    // --- previous() ---

    @Test
    fun `previous goes back one item`() {
        val queue = listOf(mediaItem("1"), mediaItem("2"), mediaItem("3"))
        manager.play(queue[1], queue)
        assertThat(manager.state.value.currentIndex).isEqualTo(1)

        manager.previous()

        val state = manager.state.value
        assertThat(state.currentIndex).isEqualTo(0)
        assertThat(state.currentMedia).isEqualTo(queue[0])
    }

    @Test
    fun `previous at start does nothing`() {
        val queue = listOf(mediaItem("1"), mediaItem("2"))
        manager.play(queue.first(), queue)

        manager.previous()

        val state = manager.state.value
        assertThat(state.currentIndex).isEqualTo(0)
        assertThat(state.currentMedia).isEqualTo(queue[0])
    }

    // --- toggleShuffle() ---

    @Test
    fun `toggleShuffle flips isShuffled flag`() {
        assertThat(manager.state.value.isShuffled).isFalse()

        manager.toggleShuffle()
        assertThat(manager.state.value.isShuffled).isTrue()

        manager.toggleShuffle()
        assertThat(manager.state.value.isShuffled).isFalse()
    }

    // --- getNextMedia() / getPreviousMedia() ---

    @Test
    fun `getNextMedia returns next item without changing state`() {
        val queue = listOf(mediaItem("1"), mediaItem("2"), mediaItem("3"))
        manager.play(queue.first(), queue)

        val next = manager.getNextMedia()

        assertThat(next).isEqualTo(queue[1])
        assertThat(manager.state.value.currentIndex).isEqualTo(0) // unchanged
    }

    @Test
    fun `getNextMedia returns null at end of queue`() {
        val queue = listOf(mediaItem("1"))
        manager.play(queue.first(), queue)

        assertThat(manager.getNextMedia()).isNull()
    }

    @Test
    fun `getPreviousMedia returns previous item without changing state`() {
        val queue = listOf(mediaItem("1"), mediaItem("2"), mediaItem("3"))
        manager.play(queue[1], queue)

        val prev = manager.getPreviousMedia()

        assertThat(prev).isEqualTo(queue[0])
        assertThat(manager.state.value.currentIndex).isEqualTo(1) // unchanged
    }

    @Test
    fun `getPreviousMedia returns null at start`() {
        val queue = listOf(mediaItem("1"))
        manager.play(queue.first(), queue)

        assertThat(manager.getPreviousMedia()).isNull()
    }

    // --- clear() ---

    @Test
    fun `clear resets to default state`() {
        val queue = listOf(mediaItem("1"), mediaItem("2"))
        manager.play(queue.first(), queue)
        manager.toggleShuffle()

        manager.clear()

        val state = manager.state.value
        assertThat(state.currentMedia).isNull()
        assertThat(state.playQueue).isEmpty()
        assertThat(state.currentIndex).isEqualTo(-1)
        assertThat(state.isShuffled).isFalse()
    }
}
