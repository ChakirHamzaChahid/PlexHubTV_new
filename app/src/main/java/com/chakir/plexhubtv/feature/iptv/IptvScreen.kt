package com.chakir.plexhubtv.feature.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.scale
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.chakir.plexhubtv.core.model.IptvChannel

@Composable
fun IptvRoute(
    viewModel: IptvViewModel = hiltViewModel(),
    onPlayChannel: (String, String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    IptvScreen(
        state = uiState,
        onEvent = viewModel::onEvent,
        onPlayChannel = onPlayChannel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IptvScreen(
    state: IptvUiState,
    onEvent: (IptvEvent) -> Unit,
    onPlayChannel: (String, String) -> Unit,
) {
    var isSearchActive by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.padding(top = 56.dp), // Clear Netflix TopBar overlay
        topBar = {
            if (isSearchActive) {
                val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(Unit) {
                    searchFocusRequester.requestFocus()
                }
                TopAppBar(
                    title = {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = { onEvent(IptvEvent.OnSearchQueryChange(it)) },
                            placeholder = { Text("Search channels...") },
                            singleLine = true,
                            colors =
                                TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                ),
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            onEvent(IptvEvent.OnSearchQueryChange(""))
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Search")
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                )
            } else {
                TopAppBar(
                    title = { Text("Live TV") },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { onEvent(IptvEvent.Refresh) }) {
                        Text("Retry")
                    }
                }
            } else {
                val listState = rememberTvLazyListState()
                TvLazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    pivotOffsets = PivotOffsets(parentFraction = 0.0f),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = state.channels,
                        key = { channel -> channel.streamUrl },
                    ) { channel ->
                        ChannelListItem(
                            channel = channel,
                            onClick = {
                                val encodedUrl = channel.streamUrl
                                val encodedTitle = channel.name
                                onPlayChannel(encodedUrl, encodedTitle)
                            },
                        )
                    }
                }
            }
        }
    }

    if (state.showUrlDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { onEvent(IptvEvent.DismissUrlDialog) },
            title = { Text("Enter M3U Playlist URL") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("URL") },
                    placeholder = { Text("http://example.com/playlist.m3u") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = { onEvent(IptvEvent.SaveUrl(text)) },
                    enabled = text.isNotBlank(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(IptvEvent.DismissUrlDialog) }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun ChannelListItem(
    channel: IptvChannel,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isFocused) 1.05f else 1f, label = "scale")
    val borderColor by androidx.compose.animation.animateColorAsState(
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
            modifier =
                Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Logo
            Card(
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.size(60.dp, 40.dp),
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(2.dp),
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

            Spacer(modifier = Modifier.width(16.dp))

            // Name & Group
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
