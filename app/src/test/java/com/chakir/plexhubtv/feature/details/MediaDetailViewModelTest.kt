package com.chakir.plexhubtv.feature.details

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.domain.service.PlaybackManager
import com.chakir.plexhubtv.domain.usecase.*
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val testDispatcher = StandardTestDispatcher()

    private val getMediaDetailUseCase = mockk<GetMediaDetailUseCase>()
    private val toggleWatchStatusUseCase = mockk<ToggleWatchStatusUseCase>()
    private val getNextEpisodeUseCase = mockk<GetNextEpisodeUseCase>()
    private val getPlayQueueUseCase = mockk<GetPlayQueueUseCase>()
    private val playbackManager = mockk<PlaybackManager>(relaxed = true)
    private val toggleFavoriteUseCase = mockk<ToggleFavoriteUseCase>()
    private val isFavoriteUseCase = mockk<IsFavoriteUseCase>()
    private val enrichMediaItemUseCase = mockk<EnrichMediaItemUseCase>()
    private val getSimilarMediaUseCase = mockk<GetSimilarMediaUseCase>()
    private val getMediaCollectionsUseCase = mockk<GetMediaCollectionsUseCase>()

    private lateinit var viewModel: MediaDetailViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        val savedStateHandle = SavedStateHandle(mapOf("ratingKey" to "1", "serverId" to "s1"))

        // Default mocks
        coEvery { isFavoriteUseCase(any(), any()) } returns flowOf(false)
        coEvery { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.success(MediaDetail(createMediaItem("1", "s1"))))
        coEvery { getSimilarMediaUseCase(any(), any()) } returns Result.success(emptyList())
        coEvery { getMediaCollectionsUseCase(any(), any()) } returns flowOf(emptyList())
        coEvery { enrichMediaItemUseCase(any()) } answers { it.invocation.args[0] as MediaItem }

        viewModel =
            MediaDetailViewModel(
                getMediaDetailUseCase,
                toggleWatchStatusUseCase,
                getNextEpisodeUseCase,
                getPlayQueueUseCase,
                playbackManager,
                toggleFavoriteUseCase,
                isFavoriteUseCase,
                enrichMediaItemUseCase,
                getSimilarMediaUseCase,
                getMediaCollectionsUseCase,
                savedStateHandle,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init - loads media details and checks favorite status`() =
        runTest {
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isLoading).isFalse()
            assertThat(state.media).isNotNull()
            assertThat(state.media?.ratingKey).isEqualTo("1")
            coVerify { isFavoriteUseCase("1", "s1") }
        }

    @Test
    fun `onEvent ToggleWatchStatus - updates UI and calls usecase`() =
        runTest {
            val media = createMediaItem("1", "s1", isWatched = false)
            coEvery { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.success(MediaDetail(media)))
            coEvery { toggleWatchStatusUseCase(any(), any()) } returns Result.success(Unit)

            // Re-init to pick up mocked data
            val savedStateHandle = SavedStateHandle(mapOf("ratingKey" to "1", "serverId" to "s1"))
            viewModel =
                MediaDetailViewModel(
                    getMediaDetailUseCase, toggleWatchStatusUseCase, getNextEpisodeUseCase, getPlayQueueUseCase,
                    playbackManager, toggleFavoriteUseCase, isFavoriteUseCase, enrichMediaItemUseCase,
                    getSimilarMediaUseCase, getMediaCollectionsUseCase, savedStateHandle,
                )

            advanceUntilIdle()

            viewModel.onEvent(MediaDetailEvent.ToggleWatchStatus)

            // Trigger optimistic update
            testDispatcher.scheduler.runCurrent()

            assertThat(viewModel.uiState.value.media?.isWatched).isTrue()
            coVerify { toggleWatchStatusUseCase(any(), true) }
        }

    @Test
    fun `onEvent PlayClicked - initiates playback via manager`() =
        runTest {
            val media = createMediaItem("1", "s1")
            coEvery { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.success(MediaDetail(media)))
            coEvery { getNextEpisodeUseCase(any()) } returns Result.failure(Exception("No next episode"))
            coEvery { getPlayQueueUseCase(any()) } returns Result.success(listOf(media))

            advanceUntilIdle()

            viewModel.onEvent(MediaDetailEvent.PlayClicked)

            advanceUntilIdle()

            coVerify { playbackManager.play(any(), any()) }
        }

    private fun createMediaItem(
        id: String,
        serverId: String,
        isWatched: Boolean = false,
    ) = MediaItem(
        id = id,
        ratingKey = id,
        serverId = serverId,
        title = "Title $id",
        type = MediaType.Movie,
        isWatched = isWatched,
        mediaParts = emptyList(),
        genres = emptyList(),
    )
}
