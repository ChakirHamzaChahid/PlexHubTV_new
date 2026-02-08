package com.chakir.plexhubtv.feature.player

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.media3.exoplayer.ExoPlayer
import com.chakir.plexhubtv.core.database.TrackPreferenceDao
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.util.WatchNextHelper
import com.chakir.plexhubtv.domain.repository.PlaybackRepository
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import com.chakir.plexhubtv.domain.service.PlaybackManager
import com.chakir.plexhubtv.domain.usecase.GetMediaDetailUseCase
import com.chakir.plexhubtv.domain.usecase.MediaDetail
import com.chakir.plexhubtv.feature.player.controller.ChapterMarkerManager
import com.chakir.plexhubtv.feature.player.controller.PlayerScrobbler
import com.chakir.plexhubtv.feature.player.controller.PlayerStatsTracker
import com.chakir.plexhubtv.feature.player.controller.PlayerTrackController
import com.chakir.plexhubtv.feature.player.mpv.MpvPlayer
import com.chakir.plexhubtv.feature.player.url.TranscodeUrlBuilder
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val application: Application = mockk(relaxed = true)
    private val getMediaDetailUseCase: GetMediaDetailUseCase = mockk()
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val playbackManager: PlaybackManager = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val trackPreferenceDao: TrackPreferenceDao = mockk(relaxed = true)
    private val watchNextHelper: WatchNextHelper = mockk(relaxed = true)
    private val chapterMarkerManager: ChapterMarkerManager = mockk(relaxed = true)
    private val playerTrackController: PlayerTrackController = mockk(relaxed = true)
    private val playerScrobbler: PlayerScrobbler = mockk(relaxed = true)
    private val playerStatsTracker: PlayerStatsTracker = mockk(relaxed = true)
    private val transcodeUrlBuilder: TranscodeUrlBuilder = mockk(relaxed = true)
    private val playerFactory: PlayerFactory = mockk()
    private val exoPlayer: ExoPlayer = mockk(relaxed = true)
    private val exoMediaItem: androidx.media3.common.MediaItem = mockk(relaxed = true)
    private val mpvPlayer: MpvPlayer = mockk(relaxed = true)

    private lateinit var viewModel: PlayerViewModel
    private lateinit var savedStateHandle: SavedStateHandle

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        io.mockk.mockkStatic(android.util.Log::class)
        io.mockk.mockkStatic(android.net.Uri::class)
        every { android.net.Uri.parse(any()) } returns mockk()
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0

        // Mock Settings
        every { settingsRepository.getVideoQuality() } returns flowOf("Original")
        every { settingsRepository.playerEngine } returns flowOf("ExoPlayer")
        every { settingsRepository.clientId } returns flowOf("test-id")

        // Mock ExoPlayer with listener support
        every { exoPlayer.addListener(any()) } returns Unit
        every { exoPlayer.removeListener(any()) } returns Unit
        every { exoPlayer.duration } returns 0L
        every { exoPlayer.currentPosition } returns 0L
        every { exoPlayer.bufferedPosition } returns 0L
        every { exoPlayer.currentTracks } returns mockk(relaxed = true)

        every { playerFactory.createExoPlayer(any()) } returns exoPlayer
        every { playerFactory.createMediaItem(any(), any(), any()) } returns exoMediaItem
        every { playerFactory.createMpvPlayer(any(), any()) } returns mpvPlayer

        // Mock PlayerTrackController interactions
        every { playerTrackController.populateTracks(any()) } returns Pair(emptyList(), emptyList())
        coEvery { playerTrackController.resolveInitialTracks(any(), any(), any(), any(), any()) } returns Pair(null, null)

        savedStateHandle =
            SavedStateHandle(
                mapOf(
                    "ratingKey" to "123",
                    "serverId" to "s1",
                ),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkAll()
    }

    @Test
    fun `init - loads media detail and starts scrobbling`() =
        runTest(testDispatcher) {
            val mediaItem =
                MediaItem(id = "1", ratingKey = "123", serverId = "s1", title = "Movie", type = MediaType.Movie, mediaParts = emptyList())
            val detail = MediaDetail(item = mediaItem)

            every { getMediaDetailUseCase("123", "s1") } returns flowOf(Result.success(detail))

            viewModel =
                PlayerViewModel(
                    application, getMediaDetailUseCase, playbackRepository, playbackManager,
                    settingsRepository, trackPreferenceDao, watchNextHelper, savedStateHandle,
                    chapterMarkerManager, playerTrackController, playerScrobbler, playerStatsTracker, transcodeUrlBuilder,
                    playerFactory,
                )

            // Advance coroutines to execute init block logic (loadMedia)
            testScheduler.advanceUntilIdle()

            // Verify scrobbler started
            verify(timeout = 1000) { playerScrobbler.start(any(), any(), any(), any(), any()) }

            // Verify media loaded
            assertThat(viewModel.uiState.value.currentItem).isEqualTo(mediaItem)
        }

    @Test
    fun `onAction Play - calls exoPlayer play`() =
        runTest(testDispatcher) {
            val mediaItem = mockk<MediaItem>(relaxed = true)
            every { mediaItem.ratingKey } returns "123"
            every { mediaItem.serverId } returns "s1"
            every { mediaItem.id } returns "1"
            every { mediaItem.mediaParts } returns emptyList()
            // Mock getMediaDetailUseCase for initial load
            every { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.success(MediaDetail(mediaItem)))

            viewModel =
                PlayerViewModel(
                    application, getMediaDetailUseCase, playbackRepository, playbackManager,
                    settingsRepository, trackPreferenceDao, watchNextHelper, savedStateHandle,
                    chapterMarkerManager, playerTrackController, playerScrobbler, playerStatsTracker, transcodeUrlBuilder,
                    playerFactory,
                )

            testScheduler.advanceUntilIdle()

            viewModel.onAction(PlayerAction.Play)
            verify { exoPlayer.play() }
        }
}
