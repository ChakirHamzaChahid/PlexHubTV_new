package com.chakir.plexhubtv

import com.chakir.plexhubtv.core.common.auth.AuthEventBus
import com.chakir.plexhubtv.domain.repository.AuthRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `handleTokenInvalid clears token and shows dialog`() = runTest(testDispatcher) {
        val mockAuthRepository = mockk<AuthRepository>(relaxed = true)
        val eventBus = AuthEventBus()
        val viewModel = MainViewModel(eventBus, mockAuthRepository)

        // Let the ViewModel's init collector coroutine start and suspend on collect
        advanceUntilIdle()

        eventBus.emitTokenInvalid()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockAuthRepository.clearToken() }
        assertTrue(viewModel.showSessionExpiredDialog.value)
    }

    @Test
    fun `multiple TokenInvalid events only clear token once`() = runTest(testDispatcher) {
        val mockAuthRepository = mockk<AuthRepository>(relaxed = true)
        val eventBus = AuthEventBus()
        val viewModel = MainViewModel(eventBus, mockAuthRepository)

        // Let the ViewModel's init collector coroutine start and suspend on collect
        advanceUntilIdle()

        eventBus.emitTokenInvalid()
        advanceUntilIdle()

        // Dialog is now showing, subsequent emissions should be ignored
        eventBus.emitTokenInvalid()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockAuthRepository.clearToken() }
        assertTrue(viewModel.showSessionExpiredDialog.value)
    }

    @Test
    fun `onSessionExpiredDialogDismissed hides dialog and triggers navigation`() = runTest(testDispatcher) {
        val mockAuthRepository = mockk<AuthRepository>(relaxed = true)
        val eventBus = AuthEventBus()
        val viewModel = MainViewModel(eventBus, mockAuthRepository)

        // Let the ViewModel's init collector coroutine start and suspend on collect
        advanceUntilIdle()

        // Show dialog first
        eventBus.emitTokenInvalid()
        advanceUntilIdle()
        assertTrue(viewModel.showSessionExpiredDialog.value)

        // Dismiss
        var navigated = false
        viewModel.onSessionExpiredDialogDismissed { navigated = true }

        assertFalse(viewModel.showSessionExpiredDialog.value)
        assertTrue(navigated)
    }
}
