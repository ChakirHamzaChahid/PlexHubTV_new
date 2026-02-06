package com.chakir.plexhubtv.core.navigation

sealed class Screen(val route: String) {
    // --- Auth Graph ---
    data object Login : Screen("login")
    data object PinInput : Screen("pin_input")
    data object Profiles : Screen("profiles")
    data object Loading : Screen("loading")

    // --- Main Graph ---
    data object Main : Screen("main")
    data object Home : Screen("home")
    data object Movies : Screen("movies")
    data object TVShows : Screen("tv_shows")
    
    data object Search : Screen("search")
    data object Downloads : Screen("downloads")
    data object Favorites : Screen("favorites")
    data object History : Screen("history")
    data object Settings : Screen("settings")
    data object ServerStatus : Screen("server_status")

    // --- Media Graph ---
    data object MediaDetail : Screen("media_detail/{ratingKey}?serverId={serverId}") {
        fun createRoute(ratingKey: String, serverId: String) =
            "media_detail/$ratingKey?serverId=$serverId"
    }

    data object SeasonDetail : Screen("season_detail/{ratingKey}?serverId={serverId}") {
        fun createRoute(ratingKey: String, serverId: String) =
            "season_detail/$ratingKey?serverId=$serverId"
    }

    data object VideoPlayer : Screen("video_player/{ratingKey}?serverId={serverId}&startOffset={startOffset}") {
        fun createRoute(ratingKey: String, serverId: String, startOffset: Long = 0L) =
            "video_player/$ratingKey?serverId=$serverId&startOffset=$startOffset"
    }

    companion object {
        const val ARG_RATING_KEY = "ratingKey"
        const val ARG_SERVER_ID = "serverId"
        const val ARG_START_OFFSET = "startOffset"
    }
}
