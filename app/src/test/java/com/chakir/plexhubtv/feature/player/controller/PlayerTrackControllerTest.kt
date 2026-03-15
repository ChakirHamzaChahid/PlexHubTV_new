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
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale

class PlayerTrackControllerTest {
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    private val settingsRepository: SettingsRepository = mockk()
    private val trackPreferenceRepository: TrackPreferenceRepository = mockk()
    private val playbackRepository: PlaybackRepository = mockk()

    private val controller =
        PlayerTrackController(
            settingsRepository,
            trackPreferenceRepository,
            playbackRepository,
        )

    // Realistic Plex data: language = full name, languageCode = ISO 639-2
    private val audio1 =
        AudioStream(id = "a1", codec = "ac3", index = 0, language = "English", languageCode = "eng", selected = true, title = "AC3", displayTitle = "English (AC3 5.1)", channels = 6)
    private val audio2 =
        AudioStream(id = "a2", codec = "aac", index = 1, language = "French", languageCode = "fre", selected = false, title = "AAC", displayTitle = "French (AAC Stereo)", channels = 2)

    private val sub1 =
        SubtitleStream(id = "s1", codec = "srt", index = 2, language = "English", languageCode = "eng", selected = false, title = "SDH", displayTitle = "English (SRT)", forced = false, key = null)
    private val sub2 =
        SubtitleStream(id = "s2", codec = "srt", index = 3, language = "French", languageCode = "fre", selected = true, title = "Forced", displayTitle = "French (SRT)", forced = true, key = null)
    private val sub3 =
        SubtitleStream(id = "s3", codec = "srt", index = 4, language = "French", languageCode = "fra", selected = false, title = "Full", displayTitle = "French Full (SRT)", forced = false, key = null)

    private val mediaPart =
        MediaPart(
            id = "part1",
            key = "key",
            duration = 1000,
            file = "file",
            size = 1000,
            container = "mkv",
            streams = listOf(audio1, audio2, sub1, sub2, sub3),
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
    fun `resolveInitialTracks - matches ISO code settings against full language names`() =
        runTest {
            coEvery { trackPreferenceRepository.getPreference(any(), any()) } returns null

            // Settings use 3-letter ISO codes (as set by PlaybackSettingsScreen)
            every { settingsRepository.preferredAudioLanguage } returns flowOf("fra")
            every { settingsRepository.preferredSubtitleLanguage } returns flowOf("eng")

            val result =
                controller.resolveInitialTracks(
                    ratingKey = "1",
                    serverId = "s1",
                    part = mediaPart,
                    argAudioStreamId = null,
                    argSubtitleStreamId = null,
                )

            // French audio matched via languageCode "fre" ↔ "fra" (B/T bridging)
            assertThat(result.first).isEqualTo("a2")
            // English sub matched via languageCode "eng" = "eng"
            assertThat(result.second).isEqualTo("s1")
        }

    @Test
    fun `resolveInitialTracks - prefers non-forced subtitles`() =
        runTest {
            coEvery { trackPreferenceRepository.getPreference(any(), any()) } returns null

            every { settingsRepository.preferredAudioLanguage } returns flowOf("eng")
            every { settingsRepository.preferredSubtitleLanguage } returns flowOf("fra")

            val result =
                controller.resolveInitialTracks(
                    ratingKey = "1",
                    serverId = "s1",
                    part = mediaPart,
                    argAudioStreamId = null,
                    argSubtitleStreamId = null,
                )

            assertThat(result.first).isEqualTo("a1") // English audio
            // sub3 (non-forced French, code="fra") preferred over sub2 (forced French, code="fre")
            assertThat(result.second).isEqualTo("s3")
        }

    @Test
    fun `resolveInitialTracks - uses Plex selected and smart defaults when no prefs`() =
        runTest {
            coEvery { trackPreferenceRepository.getPreference(any(), any()) } returns null

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

            // audio1 is Plex-selected
            assertThat(result.first).isEqualTo("a1")
            // Subtitle depends on device locale vs audio language:
            // JVM default locale is "en", audio1 is "English" → audioMatchesDevice = true → no subs
            assertThat(result.second).isNull()
        }

    @Test
    fun `resolveInitialTracks - handles ISO 639-2 B-T variant fre vs fra`() =
        runTest {
            coEvery { trackPreferenceRepository.getPreference(any(), any()) } returns null

            every { settingsRepository.preferredAudioLanguage } returns flowOf("eng")
            // Setting uses T-form "fra", but stream has B-form "fre"
            every { settingsRepository.preferredSubtitleLanguage } returns flowOf("fra")

            // Part with only B-form coded French sub (no T-form)
            val partBForm = MediaPart(
                id = "part1", key = "key", duration = 1000, file = "file", size = 1000, container = "mkv",
                streams = listOf(audio1, sub2), // sub2 has languageCode="fre"
            )

            val result =
                controller.resolveInitialTracks(
                    ratingKey = "1",
                    serverId = "s1",
                    part = partBForm,
                    argAudioStreamId = null,
                    argSubtitleStreamId = null,
                )

            // "fre" should match "fra" via B/T mapping
            assertThat(result.second).isEqualTo("s2")
        }

    // --- areLanguagesEqual unit tests ---

    @Test
    fun `areLanguagesEqual - same codes match`() {
        assertThat(controller.areLanguagesEqual("eng", "eng")).isTrue()
        assertThat(controller.areLanguagesEqual("fra", "fra")).isTrue()
        assertThat(controller.areLanguagesEqual("en", "en")).isTrue()
    }

    @Test
    fun `areLanguagesEqual - B-T variants match`() {
        assertThat(controller.areLanguagesEqual("fre", "fra")).isTrue()
        assertThat(controller.areLanguagesEqual("fra", "fre")).isTrue()
        assertThat(controller.areLanguagesEqual("ger", "deu")).isTrue()
        assertThat(controller.areLanguagesEqual("deu", "ger")).isTrue()
        assertThat(controller.areLanguagesEqual("chi", "zho")).isTrue()
        assertThat(controller.areLanguagesEqual("dut", "nld")).isTrue()
    }

    @Test
    fun `areLanguagesEqual - 2-letter and 3-letter match via Locale`() {
        assertThat(controller.areLanguagesEqual("en", "eng")).isTrue()
        assertThat(controller.areLanguagesEqual("fr", "fra")).isTrue()
        assertThat(controller.areLanguagesEqual("de", "deu")).isTrue()
    }

    @Test
    fun `areLanguagesEqual - null and und handling`() {
        assertThat(controller.areLanguagesEqual(null, null)).isTrue()
        assertThat(controller.areLanguagesEqual("und", null)).isTrue()
        assertThat(controller.areLanguagesEqual(null, "und")).isTrue()
        assertThat(controller.areLanguagesEqual("und", "und")).isTrue()
        assertThat(controller.areLanguagesEqual("eng", null)).isFalse()
        assertThat(controller.areLanguagesEqual(null, "eng")).isFalse()
    }

    @Test
    fun `areLanguagesEqual - different languages do not match`() {
        assertThat(controller.areLanguagesEqual("eng", "fra")).isFalse()
        assertThat(controller.areLanguagesEqual("en", "fr")).isFalse()
        assertThat(controller.areLanguagesEqual("eng", "deu")).isFalse()
    }
}
