package com.chakir.plexhubtv.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.scale
import com.chakir.plexhubtv.core.designsystem.NetflixBlack
import com.chakir.plexhubtv.core.designsystem.NetflixRed
import com.chakir.plexhubtv.core.designsystem.NetflixWhite

@Composable
fun NetflixOnScreenKeyboard(
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    initialFocusRequester: FocusRequester? = null
) {
    val keys = listOf(
        listOf("A", "B", "C", "D", "E", "F", "G"),
        listOf("H", "I", "J", "K", "L", "M", "N"),
        listOf("O", "P", "Q", "R", "S", "T", "U"),
        listOf("V", "W", "X", "Y", "Z", "0", "1"),
        listOf("2", "3", "4", "5", "6", "7", "8"),
        listOf("9", " ", "⌫", "✕", "SEARCH")
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        keys.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEachIndexed { colIndex, key ->
                    if (key.isNotEmpty()) {
                        val isFirstKey = rowIndex == 0 && colIndex == 0
                        val isSearchButton = key == "SEARCH"
                        KeyButton(
                            key = key,
                            onClick = {
                                when (key) {
                                    " " -> onKeyPress(" ")
                                    "⌫" -> onBackspace()
                                    "✕" -> onClear()
                                    "SEARCH" -> onSearch()
                                    else -> onKeyPress(key)
                                }
                            },
                            isSearchButton = isSearchButton,
                            modifier = Modifier
                                .weight(if (isSearchButton) 3f else 1f) // Search button 3x wider
                                .then(
                                    if (isFirstKey && initialFocusRequester != null) {
                                        Modifier.focusRequester(initialFocusRequester)
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    key: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSearchButton: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isFocused) 1.05f else 1f, label = "scale")

    Box(
        modifier = modifier
            .height(if (isSearchButton) 48.dp else 44.dp) // Reduced height for better fit
            .scale(scale)
            .background(
                color = if (isSearchButton) NetflixRed else if (isFocused) NetflixRed else NetflixBlack, // Always red for search
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = if (isFocused) 2.dp else if (isSearchButton) 2.dp else 0.dp, // Permanent border for search
                color = if (isFocused) NetflixWhite else if (isSearchButton) NetflixWhite.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
            when (key) {
                " " -> Icon(
                    imageVector = Icons.Default.SpaceBar,
                    contentDescription = "Space",
                    tint = NetflixWhite
                )
                "⌫" -> Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "Backspace",
                    tint = NetflixWhite
                )
                "✕" -> Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear",
                    tint = NetflixWhite
                )
                "SEARCH" -> {
                    // Large prominent search button with icon + text
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = NetflixWhite,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SEARCH",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NetflixWhite
                        )
                    }
                }
                else -> Text(
                    text = key,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = NetflixWhite
                )
            }
    }
}
