package com.chakir.plexhubtv.data.repository.aggregation

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.di.network.ConnectionManager
import com.chakir.plexhubtv.core.util.MediaUrlResolver
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MediaDeduplicatorTest {
    private val connectionManager = mockk<ConnectionManager>()
    private val mediaUrlResolver = mockk<MediaUrlResolver>()
    private lateinit var deduplicator: DefaultMediaDeduplicator

    @Before
    fun setup() {
        deduplicator = DefaultMediaDeduplicator(connectionManager, mediaUrlResolver)

        // Default mocks
        every { connectionManager.getCachedUrl(any()) } returns null
        every { mediaUrlResolver.resolveImageUrl(any(), any(), any(), any(), any()) } returns null
    }

    @Test
    fun `deduplicate - groups by imdbId`() =
        runTest {
            val items =
                listOf(
                    createMediaItem(id = "s1_m1", serverId = "s1", title = "Movie", imdbId = "tt123", ratingKey = "1"),
                    createMediaItem(id = "s2_m2", serverId = "s2", title = "Movie duplicated", imdbId = "tt123", ratingKey = "2"),
                )

            val result = deduplicator.deduplicate(items, setOf("s1"), emptyList())

            assertThat(result).hasSize(1)
            assertThat(result[0].remoteSources).hasSize(2)
            assertThat(result[0].serverId).isEqualTo("s1") // Prioritized owned server
        }

    @Test
    fun `deduplicate - groups by tmdbId`() =
        runTest {
            val items =
                listOf(
                    createMediaItem(id = "s1_m1", serverId = "s1", title = "Movie", tmdbId = "456", ratingKey = "1"),
                    createMediaItem(id = "s2_m2", serverId = "s2", title = "Movie Other", tmdbId = "456", ratingKey = "2"),
                )

            val result = deduplicator.deduplicate(items, emptySet(), emptyList())

            assertThat(result).hasSize(1)
            assertThat(result[0].remoteSources).hasSize(2)
        }

    @Test
    fun `deduplicate - groups by title and year normalized`() =
        runTest {
            val items =
                listOf(
                    createMediaItem(id = "s1_m1", serverId = "s1", title = "Interstellar!!!", year = 2014, ratingKey = "1"),
                    createMediaItem(id = "s2_m2", serverId = "s2", title = "interstellar   ", year = 2014, ratingKey = "2"),
                )

            val result = deduplicator.deduplicate(items, emptySet(), emptyList())

            assertThat(result).hasSize(1)
            assertThat(result[0].remoteSources).hasSize(2)
        }

    @Test
    fun `deduplicate - prioritizes owned server`() =
        runTest {
            val items =
                listOf(
                    createMediaItem(id = "s2_m2", serverId = "s2", title = "Movie", imdbId = "tt1", updatedAt = 100, ratingKey = "2"),
                    createMediaItem(id = "s1_m1", serverId = "s1", title = "Movie", imdbId = "tt1", updatedAt = 50, ratingKey = "1"),
                )

            // s1 is owned, so it should be prioritized even if s2 is newer
            val result = deduplicator.deduplicate(items, setOf("s1"), emptyList())

            assertThat(result[0].serverId).isEqualTo("s1")
        }

    @Test
    fun `deduplicate - aggregates ratings`() =
        runTest {
            val items =
                listOf(
                    createMediaItem(id = "s1_m1", title = "M", imdbId = "tt1", rating = 8.0, audienceRating = 9.0, ratingKey = "1"),
                    createMediaItem(id = "s2_m2", title = "M", imdbId = "tt1", rating = 7.0, audienceRating = 8.0, ratingKey = "2"),
                )

            val result = deduplicator.deduplicate(items, emptySet(), emptyList())

            assertThat(result[0].rating).isEqualTo(7.5)
            assertThat(result[0].audienceRating).isEqualTo(8.5)
        }

    private fun createMediaItem(
        id: String,
        serverId: String = "s1",
        title: String,
        imdbId: String? = null,
        tmdbId: String? = null,
        year: Int? = null,
        updatedAt: Long? = null,
        rating: Double? = null,
        audienceRating: Double? = null,
        ratingKey: String = "1",
    ) = MediaItem(
        id = id,
        ratingKey = ratingKey,
        serverId = serverId,
        title = title,
        type = MediaType.Movie,
        imdbId = imdbId,
        tmdbId = tmdbId,
        year = year,
        updatedAt = updatedAt,
        rating = rating,
        audienceRating = audienceRating,
        mediaParts = emptyList(),
        genres = emptyList(),
    )
}
