package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.AudioTrack
import com.chakir.plexhubtv.core.model.SubtitleTrack
import com.chakir.plexhubtv.core.model.UserProfile
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackSelectionUseCaseTest {
    private val useCase = TrackSelectionUseCase()

    // --- Audio Selection Tests ---

    @Test
    fun `selectAudioTrack returns null when no tracks available`() {
        val result = useCase.selectAudioTrack(emptyList())
        assertThat(result).isNull()
    }

    @Test
    fun `selectAudioTrack prioritizes Navigation preference`() {
        val track1 = createAudioTrack("1", "eng", false)
        val track2 = createAudioTrack("2", "fra", false)

        val result =
            useCase.selectAudioTrack(
                availableTracks = listOf(track1, track2),
                preferredTrack = track2,
            )

        assertThat(result).isNotNull()
        assertThat(result!!.track).isEqualTo(track2)
        assertThat(result.priority).isEqualTo(TrackSelectionPriority.NAVIGATION)
    }

    @Test
    fun `selectAudioTrack prioritizes Plex Selected over Default`() {
        val track1 = createAudioTrack("1", "eng", true) // Default
        val track2 = createAudioTrack("2", "fra", false)

        val result =
            useCase.selectAudioTrack(
                availableTracks = listOf(track1, track2),
                plexSelectedIndex = 1, // Selects track2
            )

        assertThat(result).isNotNull()
        assertThat(result!!.track).isEqualTo(track2)
        assertThat(result.priority).isEqualTo(TrackSelectionPriority.PLEX_SELECTED)
    }

    @Test
    fun `selectAudioTrack falls back to Default if no preferences`() {
        val track1 = createAudioTrack("1", "eng", true) // Default
        val track2 = createAudioTrack("2", "fra", false)

        val result =
            useCase.selectAudioTrack(
                availableTracks = listOf(track1, track2),
            )

        assertThat(result).isNotNull()
        assertThat(result!!.track).isEqualTo(track1)
        assertThat(result.priority).isEqualTo(TrackSelectionPriority.DEFAULT_TRACK)
    }

    // --- Subtitle Selection Tests ---

    @Test
    fun `selectSubtitleTrack prioritizes valid Navigation preference`() {
        val sub1 = createSubtitleTrack("1", "eng", false)
        val sub2 = createSubtitleTrack("2", "fra", false)

        val result =
            useCase.selectSubtitleTrack(
                availableTracks = listOf(sub1, sub2),
                preferredTrack = sub2,
            )

        assertThat(result.track).isEqualTo(sub2)
        assertThat(result.priority).isEqualTo(TrackSelectionPriority.NAVIGATION)
    }

    @Test
    fun `selectSubtitleTrack picks forced subtitle when profile enabled`() {
        // Audio is English
        val audio = createAudioTrack("a1", "eng", true)

        // Subtitle 1: English Forced (Should be selected if audio is English)
        val sub1 = createSubtitleTrack("s1", "eng", false, isForced = true)
        // Subtitle 2: French (Standard)
        val sub2 = createSubtitleTrack("s2", "fra", false)

        // Profile configured to ALWAYS select subtitles (Mode 2) and prefer forced (1)
        val profile =
            UserProfile(
                id = "test",
                title = "Test User",
                autoSelectSubtitle = 2,
                defaultSubtitleForced = 1,
                defaultSubtitleLanguages = listOf("eng"), // Prefer English
            )

        val result =
            useCase.selectSubtitleTrack(
                availableTracks = listOf(sub1, sub2),
                selectedAudioTrack = audio,
                userProfile = profile,
            )

        assertThat(result.track).isEqualTo(sub1)
        assertThat(result.priority).isEqualTo(TrackSelectionPriority.PROFILE)
    }

    // Define helper methods to create dummy objects
    private fun createAudioTrack(
        id: String,
        lang: String,
        isDefault: Boolean,
    ): AudioTrack {
        return AudioTrack(
            id = id,
            title = "Track $id",
            language = lang,
            codec = "aac",
            channels = 2,
            index = 0,
            isDefault = isDefault,
            isForced = false,
            isSelected = false,
            streamId = id,
        )
    }

    private fun createSubtitleTrack(
        id: String,
        lang: String,
        isDefault: Boolean,
        isForced: Boolean = false,
    ): SubtitleTrack {
        return SubtitleTrack(
            id = id,
            title = "Subtitle $id",
            language = lang,
            codec = "srt",
            index = 0,
            isDefault = isDefault,
            isForced = isForced,
            isExternal = false,
            isSelected = false,
            streamId = id,
        )
    }
}
