package com.chakir.plexhubtv.feature.home

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkManager
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.di.image.ImagePrefetchManager
import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.domain.repository.FavoritesRepository
import com.chakir.plexhubtv.domain.usecase.GetUnifiedHomeContentUseCase
import com.chakir.plexhubtv.domain.usecase.HomeContent
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
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

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val getUnifiedHomeContentUseCase = mockk<GetUnifiedHomeContentUseCase>()
    private val favoritesRepository = mockk<FavoritesRepository>()
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val settingsDataStore = mockk<SettingsDataStore>()
    private val imagePrefetchManager = mockk<ImagePrefetchManager>()
    private val savedStateHandle = SavedStateHandle()

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock Android Log
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        // Default mocks
        every { settingsDataStore.isFirstSyncComplete } returns flowOf(true)
        every { getUnifiedHomeContentUseCase() } returns flowOf(Result.success(HomeContent(emptyList(), emptyList())))
        // Relaxed workManager will return an empty flow by default if we use mockk(relaxed=true)
        // OR we can explicitly mock it if it's an extension (which we suspect)

        every { imagePrefetchManager.prefetchImages(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init - loads content immediately`() =
        runTest {
            val onDeck = listOf(createMediaItem("1", "On Deck Item"))
            val hubs = listOf(Hub("key", "Trending", "movie", items = listOf(createMediaItem("2", "Hub Item"))))

            every { getUnifiedHomeContentUseCase() } returns flowOf(Result.success(HomeContent(onDeck, hubs)))

            viewModel =
                HomeViewModel(
                    getUnifiedHomeContentUseCase,
                    favoritesRepository,
                    workManager,
                    settingsDataStore,
                    imagePrefetchManager,
                    savedStateHandle,
                )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isLoading).isFalse()
            assertThat(state.onDeck).hasSize(1)
            assertThat(state.onDeck[0].title).isEqualTo("On Deck Item")
            assertThat(state.hubs).hasSize(1)
            assertThat(state.hubs[0].title).isEqualTo("Trending")
        }

    @Test
    fun `onAction Refresh - triggers reload`() =
        runTest {
            every { getUnifiedHomeContentUseCase() } returns flowOf(Result.success(HomeContent(emptyList(), emptyList())))

            viewModel =
                HomeViewModel(
                    getUnifiedHomeContentUseCase,
                    favoritesRepository,
                    workManager,
                    settingsDataStore,
                    imagePrefetchManager,
                    savedStateHandle,
                )

            advanceUntilIdle()

            viewModel.onAction(HomeAction.Refresh)

            advanceUntilIdle()

            // Verify it was called again
            io.mockk.verify(exactly = 2) { getUnifiedHomeContentUseCase() }
        }

    private fun createMediaItem(
        id: String,
        title: String,
    ) = MediaItem(
        id = id,
        ratingKey = id,
        serverId = "s1",
        title = title,
        type = MediaType.Movie,
        mediaParts = emptyList(),
        genres = emptyList(),
    )
}
