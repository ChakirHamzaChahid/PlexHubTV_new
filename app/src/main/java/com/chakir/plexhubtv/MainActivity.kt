package com.chakir.plexhubtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chakir.plexhubtv.di.navigation.Screen
import com.chakir.plexhubtv.feature.auth.AuthRoute
import com.chakir.plexhubtv.feature.auth.components.SessionExpiredDialog
import com.chakir.plexhubtv.feature.auth.profiles.ProfileRoute
import com.chakir.plexhubtv.feature.details.MediaDetailRoute
import com.chakir.plexhubtv.feature.details.SeasonDetailRoute
import com.chakir.plexhubtv.feature.player.VideoPlayerRoute
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Activité principale de PlexHubTV.
 * Point d'entrée de l'application, configure le thème et le système de navigation.
 * Utilise Jetpack Compose pour l'UI et Hilt pour l'injection de dépendances.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @javax.inject.Inject
    lateinit var settingsDataStore: com.chakir.plexhubtv.core.datastore.SettingsDataStore

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("APP STARTUP: Launching PlexHubTV")

        // Play Intro Sound - DISABLED (video intro has sound instead)
        // try {
        //     val mediaPlayer = android.media.MediaPlayer.create(this, R.raw.intro_sound)
        //     mediaPlayer.setOnCompletionListener { it.release() }
        //     mediaPlayer.start()
        // } catch (e: Exception) {
        //     Timber.e("Failed to play intro: ${e.message}")
        // }

        enableEdgeToEdge()
        setContent {
            val appThemeState = settingsDataStore.appTheme.collectAsState(initial = "Plex")

            com.chakir.plexhubtv.core.designsystem.PlexHubTheme(
                appTheme = appThemeState.value,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PlexHubApp(mainViewModel = mainViewModel)
                }
            }
        }
    }
}

/**
 * Composable racine de l'application PlexHub.
 * Configure le NavHost et définit le graphe de navigation entre les écrans :
 * - Login/Auth
 * - Profiles (Plex Home)
 * - Main (Conteneur avec BottomBar)
 * - MediaDetail, SeasonDetail
 * - VideoPlayer (avec DeepLink support)
 */
@Composable
fun PlexHubApp(mainViewModel: MainViewModel) {
    val navController = rememberNavController()
    val showSessionExpiredDialog by mainViewModel.showSessionExpiredDialog.collectAsState()

    // Show session expired dialog if token invalidated
    if (showSessionExpiredDialog) {
        SessionExpiredDialog(
            onDismiss = {
                mainViewModel.onSessionExpiredDialogDismissed {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        )
    }

    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        // Splash Screen (Netflix-style auto-login check)
        composable(Screen.Splash.route) {
            com.chakir.plexhubtv.feature.splash.SplashRoute(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLoading = {
                    navController.navigate(Screen.Loading.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Login.route) {
            AuthRoute(
                onAuthSuccess = {
                    // Redirect to Loading to wait for Sync
                    navController.navigate(Screen.Loading.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Loading.route) {
            com.chakir.plexhubtv.feature.loading.LoadingRoute(
                onNavigateToMain = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Loading.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Profiles.route) {
            ProfileRoute(
                onSwitchSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Profiles.route) { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(Screen.Main.route) {
            com.chakir.plexhubtv.feature.main.MainScreen(
                onNavigateToDetails = { ratingKey, serverId ->
                    navController.navigate(Screen.MediaDetail.createRoute(ratingKey, serverId))
                },
                onNavigateToPlayer = { ratingKey, serverId ->
                    navController.navigate(Screen.VideoPlayer.createRoute(ratingKey, serverId))
                },
                onPlayUrl = { url, title ->
                    navController.navigate(Screen.VideoPlayer.createRoute("iptv", "iptv", 0L, url, title))
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // Media Details
        composable(
            route = Screen.MediaDetail.route,
            arguments =
                listOf(
                    navArgument(Screen.ARG_RATING_KEY) { type = NavType.StringType },
                    navArgument(Screen.ARG_SERVER_ID) { type = NavType.StringType },
                ),
        ) {
            MediaDetailRoute(
                onNavigateToPlayer = { ratingKey, serverId ->
                    navController.navigate(Screen.VideoPlayer.createRoute(ratingKey, serverId))
                },
                onNavigateToDetail = { ratingKey, serverId ->
                    navController.navigate(Screen.MediaDetail.createRoute(ratingKey, serverId))
                },
                onNavigateToSeason = { ratingKey, serverId ->
                    navController.navigate(Screen.SeasonDetail.createRoute(ratingKey, serverId))
                },
                onNavigateToCollection = { collectionId, serverId ->
                    navController.navigate(Screen.CollectionDetail.createRoute(collectionId, serverId))
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        // Season Details
        composable(
            route = Screen.SeasonDetail.route,
            arguments =
                listOf(
                    navArgument(Screen.ARG_RATING_KEY) { type = NavType.StringType },
                    navArgument(Screen.ARG_SERVER_ID) { type = NavType.StringType },
                ),
        ) {
            SeasonDetailRoute(
                onNavigateToPlayer = { ratingKey, serverId, startOffset ->
                    navController.navigate(Screen.VideoPlayer.createRoute(ratingKey, serverId, startOffset))
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        // Collection Details
        composable(
            route = Screen.CollectionDetail.route,
            arguments =
                listOf(
                    navArgument("collectionId") { type = NavType.StringType },
                    navArgument(Screen.ARG_SERVER_ID) { type = NavType.StringType },
                ),
        ) {
            com.chakir.plexhubtv.feature.collection.CollectionDetailRoute(
                onNavigateToDetail = { ratingKey, serverId ->
                    navController.navigate(Screen.MediaDetail.createRoute(ratingKey, serverId))
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        // VideoPlayer
        composable(
            route = Screen.VideoPlayer.route,
            arguments =
                listOf(
                    navArgument(Screen.ARG_RATING_KEY) { type = NavType.StringType },
                    navArgument(Screen.ARG_SERVER_ID) { type = NavType.StringType },
                    navArgument(Screen.ARG_START_OFFSET) {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument(Screen.ARG_URL) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(Screen.ARG_TITLE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            deepLinks =
                listOf(
                    androidx.navigation.navDeepLink { uriPattern = "plexhub://play/{ratingKey}?serverId={serverId}" },
                ),
        ) {
            VideoPlayerRoute(
                onClose = {
                    navController.popBackStack()
                },
            )
        }
    }
}
