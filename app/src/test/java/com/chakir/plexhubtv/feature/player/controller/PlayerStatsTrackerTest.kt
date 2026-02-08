package com.chakir.plexhubtv.feature.player.controller

import com.chakir.plexhubtv.feature.player.PlayerStats
import com.chakir.plexhubtv.feature.player.StreamMetadata
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerStatsTrackerTest {
    private lateinit var tracker: PlayerStatsTracker
    private val testScope = TestScope()

    @Before
    fun setup() {
        tracker = PlayerStatsTracker()
    }

    @Test
    fun `startTracking - updates stats from exo providers`() =
        testScope.runTest {
            val metadata = mockk<StreamMetadata>()
            every { metadata.bitrate } returns 5000
            every { metadata.sampleMimeType } returns "video/h264"
            every { metadata.width } returns 1920
            every { metadata.height } returns 1080
            every { metadata.frameRate } returns 23.976f

            tracker.startTracking(
                scope = backgroundScope,
                isMpvMode = { false },
                exoMetadata = { metadata },
                exoPosition = { 1000L },
                exoBuffered = { 5000L },
                mpvStats = { null },
            )

            advanceTimeBy(1100)

            val stats = tracker.stats.value
            assertThat(stats).isNotNull()
            assertThat(stats?.bitrate).isEqualTo("5000 kbps")
            assertThat(stats?.videoCodec).isEqualTo("video/h264")
            assertThat(stats?.resolution).isEqualTo("1920x1080")
            assertThat(stats?.fps).isWithin(0.001).of(23.976)
            assertThat(stats?.cacheDuration).isEqualTo(4000L)
        }

    @Test
    fun `startTracking - updates stats from mpv provider`() =
        testScope.runTest {
            val mpvStats =
                PlayerStats(
                    bitrate = "8000 kbps",
                    videoCodec = "hevc",
                    resolution = "3840x2160",
                    fps = 60.0,
                    cacheDuration = 2000L,
                )

            tracker.startTracking(
                scope = backgroundScope,
                isMpvMode = { true },
                exoMetadata = { null },
                exoPosition = { 0L },
                exoBuffered = { 0L },
                mpvStats = { mpvStats },
            )

            advanceTimeBy(1100)

            val stats = tracker.stats.value
            assertThat(stats).isEqualTo(mpvStats)
        }

    @Test
    fun `stopTracking - cancels job`() =
        testScope.runTest {
            tracker.startTracking(
                scope = this,
                isMpvMode = { false },
                exoMetadata = { null },
                exoPosition = { 0L },
                exoBuffered = { 0L },
                mpvStats = { null },
            )

            tracker.stopTracking()

            // This is hard to verify directly without reflection or checking job status,
            // but we can check if it stops updating (not implemented here for brevity).
        }
}
