package com.chakir.plexhubtv.data.mapper

import com.chakir.plexhubtv.core.database.MediaEntity
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.network.model.GenreDTO
import com.chakir.plexhubtv.core.network.model.GuidDTO
import com.chakir.plexhubtv.core.network.model.MetadataDTO
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class MediaMapperTest {
    private lateinit var mapper: MediaMapper

    private val testServerId = "server123"
    private val testBaseUrl = "http://localhost:32400"
    private val testAccessToken = "test_token"
    private val testLibraryKey = "1"

    @Before
    fun setup() {
        mapper = MediaMapper()
    }

    @Test
    fun `mapDtoToDomain maps all fields correctly`() {
        val dto = MetadataDTO(
            ratingKey = "123",
            key = "/library/metadata/123",
            title = "The Matrix",
            type = "movie",
            thumb = "/library/metadata/123/thumb/1234567890",
            art = "/library/metadata/123/art/1234567890",
            summary = "A computer hacker learns about the true nature of reality.",
            year = 1999,
            duration = 8160000L,
            viewOffset = 1000L,
            viewCount = 2,
            guid = "plex://movie/5d776825880197001ec967c9",
            guids = listOf(
                GuidDTO(id = "imdb://tt0133093"),
                GuidDTO(id = "tmdb://603")
            ),
            studio = "Warner Bros.",
            contentRating = "R",
            audienceRating = 8.7,
            rating = 8.5,
            addedAt = 1234567890L,
            updatedAt = 1234567900L,
            tagline = "Welcome to the Real World",
            genres = listOf(GenreDTO(tag = "Action"), GenreDTO(tag = "Sci-Fi"))
        )

        val result = mapper.mapDtoToDomain(dto, testServerId, testBaseUrl, testAccessToken)

        assertThat(result.ratingKey).isEqualTo("123")
        assertThat(result.serverId).isEqualTo(testServerId)
        assertThat(result.title).isEqualTo("The Matrix")
        assertThat(result.type).isEqualTo(MediaType.Movie)
        assertThat(result.thumbUrl).contains(testBaseUrl)
        assertThat(result.thumbUrl).contains(testAccessToken)
        assertThat(result.artUrl).contains(testBaseUrl)
        assertThat(result.artUrl).contains(testAccessToken)
        assertThat(result.summary).isEqualTo(dto.summary)
        assertThat(result.year).isEqualTo(1999)
        assertThat(result.durationMs).isEqualTo(8160000L)
        assertThat(result.viewOffset).isEqualTo(1000L)
        assertThat(result.viewCount).isEqualTo(2L)
        assertThat(result.isWatched).isTrue()
        assertThat(result.imdbId).isEqualTo("tt0133093")
        assertThat(result.tmdbId).isEqualTo("603")
        assertThat(result.studio).isEqualTo("Warner Bros.")
        assertThat(result.audienceRating).isEqualTo(8.7)
        assertThat(result.rating).isEqualTo(8.5)
        assertThat(result.tagline).isEqualTo("Welcome to the Real World")
        assertThat(result.genres).containsExactly("Action", "Sci-Fi")
    }

    @Test
    fun `mapDtoToEntity maps all fields correctly`() {
        val dto = MetadataDTO(
            ratingKey = "456",
            key = "/library/metadata/456",
            title = "Breaking Bad",
            type = "show",
            thumb = "/library/metadata/456/thumb/1234567890",
            art = "/library/metadata/456/art/1234567890",
            summary = "A high school chemistry teacher turned meth manufacturer.",
            year = 2008,
            duration = 2700000L,
            viewOffset = 500L,
            guid = "plex://show/5d9c086fe9d5a1001f4d87d2",
            guids = listOf(
                GuidDTO(id = "imdb://tt0903747"),
                GuidDTO(id = "tmdb://1396")
            ),
            rating = 9.5,
            audienceRating = 9.8,
            genres = listOf(GenreDTO(tag = "Drama"), GenreDTO(tag = "Crime"))
        )

        val result = mapper.mapDtoToEntity(dto, testServerId, testLibraryKey)

        assertThat(result.ratingKey).isEqualTo("456")
        assertThat(result.serverId).isEqualTo(testServerId)
        assertThat(result.librarySectionId).isEqualTo(testLibraryKey)
        assertThat(result.title).isEqualTo("Breaking Bad")
        assertThat(result.type).isEqualTo("show")
        assertThat(result.thumbUrl).isEqualTo(dto.thumb)
        assertThat(result.artUrl).isEqualTo(dto.art)
        assertThat(result.summary).isEqualTo(dto.summary)
        assertThat(result.year).isEqualTo(2008)
        assertThat(result.imdbId).isEqualTo("tt0903747")
        assertThat(result.tmdbId).isEqualTo("1396")
        assertThat(result.rating).isEqualTo(9.5)
        assertThat(result.audienceRating).isEqualTo(9.8)
        assertThat(result.displayRating).isEqualTo(9.8) // audienceRating takes precedence
        assertThat(result.genres).isEqualTo("Drama,Crime")
        assertThat(result.unificationId).isEqualTo("imdb://tt0903747")
    }

    @Test
    fun `mapEntityToDomain maps all fields correctly`() {
        val entity = MediaEntity(
            ratingKey = "789",
            serverId = testServerId,
            librarySectionId = testLibraryKey,
            title = "Inception",
            titleSortable = "inception",
            type = "movie",
            thumbUrl = "/library/metadata/789/thumb/1234567890",
            artUrl = "/library/metadata/789/art/1234567890",
            summary = "A thief who steals corporate secrets.",
            year = 2010,
            duration = 8880000L,
            viewOffset = 2000L,
            guid = "plex://movie/5d776b7f3fcfdb001f195039",
            imdbId = "tt1375666",
            tmdbId = "27205",
            rating = 8.8,
            audienceRating = 9.0,
            displayRating = 9.0,
            genres = "Action,Sci-Fi,Thriller",
            unificationId = "imdb://tt1375666"
        )

        val result = mapper.mapEntityToDomain(entity)

        assertThat(result.ratingKey).isEqualTo("789")
        assertThat(result.serverId).isEqualTo(testServerId)
        assertThat(result.title).isEqualTo("Inception")
        assertThat(result.type).isEqualTo(MediaType.Movie)
        assertThat(result.thumbUrl).isEqualTo(entity.thumbUrl)
        assertThat(result.artUrl).isEqualTo(entity.artUrl)
        assertThat(result.summary).isEqualTo("A thief who steals corporate secrets.")
        assertThat(result.year).isEqualTo(2010)
        assertThat(result.durationMs).isEqualTo(8880000L)
        assertThat(result.viewOffset).isEqualTo(2000L)
        assertThat(result.imdbId).isEqualTo("tt1375666")
        assertThat(result.tmdbId).isEqualTo("27205")
        assertThat(result.rating).isEqualTo(9.0) // displayRating is used
        assertThat(result.genres).containsExactly("Action", "Sci-Fi", "Thriller")
        assertThat(result.unificationId).isEqualTo("imdb://tt1375666")
    }

    @Test
    fun `mapDomainToEntity preserves all domain fields`() {
        val item = MediaItem(
            id = "server_999",
            ratingKey = "999",
            serverId = testServerId,
            title = "Interstellar",
            type = MediaType.Movie,
            thumbUrl = "http://localhost/thumb.jpg",
            artUrl = "http://localhost/art.jpg",
            summary = "A team of explorers travel through a wormhole.",
            year = 2014,
            durationMs = 10140000L,
            viewOffset = 3000L,
            imdbId = "tt0816692",
            tmdbId = "157336",
            rating = 8.6,
            audienceRating = 8.9,
            genres = listOf("Sci-Fi", "Drama", "Adventure"),
            unificationId = "imdb://tt0816692"
        )

        val result = mapper.mapDomainToEntity(item, testLibraryKey)

        assertThat(result.ratingKey).isEqualTo("999")
        assertThat(result.serverId).isEqualTo(testServerId)
        assertThat(result.librarySectionId).isEqualTo(testLibraryKey)
        assertThat(result.title).isEqualTo("Interstellar")
        assertThat(result.type).isEqualTo("movie")
        assertThat(result.year).isEqualTo(2014)
        assertThat(result.imdbId).isEqualTo("tt0816692")
        assertThat(result.tmdbId).isEqualTo("157336")
        assertThat(result.genres).isEqualTo("Sci-Fi,Drama,Adventure")
        assertThat(result.unificationId).isEqualTo("imdb://tt0816692")
    }

    @Test
    fun `isQualityMetadata returns true for movie with IMDb ID`() {
        val dto = MetadataDTO(
            ratingKey = "1",
            key = "/library/metadata/1",
            title = "Test Movie",
            type = "movie",
            guids = listOf(GuidDTO(id = "imdb://tt1234567"))
        )

        val result = mapper.isQualityMetadata(dto)

        assertThat(result).isTrue()
    }

    @Test
    fun `isQualityMetadata returns true for movie with TMDB ID`() {
        val dto = MetadataDTO(
            ratingKey = "1",
            key = "/library/metadata/1",
            title = "Test Movie",
            type = "movie",
            guids = listOf(GuidDTO(id = "tmdb://12345"))
        )

        val result = mapper.isQualityMetadata(dto)

        assertThat(result).isTrue()
    }

    @Test
    fun `isQualityMetadata returns false for movie without IMDb or TMDB ID`() {
        val dto = MetadataDTO(
            ratingKey = "1",
            key = "/library/metadata/1",
            title = "Test Movie",
            type = "movie",
            guids = null
        )

        val result = mapper.isQualityMetadata(dto)

        assertThat(result).isFalse()
    }

    @Test
    fun `isQualityMetadata returns true for show with title`() {
        val dto = MetadataDTO(
            ratingKey = "1",
            key = "/library/metadata/1",
            title = "Test Show",
            type = "show"
        )

        val result = mapper.isQualityMetadata(dto)

        assertThat(result).isTrue()
    }

    @Test
    fun `extractImdbId from guids list`() {
        val dto = MetadataDTO(
            ratingKey = "1",
            key = "/library/metadata/1",
            title = "Test",
            type = "movie",
            guids = listOf(
                GuidDTO(id = "tmdb://12345"),
                GuidDTO(id = "imdb://tt9876543")
            )
        )

        val result = mapper.mapDtoToDomain(dto, testServerId, testBaseUrl, testAccessToken)

        assertThat(result.imdbId).isEqualTo("tt9876543")
    }

    @Test
    fun `extractTmdbId from guids list`() {
        val dto = MetadataDTO(
            ratingKey = "1",
            key = "/library/metadata/1",
            title = "Test",
            type = "movie",
            guids = listOf(
                GuidDTO(id = "imdb://tt1234567"),
                GuidDTO(id = "tmdb://54321")
            )
        )

        val result = mapper.mapDtoToDomain(dto, testServerId, testBaseUrl, testAccessToken)

        assertThat(result.tmdbId).isEqualTo("54321")
    }

    @Test
    fun `unificationId prefers IMDb over TMDB`() {
        val dto = MetadataDTO(
            ratingKey = "1",
            key = "/library/metadata/1",
            title = "Test Movie",
            type = "movie",
            year = 2020,
            guids = listOf(
                GuidDTO(id = "imdb://tt1111111"),
                GuidDTO(id = "tmdb://22222")
            )
        )

        val result = mapper.mapDtoToEntity(dto, testServerId, testLibraryKey)

        assertThat(result.unificationId).isEqualTo("imdb://tt1111111")
    }

    @Test
    fun `unificationId uses TMDB when IMDb is absent`() {
        val dto = MetadataDTO(
            ratingKey = "1",
            key = "/library/metadata/1",
            title = "Test Movie",
            type = "movie",
            year = 2020,
            guids = listOf(GuidDTO(id = "tmdb://33333"))
        )

        val result = mapper.mapDtoToEntity(dto, testServerId, testLibraryKey)

        assertThat(result.unificationId).isEqualTo("tmdb://33333")
    }

    @Test
    fun `unificationId falls back to title and year`() {
        val dto = MetadataDTO(
            ratingKey = "1",
            key = "/library/metadata/1",
            title = "Test Movie!",
            type = "movie",
            year = 2020,
            guids = null
        )

        val result = mapper.mapDtoToEntity(dto, testServerId, testLibraryKey)

        // Should normalize title (remove special chars) and append year
        assertThat(result.unificationId).contains("test movie")
        assertThat(result.unificationId).contains("2020")
    }
}
