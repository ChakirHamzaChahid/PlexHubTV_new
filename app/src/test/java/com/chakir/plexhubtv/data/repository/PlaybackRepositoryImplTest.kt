package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.Server
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.PlexApiCache
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.core.util.MediaUrlResolver
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackRepositoryImplTest {
    private val testDispatcher = StandardTestDispatcher()
    private val api = mockk<PlexApiService>()
    private val authRepository = mockk<AuthRepository>()
    private val connectionManager = mockk<ConnectionManager>()
    private val mediaDao = mockk<MediaDao>(relaxed = true)
    private val plexApiCache = mockk<PlexApiCache>(relaxed = true)
    private val mapper = mockk<MediaMapper>()
    private val mediaUrlResolver = mockk<MediaUrlResolver>()
    private val mediaDetailRepository = mockk<MediaDetailRepository>()

    private lateinit var repository: PlaybackRepositoryImpl

    @Before
    fun setup() {
        repository =
            PlaybackRepositoryImpl(
                api, authRepository, connectionManager, mediaDao, plexApiCache, mapper, mediaUrlResolver, mediaDetailRepository, testDispatcher,
            )

        // Default mocks for getClient logic
        val server =
            Server(
                clientIdentifier = "s1",
                name = "Server",
                address = "192.168.1.10",
                port = 32400,
                connectionUri = "http://192.168.1.10:32400",
                accessToken = "token",
                isOwned = true,
            )
        coEvery { authRepository.getServers(any()) } returns Result.success(listOf(server))
        coEvery { connectionManager.findBestConnection(any()) } returns "http://192.168.1.10:32400"
    }

    @Test
    fun `toggleWatchStatus - scrobbles when isWatched is true`() =
        runTest {
            val media = createMediaItem("1", "s1")
            coEvery { api.scrobble(any(), any(), any(), any()) } returns Response.success(Unit)

            val result = repository.toggleWatchStatus(media, true)

            assertThat(result.isSuccess).isTrue()
            coVerify {
                api.scrobble(
                    url = match { it.startsWith("http://192.168.1.10:32400/:/scrobble") },
                    ratingKey = "1",
                    token = "token",
                )
            }
        }

    @Test
    fun `toggleWatchStatus - unscrobbles when isWatched is false`() =
        runTest {
            val media = createMediaItem("1", "s1")
            coEvery { api.unscrobble(any(), any(), any(), any()) } returns Response.success(Unit)

            val result = repository.toggleWatchStatus(media, false)

            assertThat(result.isSuccess).isTrue()
            coVerify {
                api.unscrobble(
                    url = match { it.startsWith("http://192.168.1.10:32400/:/unscrobble") },
                    ratingKey = "1",
                    token = "token",
                )
            }
        }

    @Test
    fun `updatePlaybackProgress - calls updateTimeline`() =
        runTest {
            val media = createMediaItem("1", "s1", durationMs = 1000)
            coEvery { api.updateTimeline(any(), any(), any(), any(), any(), any()) } returns Response.success(Unit)

            val result = repository.updatePlaybackProgress(media, 500)

            assertThat(result.isSuccess).isTrue()
            coVerify {
                api.updateTimeline(
                    url = "http://192.168.1.10:32400/:/timeline",
                    ratingKey = "1",
                    state = "playing",
                    time = 500,
                    duration = 1000,
                    token = "token",
                )
            }
        }

    @Test
    fun `getNextMedia - returns next episode in season`() =
        runTest {
            val current = createMediaItem("1", "s1", type = MediaType.Episode, parentKey = "season1")
            val next = createMediaItem("2", "s1", type = MediaType.Episode, parentKey = "season1")

            coEvery { mediaDetailRepository.getSeasonEpisodes("season1", "s1") } returns Result.success(listOf(current, next))

            val result = repository.getNextMedia(current)

            assertThat(result).isEqualTo(next)
        }

    private fun createMediaItem(
        id: String,
        serverId: String,
        type: MediaType = MediaType.Movie,
        durationMs: Long? = null,
        parentKey: String? = null,
    ) = MediaItem(
        id = "${serverId}_$id",
        ratingKey = id,
        serverId = serverId,
        title = "Title $id",
        type = type,
        durationMs = durationMs,
        parentRatingKey = parentKey,
        mediaParts = emptyList(),
        genres = emptyList(),
    )

    private fun statusUrl(path: String) = "http://192.168.1.10:32400$path?X-Plex-Token=token"
}
