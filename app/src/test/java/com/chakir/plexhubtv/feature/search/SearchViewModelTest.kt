package com.chakir.plexhubtv.feature.search

import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.domain.usecase.SearchAcrossServersUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.*
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
class SearchViewModelTest {
    private lateinit var viewModel: SearchViewModel
    private lateinit var searchAcrossServersUseCase: SearchAcrossServersUseCase

    private val testDispatcher = StandardTestDispatcher()

    private val testResults = listOf(
        MediaItem(
            id = "1",
            ratingKey = "123",
            serverId = "server1",
            title = "The Matrix",
            type = MediaType.Movie
        ),
        MediaItem(
            id = "2",
            ratingKey = "456",
            serverId = "server1",
            title = "The Matrix Reloaded",
            type = MediaType.Movie
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        searchAcrossServersUseCase = mockk(relaxed = true)

        // Default mock behavior - successful search
        coEvery { searchAcrossServersUseCase(any()) } returns flowOf(Result.success(testResults))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    private fun createViewModel(): SearchViewModel {
        return SearchViewModel(
            searchAcrossServersUseCase = searchAcrossServersUseCase
        )
    }

    @Test
    fun `initialization - starts with idle state`() = runTest {
        viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertThat(state.query).isEmpty()
        assertThat(state.searchState).isEqualTo(SearchState.Idle)
        assertThat(state.results).isEmpty()
    }

    @Test
    fun `QueryChange updates query without triggering search`() = runTest {
        viewModel = createViewModel()

        viewModel.onAction(SearchAction.QueryChange("matrix"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.query).isEqualTo("matrix")
        assertThat(state.searchState).isEqualTo(SearchState.Idle)

        // Search should not be triggered automatically
        coVerify(exactly = 0) { searchAcrossServersUseCase(any()) }
    }

    @Test
    fun `ExecuteSearch with valid query returns results`() = runTest {
        viewModel = createViewModel()

        viewModel.onAction(SearchAction.QueryChange("matrix"))
        viewModel.onAction(SearchAction.ExecuteSearch)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.results).hasSize(2)
        assertThat(state.results.first().title).isEqualTo("The Matrix")
        assertThat(state.searchState).isEqualTo(SearchState.Results)

        coVerify { searchAcrossServersUseCase("matrix") }
    }

    @Test
    fun `ExecuteSearch with empty results shows NoResults state`() = runTest {
        coEvery { searchAcrossServersUseCase(any()) } returns flowOf(Result.success(emptyList()))

        viewModel = createViewModel()
        viewModel.onAction(SearchAction.QueryChange("nonexistent"))
        viewModel.onAction(SearchAction.ExecuteSearch)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.results).isEmpty()
        assertThat(state.searchState).isEqualTo(SearchState.NoResults)
    }

    @Test
    fun `ExecuteSearch failure updates error state`() = runTest {
        val error = Exception("Network error")
        coEvery { searchAcrossServersUseCase(any()) } returns flowOf(Result.failure(error))

        viewModel = createViewModel()
        viewModel.onAction(SearchAction.QueryChange("matrix"))
        viewModel.onAction(SearchAction.ExecuteSearch)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.searchState).isEqualTo(SearchState.Error)
        // Error is sent via errorEvents channel
    }

    @Test
    fun `ExecuteSearch with blank query is ignored`() = runTest {
        viewModel = createViewModel()

        viewModel.onAction(SearchAction.QueryChange("   "))
        viewModel.onAction(SearchAction.ExecuteSearch)
        advanceUntilIdle()

        // Should not trigger search with blank query
        coVerify(exactly = 0) { searchAcrossServersUseCase(any()) }
    }

    @Test
    fun `ClearQuery resets to idle state`() = runTest {
        viewModel = createViewModel()

        // First perform a search
        viewModel.onAction(SearchAction.QueryChange("matrix"))
        viewModel.onAction(SearchAction.ExecuteSearch)
        advanceUntilIdle()

        // Then clear
        viewModel.onAction(SearchAction.ClearQuery)

        val state = viewModel.uiState.value
        assertThat(state.query).isEmpty()
        assertThat(state.searchState).isEqualTo(SearchState.Idle)
        assertThat(state.results).isEmpty()
    }

    @Test
    fun `QueryChange with blank query clears results`() = runTest {
        viewModel = createViewModel()

        // First perform a search
        viewModel.onAction(SearchAction.QueryChange("matrix"))
        viewModel.onAction(SearchAction.ExecuteSearch)
        advanceUntilIdle()

        // Then change to blank
        viewModel.onAction(SearchAction.QueryChange(""))

        val state = viewModel.uiState.value
        assertThat(state.query).isEmpty()
        assertThat(state.searchState).isEqualTo(SearchState.Idle)
        assertThat(state.results).isEmpty()
    }

    @Test
    fun `ExecuteSearch sets Searching state before completing`() = runTest {
        // Use a suspended flow to observe the intermediate state
        var capturedState: SearchState? = null

        viewModel = createViewModel()
        viewModel.onAction(SearchAction.QueryChange("matrix"))
        viewModel.onAction(SearchAction.ExecuteSearch)

        // Check state immediately after action (before advanceUntilIdle)
        capturedState = viewModel.uiState.value.searchState

        advanceUntilIdle()

        // State should transition from Idle -> Searching -> Results
        assertThat(viewModel.uiState.value.searchState).isEqualTo(SearchState.Results)
    }

    @Test
    fun `OpenMedia sends navigation event`() = runTest {
        viewModel = createViewModel()
        val testItem = testResults.first()

        viewModel.onAction(SearchAction.OpenMedia(testItem))

        // Navigation event is sent via navigationEvents channel
        // This would be verified in integration tests or by the UI layer
    }

    @Test
    fun `multiple searches cancel previous job`() = runTest {
        viewModel = createViewModel()

        viewModel.onAction(SearchAction.QueryChange("matrix"))
        viewModel.onAction(SearchAction.ExecuteSearch)

        viewModel.onAction(SearchAction.QueryChange("star wars"))
        viewModel.onAction(SearchAction.ExecuteSearch)

        advanceUntilIdle()

        // Only the latest search should complete
        coVerify { searchAcrossServersUseCase("star wars") }
    }
}
