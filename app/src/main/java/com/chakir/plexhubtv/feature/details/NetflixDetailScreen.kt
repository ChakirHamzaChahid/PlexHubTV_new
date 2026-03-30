package com.chakir.plexhubtv.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.focus.onFocusChanged
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
import com.chakir.plexhubtv.core.ui.rememberBackdropColors
import com.chakir.plexhubtv.core.model.AudioStream
import com.chakir.plexhubtv.core.model.SubtitleStream
import com.chakir.plexhubtv.core.model.VideoStream
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.text.font.FontStyle
import java.util.Calendar

private enum class DetailFocusTarget { PlayButton, Tabs, ContentRow }

@Composable
fun NetflixDetailScreen(
    media: MediaItem,
    seasons: List<MediaItem>,
    similarItems: List<MediaItem>,
    state: MediaDetailUiState,
    onAction: (MediaDetailEvent) -> Unit,
    onCollectionClicked: (String, String) -> Unit,
    showYear: Boolean = false,
) {
    var selectedTab by remember { mutableStateOf(if (media.type == MediaType.Show) DetailTab.Episodes else DetailTab.MoreLikeThis) }
    val listState = rememberLazyListState()
    val playButtonFocusRequester = remember { FocusRequester() }
    val tabsFocusRequester = remember { FocusRequester() }
    val contentRowFocusRequester = remember { FocusRequester() }
    var lastFocusTarget by remember { mutableStateOf(DetailFocusTarget.PlayButton) }

    // NAV-03: Restore focus to last-active element (play button, tabs, or content row)
    LaunchedEffect(Unit) {
        try {
            when (lastFocusTarget) {
                DetailFocusTarget.PlayButton -> playButtonFocusRequester.requestFocus()
                DetailFocusTarget.Tabs -> tabsFocusRequester.requestFocus()
                DetailFocusTarget.ContentRow -> contentRowFocusRequester.requestFocus()
            }
        } catch (_: Exception) { }
    }

    val cs = MaterialTheme.colorScheme
    val backdropColors = rememberBackdropColors(media.artUrl ?: media.thumbUrl)
    val gradientBase = if (backdropColors.isDefault) cs.background else backdropColors.secondary

    Box(modifier = Modifier.fillMaxSize().background(gradientBase)) {
        // 1. Full Screen Backdrop with Gradient
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(media.artUrl ?: media.thumbUrl)
                    .size(1920, 1080)
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
                                gradientBase.copy(alpha = 0.5f),
                                gradientBase.copy(alpha = 0.9f),
                                gradientBase
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
                                gradientBase.copy(alpha = 0.9f),
                                gradientBase.copy(alpha = 0.5f),
                                if (backdropColors.isDefault) Color.Transparent else backdropColors.primary
                            ),
                            startX = 0f,
                            endX = 1500f
                        )
                    )
                    .zIndex(1f)
            )
        }

        // 2. Content — Fixed header + scrollable body
        Column(
            modifier = Modifier.fillMaxSize().zIndex(2f),
        ) {
            // ── FIXED HEADER: Title + Meta + Genres (always visible) ──
            DetailFixedHeader(
                media = media,
                seasons = seasons,
                modifier = Modifier.padding(top = 24.dp),
            )

            // ── SCROLLABLE BODY: Buttons, tech info, summary, tabs, content ──
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 50.dp, bottom = 50.dp),
            ) {
                // Action Buttons
                item(key = "detail_buttons") {
                    Column(modifier = Modifier.fillMaxWidth().padding(end = 50.dp)) {
                        // Available on
                        if (!state.isEnriching && media.remoteSources.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Available on: ", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                                Text(
                                    text = media.remoteSources.joinToString(", ") { it.serverName },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cs.onBackground, fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Box(modifier = Modifier.onFocusChanged {
                            if (it.hasFocus) lastFocusTarget = DetailFocusTarget.PlayButton
                        }) {
                            ActionButtonsRow(media, state, onAction, playButtonFocusRequester)
                        }
                    }
                }

                // Tagline + Summary + Director + Cast
                item(key = "detail_info") {
                    Column(modifier = Modifier.fillMaxWidth().padding(end = 50.dp, top = 20.dp)) {
                        if (!media.tagline.isNullOrBlank()) {
                            Text(
                                text = "\"${media.tagline}\"",
                                style = MaterialTheme.typography.bodyLarge,
                                fontStyle = FontStyle.Italic,
                                color = cs.onBackground.copy(alpha = 0.8f),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        ExpandableSummary(text = media.summary ?: "", modifier = Modifier.width(600.dp))

                        if (media.directors.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Directed by ${media.directors.joinToString(", ")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                            )
                        }


                        val castList = media.role
                        if (!castList.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            CastRow(
                                cast = castList,
                                onPersonClicked = { name -> onAction(MediaDetailEvent.OpenPerson(name)) },
                            )
                        }
                    }
                }

                item(key = "detail_spacer_tabs") { Spacer(modifier = Modifier.height(40.dp)) }

                // Tabs
                item(key = "detail_tabs") {
                    Box(modifier = Modifier
                        .focusRequester(tabsFocusRequester)
                        .onFocusChanged { if (it.hasFocus) lastFocusTarget = DetailFocusTarget.Tabs }
                    ) {
                        NetflixDetailTabs(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            showEpisodes = media.type == MediaType.Show,
                            showCollections = state.collections.isNotEmpty(),
                            showTrailers = media.extras.isNotEmpty(),
                        )
                    }
                }

                // Tab Content
                when (selectedTab) {
                    DetailTab.Episodes -> {
                        if (seasons.isNotEmpty()) {
                            item(key = "detail_seasons_row") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(end = 50.dp),
                                    modifier = Modifier
                                        .focusGroup()
                                        .focusRequester(contentRowFocusRequester)
                                        .onFocusChanged { if (it.hasFocus) lastFocusTarget = DetailFocusTarget.ContentRow }
                                ) {
                                    items(seasons, key = { "${it.ratingKey}_${it.serverId}" }, contentType = { "season" }) { season ->
                                        NetflixMediaCard(
                                            media = season,
                                            onClick = { onAction(MediaDetailEvent.OpenSeason(season)) },
                                            onPlay = {},
                                            showYear = showYear,
                                        )
                                    }
                                }
                            }
                        } else {
                            item(key = "detail_no_seasons") { Text("No seasons available", color = cs.onSurfaceVariant) }
                        }
                    }
                    DetailTab.MoreLikeThis -> {
                        if (similarItems.isNotEmpty()) {
                            item(key = "detail_similar_row") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(end = 50.dp),
                                    modifier = Modifier
                                        .focusGroup()
                                        .focusRequester(contentRowFocusRequester)
                                        .onFocusChanged { if (it.hasFocus) lastFocusTarget = DetailFocusTarget.ContentRow }
                                ) {
                                    items(similarItems, key = { "${it.ratingKey}_${it.serverId}" }, contentType = { "media" }) { item ->
                                        NetflixMediaCard(
                                            media = item,
                                            onClick = { onAction(MediaDetailEvent.OpenMediaDetail(item)) },
                                            onPlay = {},
                                            showYear = showYear,
                                        )
                                    }
                                }
                            }
                        } else {
                            item(key = "detail_no_similar") { Text("No similar items found", color = cs.onSurfaceVariant) }
                        }
                    }
                    DetailTab.Collections -> {
                        if (state.collections.isNotEmpty()) {
                            item(key = "detail_collections_row") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(end = 50.dp),
                                    modifier = Modifier
                                        .focusGroup()
                                        .focusRequester(contentRowFocusRequester)
                                        .onFocusChanged { if (it.hasFocus) lastFocusTarget = DetailFocusTarget.ContentRow }
                                ) {
                                    items(state.collections, key = { it.id }, contentType = { "collection" }) { collection ->
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
                            item(key = "detail_no_collections") { Text("No collections available", color = cs.onSurfaceVariant) }
                        }
                    }
                    DetailTab.Trailers -> {
                        val extras = media.extras
                        if (extras.isNotEmpty()) {
                            item(key = "detail_trailers_row") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(end = 50.dp),
                                    modifier = Modifier
                                        .focusGroup()
                                        .focusRequester(contentRowFocusRequester)
                                        .onFocusChanged { if (it.hasFocus) lastFocusTarget = DetailFocusTarget.ContentRow }
                                ) {
                                    items(extras, key = { it.ratingKey }, contentType = { "extra" }) { extra ->
                                        ExtraCard(
                                            extra = extra,
                                            onClick = { onAction(MediaDetailEvent.PlayExtra(extra)) },
                                        )
                                    }
                                }
                            }
                        } else {
                            item(key = "detail_no_trailers") { Text("No trailers available", color = cs.onSurfaceVariant) }
                        }
                    }
                }

                item(key = "detail_bottom_spacer") { Spacer(modifier = Modifier.height(50.dp)) }
            }
        }
    }
}

/**
 * Fixed header — always visible at the top of the detail screen.
 * Shows title, meta row (year, duration, remaining, rating), and genres.
 */
@Composable
private fun DetailFixedHeader(
    media: MediaItem,
    seasons: List<MediaItem>,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier.fillMaxWidth().padding(start = 50.dp, end = 50.dp, bottom = 8.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column {
            Text(
                text = media.title,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                color = cs.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Meta Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                media.year?.let {
                    Text(text = it.toString(), style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
                    MetaDot()
                }

                if (media.type == MediaType.Show) {
                    if (seasons.isNotEmpty()) {
                        Text(
                            text = "${seasons.size} Season${if (seasons.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant,
                        )
                    }
                } else {
                    media.durationMs?.let { durationMs ->
                        Text(text = formatDuration(durationMs), style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
                    }
                }

                val durationMs = media.durationMs
                if (media.type != MediaType.Show && media.viewOffset > 0 && durationMs != null && durationMs > 0) {
                    val remainingMs = (durationMs - media.viewOffset).coerceAtLeast(0)
                    if (remainingMs > 0) {
                        MetaDot()
                        Text(text = "${formatDuration(remainingMs)} left", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
                    }
                }

                media.contentRating?.let {
                    MetaDot()
                    Box(
                        modifier = Modifier
                            .background(cs.onBackground.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = it, style = MaterialTheme.typography.labelMedium, color = cs.onBackground)
                    }
                }

                val ratingValue = media.rating
                if (ratingValue != null && ratingValue > 0) {
                    MetaDot()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = cs.tertiary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(String.format("%.1f", ratingValue), style = MaterialTheme.typography.titleMedium, color = cs.onBackground, fontWeight = FontWeight.Bold)
                    }
                }

                if (media.type != MediaType.Show && media.viewOffset > 0 && durationMs != null && durationMs > 0) {
                    val remainingMs = (durationMs - media.viewOffset).coerceAtLeast(0)
                    if (remainingMs > 0) {
                        MetaDot()
                        val endsAt = remember(remainingMs) {
                            val cal = Calendar.getInstance()
                            cal.add(Calendar.MILLISECOND, remainingMs.toInt())
                            String.format("%d:%02d %s",
                                if (cal.get(Calendar.HOUR) == 0) 12 else cal.get(Calendar.HOUR),
                                cal.get(Calendar.MINUTE),
                                if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
                            )
                        }
                        Text(text = "Ends at $endsAt", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
                    }
                }

                // Tech badges inline
                val streams = media.mediaParts.firstOrNull()?.streams ?: emptyList()
                val videoStreams = streams.filterIsInstance<VideoStream>()
                val audioStreams = streams.filterIsInstance<AudioStream>()
                val subtitleStreams = streams.filterIsInstance<SubtitleStream>()

                if (videoStreams.isNotEmpty() || audioStreams.isNotEmpty() || subtitleStreams.isNotEmpty()) {
                    MetaDot()
                    videoStreams.firstOrNull()?.let { video ->
                        val resolution = when {
                            (video.height ?: 0) >= 2160 -> "4K"
                            (video.height ?: 0) >= 1080 -> "1080p"
                            (video.height ?: 0) >= 720 -> "720p"
                            (video.height ?: 0) >= 480 -> "480p"
                            else -> "${video.height}p"
                        }
                        val hdrSuffix = if (video.hasHDR) " HDR" else ""
                        TechBadge(text = "$resolution$hdrSuffix")
                    }
                    videoStreams.firstOrNull()?.codec?.uppercase()?.let { codec ->
                        TechBadge(text = codec)
                    }
                    audioStreams.firstOrNull()?.let { audio ->
                        val channelLabel = when (audio.channels) {
                            1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"
                            else -> audio.channels?.toString() ?: ""
                        }
                        val codec = audio.codec?.uppercase() ?: ""
                        val label = listOfNotNull(
                            codec.ifEmpty { null }, channelLabel.ifEmpty { null },
                        ).joinToString(" ")
                        TechBadge(text = "\uD83D\uDD0A $label")
                    }
                    if (subtitleStreams.isNotEmpty()) {
                        val count = subtitleStreams.size
                        TechBadge(text = "CC ($count)")
                    }
                }
            }

            // Genres
            if (media.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = media.genres.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
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
    val cs = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(200),
        label = "collectionScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) cs.onBackground else cs.onBackground.copy(alpha = 0.2f),
        animationSpec = tween(200),
        label = "collectionBorder"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) cs.onBackground.copy(alpha = 0.2f) else cs.onBackground.copy(alpha = 0.08f),
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
                contentDescription = title,
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
                color = if (isFocused) cs.onBackground else cs.onBackground.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (itemCount > 0) {
                Text(
                    text = "$itemCount items",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFocused) cs.onBackground.copy(alpha = 0.9f) else cs.onBackground.copy(alpha = 0.5f),
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
    val cs = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(200),
        label = "extraScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) cs.onBackground else Color.Transparent,
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
                    .background(cs.background.copy(alpha = if (isFocused) 0.2f else 0.4f))
            )

            // Play icon center
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = cs.onBackground.copy(alpha = if (isFocused) 1f else 0.8f),
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
                        .background(cs.background.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onBackground,
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
            color = if (isFocused) cs.onBackground else cs.onBackground.copy(alpha = 0.9f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Subtype label
        Text(
            text = subtypeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
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

    val cs = MaterialTheme.colorScheme
    var isExpanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onBackground,
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
                targetValue = if (isFocused) cs.onBackground else cs.onBackground.copy(alpha = 0.6f),
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

@Composable
private fun MetaDot() {
    val cs = MaterialTheme.colorScheme
    Text(
        text = "•",
        style = MaterialTheme.typography.titleMedium,
        color = cs.onSurfaceVariant,
    )
}

@Composable
private fun TechBadge(text: String) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .background(cs.onBackground.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = cs.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

@Composable
private fun CastRow(
    cast: List<com.chakir.plexhubtv.core.model.CastMember>,
    onPersonClicked: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val displayCast = cast.take(8)
    Column {
        Text(
            text = "Cast",
            style = MaterialTheme.typography.titleSmall,
            color = cs.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            displayCast.forEach { member ->
                val name = member.tag ?: return@forEach
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(72.dp)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onPersonClicked(name) },
                        ),
                ) {
                    if (member.thumb != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(member.thumb)
                                .build(),
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .then(
                                    if (isFocused) Modifier.border(2.dp, cs.onBackground, androidx.compose.foundation.shape.CircleShape)
                                    else Modifier
                                ),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(cs.surfaceVariant)
                                .then(
                                    if (isFocused) Modifier.border(2.dp, cs.onBackground, androidx.compose.foundation.shape.CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = name.first().toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = cs.onBackground,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFocused) cs.onBackground else cs.onSurfaceVariant,
                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    member.role?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onBackground.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
