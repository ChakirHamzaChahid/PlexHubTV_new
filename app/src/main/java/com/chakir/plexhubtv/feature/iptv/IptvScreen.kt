package com.chakir.plexhubtv.feature.iptv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.model.Category
import com.chakir.plexhubtv.core.model.IptvChannel
import com.chakir.plexhubtv.core.model.LiveChannel
import com.chakir.plexhubtv.feature.iptv.components.CategoryColumn
import com.chakir.plexhubtv.feature.iptv.components.ChannelColumn
import com.chakir.plexhubtv.feature.iptv.components.PlayerEpgColumn

@Composable
fun IptvRoute(
    viewModel: IptvViewModel = hiltViewModel(),
    onPlayChannel: (String, String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val playerFactory = viewModel.playerFactory

    // Create ExoPlayer for embedded preview (Backend mode only)
    val exoPlayer = remember {
        playerFactory.createExoPlayer(context, isRelay = true)
    }
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    // When streamUrl changes, load media into embedded player
    LaunchedEffect(uiState.streamUrl) {
        val url = uiState.streamUrl ?: run {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            return@LaunchedEffect
        }
        val isM3u8 = url.contains(".m3u8", ignoreCase = true)
        val mediaItem = playerFactory.createMediaItem(
            uri = android.net.Uri.parse(url),
            mediaId = "live_preview",
            isM3u8 = isM3u8,
        )
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    // Collect one-shot stream URL events for fullscreen playback
    LaunchedEffect(Unit) {
        viewModel.playStreamEvent.collect { event ->
            onPlayChannel(event.url, event.title)
        }
    }

    LiveTvScreen(
        state = uiState,
        onAction = viewModel::onAction,
        onPlayChannel = onPlayChannel,
        exoPlayer = exoPlayer,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(
    state: LiveTvUiState,
    onAction: (LiveTvAction) -> Unit,
    onPlayChannel: (String, String) -> Unit,
    exoPlayer: ExoPlayer? = null,
) {
    var isSearchActive by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.padding(top = 56.dp), // Clear Netflix TopBar overlay
        topBar = {
            if (isSearchActive) {
                val searchFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }
                TopAppBar(
                    title = {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = { onAction(LiveTvAction.OnSearchQueryChange(it)) },
                            placeholder = { Text(stringResource(R.string.live_tv_search_hint)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            onAction(LiveTvAction.OnSearchQueryChange(""))
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.iptv_close_search))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.iptv_live_tv_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                    actions = {
                        // Source selector button (only if backend is available)
                        if (state.hasBackend) {
                            IconButton(onClick = { onAction(LiveTvAction.ShowSourceSelector) }) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = stringResource(R.string.iptv_switch_source))
                            }
                        }
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search))
                        }
                    },
                )
            }
        },
    ) { padding ->
        val liveTvScreenDescription = stringResource(R.string.iptv_live_tv_screen)
        Column(
            modifier = Modifier
                .padding(padding)
                .testTag("screen_live_tv")
                .semantics { contentDescription = liveTvScreenDescription },
        ) {
            // Content
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("live_tv_loading"),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.error != null -> {
                    ErrorContent(
                        error = state.error,
                        sourceMode = state.sourceMode,
                        onRetry = { onAction(LiveTvAction.Refresh) },
                        onChangeUrl = { onAction(LiveTvAction.ShowUrlDialog) },
                    )
                }

                else -> {
                    when (state.sourceMode) {
                        SourceMode.M3U -> M3uChannelList(
                            channels = state.m3uChannels,
                            onPlayChannel = onPlayChannel,
                        )

                        SourceMode.Backend -> BackendThreeColumnLayout(
                            state = state,
                            exoPlayer = exoPlayer,
                            onAction = onAction,
                        )
                    }
                }
            }
        }
    }

    // M3U URL dialog
    if (state.showUrlDialog) {
        M3uUrlDialog(
            onSave = { onAction(LiveTvAction.SaveUrl(it)) },
            onDismiss = { onAction(LiveTvAction.DismissUrlDialog) },
        )
    }

    // Source selector dialog
    if (state.showSourceSelector) {
        SourceSelectorDialog(
            currentMode = state.sourceMode,
            onSelectMode = { onAction(LiveTvAction.ChangeSourceMode(it)) },
            onDismiss = { onAction(LiveTvAction.DismissSourceSelector) },
        )
    }
}

// ── Backend 3-Column Layout ──────────────────────────────────────────

@Composable
private fun BackendThreeColumnLayout(
    state: LiveTvUiState,
    exoPlayer: ExoPlayer?,
    onAction: (LiveTvAction) -> Unit,
) {
    val categoryColumnFocusRequester = remember { FocusRequester() }
    val channelColumnFocusRequester = remember { FocusRequester() }
    val playerColumnFocusRequester = remember { FocusRequester() }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .testTag("live_tv_3col"),
    ) {
        CategoryColumn(
            categories = state.filteredCategories,
            selectedCategoryId = state.selectedCategoryId,
            searchQuery = state.categorySearchQuery,
            onCategoryClick = { onAction(LiveTvAction.SelectCategory(it)) },
            onSearchChange = { onAction(LiveTvAction.OnCategorySearchChange(it)) },
            channelColumnFocusRequester = channelColumnFocusRequester,
            modifier = Modifier.weight(0.22f),
        )

        ChannelColumn(
            channels = state.backendChannels,
            selectedChannel = state.selectedChannel,
            hasMore = state.hasMore,
            isLoadingMore = state.isLoadingMore,
            onChannelClick = { onAction(LiveTvAction.SelectChannel(it)) },
            onLoadMore = { onAction(LiveTvAction.LoadMore) },
            categoryColumnFocusRequester = categoryColumnFocusRequester,
            playerColumnFocusRequester = playerColumnFocusRequester,
            modifier = Modifier.weight(0.30f),
        )

        PlayerEpgColumn(
            selectedChannel = state.selectedChannel,
            streamUrl = state.streamUrl,
            isResolvingStream = state.isResolvingStream,
            channelEpg = state.channelEpg,
            isLoadingEpg = state.isLoadingEpg,
            exoPlayer = exoPlayer,
            onGoFullscreen = { onAction(LiveTvAction.GoFullscreen) },
            channelColumnFocusRequester = channelColumnFocusRequester,
            modifier = Modifier.weight(0.48f),
        )
    }
}

// ── Category Chips (kept for potential future use) ──────────────────

@Composable
private fun CategoryChipsRow(
    categories: List<Category>,
    selectedCategoryId: String?,
    onCategoryClick: (String?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
    ) {
        item {
            FilterChip(
                selected = selectedCategoryId == null,
                onClick = { onCategoryClick(null) },
                label = { Text(stringResource(R.string.live_tv_category_all)) },
            )
        }
        items(categories, key = { it.categoryId }) { category ->
            FilterChip(
                selected = selectedCategoryId == category.categoryId,
                onClick = { onCategoryClick(category.categoryId) },
                label = { Text(category.categoryName) },
            )
        }
    }
}

// ── M3U Channel List ─────────────────────────────────────────────────

@Composable
private fun M3uChannelList(
    channels: List<IptvChannel>,
    onPlayChannel: (String, String) -> Unit,
) {
    val listState = rememberLazyListState()
    val channelListFocusRequester = remember { FocusRequester() }

    LaunchedEffect(channels) {
        if (channels.isNotEmpty()) {
            channelListFocusRequester.requestFocus()
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("live_tv_m3u_list")
            .focusRequester(channelListFocusRequester),
    ) {
        items(items = channels, key = { it.streamUrl }) { channel ->
            M3uChannelCard(
                channel = channel,
                onClick = { onPlayChannel(channel.streamUrl, channel.name) },
            )
        }
    }
}

// ── Backend Channel List (with pagination) ───────────────────────────

@Composable
private fun BackendChannelList(
    channels: List<LiveChannel>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onAction: (LiveTvAction) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val channelListFocusRequester = remember { FocusRequester() }

    LaunchedEffect(channels) {
        if (channels.isNotEmpty()) {
            channelListFocusRequester.requestFocus()
        }
    }

    // Trigger load more when near end
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !isLoadingMore && lastVisible >= channels.size - 10
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("live_tv_backend_list")
            .focusRequester(channelListFocusRequester),
    ) {
        items(items = channels, key = { "${it.serverId}:${it.streamId}" }) { channel ->
            BackendChannelCard(
                channel = channel,
                onClick = { onAction(LiveTvAction.PlayBackendChannel(channel)) },
            )
        }

        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

// ── Channel Cards ────────────────────────────────────────────────────

@Composable
private fun M3uChannelCard(
    channel: IptvChannel,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "scale")
    val borderColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "border",
    )

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scale)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(logoUrl = channel.logoUrl, channelName = channel.name)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                channel.group?.let { group ->
                    if (group.isNotBlank()) {
                        Text(
                            text = group,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackendChannelCard(
    channel: LiveChannel,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "scale")
    val borderColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "border",
    )

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scale)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(logoUrl = channel.logoUrl, channelName = channel.name)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Account label + category on the same line
                val subtitle = buildString {
                    channel.accountLabel?.let { append(it) }
                    channel.categoryName?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it)
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // EPG now playing
                channel.nowPlaying?.let { epg ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = epg.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LinearProgressIndicator(
                        progress = { epg.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelLogo(logoUrl: String?, channelName: String? = null) {
    Card(
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.size(60.dp, 40.dp),
    ) {
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = channelName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp),
                alignment = Alignment.Center,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ── Error Content ────────────────────────────────────────────────────

@Composable
private fun ErrorContent(
    error: String,
    sourceMode: SourceMode,
    onRetry: () -> Unit,
    onChangeUrl: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("live_tv_error"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(48.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(R.string.iptv_connection_error),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 600.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                val retryFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { retryFocusRequester.requestFocus() }
                Button(
                    onClick = onRetry,
                    modifier = Modifier.focusRequester(retryFocusRequester),
                ) {
                    Text(stringResource(R.string.action_retry))
                }
                if (sourceMode == SourceMode.M3U) {
                    OutlinedButton(onClick = onChangeUrl) {
                        Text(stringResource(R.string.iptv_change_url))
                    }
                }
            }
        }
    }
}

// ── Dialogs ──────────────────────────────────────────────────────────

@Composable
private fun M3uUrlDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.iptv_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.iptv_url_label)) },
                placeholder = { Text(stringResource(R.string.iptv_url_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun SourceSelectorDialog(
    currentMode: SourceMode,
    onSelectMode: (SourceMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.live_tv_source_title)) },
        text = {
            Column {
                SourceRadioOption(
                    label = stringResource(R.string.live_tv_source_m3u),
                    icon = Icons.Default.Tv,
                    isSelected = currentMode == SourceMode.M3U,
                    onClick = {
                        onSelectMode(SourceMode.M3U)
                        onDismiss()
                    },
                )
                Spacer(Modifier.height(8.dp))
                SourceRadioOption(
                    label = stringResource(R.string.live_tv_source_backend),
                    icon = Icons.Default.CloudQueue,
                    isSelected = currentMode == SourceMode.Backend,
                    onClick = {
                        onSelectMode(SourceMode.Backend)
                        onDismiss()
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun SourceRadioOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            RadioButton(selected = isSelected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
