package com.chakir.plexhubtv.feature.playlist

import androidx.lifecycle.SavedStateHandle
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.Playlist
import com.chakir.plexhubtv.domain.repository.PlaylistRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {
    private lateinit var playlistRepository: PlaylistRepository

    private val testDispatcher = StandardTestDispatcher()

    private val testMediaItem = MediaItem(
        id = "1",
        ratingKey = "123",
        serverId = "server1",
        title = "Test Movie",
        type = MediaType.Movie,
    )

    private val testPlaylist = Playlist(
        id = "pl1",
        serverId = "server1",
        title = "My Playlist",
        summary = "A test playlist",
        itemCount = 1,
        durationMs = 120_000,
        items = listOf(testMediaItem),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        playlistRepository = mockk(relaxed = true)
        coEvery { playlistRepository.getPlaylistDetail("pl1", "server1") } returns testPlaylist
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    private fun createViewModel(
        playlistId: String? = "pl1",
        serverId: String? = "server1",
    ): PlaylistDetailViewModel {
        val savedStateHandle = SavedStateHandle(
            buildMap {
                playlistId?.let { put("playlistId", it) }
                serverId?.let { put("serverId", it) }
            }
        )
        return PlaylistDetailViewModel(
            playlistRepository = playlistRepository,
            savedStateHandle = savedStateHandle,
        )
    }

    // ── Init / Loading ──

    @Test
    fun `init - loads playlist detail from repository`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.playlist).isNotNull()
        assertThat(state.playlist?.title).isEqualTo("My Playlist")
        assertThat(state.playlist?.items).hasSize(1)
        assertThat(state.error).isNull()
    }

    @Test
    fun `init - missing playlistId sets error state`() = runTest {
        val viewModel = createViewModel(playlistId = null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isEqualTo("Invalid navigation arguments")
    }

    @Test
    fun `init - missing serverId sets error state`() = runTest {
        val viewModel = createViewModel(serverId = null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isEqualTo("Invalid navigation arguments")
    }

    @Test
    fun `init - repository returns null sets error`() = runTest {
        coEvery { playlistRepository.getPlaylistDetail("pl1", "server1") } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isEqualTo("Playlist not found")
    }

    @Test
    fun `init - repository throws exception sets error`() = runTest {
        coEvery { playlistRepository.getPlaylistDetail("pl1", "server1") } throws RuntimeException("Network error")

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isEqualTo("Network error")
    }

    // ── Delete Playlist ──

    @Test
    fun `DeletePlaylistClicked - shows delete confirmation`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(PlaylistDetailEvent.DeletePlaylistClicked)

        assertThat(viewModel.uiState.value.showDeleteConfirmation).isTrue()
    }

    @Test
    fun `DismissDeleteDialog - hides delete confirmation`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(PlaylistDetailEvent.DeletePlaylistClicked)
        assertThat(viewModel.uiState.value.showDeleteConfirmation).isTrue()

        viewModel.onEvent(PlaylistDetailEvent.DismissDeleteDialog)
        assertThat(viewModel.uiState.value.showDeleteConfirmation).isFalse()
    }

    @Test
    fun `ConfirmDeletePlaylist - success clears playlist`() = runTest {
        coEvery { playlistRepository.deletePlaylist("pl1", "server1") } returns Result.success(Unit)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(PlaylistDetailEvent.ConfirmDeletePlaylist)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.playlist).isNull()
        assertThat(state.isDeleting).isFalse()
        assertThat(state.showDeleteConfirmation).isFalse()
    }

    @Test
    fun `ConfirmDeletePlaylist - failure sets error`() = runTest {
        coEvery { playlistRepository.deletePlaylist("pl1", "server1") } returns Result.failure(Exception("Delete failed"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(PlaylistDetailEvent.ConfirmDeletePlaylist)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isDeleting).isFalse()
        assertThat(state.error).isEqualTo("Delete failed")
    }

    @Test
    fun `ConfirmDeletePlaylist - sets isDeleting during operation`() = runTest {
        coEvery { playlistRepository.deletePlaylist("pl1", "server1") } coAnswers {
            // Simulate delay - the state should show isDeleting=true before this returns
            Result.success(Unit)
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(PlaylistDetailEvent.DeletePlaylistClicked)
        assertThat(viewModel.uiState.value.showDeleteConfirmation).isTrue()

        viewModel.onEvent(PlaylistDetailEvent.ConfirmDeletePlaylist)
        // Before coroutine completes, showDeleteConfirmation should be false, isDeleting true
        assertThat(viewModel.uiState.value.showDeleteConfirmation).isFalse()
        assertThat(viewModel.uiState.value.isDeleting).isTrue()

        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isDeleting).isFalse()
    }
}
