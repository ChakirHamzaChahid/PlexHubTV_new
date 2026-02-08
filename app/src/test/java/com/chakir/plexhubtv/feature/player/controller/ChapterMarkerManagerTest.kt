package com.chakir.plexhubtv.feature.player.controller

import com.chakir.plexhubtv.core.model.Chapter
import com.chakir.plexhubtv.core.model.Marker
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class ChapterMarkerManagerTest {
    private lateinit var manager: ChapterMarkerManager

    @Before
    fun setup() {
        manager = ChapterMarkerManager()
    }

    @Test
    fun `setChapters - updates chapters state`() {
        val chapters =
            listOf(
                Chapter(title = "Chap 1", startTime = 0, endTime = 10000),
                Chapter(title = "Chap 2", startTime = 10000, endTime = 20000),
            )
        manager.setChapters(chapters)

        assertThat(manager.chapters.value).isEqualTo(chapters)
        assertThat(manager.hasChapters()).isTrue()
    }

    @Test
    fun `setMarkers - updates markers and special refs`() {
        val markers =
            listOf(
                Marker(type = "intro", startTime = 5000, endTime = 15000),
                Marker(type = "credits", startTime = 100000, endTime = 110000),
            )
        manager.setMarkers(markers)

        assertThat(manager.markers.value).isEqualTo(markers)
        assertThat(manager.introMarker.value?.type).isEqualTo("intro")
        assertThat(manager.creditsMarker.value?.type).isEqualTo("credits")
        assertThat(manager.hasIntro()).isTrue()
        assertThat(manager.hasCredits()).isTrue()
    }

    @Test
    fun `updatePlaybackPosition - updates current chapter and visible markers`() {
        val chapters =
            listOf(
                Chapter(title = "Chap 1", startTime = 0, endTime = 10000),
                Chapter(title = "Chap 2", startTime = 10000, endTime = 20000),
            )
        val markers =
            listOf(
                Marker(type = "intro", startTime = 5000, endTime = 15000),
            )
        manager.setChapters(chapters)
        manager.setMarkers(markers)

        // At 2000ms: Chap 1 is active, no marker
        manager.updatePlaybackPosition(2000)
        assertThat(manager.currentChapter.value?.title).isEqualTo("Chap 1")
        assertThat(manager.visibleMarkers.value).isEmpty()

        // At 7000ms: Chap 1 is active, Marker m1 is visible
        manager.updatePlaybackPosition(7000)
        assertThat(manager.currentChapter.value?.title).isEqualTo("Chap 1")
        assertThat(manager.visibleMarkers.value.first().type).isEqualTo("intro")

        // At 12000ms: Chap 2 is active, Marker m1 is still visible
        manager.updatePlaybackPosition(12000)
        assertThat(manager.currentChapter.value?.title).isEqualTo("Chap 2")
        assertThat(manager.visibleMarkers.value.first().type).isEqualTo("intro")

        // At 17000ms: Chap 2 is active (if we had longer end time, but Chap 2 ends at 20000), Marker m1 is gone
        manager.updatePlaybackPosition(17000)
        assertThat(manager.currentChapter.value?.title).isEqualTo("Chap 2")
        assertThat(manager.visibleMarkers.value).isEmpty()
    }

    @Test
    fun `clear - resets all states`() {
        manager.setChapters(listOf(Chapter("C1", 0, 1000)))
        manager.setMarkers(listOf(Marker(type = "intro", startTime = 0, endTime = 1000)))
        manager.updatePlaybackPosition(500)

        manager.clear()

        assertThat(manager.chapters.value).isEmpty()
        assertThat(manager.markers.value).isEmpty()
        assertThat(manager.currentChapter.value).isNull()
        assertThat(manager.visibleMarkers.value).isEmpty()
        assertThat(manager.introMarker.value).isNull()
    }
}
