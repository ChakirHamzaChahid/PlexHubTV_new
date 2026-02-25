package com.chakir.plexhubtv.core.navigation

/**
 * Navigation routes for core navigation module.
 * Note: Full route definitions are in app/di/navigation/Screen.kt
 * This minimal version exists only to satisfy NavigationItem dependencies.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Hub : Screen("hub")
    data object Movies : Screen("movies")
    data object TVShows : Screen("tv_shows")
    data object Search : Screen("search")
    data object Downloads : Screen("downloads")
    data object Favorites : Screen("favorites")
    data object History : Screen("history")
    data object Settings : Screen("settings")
    data object Iptv : Screen("iptv")
}
