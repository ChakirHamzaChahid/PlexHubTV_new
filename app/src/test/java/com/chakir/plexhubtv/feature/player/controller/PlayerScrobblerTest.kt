package com.chakir.plexhubtv.feature.player.controller

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.util.WatchNextHelper
import com.chakir.plexhubtv.domain.repository.PlaybackRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerScrobblerTest {
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val watchNextHelper: WatchNextHelper = mockk(relaxed = true)
    private lateinit var scrobbler: PlayerScrobbler

    @Before
    fun setup() {
        scrobbler = PlayerScrobbler(playbackRepository, watchNextHelper)
    }

    @Test
    fun `scrobbler - periodically updates progress during playback`() =
        runTest {
            val item =
                MediaItem(id = "1", ratingKey = "1", serverId = "s1", title = "Movie", type = MediaType.Movie, mediaParts = emptyList())
            var currentPosition = 5000L

            scrobbler.start(
                scope = this,
                currentItemProvider = { item },
                isPlayingProvider = { true },
                currentPositionProvider = { currentPosition },
                durationProvider = { 100000L },
            )

            // Advance 10s -> Should trigger first scrobble
            advanceTimeBy(10001)
            coVerify(exactly = 1) {
                playbackRepository.updatePlaybackProgress(item, any())
                watchNextHelper.updateWatchNext(item, any(), any())
            }

            currentPosition = 15000L
            // Advance another 10s -> Second scrobble
            advanceTimeBy(10001)
            coVerify(exactly = 2) { playbackRepository.updatePlaybackProgress(item, any()) }

            scrobbler.stop()
        }

    @Test
    fun `checkAutoNext - triggers popup at 90 percent progress`() {
        // Duration 100s, position 91s -> 91%
        scrobbler.checkAutoNext(
            position = 91000L,
            duration = 100000L,
            hasNextItem = true,
            isPopupAlreadyShown = false,
        )

        assertThat(scrobbler.showAutoNextPopup.value).isTrue()
    }

    @Test
    fun `checkAutoNext - does not trigger if no next item`() {
        scrobbler.checkAutoNext(
            position = 95000L,
            duration = 100000L,
            hasNextItem = false,
            isPopupAlreadyShown = false,
        )

        assertThat(scrobbler.showAutoNextPopup.value).isFalse()
    }

    @Test
    fun `checkAutoNext - does not trigger early`() {
        scrobbler.checkAutoNext(
            position = 50000L,
            duration = 100000L,
            hasNextItem = true,
            isPopupAlreadyShown = false,
        )

        assertThat(scrobbler.showAutoNextPopup.value).isFalse()
    }
}
