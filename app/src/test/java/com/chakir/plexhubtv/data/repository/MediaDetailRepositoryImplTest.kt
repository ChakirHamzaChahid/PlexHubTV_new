package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.database.MediaEntity
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.Server
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.PlexApiCache
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.core.network.model.MediaContainer
import com.chakir.plexhubtv.core.network.model.MetadataDTO
import com.chakir.plexhubtv.core.network.model.PlexResponse
import com.chakir.plexhubtv.core.util.MediaUrlResolver
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class MediaDetailRepositoryImplTest {
    private val testDispatcher = StandardTestDispatcher()
    private val api = mockk<PlexApiService>()
    private val authRepository = mockk<AuthRepository>()
    private val connectionManager = mockk<ConnectionManager>()
    private val mediaDao = mockk<MediaDao>(relaxed = true)
    private val plexApiCache = mockk<PlexApiCache>(relaxed = true)
    private val mapper = mockk<MediaMapper>()
    private val mediaUrlResolver = mockk<MediaUrlResolver>()

    private lateinit var repository: MediaDetailRepositoryImpl

    @Before
    fun setup() {
        repository =
            MediaDetailRepositoryImpl(
                api, authRepository, connectionManager, mediaDao, plexApiCache, mapper, mediaUrlResolver, testDispatcher,
            )

        val server = createServer("s1")
        coEvery { authRepository.getServers(any()) } returns Result.success(listOf(server))
        coEvery { connectionManager.findBestConnection(any()) } returns "http://s1"
    }

    @Test
    fun `getMediaDetail - returns success when API is successful`() =
        runTest {
            val dto = MetadataDTO(ratingKey = "1", key = "/metadata/1", title = "Movie", type = "movie")
            val response = PlexResponse(MediaContainer(metadata = listOf(dto)))
            coEvery { api.getMetadata(any()) } returns Response.success(response)

            val domainItem = createMediaItem("1", "s1")
            every { mapper.mapDtoToDomain(any(), any(), any(), any()) } returns domainItem

            val result = repository.getMediaDetail("1", "s1")

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()?.title).isEqualTo("Title 1")
        }

    @Test
    fun `getMediaDetail - falls back to GUID search when primary fails`() =
        runTest {
            // Primary fails
            coEvery { api.getMetadata(any()) } returns Response.error(404, mockk(relaxed = true))

            // Entity in local DB has GUID
            val entity =
                MediaEntity(
                    ratingKey = "1",
                    serverId = "s1",
                    librarySectionId = "101",
                    guid = "imdb://123",
                    title = "Movie",
                    type = "movie",
                )
            coEvery { mediaDao.getMedia("1", "s1") } returns entity

            // Secondary server
            val server2 = createServer("s2")
            coEvery { authRepository.getServers() } returns Result.success(listOf(createServer("s1"), server2))
            coEvery { connectionManager.findBestConnection(server2) } returns "http://s2"

            // Secondary server has the media
            val dtoAlt = MetadataDTO(ratingKey = "alt1", key = "/metadata/alt1", title = "Movie Alt", type = "movie")
            val responseAlt = PlexResponse(MediaContainer(metadata = listOf(dtoAlt)))
            coEvery { api.getMetadata(match { it.contains("guid=imdb://123") }) } returns Response.success(responseAlt)

            val domainItem = createMediaItem("alt1", "s2")
            every { mapper.mapDtoToDomain(any(), eq("s2"), any(), any()) } returns domainItem

            val result = repository.getMediaDetail("1", "s1")

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()?.serverId).isEqualTo("s2")
        }

    private fun createServer(id: String) =
        Server(
            clientIdentifier = id,
            name = "Server $id",
            address = "127.0.0.1",
            port = 32400,
            connectionUri = "http://127.0.0.1:32400",
            accessToken = "token_$id",
            isOwned = true,
        )

    private fun createMediaItem(
        id: String,
        serverId: String,
    ) = MediaItem(
        id = "${serverId}_$id",
        ratingKey = id,
        serverId = serverId,
        title = "Title $id",
        type = MediaType.Movie,
        mediaParts = emptyList(),
        genres = emptyList(),
    )
}
