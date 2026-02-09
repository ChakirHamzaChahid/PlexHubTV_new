package com.chakir.plexhubtv.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.feature.home.components.NetflixMediaCard
import com.chakir.plexhubtv.core.designsystem.NetflixBlack
import com.chakir.plexhubtv.core.designsystem.NetflixDarkGray
import com.chakir.plexhubtv.core.designsystem.NetflixLightGray
import com.chakir.plexhubtv.core.designsystem.NetflixRed

@Composable
fun NetflixDetailScreen(
    media: MediaItem,
    seasons: List<MediaItem>,
    similarItems: List<MediaItem>,
    state: MediaDetailUiState,
    onAction: (MediaDetailEvent) -> Unit,
    onCollectionClicked: (String, String) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(if (media.type == MediaType.Show) DetailTab.Episodes else DetailTab.MoreLikeThis) }

    Box(modifier = Modifier.fillMaxSize().background(NetflixBlack)) {
        // 1. Full Screen Backdrop with Gradient
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(media.artUrl ?: media.thumbUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().zIndex(0f)
            )

            // Gradient Overlays
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                NetflixBlack.copy(alpha = 0.5f),
                                NetflixBlack.copy(alpha = 0.9f),
                                NetflixBlack
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
                    .zIndex(1f)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                NetflixBlack.copy(alpha = 0.9f),
                                NetflixBlack.copy(alpha = 0.5f),
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = 1500f
                        )
                    )
                    .zIndex(1f)
            )
        }

        // 2. Content Scroll
        LazyColumn(
            modifier = Modifier.fillMaxSize().zIndex(2f),
            contentPadding = PaddingValues(start = 50.dp, bottom = 50.dp, top = 0.dp) // Added top padding control
        ) {
            // Spacer to push content down so header shows nicely
            item { Spacer(modifier = Modifier.height(350.dp)) }

            // Hero Metadata
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(end = 50.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Title
                    Text(
                        text = media.title,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Meta Row (Match, Year, Rating, Duration)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Match % (Fake for now or calculated from rating)
                        val matchPercentage = ((media.rating ?: 0.0) * 10).toInt()
                        if (matchPercentage > 0) {
                            Text(
                                text = "$matchPercentage% Match",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF46D369), // Netflix Match Green
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        // Year
                        media.year?.let {
                            Text(
                                text = it.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = NetflixLightGray
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        // Content Rating
                        media.contentRating?.let {
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        // Duration
                        media.durationMs?.let {
                            val mins = it / 60000
                            Text(
                                text = if (media.type == MediaType.Show) "${seasons.size} Seasons" else "$mins m",
                                style = MaterialTheme.typography.titleMedium,
                                color = NetflixLightGray
                            )
                        }
                        
                        // Quality Badges (HD/4K) - optional
                        Spacer(modifier = Modifier.width(16.dp))
                        Box(
                            modifier = Modifier
                                .background(Color.Transparent, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                .run {
                                    // simple border logic manually since no Border modifier on Box easily without surface
                                    this
                                }
                        ) {
                             Text("HD", style = MaterialTheme.typography.labelSmall, color = NetflixLightGray, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Buttons Row
                    ActionButtonsRow(media, state, onAction)

                    Spacer(modifier = Modifier.height(24.dp))

                    // Synopsis
                    Text(
                        text = media.summary ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(600.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }

            // 3. Tabs
            item {
                NetflixDetailTabs(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    showEpisodes = media.type == MediaType.Show
                )
            }

            // 4. Tab Content
            item {
                when (selectedTab) {
                    DetailTab.Episodes -> {
                         if (seasons.isNotEmpty()) {
                             LazyRow(
                                 horizontalArrangement = Arrangement.spacedBy(16.dp),
                                 contentPadding = PaddingValues(end = 50.dp)
                             ) {
                                 items(seasons) { season ->
                                     // Using NetflixMediaCard for seasons but maybe slightly adjusted aspect ratio?
                                     // Standard is usually 2/3 for posters.
                                     NetflixMediaCard(
                                         media = season,
                                         onClick = { onAction(MediaDetailEvent.OpenSeason(season)) },
                                         onPlay = {}, // Optional: Play season?
                                         onFocus = {},
                                         modifier = Modifier.width(140.dp).aspectRatio(2f/3f)
                                     )
                                 }
                             }
                         } else {
                             Text("No seasons available", color = NetflixLightGray)
                         }
                    }
                    DetailTab.MoreLikeThis -> {
                        if (similarItems.isNotEmpty()) {
                             LazyRow(
                                 horizontalArrangement = Arrangement.spacedBy(16.dp),
                                 contentPadding = PaddingValues(end = 50.dp)
                             ) {
                                 items(similarItems) { item ->
                                     NetflixMediaCard(
                                         media = item,
                                         onClick = { onAction(MediaDetailEvent.OpenMediaDetail(item)) },
                                         onPlay = {},
                                         onFocus = {},
                                         modifier = Modifier.width(140.dp).aspectRatio(2f/3f)
                                     )
                                 }
                             }
                         } else {
                             Text("No similar items found", color = NetflixLightGray)
                         }
                         
                         // Collections also here?
                         if (state.collections.isNotEmpty()) {
                             Spacer(modifier = Modifier.height(24.dp))
                             Text("Included in Collections", style = MaterialTheme.typography.titleMedium, color = NetflixDarkGray)
                             Spacer(modifier = Modifier.height(12.dp))
                              LazyRow(
                                 horizontalArrangement = Arrangement.spacedBy(16.dp),
                                 contentPadding = PaddingValues(end = 50.dp)
                             ) {
                                 items(state.collections) { collection ->
                                     // Simple text card for collections
                                     Button(
                                         onClick = { onCollectionClicked(collection.id, collection.serverId) },
                                         colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                                     ) {
                                         Text(collection.title, color = Color.White)
                                     }
                                 }
                             }
                         }
                    }
                }
            }
            
            // Padding at bottom
            item { Spacer(modifier = Modifier.height(50.dp)) }
        }

        // Back Button
        IconButton(
            onClick = { onAction(MediaDetailEvent.Back) },
            modifier = Modifier.padding(32.dp).align(Alignment.TopEnd).zIndex(3f)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}
