package com.chakir.plexhubtv.feature.playlist

import com.chakir.plexhubtv.core.model.Playlist
import com.chakir.plexhubtv.domain.repository.PlaylistRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistListViewModelTest {
    private lateinit var playlistRepository: PlaylistRepository

    private val testDispatcher = StandardTestDispatcher()

    private val testPlaylist = Playlist(
        id = "pl1",
        serverId = "server1",
        title = "My Playlist",
        summary = "A test playlist",
        thumbUrl = "http://example.com/thumb.jpg",
        itemCount = 5,
        durationMs = 300_000,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        playlistRepository = mockk(relaxed = true)
        every { playlistRepository.getPlaylists() } returns flowOf(listOf(testPlaylist))
        coEvery { playlistRepository.refreshPlaylists() } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    private fun createViewModel(): PlaylistListViewModel {
        return PlaylistListViewModel(playlistRepository = playlistRepository)
    }

    @Test
    fun `init - loads playlists from repository`() = runTest {
        val viewModel = createViewModel()
        // WhileSubscribed needs an active subscriber to start upstream collection
        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.playlists).hasSize(1)
        assertThat(state.playlists.first().title).isEqualTo("My Playlist")
        assertThat(state.error).isNull()
    }

    @Test
    fun `init - calls refreshPlaylists on start`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        coVerify { playlistRepository.refreshPlaylists() }
    }

    @Test
    fun `init - empty playlists shows empty list`() = runTest {
        every { playlistRepository.getPlaylists() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.playlists).isEmpty()
        assertThat(state.error).isNull()
    }

    @Test
    fun `init - repository flow error sets error state`() = runTest {
        every { playlistRepository.getPlaylists() } returns kotlinx.coroutines.flow.flow {
            throw RuntimeException("DB error")
        }

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isEqualTo("DB error")
    }

    @Test
    fun `refreshPlaylists - calls repository refresh`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        clearMocks(playlistRepository, answers = false, recordedCalls = true)

        viewModel.refreshPlaylists()
        advanceUntilIdle()

        coVerify { playlistRepository.refreshPlaylists() }
    }

    @Test
    fun `refreshPlaylists - swallows exceptions gracefully`() = runTest {
        coEvery { playlistRepository.refreshPlaylists() } throws RuntimeException("Network error")

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Should not crash — exception is caught in viewModelScope.launch
        val state = viewModel.uiState.value
        assertThat(state.playlists).hasSize(1)
    }

    @Test
    fun `uiState - updates when repository emits new data`() = runTest {
        val playlistFlow = MutableSharedFlow<List<Playlist>>()
        every { playlistRepository.getPlaylists() } returns playlistFlow

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Initially loading (no emission yet)
        assertThat(viewModel.uiState.value.isLoading).isTrue()

        // Emit playlists
        playlistFlow.emit(listOf(testPlaylist))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.playlists).hasSize(1)

        // Emit updated list
        val updatedPlaylist = testPlaylist.copy(title = "Updated Playlist")
        playlistFlow.emit(listOf(testPlaylist, updatedPlaylist))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.playlists).hasSize(2)
    }
}
