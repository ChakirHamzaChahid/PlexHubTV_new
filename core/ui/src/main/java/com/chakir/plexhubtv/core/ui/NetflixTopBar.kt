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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.core.designsystem.NetflixRed
import com.chakir.plexhubtv.core.ui.R
import androidx.compose.ui.ExperimentalComposeUiApi

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NetflixTopBar(
    selectedItem: NavigationItem,
    isScrolled: Boolean,
    isVisible: Boolean,
    onItemSelected: (NavigationItem) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    appLogoPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    requestFocusOnSelectedItem: Boolean = false,
    contentFocusRequester: FocusRequester? = null, // UX23: Focus target for DOWN navigation
    onFocusChanged: (Boolean) -> Unit = {},
) {
    // TopBar always has opaque background (does not merge with home screen)
    val backgroundColor = Color.Black.copy(alpha = 0.95f)

    // Individual focus requesters for each navigation item
    val homeFocusRequester = remember { FocusRequester() }
    val tvShowsFocusRequester = remember { FocusRequester() }
    val moviesFocusRequester = remember { FocusRequester() }
    val favoritesFocusRequester = remember { FocusRequester() }
    val historyFocusRequester = remember { FocusRequester() }
    val iptvFocusRequester = remember { FocusRequester() }

    // When requested, focus the selected navigation item
    LaunchedEffect(requestFocusOnSelectedItem) {
        if (requestFocusOnSelectedItem) {
            try {
                when (selectedItem) {
                    NavigationItem.Home -> homeFocusRequester.requestFocus()
                    NavigationItem.TVShows -> tvShowsFocusRequester.requestFocus()
                    NavigationItem.Movies -> moviesFocusRequester.requestFocus()
                    NavigationItem.Favorites -> favoritesFocusRequester.requestFocus()
                    NavigationItem.History -> historyFocusRequester.requestFocus()
                    NavigationItem.Iptv -> iptvFocusRequester.requestFocus()
                    else -> homeFocusRequester.requestFocus()
                }
            } catch (_: Exception) {
                // Ignore focus failures
            }
        }
    }

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
                .onFocusChanged { focusState ->
                    onFocusChanged(focusState.hasFocus)
                }
                // UX23: Focus trap - prevent UP from exiting TopBar
                .focusProperties {
                    @Suppress("DEPRECATION")
                    exit = { direction ->
                        if (direction == FocusDirection.Up) {
                            FocusRequester.Cancel // Block UP navigation
                        } else {
                            FocusRequester.Default // Allow other directions
                        }
                    }
                }
                // UX23: Route DOWN to content when available
                .onPreviewKeyEvent { event ->
                    if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                        event.key.nativeKeyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN &&
                        contentFocusRequester != null
                    ) {
                        try {
                            contentFocusRequester.requestFocus()
                            true
                        } catch (e: Exception) {
                            false
                        }
                    } else {
                        false
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Logo + Name - PlexHubTV
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 32.dp)
            ) {
                if (appLogoPainter != null) {
                    Icon(
                        painter = appLogoPainter,
                        contentDescription = stringResource(R.string.topbar_logo_description),
                        tint = Color.Unspecified,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    // Fallback placeholder if no logo provided
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(Color(0xFFE50914), Color(0xFFB81D24))
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "P",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
            /*    Text(
                    text = "PlexHub TV",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )*/
            }

            // Navigation Items
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.weight(1f)
            ) {
                NetflixNavItem(
                    item = NavigationItem.Home,
                    isSelected = selectedItem == NavigationItem.Home,
                    onSelected = { onItemSelected(NavigationItem.Home) },
                    focusRequester = homeFocusRequester
                )
                NetflixNavItem(
                    item = NavigationItem.TVShows,
                    isSelected = selectedItem == NavigationItem.TVShows,
                    onSelected = { onItemSelected(NavigationItem.TVShows) },
                    focusRequester = tvShowsFocusRequester
                )
                NetflixNavItem(
                    item = NavigationItem.Movies,
                    isSelected = selectedItem == NavigationItem.Movies,
                    onSelected = { onItemSelected(NavigationItem.Movies) },
                    focusRequester = moviesFocusRequester
                )
                NetflixNavItem(
                    item = NavigationItem.Favorites, // Will be renamed "My List"
                    isSelected = selectedItem == NavigationItem.Favorites,
                    onSelected = { onItemSelected(NavigationItem.Favorites) },
                    focusRequester = favoritesFocusRequester
                )
                NetflixNavItem(
                    item = NavigationItem.History,
                    isSelected = selectedItem == NavigationItem.History,
                    onSelected = { onItemSelected(NavigationItem.History) },
                    focusRequester = historyFocusRequester
                )
                NetflixNavItem(
                    item = NavigationItem.Iptv,
                    isSelected = selectedItem == NavigationItem.Iptv,
                    onSelected = { onItemSelected(NavigationItem.Iptv) },
                    focusRequester = iptvFocusRequester
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
    focusRequester: FocusRequester = remember { FocusRequester() }
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
            .focusRequester(focusRequester)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelected
            )
    ) {
        Text(
            text = stringResource(item.labelResId),
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

    // Match NavItem behavior: Red when focused, transparent white otherwise
    val iconColor by animateColorAsState(
        targetValue = if (isFocused) NetflixRed else Color.White.copy(alpha = 0.7f),
        animationSpec = tween(150),
        label = "searchIconColor"
    )
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        animationSpec = tween(150),
        label = "searchIconScale"
    )

    Box(
        modifier = Modifier
            .size(32.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = stringResource(R.string.topbar_search_description),
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

    // Settings icon with Netflix-style background
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
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = stringResource(R.string.topbar_profile_description),
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}
