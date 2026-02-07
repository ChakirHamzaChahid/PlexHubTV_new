package com.chakir.plexhubtv.feature.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Download
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.tv.material3.*
import com.chakir.plexhubtv.core.navigation.Screen

/**
 * Barre de navigation latérale (Drawer) pour TV.
 * S'adapte à l'état de connexion (Online/Offline).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppSidebar(
    navController: NavController,
    isOffline: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val items = listOf(
        NavigationItem.Home,
        NavigationItem.Movies,
        NavigationItem.TVShows,
        NavigationItem.Favorites,
        NavigationItem.History,
        NavigationItem.Downloads,
        NavigationItem.Iptv,
        NavigationItem.Settings
    )

    // TV Navigation Drawer
    NavigationDrawer(
        drawerContent = { drawerValue ->
            val isExpanded = drawerValue == DrawerValue.Open
            
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                items.forEach { item ->
                    val selected = currentRoute == item.route
                    val enabled = !isOffline || (item == NavigationItem.Downloads || item == NavigationItem.Settings || item == NavigationItem.Favorites || item == NavigationItem.History)

                    NavigationDrawerItem(
                        selected = selected,
                        onClick = {
                            if (enabled) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        content = {
                           if (isExpanded) {
                               Text(
                                   text = item.label,
                                   modifier = Modifier.padding(start = 12.dp)
                               )
                           }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContentColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            focusedContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            pressedContentColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        ),
                        enabled = enabled,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        },
        modifier = modifier
    ) {
        content()
    }
}
