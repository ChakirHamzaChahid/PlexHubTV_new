package com.chakir.plexhubtv.feature.player.controller

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.domain.repository.PlaybackRepository
import com.chakir.plexhubtv.domain.service.TvChannelManager
import com.chakir.plexhubtv.domain.usecase.GetUnifiedHomeContentUseCase
import com.chakir.plexhubtv.domain.usecase.PrefetchNextEpisodeUseCase
import com.chakir.plexhubtv.util.WatchNextHelper
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerScrobblerTest {
    private lateinit var scrobbler: PlayerScrobbler
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var watchNextHelper: WatchNextHelper
    private lateinit var tvChannelManager: TvChannelManager
    private lateinit var prefetchNextEpisodeUseCase: PrefetchNextEpisodeUseCase
    private lateinit var getUnifiedHomeContentUseCase: GetUnifiedHomeContentUseCase

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val testItem = MediaItem(
        id = "1",
        ratingKey = "100",
        serverId = "server1",
        title = "Test Movie",
        type = MediaType.Movie,
        durationMs = 100_000L,
    )

    @Before
    fun setup() {
        playbackRepository = mockk(relaxed = true)
        watchNextHelper = mockk(relaxed = true)
        tvChannelManager = mockk(relaxed = true)
        prefetchNextEpisodeUseCase = mockk(relaxed = true)
        getUnifiedHomeContentUseCase = mockk(relaxed = true)

        scrobbler = PlayerScrobbler(
            playbackRepository = playbackRepository,
            watchNextHelper = watchNextHelper,
            tvChannelManager = tvChannelManager,
            prefetchNextEpisodeUseCase = prefetchNextEpisodeUseCase,
            getUnifiedHomeContentUseCase = getUnifiedHomeContentUseCase,
            applicationScope = testScope,
            ioDispatcher = testDispatcher,
        )
    }

    // --- checkAutoScrobble ---

    @Test
    fun `checkAutoScrobble - triggers at 95 percent progress`() = testScope.runTest {
        scrobbler.checkAutoScrobble(95_000, 100_000, testItem)
        advanceUntilIdle()

        assertThat(scrobbler.isScrobbled).isTrue()
        coVerify { playbackRepository.toggleWatchStatus(testItem, isWatched = true) }
    }

    @Test
    fun `checkAutoScrobble - does not trigger below 95 percent`() = testScope.runTest {
        scrobbler.checkAutoScrobble(94_000, 100_000, testItem)
        advanceUntilIdle()

        assertThat(scrobbler.isScrobbled).isFalse()
        coVerify(exactly = 0) { playbackRepository.toggleWatchStatus(any(), any()) }
    }

    @Test
    fun `checkAutoScrobble - fires only once per session`() = testScope.runTest {
        scrobbler.checkAutoScrobble(95_000, 100_000, testItem)
        scrobbler.checkAutoScrobble(96_000, 100_000, testItem)
        scrobbler.checkAutoScrobble(99_000, 100_000, testItem)
        advanceUntilIdle()

        coVerify(exactly = 1) { playbackRepository.toggleWatchStatus(any(), any()) }
    }

    @Test
    fun `checkAutoScrobble - ignores short durations`() = testScope.runTest {
        scrobbler.checkAutoScrobble(950, 1000, testItem)
        advanceUntilIdle()

        assertThat(scrobbler.isScrobbled).isFalse()
        coVerify(exactly = 0) { playbackRepository.toggleWatchStatus(any(), any()) }
    }

    // --- resetAutoNext ---

    @Test
    fun `resetAutoNext - resets scrobble flag for episode transitions`() = testScope.runTest {
        scrobbler.checkAutoScrobble(95_000, 100_000, testItem)
        advanceUntilIdle()
        assertThat(scrobbler.isScrobbled).isTrue()

        scrobbler.resetAutoNext()
        assertThat(scrobbler.isScrobbled).isFalse()

        // Can scrobble again after reset
        scrobbler.checkAutoScrobble(95_000, 100_000, testItem)
        advanceUntilIdle()
        assertThat(scrobbler.isScrobbled).isTrue()
        coVerify(exactly = 2) { playbackRepository.toggleWatchStatus(any(), any()) }
    }

    // --- stop ---

    @Test
    fun `stop - sends stopped timeline with item and position`() = testScope.runTest {
        scrobbler.stop(testItem, 50_000)
        advanceUntilIdle()

        coVerify { playbackRepository.sendStoppedTimeline(testItem, 50_000) }
    }

    @Test
    fun `stop - flushes local progress`() = testScope.runTest {
        scrobbler.stop(testItem, 50_000)
        advanceUntilIdle()

        coVerify { playbackRepository.flushLocalProgress() }
    }

    @Test
    fun `stop - refreshes On Deck for home screen`() = testScope.runTest {
        scrobbler.stop(testItem, 50_000)
        advanceUntilIdle()

        verify { getUnifiedHomeContentUseCase.refresh() }
    }

    @Test
    fun `stop - skips stopped timeline when no item`() = testScope.runTest {
        scrobbler.stop(currentItem = null, currentPosition = 0)
        advanceUntilIdle()

        coVerify(exactly = 0) { playbackRepository.sendStoppedTimeline(any(), any()) }
        // Should still flush and refresh
        coVerify { playbackRepository.flushLocalProgress() }
        verify { getUnifiedHomeContentUseCase.refresh() }
    }

    @Test
    fun `stop - resets scrobble flag`() = testScope.runTest {
        scrobbler.checkAutoScrobble(95_000, 100_000, testItem)
        advanceUntilIdle()
        assertThat(scrobbler.isScrobbled).isTrue()

        scrobbler.stop(testItem, 95_000)
        assertThat(scrobbler.isScrobbled).isFalse()
    }
}
