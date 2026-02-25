package com.chakir.plexhubtv.feature.plexhome

import com.chakir.plexhubtv.core.model.PlexHomeUser
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
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
class PlexHomeSwitcherViewModelTest {
    private lateinit var viewModel: PlexHomeSwitcherViewModel
    private lateinit var authRepository: AuthRepository

    private val testDispatcher = StandardTestDispatcher()

    private val testUsers = listOf(
        PlexHomeUser(
            id = 1, uuid = "u1", title = "Alice",
            username = "alice", email = "", friendlyName = "Alice",
            thumb = "", hasPassword = false, restricted = false,
            protected = false, admin = true, guest = false,
        ),
        PlexHomeUser(
            id = 2, uuid = "u2", title = "Bob",
            username = "bob", email = "", friendlyName = "Bob",
            thumb = "", hasPassword = true, restricted = false,
            protected = true, admin = false, guest = false,
        ),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads users successfully`() = runTest {
        coEvery { authRepository.getHomeUsers() } returns Result.success(testUsers)

        viewModel = PlexHomeSwitcherViewModel(authRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.users).hasSize(2)
        assertThat(state.users[0].title).isEqualTo("Alice")
        assertThat(state.error).isNull()
    }

    @Test
    fun `init handles load error`() = runTest {
        coEvery { authRepository.getHomeUsers() } returns Result.failure(Exception("Network error"))

        viewModel = PlexHomeSwitcherViewModel(authRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNotNull()
        assertThat(state.users).isEmpty()
    }

    @Test
    fun `SelectUser without PIN switches directly`() = runTest {
        coEvery { authRepository.getHomeUsers() } returns Result.success(testUsers)
        coEvery { authRepository.switchUser(any(), any()) } returns Result.success(true)

        viewModel = PlexHomeSwitcherViewModel(authRepository)
        advanceUntilIdle()

        viewModel.onAction(PlexHomeSwitcherAction.SelectUser(testUsers[0])) // Alice, no PIN
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.switchSuccess).isTrue()
        assertThat(state.showPinDialog).isFalse()
    }

    @Test
    fun `SelectUser with PIN shows dialog`() = runTest {
        coEvery { authRepository.getHomeUsers() } returns Result.success(testUsers)

        viewModel = PlexHomeSwitcherViewModel(authRepository)
        advanceUntilIdle()

        viewModel.onAction(PlexHomeSwitcherAction.SelectUser(testUsers[1])) // Bob, has PIN

        val state = viewModel.uiState.value
        assertThat(state.showPinDialog).isTrue()
        assertThat(state.selectedUser).isEqualTo(testUsers[1])
    }

    @Test
    fun `CancelPin hides dialog`() = runTest {
        coEvery { authRepository.getHomeUsers() } returns Result.success(testUsers)

        viewModel = PlexHomeSwitcherViewModel(authRepository)
        advanceUntilIdle()

        viewModel.onAction(PlexHomeSwitcherAction.SelectUser(testUsers[1]))
        viewModel.onAction(PlexHomeSwitcherAction.CancelPin)

        val state = viewModel.uiState.value
        assertThat(state.showPinDialog).isFalse()
        assertThat(state.selectedUser).isNull()
    }
}
