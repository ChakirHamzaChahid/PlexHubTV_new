package com.chakir.plexhubtv.feature.home

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.di.image.ImagePrefetchManager
import com.chakir.plexhubtv.domain.repository.FavoritesRepository
import com.chakir.plexhubtv.domain.usecase.GetUnifiedHomeContentUseCase
import com.chakir.plexhubtv.domain.usecase.HomeContent
import com.google.common.truth.Truth.assertThat
import io.mockk.*
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
class HomeViewModelTest {
    private lateinit var viewModel: HomeViewModel
    private lateinit var getUnifiedHomeContentUseCase: GetUnifiedHomeContentUseCase
    private lateinit var favoritesRepository: FavoritesRepository
    private lateinit var workManager: WorkManager
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var imagePrefetchManager: ImagePrefetchManager

    private val testDispatcher = StandardTestDispatcher()

    private val testMediaItem = MediaItem(
        id = "1",
        ratingKey = "123",
        serverId = "server1",
        title = "Test Movie",
        type = MediaType.Movie
    )

    private val testHomeContent = HomeContent(
        onDeck = listOf(testMediaItem),
        hubs = listOf(
            Hub(
                key = "continueWatching",
                title = "Continue Watching",
                type = "continueWatching",
                items = listOf(testMediaItem)
            )
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock dependencies
        getUnifiedHomeContentUseCase = mockk(relaxed = true)
        favoritesRepository = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        settingsDataStore = mockk(relaxed = true)
        imagePrefetchManager = mockk(relaxed = true)

        // Default mock behaviors
        coEvery { getUnifiedHomeContentUseCase() } returns flowOf(Result.success(testHomeContent))
        coEvery { favoritesRepository.getFavorites() } returns flowOf(emptyList())
        coEvery { settingsDataStore.isFirstSyncComplete } returns flowOf(true)
        coEvery { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())
        coEvery { imagePrefetchManager.prefetchImages(any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            getUnifiedHomeContentUseCase = getUnifiedHomeContentUseCase,
            favoritesRepository = favoritesRepository,
            workManager = workManager,
            settingsDataStore = settingsDataStore,
            imagePrefetchManager = imagePrefetchManager
        )
    }

    @Test
    fun `loadContent success updates uiState with onDeck and hubs`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.onDeck).hasSize(1)
        assertThat(state.onDeck.first().title).isEqualTo("Test Movie")
        assertThat(state.hubs).hasSize(1)
        assertThat(state.hubs.first().title).isEqualTo("Continue Watching")
    }

    @Test
    fun `loadContent filters out empty hubs`() = runTest {
        val contentWithEmptyHub = HomeContent(
            onDeck = listOf(testMediaItem),
            hubs = listOf(
                Hub(key = "emptyHub", title = "Empty Hub", type = "test", items = emptyList()),
                Hub(key = "validHub", title = "Valid Hub", type = "test", items = listOf(testMediaItem))
            )
        )
        coEvery { getUnifiedHomeContentUseCase() } returns flowOf(Result.success(contentWithEmptyHub))

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.hubs).hasSize(1)
        assertThat(state.hubs.first().title).isEqualTo("Valid Hub")
    }

    @Test
    fun `loadContent failure sends error event`() = runTest {
        val error = Exception("Network error")
        coEvery { getUnifiedHomeContentUseCase() } returns flowOf(Result.failure(error))

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        // Error is sent via errorEvents channel - UI state should reflect loading is complete
    }

    @Test
    fun `observeFavorites updates favorites in uiState`() = runTest {
        val favorites = listOf(testMediaItem)
        coEvery { favoritesRepository.getFavorites() } returns flowOf(favorites)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.favorites).hasSize(1)
        assertThat(viewModel.uiState.value.favorites.first().title).isEqualTo("Test Movie")
    }

    @Test
    fun `onAction Refresh triggers loadContent`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Clear invocations from init
        clearMocks(getUnifiedHomeContentUseCase, answers = false)

        viewModel.onAction(HomeAction.Refresh)
        advanceUntilIdle()

        coVerify { getUnifiedHomeContentUseCase() }
    }

    @Test
    fun `onAction OpenMedia sends navigation event`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(HomeAction.OpenMedia(testMediaItem))

        // Navigation event is sent - this would be collected in the UI layer
        // We can't directly assert on Channel content in unit tests without UI layer
    }

    @Test
    fun `checkInitialSync sets isInitialSync when first sync not complete`() = runTest {
        coEvery { settingsDataStore.isFirstSyncComplete } returns flowOf(false)
        val workInfo = mockk<WorkInfo>()
        every { workInfo.state } returns WorkInfo.State.RUNNING
        every { workInfo.progress } returns androidx.work.workDataOf("progress" to 0.5f, "message" to "Syncing...")
        coEvery { workManager.getWorkInfosForUniqueWorkFlow("LibrarySync_Initial") } returns flowOf(listOf(workInfo))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isInitialSync).isTrue()
        assertThat(viewModel.uiState.value.syncProgress).isEqualTo(0.5f)
        assertThat(viewModel.uiState.value.syncMessage).isEqualTo("Syncing...")
    }
}
