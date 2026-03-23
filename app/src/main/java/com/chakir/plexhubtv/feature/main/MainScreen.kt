package com.chakir.plexhubtv.feature.main

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.chakir.plexhubtv.core.ui.rememberBackdropColors
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.zIndex
import com.chakir.plexhubtv.BuildConfig
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.designsystem.PlexHubTheme
import com.chakir.plexhubtv.core.navigation.NavigationItem
import com.chakir.plexhubtv.core.ui.NetflixTopBar
import com.chakir.plexhubtv.core.navigation.Screen
import com.chakir.plexhubtv.feature.downloads.DownloadsRoute
import com.chakir.plexhubtv.feature.home.HomeRoute
import com.chakir.plexhubtv.feature.hub.HubRoute
import com.chakir.plexhubtv.feature.library.LibraryRoute
import com.chakir.plexhubtv.feature.search.SearchRoute
import com.chakir.plexhubtv.feature.settings.SettingsCategory
import com.chakir.plexhubtv.feature.settings.SettingsRoute
import com.chakir.plexhubtv.feature.settings.categories.*

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
    onNavigateToProfiles: () -> Unit,
    onNavigateToPlexHomeSwitch: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToLibrarySelection: () -> Unit = {},
    onNavigateToJellyfinSetup: () -> Unit = {},
    onNavigateToXtreamSetup: () -> Unit = {},
    onNavigateToXtreamCategorySelection: (String) -> Unit = {},
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()

    // State for TopBar transparency and visibility
    var isTopBarScrolled by remember { mutableStateOf(false) }
    var isTopBarVisible by remember { mutableStateOf(true) }

    // TopBar focus management for Back button handling
    var requestTopBarFocus by remember { mutableStateOf(false) }
    var isTopBarFocused by remember { mutableStateOf(false) }

    // NAV-14: Focus requester so TopBar DPAD_DOWN can route focus into content area
    val contentFocusRequester = remember { FocusRequester() }

    // App-level backdrop image URL (driven by Home screen's focused item)
    var backdropImageUrl by remember { mutableStateOf<String?>(null) }

    // Determines the current selected item based on the route
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val selectedItem =
        remember(currentRoute) {
            when (currentRoute) {
                Screen.Home.route -> NavigationItem.Home
                Screen.Hub.route -> NavigationItem.Hub
                Screen.Movies.route -> NavigationItem.Movies
                Screen.TVShows.route -> NavigationItem.TVShows
                Screen.Favorites.route -> NavigationItem.Favorites
                Screen.Playlists.route -> NavigationItem.Playlists
                Screen.History.route -> NavigationItem.History
                Screen.Settings.route -> NavigationItem.Settings
                Screen.Search.route -> NavigationItem.Search
                Screen.Downloads.route -> NavigationItem.Downloads
                Screen.Iptv.route -> NavigationItem.Iptv
                else -> NavigationItem.Home // Default or None
            }
        }

    // Define main navigation screens (where TopBar is visible)
    val isMainNavigationScreen = currentRoute in listOf(
        Screen.Home.route,
        Screen.Hub.route,
        Screen.Movies.route,
        Screen.TVShows.route,
        Screen.Favorites.route,
        Screen.Playlists.route,
        Screen.History.route,
        Screen.Iptv.route
    )

    // Back: from content → focus TopBar (on the selected item); from TopBar → exit app
    // Enabled for all main navigation screens when TopBar doesn't have focus
    BackHandler(enabled = isMainNavigationScreen && !isTopBarFocused) {
        requestTopBarFocus = true
    }

    // Reset focus request flag after it's been processed
    LaunchedEffect(requestTopBarFocus) {
        if (requestTopBarFocus) {
            // Give TopBar time to request focus, then reset
            kotlinx.coroutines.delay(100)
            requestTopBarFocus = false
        }
    }

    // Clear backdrop when navigating away from Home
    LaunchedEffect(currentRoute) {
        if (currentRoute != Screen.Home.route) {
            backdropImageUrl = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // App-level backdrop (visible only on Home screen)
        val showBackdrop = currentRoute == Screen.Home.route && backdropImageUrl != null
        if (showBackdrop) {
            AppBackdrop(imageUrl = backdropImageUrl)
        }

        if (uiState.isOffline) {
            OfflineBanner()
        }

        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            enterTransition = { fadeIn(tween(300)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) },
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (currentRoute == Screen.Home.route)
                        Modifier // Transparent — backdrop behind
                    else
                        Modifier.background(MaterialTheme.colorScheme.background)
                )
                .focusRequester(contentFocusRequester),
        ) {
            composable(Screen.Home.route) {
                HomeRoute(
                    onNavigateToDetails = { ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) },
                    onNavigateToPlayer = { ratingKey, serverId -> onNavigateToPlayer(ratingKey, serverId) },
                    onNavigateUp = { requestTopBarFocus = true },
                    onBackdropChanged = { url -> backdropImageUrl = url },
                )
            }
            composable(Screen.Hub.route) {
                HubRoute(
                    onNavigateToDetails = { ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) },
                    onNavigateToPlayer = { ratingKey, serverId -> onNavigateToPlayer(ratingKey, serverId) },
                    onScrollStateChanged = { isScrolled -> isTopBarScrolled = isScrolled },
                )
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
                SearchRoute(
                    onNavigateToDetail = { ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) },
                )
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
                    onNavigateToDebug = { navController.navigate(Screen.Debug.route) },
                    onNavigateToPlexHomeSwitch = { onNavigateToPlexHomeSwitch() },
                    onNavigateToAppProfiles = { onNavigateToProfiles() },
                    onNavigateToLibrarySelection = { onNavigateToLibrarySelection() },
                    onNavigateToJellyfinSetup = { onNavigateToJellyfinSetup() },
                    onNavigateToXtreamSetup = { onNavigateToXtreamSetup() },
                    onNavigateToXtreamCategorySelection = { accountId -> onNavigateToXtreamCategorySelection(accountId) },
                    onNavigateToSubtitleStyle = { navController.navigate(Screen.SubtitleStyle.route) },
                    onNavigateToSettingsCategory = { category ->
                        navController.navigate(Screen.SettingsCategoryScreen.createRoute(category.name))
                    },
                )
            }
            composable(
                route = Screen.SettingsCategoryScreen.route,
                arguments = listOf(navArgument(Screen.SettingsCategoryScreen.ARG_CATEGORY) { defaultValue = "" }),
            ) { backStackEntry ->
                val categoryName = backStackEntry.arguments?.getString(Screen.SettingsCategoryScreen.ARG_CATEGORY) ?: ""
                val settingsViewModel: com.chakir.plexhubtv.feature.settings.SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                val settingsState by settingsViewModel.uiState.collectAsState()
                val isTvChannelsEnabled by settingsViewModel.isTvChannelsEnabled.collectAsState()
                val events = settingsViewModel.navigationEvents

                // Handle navigation events from sub-screens
                LaunchedEffect(events) {
                    events.collect { event ->
                        when (event) {
                            is com.chakir.plexhubtv.feature.settings.SettingsNavigationEvent.NavigateBack -> navController.popBackStack()
                            is com.chakir.plexhubtv.feature.settings.SettingsNavigationEvent.NavigateToLogin -> onLogout()
                            is com.chakir.plexhubtv.feature.settings.SettingsNavigationEvent.NavigateToServerStatus -> navController.navigate(Screen.ServerStatus.route)
                            is com.chakir.plexhubtv.feature.settings.SettingsNavigationEvent.NavigateToPlexHomeSwitch -> onNavigateToPlexHomeSwitch()
                            is com.chakir.plexhubtv.feature.settings.SettingsNavigationEvent.NavigateToAppProfiles -> onNavigateToProfiles()
                            is com.chakir.plexhubtv.feature.settings.SettingsNavigationEvent.NavigateToLibrarySelection -> onNavigateToLibrarySelection()
                            is com.chakir.plexhubtv.feature.settings.SettingsNavigationEvent.NavigateToJellyfinSetup -> onNavigateToJellyfinSetup()
                            is com.chakir.plexhubtv.feature.settings.SettingsNavigationEvent.NavigateToXtreamSetup -> onNavigateToXtreamSetup()
                            is com.chakir.plexhubtv.feature.settings.SettingsNavigationEvent.NavigateToXtreamCategorySelection -> onNavigateToXtreamCategorySelection(event.accountId)
                            is com.chakir.plexhubtv.feature.settings.SettingsNavigationEvent.NavigateToSubtitleStyle -> navController.navigate(Screen.SubtitleStyle.route)
                        }
                    }
                }

                when (SettingsCategory.fromRoute(categoryName)) {
                    SettingsCategory.General -> GeneralSettingsScreen(
                        state = settingsState,
                        onAction = settingsViewModel::onAction,
                        onNavigateBack = { navController.popBackStack() },
                    )
                    SettingsCategory.Playback -> PlaybackSettingsScreen(
                        state = settingsState,
                        onAction = settingsViewModel::onAction,
                        onNavigateBack = { navController.popBackStack() },
                    )
                    SettingsCategory.Server -> ServerSettingsScreen(
                        state = settingsState,
                        onAction = settingsViewModel::onAction,
                        onNavigateBack = { navController.popBackStack() },
                        isTvChannelsEnabled = isTvChannelsEnabled,
                        onTvChannelsEnabledChange = settingsViewModel::setTvChannelsEnabled,
                    )
                    SettingsCategory.DataSync -> DataSyncSettingsScreen(
                        state = settingsState,
                        onAction = settingsViewModel::onAction,
                        onNavigateBack = { navController.popBackStack() },
                    )
                    SettingsCategory.Services -> ServicesSettingsScreen(
                        state = settingsState,
                        onAction = settingsViewModel::onAction,
                        onNavigateBack = { navController.popBackStack() },
                    )
                    SettingsCategory.System -> SystemSettingsScreen(
                        state = settingsState,
                        onAction = settingsViewModel::onAction,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToDebug = { navController.navigate(Screen.Debug.route) },
                    )
                    else -> navController.popBackStack()
                }
            }
            composable(Screen.ServerStatus.route) {
                com.chakir.plexhubtv.feature.settings.serverstatus.ServerStatusRoute(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(Screen.SubtitleStyle.route) {
                com.chakir.plexhubtv.feature.settings.SubtitleStyleRoute(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            // Debug screen only available in DEBUG builds
            if (BuildConfig.DEBUG) {
                composable(Screen.Debug.route) {
                    com.chakir.plexhubtv.feature.debug.DebugRoute(
                        onNavigateBack = { navController.popBackStack() },
                    )
                }
            }
            composable(Screen.Favorites.route) {
                com.chakir.plexhubtv.feature.favorites.FavoritesRoute(
                    onNavigateToMedia = { ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) },
                )
            }
            composable(Screen.Playlists.route) {
                com.chakir.plexhubtv.feature.playlist.PlaylistListRoute(
                    onNavigateToPlaylist = { playlistId, serverId ->
                        navController.navigate(Screen.PlaylistDetail.createRoute(playlistId, serverId))
                    },
                )
            }
            composable(
                route = Screen.PlaylistDetail.route,
                arguments = listOf(
                    navArgument("playlistId") { defaultValue = "" },
                    navArgument("serverId") { defaultValue = "" },
                ),
            ) {
                com.chakir.plexhubtv.feature.playlist.PlaylistDetailRoute(
                    onNavigateToDetail = { ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) },
                    onNavigateBack = { navController.popBackStack() },
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

        // Netflix TopBar Overlay - Only visible on main navigation screens
        if (!uiState.isOffline && isMainNavigationScreen) {
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
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onProfileClick = { onNavigateToProfiles() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f),
                appLogoPainter = painterResource(id = R.drawable.tv_banner),
                requestFocusOnSelectedItem = requestTopBarFocus,
                onFocusChanged = { hasFocus -> isTopBarFocused = hasFocus },
                contentFocusRequester = contentFocusRequester,
            )
        }
    }
}

@Composable
private fun AppBackdrop(imageUrl: String?) {
    val backdropColors = rememberBackdropColors(imageUrl)
    val gradientBase = if (backdropColors.isDefault) Color.Black else backdropColors.secondary

    Box(modifier = Modifier.fillMaxSize()) {
        // Background image with crossfade
        Crossfade(
            targetState = imageUrl,
            animationSpec = tween(600),
            label = "app_backdrop"
        ) { url ->
            if (url != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .size(1280, 720)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.65f }
                )
            }
        }

        // Vertical gradient (transparent → dark at bottom)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            gradientBase.copy(alpha = 0.6f),
                            gradientBase,
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )
        // Horizontal gradient (dark left → transparent right)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            gradientBase.copy(alpha = 0.7f),
                            Color.Transparent,
                        ),
                        startX = 0f,
                        endX = 1600f
                    )
                )
        )
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
