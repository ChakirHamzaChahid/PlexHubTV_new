package com.chakir.plexhubtv.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.chakir.plexhubtv.TestData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // --- Screen Tag ---

    @Test
    fun screenTag_isPresent() {
        composeTestRule.setContent {
            NetflixHomeContent(
                focusedItem = null,
                hubs = persistentListOf(),
                favorites = persistentListOf(),
                suggestions = persistentListOf(),
                onDeck = persistentListOf(),
                onAction = {},
            )
        }
        composeTestRule.onNodeWithTag("screen_home").assertIsDisplayed()
    }

    // --- Continue Watching Row ---

    @Test
    fun continueWatchingRow_showsWhenOnDeckNotEmpty() {
        val episode = TestData.createEpisode()
        composeTestRule.setContent {
            NetflixHomeContent(
                focusedItem = null,
                hubs = persistentListOf(),
                favorites = persistentListOf(),
                suggestions = persistentListOf(),
                onDeck = persistentListOf(episode),
                onAction = {},
                showContinueWatching = true,
            )
        }
        composeTestRule.onNodeWithTag("hub_row_home_on_deck").assertIsDisplayed()
    }

    // --- My List Row ---

    @Test
    fun myListRow_showsWhenFavoritesNotEmpty() {
        val movie = TestData.createMovie()
        composeTestRule.setContent {
            NetflixHomeContent(
                focusedItem = null,
                hubs = persistentListOf(),
                favorites = persistentListOf(movie),
                suggestions = persistentListOf(),
                onDeck = persistentListOf(),
                onAction = {},
                showMyList = true,
            )
        }
        composeTestRule.onNodeWithTag("hub_row_home_my_list").assertIsDisplayed()
    }

    // --- Suggestions Row ---

    @Test
    fun suggestionsRow_showsWhenSuggestionsNotEmpty() {
        val movie = TestData.createMovie(id = "5", title = "Suggested Film")
        composeTestRule.setContent {
            NetflixHomeContent(
                focusedItem = null,
                hubs = persistentListOf(),
                favorites = persistentListOf(),
                suggestions = persistentListOf(movie),
                onDeck = persistentListOf(),
                onAction = {},
                showSuggestions = true,
            )
        }
        composeTestRule.onNodeWithTag("hub_row_home_suggestions").assertIsDisplayed()
    }

    // --- Hub Rows ---

    @Test
    fun hubRow_displaysWithCorrectTag() {
        val hub = TestData.createHub(
            key = "hub.trending",
            title = "Trending Now",
            items = listOf(TestData.createMovie()),
        )
        composeTestRule.setContent {
            NetflixHomeContent(
                focusedItem = null,
                hubs = persistentListOf(hub),
                favorites = persistentListOf(),
                suggestions = persistentListOf(),
                onDeck = persistentListOf(),
                onAction = {},
            )
        }
        composeTestRule.onNodeWithTag("hub_row_home_hub_hub.trending").assertIsDisplayed()
    }

    // --- Empty State ---

    @Test
    fun emptyState_showsScreenWithNoRows() {
        composeTestRule.setContent {
            NetflixHomeContent(
                focusedItem = null,
                hubs = persistentListOf(),
                favorites = persistentListOf(),
                suggestions = persistentListOf(),
                onDeck = persistentListOf(),
                onAction = {},
            )
        }
        composeTestRule.onNodeWithTag("screen_home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hub_row_home_on_deck").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hub_row_home_my_list").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hub_row_home_suggestions").assertDoesNotExist()
    }
}
