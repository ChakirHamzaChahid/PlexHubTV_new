package com.chakir.plexhubtv.feature.player

import androidx.lifecycle.SavedStateHandle
import com.chakir.plexhubtv.feature.player.controller.ChapterMarkerManager
import com.chakir.plexhubtv.feature.player.controller.PlayerController
import com.chakir.plexhubtv.domain.service.PlaybackManager
import com.chakir.plexhubtv.core.model.Chapter
import com.chakir.plexhubtv.core.model.Marker
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
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
class PlayerControlViewModelTest {
    private lateinit var viewModel: PlayerControlViewModel
    private lateinit var playerController: PlayerController
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var chapterMarkerManager: ChapterMarkerManager
    private lateinit var playbackManager: PlaybackManager

    private val testDispatcher = StandardTestDispatcher()
    private val testUiState = MutableStateFlow(PlayerUiState())
    private val testMediaFlow = MutableStateFlow<MediaItem?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock dependencies
        playerController = mockk(relaxed = true) {
            every { uiState } returns testUiState
        }
        chapterMarkerManager = ChapterMarkerManager()
        playbackManager = mockk(relaxed = true) {
            every { currentMedia } returns testMediaFlow
        }

        // Setup SavedStateHandle with test data
        savedStateHandle = SavedStateHandle(
            mapOf(
                "ratingKey" to "123",
                "serverId" to "server1",
                "startOffset" to 0L
            )
        )

        viewModel = PlayerControlViewModel(
            playerController = playerController,
            savedStateHandle = savedStateHandle,
            chapterMarkerManager = chapterMarkerManager,
            playbackManager = playbackManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `initialization - calls playerController initialize with savedState params`() {
        // Verify that controller was initialized with params from SavedStateHandle
        verify {
            playerController.initialize(
                startRatingKey = "123",
                startServerId = "server1",
                startDirectUrl = null,
                offset = 0L
            )
        }
    }

    @Test
    fun `onAction Play - calls playerController play`() {
        viewModel.onAction(PlayerAction.Play)

        verify { playerController.play() }
    }

    @Test
    fun `onAction Pause - calls playerController pause`() {
        viewModel.onAction(PlayerAction.Pause)

        verify { playerController.pause() }
    }

    @Test
    fun `onAction SeekTo - calls playerController seekTo with correct position`() {
        val targetPosition = 30000L

        viewModel.onAction(PlayerAction.SeekTo(targetPosition))

        verify { playerController.seekTo(targetPosition) }
    }

    @Test
    fun `onAction Next - calls playbackManager next and loads next media`() {
        val nextMedia = MediaItem(
            id = "2",
            ratingKey = "456",
            serverId = "server1",
            title = "Next Episode",
            type = MediaType.Episode
        )
        testMediaFlow.value = nextMedia

        viewModel.onAction(PlayerAction.Next)

        verify { playbackManager.next() }
        verify { playerController.loadMedia("456", "server1") }
    }

    @Test
    fun `onAction Previous - calls playbackManager previous and loads previous media`() {
        val prevMedia = MediaItem(
            id = "0",
            ratingKey = "000",
            serverId = "server1",
            title = "Previous Episode",
            type = MediaType.Episode
        )
        testMediaFlow.value = prevMedia

        viewModel.onAction(PlayerAction.Previous)

        verify { playbackManager.previous() }
        verify { playerController.loadMedia("000", "server1") }
    }

    @Test
    fun `onAction SkipMarker - seeks to marker end time`() {
        val marker = Marker(type = "intro", startTime = 5000, endTime = 15000)

        viewModel.onAction(PlayerAction.SkipMarker(marker))

        verify { playerController.seekTo(15000) }
    }

    @Test
    fun `onAction SetPlaybackSpeed - calls playerController setPlaybackSpeed`() {
        val speed = 1.5f

        viewModel.onAction(PlayerAction.SetPlaybackSpeed(speed))

        verify { playerController.setPlaybackSpeed(speed) }
    }

    @Test
    fun `onAction SetAudioDelay - calls playerController setAudioDelay`() {
        val delay = 250L

        viewModel.onAction(PlayerAction.SetAudioDelay(delay))

        verify { playerController.setAudioDelay(delay) }
    }

    @Test
    fun `onAction SetSubtitleDelay - calls playerController setSubtitleDelay`() {
        val delay = -150L

        viewModel.onAction(PlayerAction.SetSubtitleDelay(delay))

        verify { playerController.setSubtitleDelay(delay) }
    }

    @Test
    fun `onAction SelectQuality - hides settings and loads media with new quality`() {
        val testMedia = MediaItem(
            id = "1",
            ratingKey = "123",
            serverId = "server1",
            title = "Test Movie",
            type = MediaType.Movie
        )
        testUiState.value = PlayerUiState(currentItem = testMedia, showSettings = true)

        val quality = VideoQuality(name = "8 Mbps (1080p)", bitrate = 8000)

        viewModel.onAction(PlayerAction.SelectQuality(quality))

        verify {
            playerController.updateState(any())
            playerController.loadMedia("123", "server1", 8000)
        }
    }

    @Test
    fun `onAction ToggleSettings - toggles settings visibility`() {
        testUiState.value = PlayerUiState(showSettings = false)

        viewModel.onAction(PlayerAction.ToggleSettings)

        verify {
            playerController.updateState(match {
                it(testUiState.value).showSettings == true
            })
        }
    }

    @Test
    fun `onAction SeekToNextChapter - seeks to next chapter start time`() {
        val chapters = listOf(
            Chapter(title = "Chapter 1", startTime = 0, endTime = 10000),
            Chapter(title = "Chapter 2", startTime = 10000, endTime = 20000),
            Chapter(title = "Chapter 3", startTime = 20000, endTime = 30000)
        )
        chapterMarkerManager.setChapters(chapters)
        testUiState.value = PlayerUiState(currentPosition = 5000)

        viewModel.onAction(PlayerAction.SeekToNextChapter)

        verify { playerController.seekTo(10000) }
    }

    @Test
    fun `onAction SeekToPreviousChapter - seeks to current chapter start if past 3 seconds`() {
        val chapters = listOf(
            Chapter(title = "Chapter 1", startTime = 0, endTime = 10000),
            Chapter(title = "Chapter 2", startTime = 10000, endTime = 20000)
        )
        chapterMarkerManager.setChapters(chapters)
        testUiState.value = PlayerUiState(currentPosition = 15000) // 5 seconds into Chapter 2

        viewModel.onAction(PlayerAction.SeekToPreviousChapter)

        verify { playerController.seekTo(10000) } // Seek to Chapter 2 start
    }

    @Test
    fun `onAction DismissDialog - closes all dialogs`() {
        testUiState.value = PlayerUiState(
            showSettings = true,
            showAudioSelection = true,
            showSubtitleSelection = true
        )

        viewModel.onAction(PlayerAction.DismissDialog)

        verify {
            playerController.updateState(match {
                val state = it(testUiState.value)
                !state.showSettings && !state.showAudioSelection && !state.showSubtitleSelection
            })
        }
    }

    @Test
    fun `uiState - exposes playerController uiState`() {
        val state = viewModel.uiState

        assertThat(state).isEqualTo(testUiState)
    }
}
