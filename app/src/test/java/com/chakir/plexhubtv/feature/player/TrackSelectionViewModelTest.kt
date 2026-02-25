package com.chakir.plexhubtv.feature.player

import com.chakir.plexhubtv.core.model.AudioTrack
import com.chakir.plexhubtv.core.model.SubtitleTrack
import com.chakir.plexhubtv.feature.player.controller.PlayerController
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackSelectionViewModelTest {
    private lateinit var viewModel: TrackSelectionViewModel
    private lateinit var playerController: PlayerController

    private val testDispatcher = StandardTestDispatcher()
    private val testUiState = MutableStateFlow(PlayerUiState())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        playerController = mockk(relaxed = true) {
            every { uiState } returns testUiState
        }

        viewModel = TrackSelectionViewModel(playerController)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `onAction SelectAudioTrack - hides audio selection and selects audio track`() {
        val track = mockk<AudioTrack>()
        testUiState.value = PlayerUiState(showAudioSelection = true)

        viewModel.onAction(PlayerAction.SelectAudioTrack(track))

        verify {
            playerController.updateState(match {
                it(testUiState.value).showAudioSelection == false
            })
            playerController.selectAudioTrack(track)
        }
    }

    @Test
    fun `onAction SelectSubtitleTrack - hides subtitle selection and selects subtitle track`() {
        val track = mockk<SubtitleTrack>()
        testUiState.value = PlayerUiState(showSubtitleSelection = true)

        viewModel.onAction(PlayerAction.SelectSubtitleTrack(track))

        verify {
            playerController.updateState(match {
                it(testUiState.value).showSubtitleSelection == false
            })
            playerController.selectSubtitleTrack(track)
        }
    }

    @Test
    fun `onAction ShowAudioSelector - shows audio selection and hides others`() {
        testUiState.value = PlayerUiState(
            showAudioSelection = false,
            showSubtitleSelection = true,
            showSettings = true
        )

        viewModel.onAction(PlayerAction.ShowAudioSelector)

        verify {
            playerController.updateState(match {
                val state = it(testUiState.value)
                state.showAudioSelection && !state.showSubtitleSelection && !state.showSettings
            })
        }
    }

    @Test
    fun `onAction ShowSubtitleSelector - shows subtitle selection and hides others`() {
        testUiState.value = PlayerUiState(
            showAudioSelection = true,
            showSubtitleSelection = false,
            showSettings = true
        )

        viewModel.onAction(PlayerAction.ShowSubtitleSelector)

        verify {
            playerController.updateState(match {
                val state = it(testUiState.value)
                state.showSubtitleSelection && !state.showAudioSelection && !state.showSettings
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

        // Verify no interactions with playerController (except for the uiState getter during initialization)
        verify(exactly = 0) { playerController.play() }
    }
}
