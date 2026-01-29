package com.chakir.plexhubtv.core.util

import androidx.navigation.NavController
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.model.MediaType

enum class MediaNavigationResult {
    NAVIGATED,
    LIST_REFRESH_NEEDED,
    UNSUPPORTED
}

/**
 * Navigates to the appropriate screen based on media item type
 */
fun navigateToMediaItem(
    navController: NavController,
    item: MediaItem,
    playDirectly: Boolean = false
): MediaNavigationResult {
    when (item.type) {
        MediaType.Episode -> {
            // Navigate to video player
            navController.navigate("player/${item.ratingKey}/${item.serverId}")
            return MediaNavigationResult.NAVIGATED
        }
        
        MediaType.Movie -> {
            if (playDirectly) {
                // Direct playback from continue watching
                navController.navigate("player/${item.ratingKey}/${item.serverId}")
            } else {
                // Show detail screen first
                navController.navigate("details/${item.ratingKey}/${item.serverId}")
            }
            return MediaNavigationResult.NAVIGATED
        }
        
        MediaType.Season -> {
            // Navigate to season detail (episode list)
            navController.navigate("season/${item.ratingKey}/${item.serverId}")
            return MediaNavigationResult.NAVIGATED
        }
        
        MediaType.Show -> {
            // Navigate to show detail (seasons list)
            navController.navigate("details/${item.ratingKey}/${item.serverId}")
            return MediaNavigationResult.NAVIGATED
        }
        
        MediaType.Collection -> {
            // Navigate to collection detail
            navController.navigate("collection/${item.ratingKey}/${item.serverId}")
            return MediaNavigationResult.NAVIGATED
        }
        
        else -> {
            // Unsupported type (e.g., music)
            return MediaNavigationResult.UNSUPPORTED
        }
    }
}
