package com.chakir.plexhubtv.feature.favorites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.chakir.plexhubtv.TestData
import org.junit.Rule
import org.junit.Test

class FavoritesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // --- Loading State ---

    @Test
    fun loadingState_showsLoadingSkeleton() {
        composeTestRule.setContent {
            FavoritesScreen(
                uiState = FavoritesUiState(isLoading = true),
                onMediaClick = {},
            )
        }
        composeTestRule.onNodeWithTag("screen_favorites").assertIsDisplayed()
        composeTestRule.onNodeWithTag("favorites_loading").assertIsDisplayed()
    }

    // --- Empty State (Media) ---

    @Test
    fun emptyMediaState_showsEmptyMessage() {
        composeTestRule.setContent {
            FavoritesScreen(
                uiState = FavoritesUiState(
                    isLoading = false,
                    category = FavoritesCategory.MEDIA,
                    favorites = emptyList(),
                ),
                onMediaClick = {},
            )
        }
        composeTestRule.onNodeWithTag("favorites_empty").assertIsDisplayed()
    }

    // --- Media Content ---

    @Test
    fun mediaContent_showsFavoriteItems() {
        val favorites = listOf(
            TestData.createMovie(id = "1", title = "Inception"),
            TestData.createMovie(id = "2", title = "Interstellar"),
        )

        composeTestRule.setContent {
            FavoritesScreen(
                uiState = FavoritesUiState(
                    isLoading = false,
                    category = FavoritesCategory.MEDIA,
                    favorites = favorites,
                ),
                onMediaClick = {},
            )
        }
        composeTestRule.onNodeWithTag("screen_favorites").assertIsDisplayed()
        // Title shows count
        composeTestRule.onNodeWithText("Favorites (2)", substring = true).assertIsDisplayed()
    }

    // --- Empty State (Actors) ---

    @Test
    fun emptyActorsState_showsActorsEmptyMessage() {
        composeTestRule.setContent {
            FavoritesScreen(
                uiState = FavoritesUiState(
                    isLoading = false,
                    category = FavoritesCategory.ACTORS,
                    favoriteActors = emptyList(),
                ),
                onMediaClick = {},
            )
        }
        composeTestRule.onNodeWithTag("favorites_actors_empty").assertIsDisplayed()
    }

    // --- Actor Content ---

    @Test
    fun actorContent_showsActorCards() {
        val actors = listOf(
            TestData.createActor(tmdbId = 1, name = "Tom Hanks"),
            TestData.createActor(tmdbId = 2, name = "Meryl Streep"),
        )

        composeTestRule.setContent {
            FavoritesScreen(
                uiState = FavoritesUiState(
                    isLoading = false,
                    category = FavoritesCategory.ACTORS,
                    favoriteActors = actors,
                ),
                onMediaClick = {},
            )
        }
        composeTestRule.onNodeWithText("Tom Hanks").assertIsDisplayed()
        composeTestRule.onNodeWithText("Meryl Streep").assertIsDisplayed()
    }

    // --- Screen Tag ---

    @Test
    fun screenTag_isPresent() {
        composeTestRule.setContent {
            FavoritesScreen(
                uiState = FavoritesUiState(isLoading = false, favorites = emptyList()),
                onMediaClick = {},
            )
        }
        composeTestRule.onNodeWithTag("screen_favorites").assertIsDisplayed()
    }
}
