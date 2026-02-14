package com.chakir.plexhubtv.core.ui

import com.chakir.plexhubtv.core.navigation.NavigationItem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.core.designsystem.NetflixRed

@Composable
fun NetflixTopBar(
    selectedItem: NavigationItem,
    isScrolled: Boolean,
    isVisible: Boolean,
    onItemSelected: (NavigationItem) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: (Boolean) -> Unit = {},
) {
    // TopBar always has opaque background (does not merge with home screen)
    val backgroundColor = Color.Black.copy(alpha = 0.95f)

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(300)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(backgroundColor)
                .padding(horizontal = 48.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChanged(it.hasFocus) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo
            Icon(
                painter = painterResource(id = R.mipmap.ic_launcher), // Placeholder or actual logo
                contentDescription = "App Logo",
                tint = NetflixRed,
                modifier = Modifier
                    .size(32.dp)
                    .padding(end = 24.dp)
            )

            // Navigation Items
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.weight(1f)
            ) {
                NetflixNavItem(
                    item = NavigationItem.Home,
                    isSelected = selectedItem == NavigationItem.Home,
                    onSelected = { onItemSelected(NavigationItem.Home) }
                )
                NetflixNavItem(
                    item = NavigationItem.TVShows,
                    isSelected = selectedItem == NavigationItem.TVShows,
                    onSelected = { onItemSelected(NavigationItem.TVShows) }
                )
                NetflixNavItem(
                    item = NavigationItem.Movies,
                    isSelected = selectedItem == NavigationItem.Movies,
                    onSelected = { onItemSelected(NavigationItem.Movies) }
                )
                NetflixNavItem(
                    item = NavigationItem.Favorites, // Will be renamed "My List"
                    isSelected = selectedItem == NavigationItem.Favorites,
                    onSelected = { onItemSelected(NavigationItem.Favorites) }
                )
                NetflixNavItem(
                    item = NavigationItem.History,
                    isSelected = selectedItem == NavigationItem.History,
                    onSelected = { onItemSelected(NavigationItem.History) }
                )
                NetflixNavItem(
                    item = NavigationItem.Iptv,
                    isSelected = selectedItem == NavigationItem.Iptv,
                    onSelected = { onItemSelected(NavigationItem.Iptv) }
                )
            }

            // Right Actions: Search + Profile
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                NetflixSearchIcon(
                    onClick = onSearchClick
                )
                NetflixProfileAvatar(
                    onClick = onProfileClick
                )
            }
        }
    }
}

@Composable
private fun NetflixNavItem(
    item: NavigationItem,
    isSelected: Boolean,
    onSelected: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Different colors for selected vs focused vs normal
    val textColor = when {
        isFocused && isSelected -> NetflixRed // Both focused and selected: Red (for clarity)
        isFocused -> NetflixRed // Focused during navigation: Red
        isSelected -> Color.White // Selected (active screen): White
        else -> Color.White.copy(alpha = 0.7f) // Not selected/focused: Transparent white
    }

    val fontWeight = when {
        isFocused || isSelected -> FontWeight.Bold
        else -> FontWeight.Normal
    }

    Box(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelected
            )
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = fontWeight,
                color = textColor
            )
        )
    }
}

@Composable
private fun NetflixSearchIcon(
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val iconColor = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun NetflixProfileAvatar(
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Premium Netflix-style profile avatar with "P" like Netflix "N"
    val borderColor = if (isFocused) Color.White else Color.Transparent
    val scale = if (isFocused) 1.1f else 1f

    Box(
        modifier = Modifier
            .size(32.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(Color(0xFFE50914), Color(0xFFB81D24))
                )
            ) // Netflix Red Gradient
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
        Text(
            text = "P",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            ),
            modifier = Modifier.padding(bottom = 2.dp) // Slight adjustment for vertical centering
        )
    }
}
