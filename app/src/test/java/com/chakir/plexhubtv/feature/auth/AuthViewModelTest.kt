package com.chakir.plexhubtv.feature.auth

import androidx.lifecycle.SavedStateHandle
import com.chakir.plexhubtv.core.model.AuthPin
import com.chakir.plexhubtv.core.model.Server
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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

@ExperimentalCoroutinesApi
class AuthViewModelTest {

    private lateinit var viewModel: AuthViewModel
    private lateinit var authRepository: AuthRepository
    private lateinit var savedStateHandle: SavedStateHandle

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        savedStateHandle = SavedStateHandle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is loading`() = runTest {
        // Given
        coEvery { authRepository.checkAuthentication() } returns false

        // When
        viewModel = AuthViewModel(authRepository, savedStateHandle)

        // Then
        val state = viewModel.uiState.value
        assertThat(state.isLoading).isTrue()
        assertThat(state.isAuthenticated).isFalse()
    }

    @Test
    fun `checkAuth with existing token navigates to servers`() = runTest {
        // Given
        coEvery { authRepository.checkAuthentication() } returns true

        // When
        viewModel = AuthViewModel(authRepository, savedStateHandle)
        advanceUntilIdle()

        // Then
        coVerify { authRepository.checkAuthentication() }
        val state = viewModel.uiState.value
        assertThat(state.isAuthenticated).isTrue()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `checkAuth without token stays on auth screen`() = runTest {
        // Given
        coEvery { authRepository.checkAuthentication() } returns false

        // When
        viewModel = AuthViewModel(authRepository, savedStateHandle)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertThat(state.isAuthenticated).isFalse()
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    @Test
    fun `GetPin event success updates state with pin code`() = runTest {
        // Given
        val testPin = AuthPin(id = "12345", code = "ABCD")
        coEvery { authRepository.checkAuthentication() } returns false
        coEvery { authRepository.getPin(true) } returns Result.success(testPin)

        viewModel = AuthViewModel(authRepository, savedStateHandle)
        advanceUntilIdle()

        // When
        viewModel.onEvent(AuthEvent.GetPin)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertThat(state.pinCode).isEqualTo("ABCD")
        assertThat(state.pinId).isEqualTo("12345")
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    @Test
    fun `GetPin event failure shows error`() = runTest {
        // Given
        coEvery { authRepository.checkAuthentication() } returns false
        coEvery { authRepository.getPin(true) } returns Result.failure(Exception("Network error"))

        viewModel = AuthViewModel(authRepository, savedStateHandle)
        advanceUntilIdle()

        // When
        viewModel.onEvent(AuthEvent.GetPin)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertThat(state.error).isNotNull()
        assertThat(state.error).contains("Network error")
        assertThat(state.pinCode).isNull()
    }

    @Test
    fun `Retry event clears error and retries authentication`() = runTest {
        // Given
        coEvery { authRepository.checkAuthentication() } returns false

        viewModel = AuthViewModel(authRepository, savedStateHandle)
        advanceUntilIdle()

        // Set an error state
        viewModel.onEvent(AuthEvent.GetPin)
        advanceUntilIdle()

        // When
        viewModel.onEvent(AuthEvent.Retry)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertThat(state.error).isNull()
        coVerify(atLeast = 2) { authRepository.checkAuthentication() }
    }

    @Test
    fun `SubmitToken event with valid token succeeds`() = runTest {
        // Given
        val testToken = "test-plex-token-abc123"
        val testServers = listOf(
            Server(
                name = "Test Server",
                address = "http://localhost:32400",
                clientIdentifier = "server-123",
                accessToken = testToken,
                isOwned = true,
                connections = emptyList()
            )
        )

        coEvery { authRepository.checkAuthentication() } returns false
        coEvery { authRepository.loginWithToken(testToken) } returns Result.success(true)
        coEvery { authRepository.getServers(false) } returns Result.success(testServers)

        viewModel = AuthViewModel(authRepository, savedStateHandle)
        advanceUntilIdle()

        // When
        viewModel.onEvent(AuthEvent.SubmitToken(testToken))
        advanceUntilIdle()

        // Then
        coVerify { authRepository.loginWithToken(testToken) }
        coVerify { authRepository.getServers(false) }
        val state = viewModel.uiState.value
        assertThat(state.servers).hasSize(1)
        assertThat(state.servers[0].name).isEqualTo("Test Server")
    }

    @Test
    fun `SubmitToken event with invalid token shows error`() = runTest {
        // Given
        val invalidToken = "invalid-token"
        coEvery { authRepository.checkAuthentication() } returns false
        coEvery { authRepository.loginWithToken(invalidToken) } returns
            Result.failure(Exception("Invalid token"))

        viewModel = AuthViewModel(authRepository, savedStateHandle)
        advanceUntilIdle()

        // When
        viewModel.onEvent(AuthEvent.SubmitToken(invalidToken))
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertThat(state.error).isNotNull()
        assertThat(state.error).contains("Invalid token")
        assertThat(state.servers).isEmpty()
    }

    @Test
    fun `Cancel event clears pin polling`() = runTest {
        // Given
        val testPin = AuthPin(id = "12345", code = "ABCD")
        coEvery { authRepository.checkAuthentication() } returns false
        coEvery { authRepository.getPin(true) } returns Result.success(testPin)

        viewModel = AuthViewModel(authRepository, savedStateHandle)
        advanceUntilIdle()

        viewModel.onEvent(AuthEvent.GetPin)
        advanceUntilIdle()

        // When
        viewModel.onEvent(AuthEvent.Cancel)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        // Pin polling should be cancelled (implementation specific)
        assertThat(state.error).isNull()
    }

    @Test
    fun `observeAuthState emits changes correctly`() = runTest {
        // Given
        coEvery { authRepository.checkAuthentication() } returns false
        coEvery { authRepository.observeAuthState() } returns flowOf(false, true)

        // When
        viewModel = AuthViewModel(authRepository, savedStateHandle)
        advanceUntilIdle()

        // Then
        coVerify { authRepository.observeAuthState() }
    }
}
