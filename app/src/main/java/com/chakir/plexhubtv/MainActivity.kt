package com.chakir.plexhubtv

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chakir.plexhubtv.core.navigation.Screen
import com.chakir.plexhubtv.feature.auth.AuthRoute
import com.chakir.plexhubtv.feature.details.MediaDetailRoute
import com.chakir.plexhubtv.feature.details.SeasonDetailRoute
import com.chakir.plexhubtv.feature.downloads.DownloadsRoute
import com.chakir.plexhubtv.feature.home.HomeRoute
import com.chakir.plexhubtv.feature.library.LibraryRoute
import com.chakir.plexhubtv.feature.player.VideoPlayerRoute
import com.chakir.plexhubtv.feature.search.SearchRoute
import com.chakir.plexhubtv.feature.settings.SettingsRoute
import com.chakir.plexhubtv.feature.auth.profiles.ProfileRoute
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject
    lateinit var settingsDataStore: com.chakir.plexhubtv.core.datastore.SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appThemeState = settingsDataStore.appTheme.collectAsState(initial = "Plex")
            
            com.chakir.plexhubtv.core.designsystem.PlexHubTheme(
                appTheme = appThemeState.value
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PlexHubApp()
                }
            }
        }
    }
}

@Composable
fun PlexHubApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Login.route) {
        composable(Screen.Login.route) {
            AuthRoute(
                onAuthSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
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
                }
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
                onNavigateToProfiles = {
                    navController.navigate(Screen.Profiles.route)
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        // Media Details
        composable(
            route = Screen.MediaDetail.route,
            arguments = listOf(
                navArgument(Screen.ARG_RATING_KEY) { type = NavType.StringType },
                navArgument(Screen.ARG_SERVER_ID) { type = NavType.StringType }
            )
        ) {
            MediaDetailRoute(
                onNavigateToPlayer = { ratingKey, serverId ->
                    navController.navigate(Screen.VideoPlayer.createRoute(ratingKey, serverId))
                },
                onNavigateToSeason = { ratingKey, serverId ->
                    navController.navigate(Screen.SeasonDetail.createRoute(ratingKey, serverId))
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Season Details
        composable(
            route = Screen.SeasonDetail.route,
            arguments = listOf(
                navArgument(Screen.ARG_RATING_KEY) { type = NavType.StringType },
                navArgument(Screen.ARG_SERVER_ID) { type = NavType.StringType }
            )
        ) {
            SeasonDetailRoute(
                onNavigateToPlayer = { ratingKey, serverId ->
                    navController.navigate(Screen.VideoPlayer.createRoute(ratingKey, serverId))
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Video Player
        composable(
            route = Screen.VideoPlayer.route,
            arguments = listOf(
                navArgument(Screen.ARG_RATING_KEY) { type = NavType.StringType },
                navArgument(Screen.ARG_SERVER_ID) { type = NavType.StringType },
                navArgument(Screen.ARG_START_OFFSET) { 
                    type = NavType.LongType 
                    defaultValue = 0L
                }
            )
        ) {
            VideoPlayerRoute(
                onClose = {
                    navController.popBackStack()
                }
            )
        }
    }
}