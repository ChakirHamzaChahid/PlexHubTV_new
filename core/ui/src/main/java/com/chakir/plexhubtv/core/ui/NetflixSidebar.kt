package com.chakir.plexhubtv.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.core.designsystem.NetflixBlack
import com.chakir.plexhubtv.core.designsystem.NetflixRed
import com.chakir.plexhubtv.core.navigation.NavigationItem

/**
 * Netflix-style vertical sidebar with logo and navigation items
 */
@Composable
fun NetflixSidebar(
    selectedItem: NavigationItem,
    onItemSelected: (NavigationItem) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sidebarDescription = stringResource(R.string.sidebar_navigation_description)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(80.dp)
            .testTag("sidebar_menu")
            .semantics { contentDescription = sidebarDescription }
            .background(NetflixBlack.copy(alpha = 0.95f))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo at top
        Icon(
            painter = painterResource(id = R.mipmap.ic_launcher),
            contentDescription = "App Logo",
            tint = NetflixRed,
            modifier = Modifier
                .size(48.dp)
                .padding(bottom = 32.dp)
        )

        // Navigation Items
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SidebarNavItem(
                item = NavigationItem.Home,
                isSelected = selectedItem == NavigationItem.Home,
                onSelected = { onItemSelected(NavigationItem.Home) }
            )
            SidebarNavItem(
                item = NavigationItem.TVShows,
                isSelected = selectedItem == NavigationItem.TVShows,
                onSelected = { onItemSelected(NavigationItem.TVShows) }
            )
            SidebarNavItem(
                item = NavigationItem.Movies,
                isSelected = selectedItem == NavigationItem.Movies,
                onSelected = { onItemSelected(NavigationItem.Movies) }
            )
            SidebarNavItem(
                item = NavigationItem.Favorites,
                isSelected = selectedItem == NavigationItem.Favorites,
                onSelected = { onItemSelected(NavigationItem.Favorites) }
            )
            SidebarNavItem(
                item = NavigationItem.History,
                isSelected = selectedItem == NavigationItem.History,
                onSelected = { onItemSelected(NavigationItem.History) }
            )
            SidebarNavItem(
                item = NavigationItem.Iptv,
                isSelected = selectedItem == NavigationItem.Iptv,
                onSelected = { onItemSelected(NavigationItem.Iptv) }
            )
        }

        // Bottom Actions: Search + Profile
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SidebarIconButton(
                icon = Icons.Default.Search,
                contentDescription = "Search",
                onClick = onSearchClick,
                testTag = "sidebar_search_button"
            )
            SidebarProfileAvatar(
                onClick = onProfileClick,
                testTag = "sidebar_profile_button"
            )
        }
    }
}

@Composable
private fun SidebarNavItem(
    item: NavigationItem,
    isSelected: Boolean,
    onSelected: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val textColor = when {
        isFocused && isSelected -> NetflixRed
        isFocused -> NetflixRed
        isSelected -> Color.White
        else -> Color.White.copy(alpha = 0.7f)
    }

    val fontWeight = when {
        isFocused || isSelected -> FontWeight.Bold
        else -> FontWeight.Normal
    }

    val scale = if (isFocused) 1.1f else 1f

    val navTag = when (item) {
        NavigationItem.Home -> "nav_item_home"
        NavigationItem.Movies -> "nav_item_movies"
        NavigationItem.TVShows -> "nav_item_tvshows"
        NavigationItem.Search -> "nav_item_search"
        NavigationItem.Downloads -> "nav_item_downloads"
        NavigationItem.Favorites -> "nav_item_favorites"
        NavigationItem.History -> "nav_item_history"
        NavigationItem.Settings -> "nav_item_settings"
        NavigationItem.Iptv -> "nav_item_iptv"
    }

    val labelText = stringResource(item.labelResId)
    Box(
        modifier = Modifier
            .scale(scale)
            .testTag(navTag)
            .semantics { contentDescription = labelText }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelected
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = labelText,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = fontWeight,
                color = textColor
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun SidebarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String = ""
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val iconColor = if (isFocused) NetflixRed else Color.White.copy(alpha = 0.7f)
    val scale = if (isFocused) 1.1f else 1f

    Box(
        modifier = Modifier
            .size(40.dp)
            .scale(scale)
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun SidebarProfileAvatar(
    onClick: () -> Unit,
    testTag: String = ""
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = if (isFocused) Color.White else Color.Transparent
    val scale = if (isFocused) 1.1f else 1f
    val profileDescription = stringResource(R.string.sidebar_profile_description)

    Box(
        modifier = Modifier
            .size(40.dp)
            .scale(scale)
            .testTag(testTag)
            .semantics { contentDescription = profileDescription }
            .clip(CircleShape)
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(Color(0xFFE50914), Color(0xFFB81D24))
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .then(
                if (isFocused) Modifier.border(2.dp, borderColor, CircleShape) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}
