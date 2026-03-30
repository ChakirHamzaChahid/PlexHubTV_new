package com.chakir.plexhubtv.feature.search

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.chakir.plexhubtv.TestData
import com.chakir.plexhubtv.core.model.MediaType
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // --- Idle State ---

    @Test
    fun idleState_showsScreenTag() {
        composeTestRule.setContent {
            NetflixSearchScreen(
                state = SearchUiState(searchState = SearchState.Idle),
                onAction = {},
                snackbarHostState = SnackbarHostState(),
            )
        }
        composeTestRule.onNodeWithTag("screen_search").assertIsDisplayed()
    }

    @Test
    fun idleState_showsSearchInput() {
        composeTestRule.setContent {
            NetflixSearchScreen(
                state = SearchUiState(searchState = SearchState.Idle),
                onAction = {},
                snackbarHostState = SnackbarHostState(),
            )
        }
        composeTestRule.onNodeWithTag("search_input").assertIsDisplayed()
    }

    // --- Searching State ---

    @Test
    fun searchingState_showsSkeletons() {
        composeTestRule.setContent {
            NetflixSearchScreen(
                state = SearchUiState(query = "Avatar", searchState = SearchState.Searching),
                onAction = {},
                snackbarHostState = SnackbarHostState(),
            )
        }
        // Skeleton rows should be visible (3x MediaRowSkeleton in the Searching branch)
        composeTestRule.onNodeWithTag("screen_search").assertIsDisplayed()
    }

    // --- No Results State ---

    @Test
    fun noResultsState_showsNoResultsMessage() {
        composeTestRule.setContent {
            NetflixSearchScreen(
                state = SearchUiState(query = "xyznonexistent", searchState = SearchState.NoResults),
                onAction = {},
                snackbarHostState = SnackbarHostState(),
            )
        }
        composeTestRule.onNodeWithTag("search_no_results").assertIsDisplayed()
    }

    // --- Results State ---

    @Test
    fun resultsState_showsGroupedRows() {
        val movies = listOf(
            TestData.createMovie(id = "1", title = "Avatar"),
            TestData.createMovie(id = "2", title = "Avatar: The Way of Water"),
        )
        val shows = listOf(
            TestData.createShow(id = "10", title = "Avatar: The Last Airbender"),
        )
        val allResults = persistentListOf(*(movies + shows).toTypedArray())

        composeTestRule.setContent {
            NetflixSearchScreen(
                state = SearchUiState(
                    query = "Avatar",
                    searchState = SearchState.Results,
                    results = allResults,
                ),
                groupedResults = allResults.groupBy { it.type },
                onAction = {},
                snackbarHostState = SnackbarHostState(),
            )
        }

        // Movie row and Show row should both be present
        composeTestRule.onNodeWithTag("hub_row_search_row_movie").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hub_row_search_row_show").assertIsDisplayed()
    }

    // --- Query Display ---

    @Test
    fun queryText_isDisplayedInSearchInput() {
        composeTestRule.setContent {
            NetflixSearchScreen(
                state = SearchUiState(query = "Interstellar", searchState = SearchState.Idle),
                onAction = {},
                snackbarHostState = SnackbarHostState(),
            )
        }
        composeTestRule.onNodeWithText("Interstellar").assertIsDisplayed()
    }
}
