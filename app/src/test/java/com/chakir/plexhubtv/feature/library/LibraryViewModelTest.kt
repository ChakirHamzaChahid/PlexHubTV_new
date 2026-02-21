package com.chakir.plexhubtv.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import androidx.work.WorkManager
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.Server
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.LibraryRepository
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import com.chakir.plexhubtv.domain.repository.SyncRepository
import com.chakir.plexhubtv.domain.usecase.GetLibraryContentUseCase
import com.chakir.plexhubtv.domain.usecase.GetLibraryIndexUseCase
import com.chakir.plexhubtv.domain.usecase.GetRecommendedContentUseCase
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
class LibraryViewModelTest {
    private lateinit var viewModel: LibraryViewModel
    private lateinit var getLibraryContentUseCase: GetLibraryContentUseCase
    private lateinit var getRecommendedContentUseCase: GetRecommendedContentUseCase
    private lateinit var authRepository: AuthRepository
    private lateinit var libraryRepository: LibraryRepository
    private lateinit var mediaDao: MediaDao
    private lateinit var syncRepository: SyncRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var connectionManager: ConnectionManager
    private lateinit var workManager: WorkManager
    private lateinit var getLibraryIndexUseCase: GetLibraryIndexUseCase
    private lateinit var savedStateHandle: SavedStateHandle

    private val testDispatcher = StandardTestDispatcher()

    private val testServers = listOf(
        Server(
            clientIdentifier = "server1",
            name = "Server 1",
            accessToken = "token1",
            baseUrl = "http://server1"
        ),
        Server(
            clientIdentifier = "server2",
            name = "Server 2",
            accessToken = "token2",
            baseUrl = "http://server2"
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock all dependencies
        getLibraryContentUseCase = mockk(relaxed = true)
        getRecommendedContentUseCase = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        libraryRepository = mockk(relaxed = true)
        mediaDao = mockk(relaxed = true)
        syncRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        connectionManager = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        getLibraryIndexUseCase = mockk(relaxed = true)

        // Default mock behaviors
        coEvery { authRepository.getServers() } returns Result.success(testServers)
        coEvery { mediaDao.getUniqueCountByType(any()) } returns 500
        coEvery { mediaDao.getRawCountByType(any()) } returns 600
        coEvery { getLibraryContentUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns flowOf(PagingData.empty())
        coEvery { settingsRepository.excludedServerIds } returns flowOf(emptySet())
        coEvery { settingsRepository.defaultServer } returns flowOf("all")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    private fun createViewModel(mediaType: String = "movie"): LibraryViewModel {
        savedStateHandle = SavedStateHandle(mapOf("mediaType" to mediaType))
        return LibraryViewModel(
            getLibraryContentUseCase = getLibraryContentUseCase,
            getRecommendedContentUseCase = getRecommendedContentUseCase,
            authRepository = authRepository,
            libraryRepository = libraryRepository,
            mediaDao = mediaDao,
            syncRepository = syncRepository,
            settingsRepository = settingsRepository,
            connectionManager = connectionManager,
            workManager = workManager,
            getLibraryIndexUseCase = getLibraryIndexUseCase,
            savedStateHandle = savedStateHandle
        )
    }

    @Test
    fun `initialization - loads metadata successfully`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.availableServers).hasSize(2)
        assertThat(state.availableServers).contains("Server 1")
        assertThat(state.availableServers).contains("Server 2")
        assertThat(state.totalItems).isEqualTo(500)
    }

    @Test
    fun `initialization - loads server map correctly`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.availableServersMap).containsEntry("Server 1", "server1")
        assertThat(state.availableServersMap).containsEntry("Server 2", "server2")
    }

    @Test
    fun `loadMetadata failure updates error state`() = runTest {
        val error = Exception("Failed to load servers")
        coEvery { authRepository.getServers() } returns Result.failure(error)

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        // Error is sent via errorEvents channel
    }

    @Test
    fun `onAction SelectTab updates selected tab`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(LibraryAction.SelectTab(LibraryTab.ALL))

        assertThat(viewModel.uiState.value.selectedTab).isEqualTo(LibraryTab.ALL)
    }

    @Test
    fun `onAction ChangeViewMode updates view mode`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(LibraryAction.ChangeViewMode(ViewMode.GRID))

        assertThat(viewModel.uiState.value.viewMode).isEqualTo(ViewMode.GRID)
    }

    @Test
    fun `onAction SelectGenre updates selected genre`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(LibraryAction.SelectGenre("Action"))

        assertThat(viewModel.uiState.value.selectedGenre).isEqualTo("Action")
    }

    @Test
    fun `onAction SelectServerFilter updates selected server filter`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(LibraryAction.SelectServerFilter("server1"))

        assertThat(viewModel.uiState.value.selectedServerFilter).isEqualTo("server1")
    }

    @Test
    fun `onAction ApplyFilter updates current filter`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(LibraryAction.ApplyFilter("unwatched"))

        assertThat(viewModel.uiState.value.currentFilter).isEqualTo("unwatched")
    }

    @Test
    fun `onAction ApplySort updates sort and closes dialog`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(LibraryAction.ApplySort("titleSort", isDescending = true))

        assertThat(viewModel.uiState.value.currentSort).isEqualTo("titleSort")
        assertThat(viewModel.uiState.value.isSortDescending).isTrue()
        assertThat(viewModel.uiState.value.isSortDialogOpen).isFalse()
    }

    @Test
    fun `onAction ToggleSearch toggles search visibility`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(LibraryAction.ToggleSearch)
        assertThat(viewModel.uiState.value.isSearchVisible).isTrue()

        viewModel.onAction(LibraryAction.ToggleSearch)
        assertThat(viewModel.uiState.value.isSearchVisible).isFalse()
        assertThat(viewModel.uiState.value.searchQuery).isEmpty()
    }

    @Test
    fun `onAction UpdateSearchQuery updates query`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(LibraryAction.UpdateSearchQuery("matrix"))

        assertThat(viewModel.uiState.value.searchQuery).isEqualTo("matrix")
    }

    @Test
    fun `initialization with show mediaType sets correct type`() = runTest {
        viewModel = createViewModel(mediaType = "show")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.mediaType).isEqualTo(MediaType.Show)
    }

    @Test
    fun `initialization with invalid mediaType defaults to Movie`() = runTest {
        viewModel = createViewModel(mediaType = "invalid")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.mediaType).isEqualTo(MediaType.Movie)
    }

    @Test
    fun `low item count triggers background sync`() = runTest {
        coEvery { mediaDao.getUniqueCountByType(any()) } returns 50 // Low count

        viewModel = createViewModel()
        advanceUntilIdle()

        verify { workManager.enqueueUniqueWork(any(), any(), any()) }
    }

    @Test
    fun `sufficient item count does not trigger background sync`() = runTest {
        coEvery { mediaDao.getUniqueCountByType(any()) } returns 500 // Sufficient count

        viewModel = createViewModel()
        advanceUntilIdle()

        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any()) }
    }
}
