package com.chakir.plexhubtv.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Sidebar alphabétique pour la navigation rapide (Jump-to-letter).
 */
@Composable
fun AlphabetSidebar(
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val alphabet = remember { listOf("#") + ('A'..'Z').map { it.toString() } }

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .width(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TvLazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            pivotOffsets = PivotOffsets(parentFraction = 0.5f) // Center focus for sidebar
        ) {
            items(alphabet) { letter ->
                SidebarItem(
                    letter = letter,
                    onClick = { onLetterSelected(letter) },
                )
            }
        }
    }
}

@Composable
fun SidebarItem(
    letter: String,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .width(32.dp) // Increased hit target
                .clip(RoundedCornerShape(4.dp))
                .background(if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent)
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}
