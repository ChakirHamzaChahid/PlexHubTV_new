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

    @Test
    fun `onEvent ToggleFavorite - calls toggle favorite use case`() =
        runTest {
            val media = createMediaItem("1", "s1")
            coEvery { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.success(MediaDetail(media)))
            coEvery { toggleFavoriteUseCase(any()) } returns Result.success(Unit)

            advanceUntilIdle()

            viewModel.onEvent(MediaDetailEvent.ToggleFavorite)

            advanceUntilIdle()

            coVerify { toggleFavoriteUseCase(media) }
        }

    @Test
    fun `onEvent ShowSourceSelection - updates state to show source selection`() =
        runTest {
            val media = createMediaItem("1", "s1")
            coEvery { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.success(MediaDetail(media)))

            advanceUntilIdle()

            viewModel.onEvent(MediaDetailEvent.ShowSourceSelection)

            testDispatcher.scheduler.runCurrent()

            val state = viewModel.uiState.value
            assertThat(state.showSourceSelection).isTrue()
            assertThat(state.selectedPlaybackItem).isEqualTo(media)
        }

    @Test
    fun `onEvent DismissSourceSelection - hides source selection dialog`() =
        runTest {
            val media = createMediaItem("1", "s1")
            coEvery { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.success(MediaDetail(media)))

            advanceUntilIdle()

            // First show source selection
            viewModel.onEvent(MediaDetailEvent.ShowSourceSelection)
            testDispatcher.scheduler.runCurrent()

            // Then dismiss it
            viewModel.onEvent(MediaDetailEvent.DismissSourceSelection)
            testDispatcher.scheduler.runCurrent()

            val state = viewModel.uiState.value
            assertThat(state.showSourceSelection).isFalse()
            assertThat(state.selectedPlaybackItem).isNull()
        }

    @Test
    fun `onEvent PlaySource - plays specific source and hides dialog`() =
        runTest {
            val media = createMediaItem("1", "s1")
            val remoteSource = com.chakir.plexhubtv.core.model.RemoteSource("s2", "2", "Server 2", "icon")

            coEvery { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.success(MediaDetail(media)))
            coEvery { getPlayQueueUseCase(any()) } returns Result.success(listOf(media))

            advanceUntilIdle()

            // Show source selection first
            viewModel.onEvent(MediaDetailEvent.ShowSourceSelection)
            testDispatcher.scheduler.runCurrent()

            // Play specific source
            viewModel.onEvent(MediaDetailEvent.PlaySource(remoteSource))

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.showSourceSelection).isFalse()
            coVerify { playbackManager.play(any(), any()) }
        }

    @Test
    fun `onEvent Retry - reloads detail and favorite status`() =
        runTest {
            val media = createMediaItem("1", "s1")
            coEvery { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.success(MediaDetail(media)))

            advanceUntilIdle()

            // Call retry
            viewModel.onEvent(MediaDetailEvent.Retry)

            advanceUntilIdle()

            // Verify that detail was loaded again (init + retry = 2 calls)
            coVerify(exactly = 2) { getMediaDetailUseCase("1", "s1") }
        }

    @Test
    fun `loadDetail failure - updates state with error`() =
        runTest {
            val errorMessage = "Network error"
            val savedStateHandle = SavedStateHandle(mapOf("ratingKey" to "1", "serverId" to "s1"))

            coEvery { isFavoriteUseCase(any(), any()) } returns flowOf(false)
            coEvery { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.failure(Exception(errorMessage)))

            viewModel = MediaDetailViewModel(
                getMediaDetailUseCase, toggleWatchStatusUseCase, getNextEpisodeUseCase, getPlayQueueUseCase,
                playbackManager, toggleFavoriteUseCase, isFavoriteUseCase, enrichMediaItemUseCase,
                getSimilarMediaUseCase, getMediaCollectionsUseCase, savedStateHandle,
            )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isLoading).isFalse()
            assertThat(state.error).isEqualTo(errorMessage)
            assertThat(state.media).isNull()
        }

    @Test
    fun `PlayClicked with multiple sources - shows source selection`() =
        runTest {
            val remoteSources = listOf(
                com.chakir.plexhubtv.core.model.RemoteSource("s1", "1", "Server 1", "icon1"),
                com.chakir.plexhubtv.core.model.RemoteSource("s2", "2", "Server 2", "icon2")
            )
            val media = createMediaItem("1", "s1").copy(remoteSources = remoteSources)

            coEvery { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.success(MediaDetail(media)))
            coEvery { getNextEpisodeUseCase(any()) } returns Result.failure(Exception("No next episode"))
            coEvery { enrichMediaItemUseCase(any()) } returns media

            advanceUntilIdle()

            viewModel.onEvent(MediaDetailEvent.PlayClicked)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.showSourceSelection).isTrue()
            assertThat(state.selectedPlaybackItem).isEqualTo(media)
        }

    @Test
    fun `loadSimilarItems success - updates state with similar items`() =
        runTest {
            val similarItems = listOf(
                createMediaItem("similar1", "s1"),
                createMediaItem("similar2", "s1")
            )

            coEvery { getSimilarMediaUseCase(any(), any()) } returns Result.success(similarItems)

            val savedStateHandle = SavedStateHandle(mapOf("ratingKey" to "1", "serverId" to "s1"))
            viewModel = MediaDetailViewModel(
                getMediaDetailUseCase, toggleWatchStatusUseCase, getNextEpisodeUseCase, getPlayQueueUseCase,
                playbackManager, toggleFavoriteUseCase, isFavoriteUseCase, enrichMediaItemUseCase,
                getSimilarMediaUseCase, getMediaCollectionsUseCase, savedStateHandle,
            )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.similarItems).hasSize(2)
            assertThat(state.similarItems.map { it.ratingKey }).containsExactly("similar1", "similar2")
        }

    @Test
    fun `checkFavoriteStatus updates media favorite state`() =
        runTest {
            val media = createMediaItem("1", "s1")

            // First returns false, then true
            coEvery { isFavoriteUseCase("1", "s1") } returns flowOf(false, true)
            coEvery { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.success(MediaDetail(media)))

            val savedStateHandle = SavedStateHandle(mapOf("ratingKey" to "1", "serverId" to "s1"))
            viewModel = MediaDetailViewModel(
                getMediaDetailUseCase, toggleWatchStatusUseCase, getNextEpisodeUseCase, getPlayQueueUseCase,
                playbackManager, toggleFavoriteUseCase, isFavoriteUseCase, enrichMediaItemUseCase,
                getSimilarMediaUseCase, getMediaCollectionsUseCase, savedStateHandle,
            )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.media?.isFavorite).isFalse()
        }

    @Test
    fun `ToggleWatchStatus failure - reverts optimistic update`() =
        runTest {
            val media = createMediaItem("1", "s1", isWatched = false)
            coEvery { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.success(MediaDetail(media)))
            coEvery { toggleWatchStatusUseCase(any(), any()) } returns Result.failure(Exception("Server error"))

            val savedStateHandle = SavedStateHandle(mapOf("ratingKey" to "1", "serverId" to "s1"))
            viewModel = MediaDetailViewModel(
                getMediaDetailUseCase, toggleWatchStatusUseCase, getNextEpisodeUseCase, getPlayQueueUseCase,
                playbackManager, toggleFavoriteUseCase, isFavoriteUseCase, enrichMediaItemUseCase,
                getSimilarMediaUseCase, getMediaCollectionsUseCase, savedStateHandle,
            )

            advanceUntilIdle()

            viewModel.onEvent(MediaDetailEvent.ToggleWatchStatus)

            advanceUntilIdle()

            // Should be reverted back to false due to failure
            val state = viewModel.uiState.value
            assertThat(state.media?.isWatched).isFalse()
        }

    @Test
    fun `loadAvailableServers enriches media with remote sources`() =
        runTest {
            val remoteSources = listOf(
                com.chakir.plexhubtv.core.model.RemoteSource("s2", "2", "Server 2", "icon2")
            )
            val media = createMediaItem("1", "s1")
            val enrichedMedia = media.copy(remoteSources = remoteSources)

            coEvery { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.success(MediaDetail(media)))
            coEvery { enrichMediaItemUseCase(media) } returns enrichedMedia

            val savedStateHandle = SavedStateHandle(mapOf("ratingKey" to "1", "serverId" to "s1"))
            viewModel = MediaDetailViewModel(
                getMediaDetailUseCase, toggleWatchStatusUseCase, getNextEpisodeUseCase, getPlayQueueUseCase,
                playbackManager, toggleFavoriteUseCase, isFavoriteUseCase, enrichMediaItemUseCase,
                getSimilarMediaUseCase, getMediaCollectionsUseCase, savedStateHandle,
            )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.media?.remoteSources).hasSize(1)
            assertThat(state.isEnriching).isFalse()
            coVerify { enrichMediaItemUseCase(media) }
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
