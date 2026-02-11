package com.chakir.plexhubtv.feature.details

import androidx.lifecycle.SavedStateHandle
import com.chakir.plexhubtv.core.model.Collection
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.RemoteSource
import com.chakir.plexhubtv.domain.usecase.GetMediaCollectionsUseCase
import com.chakir.plexhubtv.domain.usecase.GetSimilarMediaUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class MediaEnrichmentViewModelTest {

    private lateinit var viewModel: MediaEnrichmentViewModel
    private lateinit var getSimilarMediaUseCase: GetSimilarMediaUseCase
    private lateinit var getMediaCollectionsUseCase: GetMediaCollectionsUseCase
    private lateinit var savedStateHandle: SavedStateHandle

    private val testDispatcher = StandardTestDispatcher()

    private val testRatingKey = "test-rating-key"
    private val testServerId = "test-server-id"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        getSimilarMediaUseCase = mockk()
        getMediaCollectionsUseCase = mockk()
        savedStateHandle = SavedStateHandle(
            mapOf(
                "ratingKey" to testRatingKey,
                "serverId" to testServerId
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() = runTest {
        // Given
        coEvery { getSimilarMediaUseCase(any(), any()) } returns Result.success(emptyList())

        // When
        viewModel = MediaEnrichmentViewModel(
            getSimilarMediaUseCase,
            getMediaCollectionsUseCase,
            savedStateHandle
        )

        // Then
        val state = viewModel.uiState.value
        assertThat(state.similarItems).isEmpty()
        assertThat(state.collections).isEmpty()
        assertThat(state.isLoadingSimilar).isTrue()
        assertThat(state.isLoadingCollections).isFalse()
    }

    @Test
    fun `loadSimilarItems success updates state correctly`() = runTest {
        // Given
        val similarItems = listOf(
            createTestMediaItem("similar-1", "Similar Movie 1"),
            createTestMediaItem("similar-2", "Similar Movie 2")
        )
        coEvery { getSimilarMediaUseCase(testRatingKey, testServerId) } returns Result.success(similarItems)

        // When
        viewModel = MediaEnrichmentViewModel(
            getSimilarMediaUseCase,
            getMediaCollectionsUseCase,
            savedStateHandle
        )
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertThat(state.similarItems).hasSize(2)
        assertThat(state.similarItems).containsExactlyElementsIn(similarItems)
        assertThat(state.isLoadingSimilar).isFalse()
    }

    @Test
    fun `loadSimilarItems failure sets loading to false`() = runTest {
        // Given
        coEvery { getSimilarMediaUseCase(testRatingKey, testServerId) } returns
            Result.failure(Exception("Network error"))

        // When
        viewModel = MediaEnrichmentViewModel(
            getSimilarMediaUseCase,
            getMediaCollectionsUseCase,
            savedStateHandle
        )
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertThat(state.similarItems).isEmpty()
        assertThat(state.isLoadingSimilar).isFalse()
    }

    @Test
    fun `loadEnrichment with single server loads collections`() = runTest {
        // Given
        coEvery { getSimilarMediaUseCase(any(), any()) } returns Result.success(emptyList())

        val testMedia = createTestMediaItem(testRatingKey, "Test Movie")
        val collections = listOf(
            createTestCollection("col-1", "Action Movies", 5),
            createTestCollection("col-2", "Sci-Fi Collection", 3)
        )
        coEvery { getMediaCollectionsUseCase(testRatingKey, testServerId) } returns flowOf(collections)

        viewModel = MediaEnrichmentViewModel(
            getSimilarMediaUseCase,
            getMediaCollectionsUseCase,
            savedStateHandle
        )
        advanceUntilIdle()

        // When
        viewModel.loadEnrichment(testMedia)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertThat(state.collections).hasSize(2)
        assertThat(state.collections.map { it.id }).containsExactly("col-1", "col-2")
        assertThat(state.isLoadingCollections).isFalse()
        coVerify { getMediaCollectionsUseCase(testRatingKey, testServerId) }
    }

    @Test
    fun `loadEnrichment with multiple servers aggregates collections`() = runTest {
        // Given
        coEvery { getSimilarMediaUseCase(any(), any()) } returns Result.success(emptyList())

        val testMedia = createTestMediaItem(testRatingKey, "Test Movie").copy(
            remoteSources = listOf(
                RemoteSource(testServerId, testRatingKey, "Server 1", "icon1"),
                RemoteSource("server-2", "key-2", "Server 2", "icon2")
            )
        )

        // Server 1 collections
        val collections1 = listOf(
            createTestCollection("col-1", "Action Movies", 5, testServerId)
        )
        coEvery { getMediaCollectionsUseCase(testRatingKey, testServerId) } returns flowOf(collections1)

        // Server 2 collections
        val collections2 = listOf(
            createTestCollection("col-2", "Sci-Fi Collection", 3, "server-2")
        )
        coEvery { getMediaCollectionsUseCase("key-2", "server-2") } returns flowOf(collections2)

        viewModel = MediaEnrichmentViewModel(
            getSimilarMediaUseCase,
            getMediaCollectionsUseCase,
            savedStateHandle
        )
        advanceUntilIdle()

        // When
        viewModel.loadEnrichment(testMedia)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertThat(state.collections).hasSize(2)
        assertThat(state.collections.map { it.id }).containsExactly("col-1", "col-2")
        assertThat(state.isLoadingCollections).isFalse()

        // Verify both servers were queried
        coVerify { getMediaCollectionsUseCase(testRatingKey, testServerId) }
        coVerify { getMediaCollectionsUseCase("key-2", "server-2") }
    }

    @Test
    fun `loadEnrichment deduplicates collections by title and serverId`() = runTest {
        // Given
        coEvery { getSimilarMediaUseCase(any(), any()) } returns Result.success(emptyList())

        val testMedia = createTestMediaItem(testRatingKey, "Test Movie")

        // Same collection returned multiple times (shouldn't happen but defensive)
        val duplicateCollections = listOf(
            createTestCollection("col-1", "Action Movies", 5),
            createTestCollection("col-1-dup", "Action Movies", 5), // Same title, same server
            createTestCollection("col-2", "Different Collection", 3)
        )
        coEvery { getMediaCollectionsUseCase(testRatingKey, testServerId) } returns flowOf(duplicateCollections)

        viewModel = MediaEnrichmentViewModel(
            getSimilarMediaUseCase,
            getMediaCollectionsUseCase,
            savedStateHandle
        )
        advanceUntilIdle()

        // When
        viewModel.loadEnrichment(testMedia)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        // Should be deduplicated by title + serverId
        assertThat(state.collections).hasSize(2)
        assertThat(state.collections.map { it.title }).containsExactly("Action Movies", "Different Collection")
    }

    @Test
    fun `loadEnrichment handles server failures gracefully`() = runTest {
        // Given
        coEvery { getSimilarMediaUseCase(any(), any()) } returns Result.success(emptyList())

        val testMedia = createTestMediaItem(testRatingKey, "Test Movie").copy(
            remoteSources = listOf(
                RemoteSource(testServerId, testRatingKey, "Server 1", "icon1"),
                RemoteSource("server-2", "key-2", "Server 2", "icon2")
            )
        )

        // Server 1 succeeds
        val collections1 = listOf(createTestCollection("col-1", "Action Movies", 5))
        coEvery { getMediaCollectionsUseCase(testRatingKey, testServerId) } returns flowOf(collections1)

        // Server 2 fails
        coEvery { getMediaCollectionsUseCase("key-2", "server-2") } throws Exception("Server unavailable")

        viewModel = MediaEnrichmentViewModel(
            getSimilarMediaUseCase,
            getMediaCollectionsUseCase,
            savedStateHandle
        )
        advanceUntilIdle()

        // When
        viewModel.loadEnrichment(testMedia)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        // Should still have collections from server 1
        assertThat(state.collections).hasSize(1)
        assertThat(state.collections[0].id).isEqualTo("col-1")
        assertThat(state.isLoadingCollections).isFalse()
    }

    @Test
    fun `loadEnrichment with empty remoteSources queries only primary server`() = runTest {
        // Given
        coEvery { getSimilarMediaUseCase(any(), any()) } returns Result.success(emptyList())

        val testMedia = createTestMediaItem(testRatingKey, "Test Movie").copy(
            remoteSources = emptyList()
        )

        val collections = listOf(createTestCollection("col-1", "Action Movies", 5))
        coEvery { getMediaCollectionsUseCase(testRatingKey, testServerId) } returns flowOf(collections)

        viewModel = MediaEnrichmentViewModel(
            getSimilarMediaUseCase,
            getMediaCollectionsUseCase,
            savedStateHandle
        )
        advanceUntilIdle()

        // When
        viewModel.loadEnrichment(testMedia)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { getMediaCollectionsUseCase(any(), any()) }
        coVerify { getMediaCollectionsUseCase(testRatingKey, testServerId) }
    }

    // Helper functions
    private fun createTestMediaItem(
        ratingKey: String,
        title: String,
        serverId: String = testServerId
    ) = MediaItem(
        ratingKey = ratingKey,
        serverId = serverId,
        title = title,
        type = MediaType.Movie,
        year = 2024,
        thumbUrl = "/library/metadata/$ratingKey/thumb",
        duration = 7200000L,
        viewOffset = 0L,
        isWatched = false,
        isFavorite = false,
        addedAt = System.currentTimeMillis(),
        lastViewedAt = null,
        remoteSources = emptyList()
    )

    private fun createTestCollection(
        id: String,
        title: String,
        itemCount: Int,
        serverId: String = testServerId
    ) = Collection(
        id = id,
        title = title,
        serverId = serverId,
        items = List(itemCount) { index ->
            createTestMediaItem("item-$index", "Item $index", serverId)
        }
    )
}
