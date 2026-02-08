package com.chakir.plexhubtv.feature.search

import android.util.Log
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.domain.usecase.SearchAcrossServersUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val searchAcrossServersUseCase = mockk<SearchAcrossServersUseCase>()
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        viewModel = SearchViewModel(searchAcrossServersUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onAction QueryChange - triggers searching after delay`() =
        runTest {
            every { searchAcrossServersUseCase(any()) } returns flowOf(Result.success(emptyList()))

            viewModel.onAction(SearchAction.QueryChange("Inception"))

            // Trigger initial update in debouncedSearch launch block
            testDispatcher.scheduler.runCurrent()

            // Before delay
            assertThat(viewModel.uiState.value.searchState).isEqualTo(SearchState.Searching)

            advanceTimeBy(501L)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.searchState).isEqualTo(SearchState.NoResults)
        }

    @Test
    fun `onAction QueryChange - empty query resets state`() =
        runTest {
            viewModel.onAction(SearchAction.QueryChange("Inception"))
            viewModel.onAction(SearchAction.QueryChange(""))

            assertThat(viewModel.uiState.value.query).isEqualTo("")
            assertThat(viewModel.uiState.value.searchState).isEqualTo(SearchState.Idle)
        }

    @Test
    fun `debounced search - updates results successfully`() =
        runTest {
            val results = listOf(createMediaItem("1", "Inception"))
            every { searchAcrossServersUseCase("Inception") } returns flowOf(Result.success(results))

            viewModel.onAction(SearchAction.QueryChange("Inception"))

            advanceTimeBy(501L)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.results).hasSize(1)
            assertThat(viewModel.uiState.value.searchState).isEqualTo(SearchState.Results)
        }

    @Test
    fun `debounced search - handles failure`() =
        runTest {
            every { searchAcrossServersUseCase("error") } returns flowOf(Result.failure(Exception("API Error")))

            viewModel.onAction(SearchAction.QueryChange("error"))

            advanceTimeBy(501L)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.searchState).isEqualTo(SearchState.Error)
            assertThat(viewModel.uiState.value.error).isEqualTo("API Error")
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
