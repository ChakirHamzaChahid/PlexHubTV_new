package com.chakir.plexhubtv.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.core.designsystem.NetflixRed

enum class DetailTab(val title: String) {
    Episodes("EPISODES"),
    MoreLikeThis("MORE LIKE THIS"),
    Collections("COLLECTIONS"),
    // Trailers("TRAILERS & MORE") // For future
}

@Composable
fun NetflixDetailTabs(
    selectedTab: DetailTab,
    onTabSelected: (DetailTab) -> Unit,
    showEpisodes: Boolean,
    showCollections: Boolean = false,
) {
    val tabs = buildList {
        if (showEpisodes) add(DetailTab.Episodes)
        add(DetailTab.MoreLikeThis)
        if (showCollections) add(DetailTab.Collections)
    }
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 0.dp,
        containerColor = Color.Transparent,
        contentColor = Color.White,
        indicator = { tabPositions ->
            if (selectedIndex < tabPositions.size) {
                Box(
                    modifier = Modifier
                        .tabIndicatorOffset(tabPositions[selectedIndex])
                        .height(4.dp)
                        .background(NetflixRed)
                )
            }
        },
        divider = {},
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (index == selectedIndex) Color.White else Color.White.copy(alpha = 0.6f)
                    )
                }
            )
        }
    }
}
