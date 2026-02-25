package com.chakir.plexhubtv.feature.player

import com.chakir.plexhubtv.feature.player.controller.PlayerController
import com.chakir.plexhubtv.feature.player.controller.PlayerStatsTracker
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackStatsViewModelTest {
    private lateinit var viewModel: PlaybackStatsViewModel
    private lateinit var playerController: PlayerController
    private lateinit var playerStatsTracker: PlayerStatsTracker

    private val testDispatcher = StandardTestDispatcher()
    private val testUiState = MutableStateFlow(PlayerUiState())
    private val testStatsFlow = MutableStateFlow<PlayerStats?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        playerController = mockk(relaxed = true) {
            every { uiState } returns testUiState
        }

        playerStatsTracker = mockk(relaxed = true) {
            every { stats } returns testStatsFlow
        }

        viewModel = PlaybackStatsViewModel(
            playerController = playerController,
            playerStatsTracker = playerStatsTracker
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `onAction TogglePerformanceOverlay - enables overlay and starts collecting stats`() = runTest {
        testUiState.value = PlayerUiState(showPerformanceOverlay = false)

        viewModel.onAction(PlayerAction.TogglePerformanceOverlay)

        // Verify overlay is enabled
        verify {
            playerController.updateState(match {
                it(testUiState.value).showPerformanceOverlay == true
            })
        }

        // Advance coroutines to allow stats collection to start
        advanceUntilIdle()

        // Emit test stats
        val testStats = mockk<PlayerStats>()
        testStatsFlow.value = testStats

        advanceUntilIdle()

        // Verify stats are collected and updated
        verify {
            playerController.updateState(match {
                it(testUiState.value).playerStats == testStats
            })
        }
    }

    @Test
    fun `onAction TogglePerformanceOverlay - disables overlay and clears stats`() {
        testUiState.value = PlayerUiState(showPerformanceOverlay = true, playerStats = mockk())

        viewModel.onAction(PlayerAction.TogglePerformanceOverlay)

        verify {
            playerController.updateState(match {
                val state = it(testUiState.value)
                !state.showPerformanceOverlay && state.playerStats == null
            })
        }
    }

    @Test
    fun `onAction TogglePerformanceOverlay - toggles state correctly from false to true`() {
        testUiState.value = PlayerUiState(showPerformanceOverlay = false)

        viewModel.onAction(PlayerAction.TogglePerformanceOverlay)

        verify {
            playerController.updateState(match {
                it(testUiState.value).showPerformanceOverlay == true
            })
        }
    }

    @Test
    fun `onAction TogglePerformanceOverlay - toggles state correctly from true to false`() {
        testUiState.value = PlayerUiState(showPerformanceOverlay = true)

        viewModel.onAction(PlayerAction.TogglePerformanceOverlay)

        verify {
            playerController.updateState(match {
                it(testUiState.value).showPerformanceOverlay == false
            })
        }
    }

    @Test
    fun `uiState - exposes playerController uiState`() {
        val state = viewModel.uiState

        assertThat(state).isEqualTo(testUiState)
    }

    @Test
    fun `onAction with unsupported action - does nothing`() {
        // Action not handled by this ViewModel
        viewModel.onAction(PlayerAction.Play)

        // Verify no interactions with playerController for unsupported actions
        verify(exactly = 0) { playerController.play() }
    }
}
