package com.chakir.plexhubtv.di.navigation

sealed class Screen(val route: String) {
    // --- Auth Graph ---
    data object Splash : Screen("splash")

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

    data object Debug : Screen("debug")

    data object ServerStatus : Screen("server_status")

    // --- Media Graph ---
    data object MediaDetail : Screen("media_detail/{ratingKey}?serverId={serverId}") {
        fun createRoute(
            ratingKey: String,
            serverId: String,
        ) = "media_detail/$ratingKey?serverId=$serverId"
    }

    data object SeasonDetail : Screen("season_detail/{ratingKey}?serverId={serverId}") {
        fun createRoute(
            ratingKey: String,
            serverId: String,
        ) = "season_detail/$ratingKey?serverId=$serverId"
    }

    data object CollectionDetail : Screen("collection_detail/{collectionId}?serverId={serverId}") {
        fun createRoute(
            collectionId: String,
            serverId: String,
        ) = "collection_detail/$collectionId?serverId=$serverId"
    }

    data object Iptv : Screen("iptv")

    data object VideoPlayer : Screen("video_player/{ratingKey}?serverId={serverId}&startOffset={startOffset}&url={url}&title={title}") {
        fun createRoute(
            ratingKey: String,
            serverId: String,
            startOffset: Long = 0L,
            url: String? = null,
            title: String? = null,
        ): String {
            val builder = StringBuilder("video_player/$ratingKey?serverId=$serverId&startOffset=$startOffset")
            if (url != null) builder.append("&url=${java.net.URLEncoder.encode(url, "UTF-8")}")
            if (title != null) builder.append("&title=${java.net.URLEncoder.encode(title, "UTF-8")}")
            return builder.toString()
        }
    }

    companion object {
        const val ARG_RATING_KEY = "ratingKey"
        const val ARG_SERVER_ID = "serverId"
        const val ARG_START_OFFSET = "startOffset"
        const val ARG_URL = "url"
        const val ARG_TITLE = "title"
    }
}
