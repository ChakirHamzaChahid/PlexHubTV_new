package com.chakir.plexhubtv.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.tooling.preview.Preview
import com.chakir.plexhubtv.core.designsystem.PlexHubTheme
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chakir.plexhubtv.core.navigation.Screen
import com.chakir.plexhubtv.feature.downloads.DownloadsRoute
import com.chakir.plexhubtv.feature.home.HomeRoute
import com.chakir.plexhubtv.feature.library.LibraryRoute
import com.chakir.plexhubtv.feature.search.SearchRoute
import com.chakir.plexhubtv.feature.settings.SettingsRoute

@Composable
fun MainScreen(
    viewModel: MainViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    onNavigateToPlayer: (String, String) -> Unit,
    onNavigateToDetails: (String, String) -> Unit,
    onNavigateToProfiles: () -> Unit,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    
    // Auto-redirect to Downloads if offline and on online-only tab
    LaunchedEffect(uiState.isOffline) {
        if (uiState.isOffline) {
             val currentRoute = navController.currentDestination?.route
             if (currentRoute != Screen.Downloads.route && currentRoute != Screen.Settings.route) {
                 navController.navigate(Screen.Downloads.route) {
                     popUpTo(navController.graph.findStartDestination().id) {
                         saveState = true
                     }
                     launchSingleTop = true
                     restoreState = true
                 }
             }
        }
    }

    AppSidebar(
        navController = navController,
        isOffline = uiState.isOffline
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
             if (uiState.isOffline) {
                 OfflineBanner()
             }
             NavHost(
                 navController = navController,
                 startDestination = Screen.Home.route,
                 modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
             ) {
                composable(Screen.Home.route) {
                    if (uiState.isOffline) {
                        OfflinePlaceholder()
                    } else {
                        HomeRoute(
                            onNavigateToDetails = { ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) },
                            onNavigateToPlayer = { ratingKey, serverId -> onNavigateToPlayer(ratingKey, serverId) }
                        )
                    }
                }
                composable(
                    route = Screen.Movies.route,
                    arguments = listOf(navArgument("mediaType") { defaultValue = "movie" })
                ) {
                    if (uiState.isOffline) {
                        OfflinePlaceholder()
                    } else {
                        LibraryRoute(
                            onNavigateToDetail = { ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) }
                        )
                    }
                }
                composable(
                    route = Screen.TVShows.route,
                    arguments = listOf(navArgument("mediaType") { defaultValue = "show" })
                ) {
                    if (uiState.isOffline) {
                        OfflinePlaceholder()
                    } else {
                        LibraryRoute(
                            onNavigateToDetail = { ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) }
                        )
                    }
                }
                composable(Screen.Search.route) {
                    if (uiState.isOffline) {
                         OfflinePlaceholder()
                    } else {
                        SearchRoute(
                            onNavigateToDetail = { ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) }
                        )
                    }
                }
                composable(Screen.Downloads.route) {
                    DownloadsRoute(
                        onNavigateToPlayer = { ratingKey, serverId -> onNavigateToPlayer(ratingKey, serverId) }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsRoute(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToLogin = onLogout,
                        onNavigateToServerStatus = { navController.navigate(Screen.ServerStatus.route) },
                        onNavigateToProfiles = onNavigateToProfiles
                    )
                }
                composable(Screen.ServerStatus.route) {
                    com.chakir.plexhubtv.feature.settings.serverstatus.ServerStatusRoute(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Favorites.route) {
                    com.chakir.plexhubtv.feature.favorites.FavoritesRoute(
                        onNavigateToMedia = { ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) }
                    )
                }
                composable(Screen.History.route) {
                    com.chakir.plexhubtv.feature.history.HistoryRoute(
                        onNavigateToMedia = { ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) }
                    )
                }
            }
        }
    }
}

@Composable
fun OfflineBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Offline Mode",
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun OfflinePlaceholder() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
         Text("Content unavailable offline.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// MainBottomBar removed


sealed class NavigationItem(val screen: Screen, val label: String, val icon: ImageVector) {
    open val route: String = screen.route
    data object Home : NavigationItem(Screen.Home, "Home", Icons.Default.Home)
    data object Movies : NavigationItem(Screen.Movies, "Movies", Icons.Default.Movie)
    data object TVShows : NavigationItem(Screen.TVShows, "TV Shows", Icons.Default.Tv)
    data object Search : NavigationItem(Screen.Search, "Search", Icons.Default.Search)
    data object Downloads : NavigationItem(Screen.Downloads, "Downloads", Icons.Rounded.Download)
    data object Favorites : NavigationItem(Screen.Favorites, "Favorites", Icons.Filled.Favorite)
    data object History : NavigationItem(Screen.History, "History", Icons.Filled.History)
    data object Settings : NavigationItem(Screen.Settings, "Settings", Icons.Default.Settings)
}
@Preview(showBackground = true)
@Composable
fun PreviewMainScreen() {
    PlexHubTheme {
        // MainScreen requires a NavController and several other things.
        // For preview, we might want to preview just the Sidebar or specific parts.
        // But the user asked for Screen previews.
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
             Text("Main Screen Preview - Requires NavController", modifier = Modifier.align(Alignment.Center))
        }
    }
}
