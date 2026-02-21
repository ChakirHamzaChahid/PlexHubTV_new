package com.chakir.plexhubtv.feature.player.controller

import com.chakir.plexhubtv.core.model.AudioStream
import com.chakir.plexhubtv.core.model.MediaPart
import com.chakir.plexhubtv.core.model.SubtitleStream
import com.chakir.plexhubtv.domain.model.TrackPreference
import com.chakir.plexhubtv.domain.repository.PlaybackRepository
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import com.chakir.plexhubtv.domain.repository.TrackPreferenceRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlayerTrackControllerTest {
    private val settingsRepository: SettingsRepository = mockk()
    private val trackPreferenceRepository: TrackPreferenceRepository = mockk()
    private val playbackRepository: PlaybackRepository = mockk()

    private val controller =
        PlayerTrackController(
            settingsRepository,
            trackPreferenceRepository,
            playbackRepository,
        )

    private val audio1 =
        AudioStream(id = "a1", codec = "ac3", index = 0, language = "en", languageCode = "eng", selected = true, title = "AC3", displayTitle = "English AC3", channels = 6)
    private val audio2 =
        AudioStream(id = "a2", codec = "aac", index = 1, language = "fr", languageCode = "fre", selected = false, title = "AAC", displayTitle = "French AAC", channels = 2)

    private val sub1 =
        SubtitleStream(id = "s1", codec = "srt", index = 2, language = "en", languageCode = "eng", selected = false, title = "SDH", displayTitle = "English SDH", forced = false, key = null)
    private val sub2 =
        SubtitleStream(id = "s2", codec = "srt", index = 3, language = "fr", languageCode = "fre", selected = true, title = "Forced", displayTitle = "French Forced", forced = true, key = null)

    private val mediaPart =
        MediaPart(
            id = "part1",
            key = "key",
            duration = 1000,
            file = "file",
            size = 1000,
            container = "mkv",
            streams = listOf(audio1, audio2, sub1, sub2),
        )

    @Test
    fun `resolveInitialTracks - returns arguments if provided`() =
        runTest {
            val result =
                controller.resolveInitialTracks(
                    ratingKey = "1",
                    serverId = "s1",
                    part = mediaPart,
                    argAudioStreamId = "argAudio",
                    argSubtitleStreamId = "argSub",
                )

            assertThat(result.first).isEqualTo("argAudio")
            assertThat(result.second).isEqualTo("argSub")
        }

    @Test
    fun `resolveInitialTracks - falls back to DB if arguments are null`() =
        runTest {
            // Mock DB
            coEvery { trackPreferenceRepository.getPreference("1", "s1") } returns
                TrackPreference(
                    ratingKey = "1",
                    serverId = "s1",
                    audioStreamId = "dbAudio",
                    subtitleStreamId = "dbSub",
                )

            val result =
                controller.resolveInitialTracks(
                    ratingKey = "1",
                    serverId = "s1",
                    part = mediaPart,
                    argAudioStreamId = null,
                    argSubtitleStreamId = null,
                )

            assertThat(result.first).isEqualTo("dbAudio")
            assertThat(result.second).isEqualTo("dbSub")
        }

    @Test
    fun `resolveInitialTracks - falls back to Settings if DB is empty`() =
        runTest {
            // Mock DB empty
            coEvery { trackPreferenceRepository.getPreference(any(), any()) } returns null

            // Mock Settings
            every { settingsRepository.preferredAudioLanguage } returns flowOf("fr")
            every { settingsRepository.preferredSubtitleLanguage } returns flowOf("en")

            val result =
                controller.resolveInitialTracks(
                    ratingKey = "1",
                    serverId = "s1",
                    part = mediaPart,
                    argAudioStreamId = null,
                    argSubtitleStreamId = null,
                )

            // French audio is a2, English sub is s1
            assertThat(result.first).isEqualTo("a2") // Selected because language matches "fr"
            assertThat(result.second).isEqualTo("s1") // Selected because language matches "en"
        }

    @Test
    fun `resolveInitialTracks - falls back to Plex defaults (selected flag) if no pref`() =
        runTest {
            // Mock DB empty
            coEvery { trackPreferenceRepository.getPreference(any(), any()) } returns null

            // Mock Settings empty
            every { settingsRepository.preferredAudioLanguage } returns flowOf(null)
            every { settingsRepository.preferredSubtitleLanguage } returns flowOf(null)

            val result =
                controller.resolveInitialTracks(
                    ratingKey = "1",
                    serverId = "s1",
                    part = mediaPart,
                    argAudioStreamId = null,
                    argSubtitleStreamId = null,
                )

            // Plex default selection (audio1=true, sub2=true)
            assertThat(result.first).isEqualTo("a1")
            assertThat(result.second).isEqualTo("s2")
        }
}
