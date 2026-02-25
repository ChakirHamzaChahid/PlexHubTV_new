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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.chakir.plexhubtv.core.model.Extra
import com.chakir.plexhubtv.core.model.ExtraType
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.ui.NetflixMediaCard
import com.chakir.plexhubtv.core.designsystem.NetflixBlack
import com.chakir.plexhubtv.core.designsystem.NetflixDarkGray
import com.chakir.plexhubtv.core.designsystem.NetflixLightGray
import androidx.compose.material.icons.filled.PlayArrow

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
    val listState = rememberLazyListState()
    val playButtonFocusRequester = remember { FocusRequester() }

    // Request focus on Play button when screen opens
    LaunchedEffect(Unit) {
        playButtonFocusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize().background(NetflixBlack)) {
        // 1. Full Screen Backdrop with Gradient
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(media.artUrl ?: media.thumbUrl)
                    .size(1920, 1080) // TV resolution, not Size.ORIGINAL
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

        // 2. Content Scroll — LazyColumn for proper D-Pad navigation
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().zIndex(2f),
            contentPadding = PaddingValues(start = 50.dp, bottom = 50.dp, top = 0.dp),
        ) {
            // Spacer to push content down so header shows nicely
            item(key = "detail_top_spacer") { Spacer(modifier = Modifier.height(350.dp)) }

            // Hero Metadata
            item(key = "detail_metadata") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(end = 50.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = media.title,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Meta Row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val matchPercentage = ((media.rating ?: 0.0) * 10).toInt()
                        if (matchPercentage > 0) {
                            Text(
                                text = "$matchPercentage% Match",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF46D369),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        media.year?.let {
                            Text(text = it.toString(), style = MaterialTheme.typography.titleMedium, color = NetflixLightGray)
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        media.contentRating?.let {
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(text = it, style = MaterialTheme.typography.labelMedium, color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        media.durationMs?.let {
                            val mins = it / 60000
                            Text(
                                text = if (media.type == MediaType.Show) "${seasons.size} Seasons" else "$mins m",
                                style = MaterialTheme.typography.titleMedium,
                                color = NetflixLightGray
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))
                        Text("HD", style = MaterialTheme.typography.labelSmall, color = NetflixLightGray, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Available on (servers list) - only show after enrichment is complete
                    if (!state.isEnriching && media.remoteSources.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Available on: ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NetflixLightGray
                            )
                            Text(
                                text = media.remoteSources.joinToString(", ") { it.serverName },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    ActionButtonsRow(media, state, onAction, playButtonFocusRequester)
                    Spacer(modifier = Modifier.height(24.dp))

                    ExpandableSummary(
                        text = media.summary ?: "",
                        modifier = Modifier.width(600.dp),
                    )
                }
            }

            item(key = "detail_spacer_tabs") { Spacer(modifier = Modifier.height(40.dp)) }

            // 3. Tabs
            item(key = "detail_tabs") {
                NetflixDetailTabs(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    showEpisodes = media.type == MediaType.Show,
                    showCollections = state.collections.isNotEmpty(),
                    showTrailers = media.extras.isNotEmpty(),
                )
            }

            // 4. Tab Content — LazyRow for proper D-Pad inside LazyColumn
            when (selectedTab) {
                DetailTab.Episodes -> {
                    if (seasons.isNotEmpty()) {
                        item(key = "detail_seasons_row") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(end = 50.dp),
                                modifier = Modifier.focusGroup() // Group horizontal navigation
                            ) {
                                items(seasons, key = { "${it.ratingKey}_${it.serverId}" }) { season ->
                                    NetflixMediaCard(
                                        media = season,
                                        onClick = { onAction(MediaDetailEvent.OpenSeason(season)) },
                                        onPlay = {},
                                    )
                                }
                            }
                        }
                    } else {
                        item(key = "detail_no_seasons") {
                            Text("No seasons available", color = NetflixLightGray)
                        }
                    }
                }
                DetailTab.MoreLikeThis -> {
                    if (similarItems.isNotEmpty()) {
                        item(key = "detail_similar_row") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(end = 50.dp),
                                modifier = Modifier.focusGroup() // Group horizontal navigation
                            ) {
                                items(similarItems, key = { "${it.ratingKey}_${it.serverId}" }) { item ->
                                    NetflixMediaCard(
                                        media = item,
                                        onClick = { onAction(MediaDetailEvent.OpenMediaDetail(item)) },
                                        onPlay = {},
                                    )
                                }
                            }
                        }
                    } else {
                        item(key = "detail_no_similar") {
                            Text("No similar items found", color = NetflixLightGray)
                        }
                    }
                }
                DetailTab.Collections -> {
                    if (state.collections.isNotEmpty()) {
                        item(key = "detail_collections_row") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(end = 50.dp),
                                modifier = Modifier.focusGroup()
                            ) {
                                items(state.collections, key = { it.id }) { collection ->
                                    CollectionCard(
                                        title = collection.title,
                                        itemCount = collection.items.size,
                                        thumbUrl = collection.thumbUrl,
                                        onClick = { onCollectionClicked(collection.id, collection.serverId) },
                                    )
                                }
                            }
                        }
                    } else {
                        item(key = "detail_no_collections") {
                            Text("No collections available", color = NetflixLightGray)
                        }
                    }
                }
                DetailTab.Trailers -> {
                    val extras = media.extras
                    if (extras.isNotEmpty()) {
                        item(key = "detail_trailers_row") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(end = 50.dp),
                                modifier = Modifier.focusGroup()
                            ) {
                                items(extras, key = { it.ratingKey }) { extra ->
                                    ExtraCard(
                                        extra = extra,
                                        onClick = { onAction(MediaDetailEvent.PlayExtra(extra)) },
                                    )
                                }
                            }
                        }
                    } else {
                        item(key = "detail_no_trailers") {
                            Text("No trailers available", color = NetflixLightGray)
                        }
                    }
                }
            }

            item(key = "detail_bottom_spacer") { Spacer(modifier = Modifier.height(50.dp)) }
        }
    }
}

@Composable
private fun CollectionCard(
    title: String,
    itemCount: Int,
    thumbUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(200),
        label = "collectionScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else Color.White.copy(alpha = 0.2f),
        animationSpec = tween(200),
        label = "collectionBorder"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(200),
        label = "collectionBg"
    )

    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .width(220.dp)
            .height(120.dp)
            .scale(scale)
            .clip(shape)
            .background(backgroundColor)
            .border(2.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
    ) {
        // Thumbnail background if available
        if (thumbUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumbUrl)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = if (isFocused) 0.4f else 0.2f,
            )
        }

        // Text overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (itemCount > 0) {
                Text(
                    text = "$itemCount items",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFocused) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun ExtraCard(
    extra: Extra,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(200),
        label = "extraScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else Color.Transparent,
        animationSpec = tween(200),
        label = "extraBorder"
    )

    val shape = RoundedCornerShape(8.dp)
    val durationText = extra.durationMs?.let {
        val totalSeconds = it / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        "${minutes}:${"%02d".format(seconds)}"
    }

    val subtypeLabel = when (extra.subtype) {
        ExtraType.Trailer -> "Trailer"
        ExtraType.BehindTheScenes -> "Behind the Scenes"
        ExtraType.SceneOrSample -> "Scene"
        ExtraType.DeletedScene -> "Deleted Scene"
        ExtraType.Interview -> "Interview"
        ExtraType.Featurette -> "Featurette"
        ExtraType.Unknown -> "Extra"
    }

    Column(
        modifier = modifier.width(280.dp)
    ) {
        // Thumbnail with play overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .scale(scale)
                .clip(shape)
                .border(2.dp, borderColor, shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(extra.thumbUrl)
                    .size(560, 315)
                    .build(),
                contentDescription = extra.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Dark overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isFocused) 0.2f else 0.4f))
            )

            // Play icon center
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (isFocused) 1f else 0.8f),
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center),
            )

            // Duration badge bottom-right
            if (durationText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = extra.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.9f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Subtype label
        Text(
            text = subtypeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = NetflixLightGray,
        )
    }
}

@Composable
private fun ExpandableSummary(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 3,
) {
    if (text.isBlank()) return

    var isExpanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = if (isExpanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!isExpanded) {
                    hasOverflow = result.hasVisualOverflow
                }
            },
        )

        if (hasOverflow || isExpanded) {
            val label = if (isExpanded) "Less" else "More..."
            val labelColor by animateColorAsState(
                targetValue = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f),
                animationSpec = tween(150),
                label = "expandColor"
            )

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = labelColor,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { isExpanded = !isExpanded }
                    ),
            )
        }
    }
}
