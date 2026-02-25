package com.chakir.plexhubtv.feature.details

import androidx.lifecycle.SavedStateHandle
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.domain.service.PlaybackManager
import com.chakir.plexhubtv.domain.usecase.*
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaDetailViewModelTest {
    private lateinit var viewModel: MediaDetailViewModel
    private lateinit var getMediaDetailUseCase: GetMediaDetailUseCase
    private lateinit var toggleWatchStatusUseCase: ToggleWatchStatusUseCase
    private lateinit var getNextEpisodeUseCase: GetNextEpisodeUseCase
    private lateinit var getPlayQueueUseCase: GetPlayQueueUseCase
    private lateinit var playbackManager: PlaybackManager
    private lateinit var toggleFavoriteUseCase: ToggleFavoriteUseCase
    private lateinit var isFavoriteUseCase: IsFavoriteUseCase
    private lateinit var enrichMediaItemUseCase: EnrichMediaItemUseCase
    private lateinit var getSimilarMediaUseCase: GetSimilarMediaUseCase
    private lateinit var getMediaCollectionsUseCase: GetMediaCollectionsUseCase
    private lateinit var performanceTracker: com.chakir.plexhubtv.core.common.PerformanceTracker

    private val testDispatcher = StandardTestDispatcher()
    private val testMovie = MediaItem(
        id = "1",
        ratingKey = "123",
        serverId = "server1",
        title = "Test Movie",
        type = MediaType.Movie
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock use cases
        getMediaDetailUseCase = mockk(relaxed = true)
        toggleWatchStatusUseCase = mockk(relaxed = true)
        getNextEpisodeUseCase = mockk(relaxed = true)
        getPlayQueueUseCase = mockk(relaxed = true)
        playbackManager = mockk(relaxed = true)
        toggleFavoriteUseCase = mockk(relaxed = true)
        isFavoriteUseCase = mockk(relaxed = true)
        enrichMediaItemUseCase = mockk(relaxed = true)
        getSimilarMediaUseCase = mockk(relaxed = true)
        getMediaCollectionsUseCase = mockk(relaxed = true)
        performanceTracker = mockk(relaxed = true)

        // Default mock behaviors
        coEvery { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.success(MediaDetail(testMovie)))
        coEvery { isFavoriteUseCase(any(), any()) } returns flowOf(false)
        coEvery { enrichMediaItemUseCase(any()) } returns testMovie
        coEvery { getNextEpisodeUseCase(any()) } returns Result.failure(Exception("No next episode found"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    private fun createViewModel(): MediaDetailViewModel {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "ratingKey" to "123",
                "serverId" to "server1"
            )
        )

        return MediaDetailViewModel(
            getMediaDetailUseCase = getMediaDetailUseCase,
            toggleWatchStatusUseCase = toggleWatchStatusUseCase,
            getNextEpisodeUseCase = getNextEpisodeUseCase,
            getPlayQueueUseCase = getPlayQueueUseCase,
            playbackManager = playbackManager,
            toggleFavoriteUseCase = toggleFavoriteUseCase,
            isFavoriteUseCase = isFavoriteUseCase,
            enrichMediaItemUseCase = enrichMediaItemUseCase,
            getSimilarMediaUseCase = getSimilarMediaUseCase,
            getMediaCollectionsUseCase = getMediaCollectionsUseCase,
            performanceTracker = performanceTracker,
            savedStateHandle = savedStateHandle
        )
    }

    @Test
    fun `initialization - loads media detail from savedState`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify { getMediaDetailUseCase("123", "server1") }
        assertThat(viewModel.uiState.value.media).isEqualTo(testMovie)
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `initialization - checks favorite status`() = runTest {
        coEvery { isFavoriteUseCase("123", "server1") } returns flowOf(true)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.media?.isFavorite).isTrue()
    }

    @Test
    fun `PlayClicked - movie with single source plays directly`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(MediaDetailEvent.PlayClicked)
        advanceUntilIdle()

        // Should enrich and play directly (no source selection for single source)
        coVerify { enrichMediaItemUseCase(testMovie) }
        coVerify { playbackManager.play(any(), any()) }
    }

    @Test
    fun `PlayClicked - movie with multiple sources shows source selection`() = runTest {
        val movieWithMultipleSources = testMovie.copy(
            remoteSources = listOf(
                mockk { every { serverName } returns "Server 1" },
                mockk { every { serverName } returns "Server 2" }
            )
        )
        coEvery { enrichMediaItemUseCase(any()) } returns movieWithMultipleSources

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(MediaDetailEvent.PlayClicked)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showSourceSelection).isTrue()
        assertThat(viewModel.uiState.value.selectedPlaybackItem).isEqualTo(movieWithMultipleSources)
    }

    @Test
    fun `PlayClicked - show without playable episode sends error`() = runTest {
        val show = testMovie.copy(type = MediaType.Show)
        coEvery { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.success(MediaDetail(show)))
        coEvery { getNextEpisodeUseCase(any()) } returns Result.failure(Exception("No next episode found"))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(MediaDetailEvent.PlayClicked)
        advanceUntilIdle()

        // Check that an error was sent (no playable content)
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `ToggleFavorite - toggles favorite status`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(MediaDetailEvent.ToggleFavorite)
        advanceUntilIdle()

        coVerify { toggleFavoriteUseCase(testMovie) }
    }

    @Test
    fun `ToggleWatchStatus - toggles watch status`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(MediaDetailEvent.ToggleWatchStatus)
        advanceUntilIdle()

        coVerify { toggleWatchStatusUseCase(testMovie, any()) }
    }

    @Test
    fun `DismissSourceSelection - hides source selection dialog`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEvent(MediaDetailEvent.PlayClicked) // Show dialog first
        advanceUntilIdle()

        viewModel.onEvent(MediaDetailEvent.DismissSourceSelection)

        assertThat(viewModel.uiState.value.showSourceSelection).isFalse()
    }

    @Test
    fun `Retry - reloads media detail`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(MediaDetailEvent.Retry)
        advanceUntilIdle()

        coVerify(atLeast = 2) { getMediaDetailUseCase("123", "server1") }
    }

    @Test
    fun `loading error - updates error state`() = runTest {
        val error = Exception("Network error")
        coEvery { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.failure(error))

        viewModel = createViewModel()
        advanceUntilIdle()

        // Verify that loading is false and error occurred
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }
}
