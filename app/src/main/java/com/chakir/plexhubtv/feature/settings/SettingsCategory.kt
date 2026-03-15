package com.chakir.plexhubtv.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.graphics.vector.ImageVector
import com.chakir.plexhubtv.R

enum class SettingsCategory(
    val icon: ImageVector,
    val titleRes: Int,
    val subtitleRes: Int,
    val route: String,
) {
    General(
        icon = Icons.Default.Palette,
        titleRes = R.string.settings_category_general,
        subtitleRes = R.string.settings_category_general_subtitle,
        route = "settings_category/General",
    ),
    Playback(
        icon = Icons.Default.PlayArrow,
        titleRes = R.string.settings_category_playback,
        subtitleRes = R.string.settings_category_playback_subtitle,
        route = "settings_category/Playback",
    ),
    Server(
        icon = Icons.Default.Storage,
        titleRes = R.string.settings_category_server,
        subtitleRes = R.string.settings_category_server_subtitle,
        route = "settings_category/Server",
    ),
    DataSync(
        icon = Icons.Default.Sync,
        titleRes = R.string.settings_category_data_sync,
        subtitleRes = R.string.settings_category_data_sync_subtitle,
        route = "settings_category/DataSync",
    ),
    Services(
        icon = Icons.Default.Cloud,
        titleRes = R.string.settings_category_services,
        subtitleRes = R.string.settings_category_services_subtitle,
        route = "settings_category/Services",
    ),
    System(
        icon = Icons.Default.Settings,
        titleRes = R.string.settings_category_system,
        subtitleRes = R.string.settings_category_system_subtitle,
        route = "settings_category/System",
    ),
    Account(
        icon = Icons.Default.Logout,
        titleRes = R.string.settings_category_account,
        subtitleRes = R.string.settings_category_account_subtitle,
        route = "settings_category/Account",
    ),
    ;

    companion object {
        const val ARG_CATEGORY = "category"
        const val ROUTE_PATTERN = "settings/{category}"

        fun fromRoute(category: String): SettingsCategory? =
            entries.find { it.name.equals(category, ignoreCase = true) }
    }
}
