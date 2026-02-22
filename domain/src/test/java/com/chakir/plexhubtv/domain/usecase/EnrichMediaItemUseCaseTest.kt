package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.common.PerformanceTracker
import com.chakir.plexhubtv.core.model.AudioStream
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaPart
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.Server
import com.chakir.plexhubtv.core.model.VideoStream
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import com.chakir.plexhubtv.domain.repository.MediaRepository
import com.chakir.plexhubtv.domain.repository.SearchRepository
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EnrichMediaItemUseCaseTest {
    private lateinit var useCase: EnrichMediaItemUseCase
    private lateinit var authRepository: AuthRepository
    private lateinit var searchRepository: SearchRepository
    private lateinit var mediaRepository: MediaRepository
    private lateinit var mediaDetailRepository: MediaDetailRepository
    private lateinit var performanceTracker: PerformanceTracker
    private lateinit var settingsRepository: SettingsRepository

    private val testServers = listOf(
        Server(
            clientIdentifier = "server1",
            name = "Server 1",
            address = "192.168.1.10",
            port = 32400,
            connectionUri = "http://192.168.1.10:32400",
            accessToken = "token1",
            isOwned = true
        ),
        Server(
            clientIdentifier = "server2",
            name = "Server 2",
            address = "192.168.1.20",
            port = 32400,
            connectionUri = "http://192.168.1.20:32400",
            accessToken = "token2",
            isOwned = true
        )
    )

    private val testMediaItem = MediaItem(
        id = "server1_123",
        ratingKey = "123",
        serverId = "server1",
        title = "The Matrix",
        type = MediaType.Movie,
        unificationId = "imdb://tt0133093",
        imdbId = "tt0133093",
        tmdbId = "603",
        year = 1999,
        mediaParts = listOf(
            MediaPart(
                id = "1",
                key = "/library/parts/1/file.mkv",
                duration = 8160000L,
                file = "/movies/matrix.mkv",
                size = 5000000000L,
                container = "mkv",
                streams = listOf(
                    VideoStream(
                        id = "1",
                        index = 0,
                        language = null,
                        languageCode = null,
                        title = null,
                        displayTitle = null,
                        codec = "h264",
                        selected = true,
                        width = 1920,
                        height = 1080,
                        bitrate = 5000,
                        hasHDR = false
                    ),
                    AudioStream(
                        id = "2",
                        index = 1,
                        language = null,
                        languageCode = null,
                        title = null,
                        displayTitle = null,
                        codec = "aac",
                        selected = true,
                        channels = 6
                    )
                )
            )
        )
    )

    @Before
    fun setup() {
        authRepository = mockk(relaxed = true)
        searchRepository = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        mediaDetailRepository = mockk(relaxed = true)
        performanceTracker = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)

        // Default mock behaviors
        coEvery { authRepository.getServers() } returns Result.success(testServers)
        coEvery { settingsRepository.excludedServerIds } returns flowOf(emptySet())
        coEvery { mediaDetailRepository.findRemoteSources(any()) } returns emptyList()
        coEvery { performanceTracker.startOperation(any(), any(), any(), any()) } just Runs
        coEvery { performanceTracker.endOperation(any(), any(), any()) } just Runs
        coEvery { performanceTracker.addCheckpoint(any(), any(), any()) } just Runs

        useCase = EnrichMediaItemUseCase(
            authRepository = authRepository,
            searchRepository = searchRepository,
            mediaRepository = mediaRepository,
            mediaDetailRepository = mediaDetailRepository,
            performanceTracker = performanceTracker,
            settingsRepository = settingsRepository
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `invoke with single server returns only current source`() = runTest {
        coEvery { authRepository.getServers() } returns Result.success(listOf(testServers.first()))

        val result = useCase(testMediaItem)

        assertThat(result.remoteSources).hasSize(1)
        assertThat(result.remoteSources.first().serverId).isEqualTo("server1")
        assertThat(result.remoteSources.first().serverName).isEqualTo("Server 1")
    }

    @Test
    fun `invoke enriches media with room-first strategy`() = runTest {
        val remoteMatch = testMediaItem.copy(
            id = "server2_456",
            ratingKey = "456",
            serverId = "server2"
        )
        coEvery { mediaDetailRepository.findRemoteSources(any()) } returns listOf(remoteMatch)

        val result = useCase(testMediaItem)

        assertThat(result.remoteSources).hasSize(2)
        assertThat(result.remoteSources.map { it.serverId }).containsExactly("server1", "server2")
        coVerify { mediaDetailRepository.findRemoteSources(testMediaItem) }
    }

    @Test
    fun `invoke builds media source with correct metadata`() = runTest {
        coEvery { authRepository.getServers() } returns Result.success(listOf(testServers.first()))

        val result = useCase(testMediaItem)

        val source = result.remoteSources.first()
        assertThat(source.resolution).isEqualTo("1080p")
        assertThat(source.container).isEqualTo("mkv")
        assertThat(source.videoCodec).isEqualTo("h264")
        assertThat(source.audioCodec).isEqualTo("aac")
        assertThat(source.audioChannels).isEqualTo(6)
        assertThat(source.fileSize).isEqualTo(5000000000L)
        assertThat(source.hasHDR).isFalse()
    }

    @Test
    fun `invoke caches results for same item`() = runTest {
        coEvery { authRepository.getServers() } returns Result.success(listOf(testServers.first()))

        // First invocation
        val result1 = useCase(testMediaItem)
        // Second invocation
        val result2 = useCase(testMediaItem)

        assertThat(result1).isEqualTo(result2)
        // Should only query servers once due to caching
        coVerify(exactly = 1) { authRepository.getServers() }
    }

    @Test
    fun `invoke skips room query when no unificationId and not episode`() = runTest {
        val itemWithoutId = testMediaItem.copy(unificationId = null, imdbId = null, tmdbId = null)

        val result = useCase(itemWithoutId)

        // Should skip room query and go directly to network fallback or single server
        coVerify(exactly = 0) { mediaDetailRepository.findRemoteSources(any()) }
    }

    @Test
    fun `invoke handles episode matching by hierarchy`() = runTest {
        val episode = testMediaItem.copy(
            type = MediaType.Episode,
            grandparentTitle = "Breaking Bad",
            parentTitle = "Season 1",
            episodeIndex = 1,
            parentIndex = 1
        )

        val result = useCase(episode)

        // Episodes can be queried via room even without unificationId
        coVerify { mediaDetailRepository.findRemoteSources(episode) }
    }

    @Test
    fun `invoke excludes servers from settings`() = runTest {
        coEvery { settingsRepository.excludedServerIds } returns flowOf(setOf("server2"))

        val result = useCase(testMediaItem)

        // Should only include server1 since server2 is excluded
        assertThat(result.remoteSources).hasSize(1)
        assertThat(result.remoteSources.first().serverId).isEqualTo("server1")
    }

    @Test
    fun `invoke fetches full details when mediaParts are missing`() = runTest {
        val matchWithoutParts = testMediaItem.copy(
            serverId = "server2",
            ratingKey = "456",
            mediaParts = emptyList()
        )
        coEvery { mediaDetailRepository.findRemoteSources(any()) } returns listOf(matchWithoutParts)
        coEvery { mediaRepository.getMediaDetail("456", "server2") } returns Result.success(
            testMediaItem.copy(serverId = "server2", ratingKey = "456")
        )

        val result = useCase(testMediaItem)

        coVerify { mediaRepository.getMediaDetail("456", "server2") }
        coVerify { mediaDetailRepository.updateMediaParts(any()) }
    }

    @Test
    fun `invoke handles network search failure gracefully`() = runTest {
        coEvery { mediaDetailRepository.findRemoteSources(any()) } returns emptyList()
        coEvery { searchRepository.searchOnServer(any(), any()) } returns Result.failure(Exception("Network error"))

        val result = useCase(testMediaItem)

        // Should still return current source even if network search fails
        assertThat(result.remoteSources).isNotEmpty()
        assertThat(result.remoteSources.first().serverId).isEqualTo("server1")
    }

    @Test
    fun `invoke handles auth failure gracefully`() = runTest {
        coEvery { authRepository.getServers() } returns Result.failure(Exception("Auth failed"))

        val result = useCase(testMediaItem)

        // Should return original item without enrichment
        assertThat(result).isEqualTo(testMediaItem)
    }

    @Test
    fun `invoke tracks performance metrics`() = runTest {
        coEvery { authRepository.getServers() } returns Result.success(listOf(testServers.first()))

        useCase(testMediaItem)

        coVerify { performanceTracker.startOperation(any(), any(), any(), any()) }
        coVerify { performanceTracker.endOperation(any(), any(), any(), any()) }
    }

    @Test
    fun `invoke network fallback searches all enabled servers`() = runTest {
        // Room returns empty, forcing network fallback
        coEvery { mediaDetailRepository.findRemoteSources(any()) } returns emptyList()
        coEvery { searchRepository.searchOnServer(testServers[1], "The Matrix") } returns Result.success(
            listOf(testMediaItem.copy(serverId = "server2", ratingKey = "789"))
        )
        coEvery { mediaRepository.getMediaDetail("789", "server2") } returns Result.success(
            testMediaItem.copy(serverId = "server2", ratingKey = "789")
        )

        val result = useCase(testMediaItem)

        // Should search server2 (not server1 which is the current server)
        coVerify { searchRepository.searchOnServer(testServers[1], "The Matrix") }
    }

    @Test
    fun `invoke matches by IMDb ID in network search`() = runTest {
        val candidate = testMediaItem.copy(
            serverId = "server2",
            ratingKey = "999",
            imdbId = "tt0133093" // Same IMDb ID
        )
        coEvery { mediaDetailRepository.findRemoteSources(any()) } returns emptyList()
        coEvery { searchRepository.searchOnServer(testServers[1], "The Matrix") } returns Result.success(listOf(candidate))
        coEvery { mediaRepository.getMediaDetail("999", "server2") } returns Result.success(candidate)

        val result = useCase(testMediaItem)

        // Should find match via IMDb ID
        assertThat(result.remoteSources.size).isAtLeast(2)
    }
}
