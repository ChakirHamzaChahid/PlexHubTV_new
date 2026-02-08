package com.chakir.plexhubtv.feature.library

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkManager
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.Server
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.domain.repository.*
import com.chakir.plexhubtv.domain.usecase.*
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
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
class LibraryViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val getLibraryContentUseCase = mockk<GetLibraryContentUseCase>(relaxed = true)
    private val getRecommendedContentUseCase = mockk<GetRecommendedContentUseCase>()
    private val authRepository = mockk<AuthRepository>()
    private val libraryRepository = mockk<LibraryRepository>()
    private val mediaDao = mockk<MediaDao>(relaxed = true)
    private val syncRepository = mockk<SyncRepository>()
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val connectionManager = mockk<ConnectionManager>()
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val getLibraryIndexUseCase = mockk<GetLibraryIndexUseCase>()

    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        val savedStateHandle = SavedStateHandle(mapOf("mediaType" to "movie"))

        // Default mocks
        coEvery { authRepository.getServers() } returns
            Result.success(
                listOf(
                    Server("s1", "Server 1", "127.0.0.1", 32400, "http://s1", accessToken = "t1", isOwned = true),
                ),
            )
        coEvery { mediaDao.getUniqueCountByType(any()) } returns 500
        coEvery { mediaDao.getRawCountByType(any()) } returns 1000
        every { settingsRepository.excludedServerIds } returns flowOf(emptySet())
        every { settingsRepository.defaultServer } returns flowOf("all")

        viewModel =
            LibraryViewModel(
                getLibraryContentUseCase,
                getRecommendedContentUseCase,
                authRepository,
                libraryRepository,
                mediaDao,
                syncRepository,
                settingsRepository,
                connectionManager,
                workManager,
                getLibraryIndexUseCase,
                savedStateHandle,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init - sets correct media type and loads servers`() =
        runTest {
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.mediaType).isEqualTo(MediaType.Movie)
            assertThat(state.availableServers).contains("Server 1")
            assertThat(state.totalItems).isEqualTo(500)
        }

    @Test
    fun `onAction SelectServerFilter - updates state`() =
        runTest {
            advanceUntilIdle()

            viewModel.onAction(LibraryAction.SelectServerFilter("Server 1"))

            assertThat(viewModel.uiState.value.selectedServerFilter).isEqualTo("Server 1")
        }

    @Test
    fun `onAction ApplySort - updates sort params`() =
        runTest {
            advanceUntilIdle()

            viewModel.onAction(LibraryAction.ApplySort("title", true))

            val state = viewModel.uiState.value
            assertThat(state.currentSort).isEqualTo("title")
            assertThat(state.isSortDescending).isTrue()
        }

    @Test
    fun `onAction UpdateSearchQuery - updates query in state`() =
        runTest {
            advanceUntilIdle()

            viewModel.onAction(LibraryAction.UpdateSearchQuery("Inception"))

            assertThat(viewModel.uiState.value.searchQuery).isEqualTo("Inception")
        }
}
