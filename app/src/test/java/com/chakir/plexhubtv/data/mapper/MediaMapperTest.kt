package com.chakir.plexhubtv.data.mapper

import com.chakir.plexhubtv.core.database.MediaEntity
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.di.network.model.GuidDTO
import com.chakir.plexhubtv.di.network.model.MetadataDTO
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class MediaMapperTest {
    private lateinit var mapper: MediaMapper

    @Before
    fun setup() {
        mapper = MediaMapper()
    }

    @Test
    fun `mapDtoToDomain - extracts imdbId from guids`() {
        val dto =
            createMetadataDTO(
                guids = listOf(GuidDTO("imdb://tt1234567")),
            )

        val result = mapper.mapDtoToDomain(dto, "s1", "http://base", "token")

        assertThat(result.imdbId).isEqualTo("tt1234567")
    }

    @Test
    fun `mapDtoToDomain - extracts tmdbId from guids`() {
        val dto =
            createMetadataDTO(
                guids = listOf(GuidDTO("tmdb://550")),
            )

        val result = mapper.mapDtoToDomain(dto, "s1", "http://base", "token")

        assertThat(result.tmdbId).isEqualTo("550")
    }

    @Test
    fun `mapDtoToDomain - constructs correct thumbnail url`() {
        val dto = createMetadataDTO(thumb = "/path/to/thumb")

        val result = mapper.mapDtoToDomain(dto, "s1", "http://base", "token")

        // Should be optimized transcode URL (since it's a relative path)
        assertThat(result.thumbUrl).contains("http://base/photo/:/transcode")
        assertThat(result.thumbUrl).contains("X-Plex-Token=token")
        assertThat(result.thumbUrl).contains("url=%2Fpath%2Fto%2Fthumb")
    }

    @Test
    fun `mapDtoToEntity - calculates unificationId correctly`() {
        val dto =
            createMetadataDTO(
                title = "Inception",
                year = 2010,
                guids = listOf(GuidDTO("imdb://tt1375666")),
            )

        val entity = mapper.mapDtoToEntity(dto, "s1", "library1")

        assertThat(entity.unificationId).isEqualTo("imdb://tt1375666")
    }

    @Test
    fun `mapEntityToDomain - maps basic fields`() {
        val entity =
            MediaEntity(
                ratingKey = "123",
                serverId = "s1",
                librarySectionId = "l1",
                title = "Test Movie",
                type = "movie",
                year = 2024,
                summary = "Summary",
                imdbId = "tt1",
            )

        val domain = mapper.mapEntityToDomain(entity)

        assertThat(domain.title).isEqualTo("Test Movie")
        assertThat(domain.type).isEqualTo(MediaType.Movie)
        assertThat(domain.year).isEqualTo(2024)
        assertThat(domain.imdbId).isEqualTo("tt1")
    }

    private fun createMetadataDTO(
        ratingKey: String = "1",
        key: String = "/library/metadata/1",
        title: String = "Title",
        type: String = "movie",
        thumb: String? = null,
        year: Int? = null,
        guids: List<GuidDTO>? = null,
    ) = MetadataDTO(
        ratingKey = ratingKey,
        key = key,
        title = title,
        type = type,
        thumb = thumb,
        year = year,
        guids = guids,
    )
}
