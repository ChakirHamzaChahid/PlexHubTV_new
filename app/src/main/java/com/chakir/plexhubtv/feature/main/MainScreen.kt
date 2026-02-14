package com.chakir.plexhubtv.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.zIndex
import com.chakir.plexhubtv.di.designsystem.PlexHubTheme
import com.chakir.plexhubtv.core.navigation.NavigationItem
import com.chakir.plexhubtv.core.ui.NetflixTopBar
import com.chakir.plexhubtv.di.navigation.Screen
import com.chakir.plexhubtv.feature.downloads.DownloadsRoute
import com.chakir.plexhubtv.feature.home.HomeRoute
import com.chakir.plexhubtv.feature.library.LibraryRoute
import com.chakir.plexhubtv.feature.search.SearchRoute
import com.chakir.plexhubtv.feature.settings.SettingsRoute

/**
 * Écran principal contenant le NavHost et la Sidebar.
 * Gère la navigation globale et les redirections en mode hors-ligne.
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    onNavigateToPlayer: (String, String) -> Unit,
    onNavigateToDetails: (String, String) -> Unit,
    onPlayUrl: (String, String) -> Unit,
    onLogout: () -> Unit,
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

    // State for TopBar transparency and visibility
    var isTopBarScrolled by remember { mutableStateOf(false) }
    var isTopBarVisible by remember { mutableStateOf(true) }

    // TopBar focus management for Back button handling
    val topBarFocusRequester = remember { FocusRequester() }
    var isTopBarFocused by remember { mutableStateOf(false) }

    // Determines the current selected item based on the route
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val selectedItem =
        remember(currentRoute) {
            when (currentRoute) {
                Screen.Home.route -> NavigationItem.Home
                Screen.Movies.route -> NavigationItem.Movies
                Screen.TVShows.route -> NavigationItem.TVShows
                Screen.Favorites.route -> NavigationItem.Favorites
                Screen.History.route -> NavigationItem.History
                Screen.Settings.route -> NavigationItem.Settings
                Screen.Search.route -> NavigationItem.Search
                Screen.Downloads.route -> NavigationItem.Downloads
                Screen.Iptv.route -> NavigationItem.Iptv
                else -> NavigationItem.Home // Default or None
            }
        }

    // Back: from content → focus TopBar; from TopBar → exit app
    val isOnStartDestination = currentRoute == Screen.Home.route
    BackHandler(enabled = isOnStartDestination && !isTopBarFocused) {
        topBarFocusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isOffline) {
            OfflineBanner()
        }

        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            composable(Screen.Home.route) {
                if (uiState.isOffline) {
                    OfflinePlaceholder()
                } else {
                    HomeRoute(
                        onNavigateToDetails = { ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) },
                        onNavigateToPlayer = { ratingKey, serverId -> onNavigateToPlayer(ratingKey, serverId) },
                        onScrollStateChanged = { isScrolled -> isTopBarScrolled = isScrolled },
                    )
                }
            }
            composable(
                route = Screen.Movies.route,
                arguments = listOf(navArgument("mediaType") { defaultValue = "movie" }),
            ) {
                if (uiState.isOffline) {
                    OfflinePlaceholder()
                } else {
                    LibraryRoute(
                        onNavigateToMedia = { ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) },
                    )
                }
            }
            composable(
                route = Screen.TVShows.route,
                arguments = listOf(navArgument("mediaType") { defaultValue = "show" }),
            ) {
                if (uiState.isOffline) {
                    OfflinePlaceholder()
                } else {
                    LibraryRoute(
                        onNavigateToMedia = { ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) },
                    )
                }
            }
            composable(Screen.Search.route) {
                if (uiState.isOffline) {
                    OfflinePlaceholder()
                } else {
                    SearchRoute(
                        onNavigateToDetail = { ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) },
                    )
                }
            }
            composable(Screen.Downloads.route) {
                DownloadsRoute(
                    onNavigateToPlayer = { ratingKey, serverId -> onNavigateToPlayer(ratingKey, serverId) },
                )
            }
            composable(Screen.Settings.route) {
                SettingsRoute(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLogin = onLogout,
                    onNavigateToServerStatus = { navController.navigate(Screen.ServerStatus.route) },
                )
            }
            composable(Screen.ServerStatus.route) {
                com.chakir.plexhubtv.feature.settings.serverstatus.ServerStatusRoute(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(Screen.Favorites.route) {
                com.chakir.plexhubtv.feature.favorites.FavoritesRoute(
                    onNavigateToMedia = { ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) },
                )
            }
            composable(Screen.History.route) {
                com.chakir.plexhubtv.feature.history.HistoryRoute(
                    onNavigateToMedia = { ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) },
                )
            }
            composable(Screen.Iptv.route) {
                if (uiState.isOffline) {
                    OfflinePlaceholder()
                } else {
                    com.chakir.plexhubtv.feature.iptv.IptvRoute(
                        onPlayChannel = { url, title -> onPlayUrl(url, title) },
                    )
                }
            }
        }

        // Netflix TopBar Overlay
        if (!uiState.isOffline) {
            NetflixTopBar(
                selectedItem = selectedItem,
                isScrolled = isTopBarScrolled,
                isVisible = isTopBarVisible,
                onItemSelected = { item ->
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onSearchClick = { navController.navigate(Screen.Search.route) },
                onProfileClick = { navController.navigate(Screen.Settings.route) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f),
                focusRequester = topBarFocusRequester,
                onFocusChanged = { hasFocus -> isTopBarFocused = hasFocus },
            )
        }
    }
}

@Composable
fun OfflineBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Offline Mode",
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun OfflinePlaceholder() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text("Content unavailable offline.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// MainBottomBar removed


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
