package com.chakir.plexhubtv.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.rounded.Download
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavigationItem(val screen: Screen, @StringRes val labelResId: Int, val icon: ImageVector) {
    open val route: String = screen.route

    data object Home : NavigationItem(Screen.Home, com.chakir.plexhubtv.core.navigation.R.string.nav_home, Icons.Default.Home)
    data object Hub : NavigationItem(Screen.Hub, com.chakir.plexhubtv.core.navigation.R.string.nav_hub, Icons.Default.Dashboard)
    data object Movies : NavigationItem(Screen.Movies, com.chakir.plexhubtv.core.navigation.R.string.nav_movies, Icons.Default.Movie)
    data object TVShows : NavigationItem(Screen.TVShows, com.chakir.plexhubtv.core.navigation.R.string.nav_tv_shows, Icons.Default.Tv)
    data object Search : NavigationItem(Screen.Search, com.chakir.plexhubtv.core.navigation.R.string.nav_search, Icons.Default.Search)
    data object Downloads : NavigationItem(Screen.Downloads, com.chakir.plexhubtv.core.navigation.R.string.nav_downloads, Icons.Rounded.Download)
    data object Favorites : NavigationItem(Screen.Favorites, com.chakir.plexhubtv.core.navigation.R.string.nav_favorites, Icons.Filled.Favorite)
    data object History : NavigationItem(Screen.History, com.chakir.plexhubtv.core.navigation.R.string.nav_history, Icons.Filled.History)
    data object Settings : NavigationItem(Screen.Settings, com.chakir.plexhubtv.core.navigation.R.string.nav_settings, Icons.Default.Settings)
    data object Iptv : NavigationItem(Screen.Iptv, com.chakir.plexhubtv.core.navigation.R.string.nav_live_tv, Icons.Default.Tv)
}
