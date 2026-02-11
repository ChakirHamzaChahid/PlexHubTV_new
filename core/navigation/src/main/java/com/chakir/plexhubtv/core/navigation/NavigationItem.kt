package com.chakir.plexhubtv.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.rounded.Download
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavigationItem(val screen: Screen, val label: String, val icon: ImageVector) {
    open val route: String = screen.route

    data object Home : NavigationItem(Screen.Home, "Home", Icons.Default.Home)
    data object Movies : NavigationItem(Screen.Movies, "Movies", Icons.Default.Movie)
    data object TVShows : NavigationItem(Screen.TVShows, "TV Shows", Icons.Default.Tv)
    data object Search : NavigationItem(Screen.Search, "Search", Icons.Default.Search)
    data object Downloads : NavigationItem(Screen.Downloads, "Downloads", Icons.Rounded.Download)
    data object Favorites : NavigationItem(Screen.Favorites, "My List", Icons.Filled.Favorite)
    data object History : NavigationItem(Screen.History, "History", Icons.Filled.History)
    data object Settings : NavigationItem(Screen.Settings, "Settings", Icons.Default.Settings)
    data object Iptv : NavigationItem(Screen.Iptv, "Live TV", Icons.Default.Tv)
}
