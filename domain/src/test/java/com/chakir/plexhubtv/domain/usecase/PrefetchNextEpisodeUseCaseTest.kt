package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class PrefetchNextEpisodeUseCaseTest {

    private lateinit var mediaDetailRepository: MediaDetailRepository
    private lateinit var episodeNavigationUseCase: EpisodeNavigationUseCase
    private lateinit var prefetchNextEpisodeUseCase: PrefetchNextEpisodeUseCase

    private val currentEpisode = MediaItem(
        id = "server1:ep1",
        ratingKey = "ep1",
        serverId = "server1",
        title = "Episode 1",
        type = MediaType.Episode,
        parentRatingKey = "season1",
        parentIndex = 1,
        episodeIndex = 1
    )

    private val nextEpisode = MediaItem(
        id = "server1:ep2",
        ratingKey = "ep2",
        serverId = "server1",
        title = "Episode 2",
        type = MediaType.Episode,
        parentRatingKey = "season1",
        parentIndex = 1,
        episodeIndex = 2
    )

    @Before
    fun setup() {
        mediaDetailRepository = mockk()
        episodeNavigationUseCase = mockk()
        prefetchNextEpisodeUseCase = PrefetchNextEpisodeUseCase(
            mediaDetailRepository = mediaDetailRepository,
            episodeNavigationUseCase = episodeNavigationUseCase,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun `invoke prefetches next episode successfully`() = runTest {
        // Given
        coEvery { episodeNavigationUseCase.loadAdjacentEpisodes(currentEpisode) } returns
            Result.success(AdjacentEpisodes(next = nextEpisode))
        coEvery { mediaDetailRepository.getMediaDetail("ep2", "server1") } returns
            Result.success(nextEpisode)

        // When
        val result = prefetchNextEpisodeUseCase(currentEpisode)

        // Then
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(nextEpisode)
        coVerify(exactly = 1) { mediaDetailRepository.getMediaDetail("ep2", "server1") }
    }

    @Test
    fun `invoke returns null when no next episode exists`() = runTest {
        // Given
        coEvery { episodeNavigationUseCase.loadAdjacentEpisodes(currentEpisode) } returns
            Result.success(AdjacentEpisodes(next = null))

        // When
        val result = prefetchNextEpisodeUseCase(currentEpisode)

        // Then
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isNull()
        coVerify(exactly = 0) { mediaDetailRepository.getMediaDetail(any(), any()) }
    }

    @Test
    fun `invoke returns null for non-episode media types`() = runTest {
        // Given
        val movie = currentEpisode.copy(type = MediaType.Movie)

        // When
        val result = prefetchNextEpisodeUseCase(movie)

        // Then
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isNull()
        coVerify(exactly = 0) { episodeNavigationUseCase.loadAdjacentEpisodes(any()) }
    }

    @Test
    fun `invoke skips prefetch if already prefetched for same episode`() = runTest {
        // Given
        coEvery { episodeNavigationUseCase.loadAdjacentEpisodes(currentEpisode) } returns
            Result.success(AdjacentEpisodes(next = nextEpisode))
        coEvery { mediaDetailRepository.getMediaDetail("ep2", "server1") } returns
            Result.success(nextEpisode)

        // When - First call
        prefetchNextEpisodeUseCase(currentEpisode)
        // When - Second call for same episode
        val result = prefetchNextEpisodeUseCase(currentEpisode)

        // Then - Only one prefetch should occur
        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 1) { mediaDetailRepository.getMediaDetail("ep2", "server1") }
    }

    @Test
    fun `reset clears prefetch state`() = runTest {
        // Given
        coEvery { episodeNavigationUseCase.loadAdjacentEpisodes(currentEpisode) } returns
            Result.success(AdjacentEpisodes(next = nextEpisode))
        coEvery { mediaDetailRepository.getMediaDetail("ep2", "server1") } returns
            Result.success(nextEpisode)

        // First prefetch
        prefetchNextEpisodeUseCase(currentEpisode)

        // When - Reset and prefetch again
        prefetchNextEpisodeUseCase.reset()
        val result = prefetchNextEpisodeUseCase(currentEpisode)

        // Then - Prefetch should happen again
        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 2) { mediaDetailRepository.getMediaDetail("ep2", "server1") }
    }

    @Test
    fun `invoke handles prefetch failure gracefully`() = runTest {
        // Given
        coEvery { episodeNavigationUseCase.loadAdjacentEpisodes(currentEpisode) } returns
            Result.success(AdjacentEpisodes(next = nextEpisode))
        coEvery { mediaDetailRepository.getMediaDetail("ep2", "server1") } returns
            Result.failure(Exception("Network error"))

        // When
        val result = prefetchNextEpisodeUseCase(currentEpisode)

        // Then
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isNull()
    }
}
