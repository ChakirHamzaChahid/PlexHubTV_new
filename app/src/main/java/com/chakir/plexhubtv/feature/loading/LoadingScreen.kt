package com.chakir.plexhubtv.feature.loading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.R

@Composable
fun LoadingRoute(
    viewModel: LoadingViewModel = hiltViewModel(),
    onNavigateToMain: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToLibrarySelection: () -> Unit = {},
    onNavigateToProfileSelection: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                LoadingNavigationEvent.NavigateToMain -> onNavigateToMain()
                LoadingNavigationEvent.NavigateToAuth -> onNavigateToAuth()
                LoadingNavigationEvent.NavigateToLibrarySelection -> onNavigateToLibrarySelection()
                LoadingNavigationEvent.NavigateToProfileSelection -> onNavigateToProfileSelection()
            }
        }
    }

    LoadingScreen(
        state = uiState,
        onRetryClicked = { viewModel.onRetry() },
        onExitClicked = { viewModel.onExit() }
    )
}

@Composable
fun LoadingScreen(
    state: LoadingUiState,
    onRetryClicked: () -> Unit = {},
    onExitClicked: () -> Unit = {},
) {
    val screenDesc = stringResource(R.string.loading_screen_description)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_loading")
            .semantics { contentDescription = screenDesc },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Logo or App Name
            Text(
                text = stringResource(R.string.loading_welcome),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.loading_please_wait),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(48.dp))

            when (state) {
                is LoadingUiState.Loading -> {
                    val syncState = state.syncState
                    if (syncState != null && syncState.servers.isNotEmpty()) {
                        SyncProgressContent(syncState, state.progress)
                    } else {
                        // Simple progress UI (discovering phase / no server data yet)
                        val progressDesc = stringResource(R.string.loading_progress_description, state.progress.toInt())
                        val syncBarDesc = stringResource(R.string.loading_sync_bar_description)

                        CircularProgressIndicator(
                            modifier = Modifier
                                .testTag("loading_progress")
                                .semantics { contentDescription = progressDesc }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .testTag("sync_progress_bar")
                                .semantics { contentDescription = syncBarDesc },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${state.progress.toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                is LoadingUiState.Error -> {
                    val errorIconDesc = stringResource(R.string.loading_error_icon_description)
                    val errorDesc = stringResource(R.string.loading_error_description, state.message)

                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = errorIconDesc,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("loading_error")
                            .semantics { contentDescription = errorDesc }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 48.dp)
                    ) {
                        val retryFocusRequester = remember { FocusRequester() }
                        Button(
                            onClick = onRetryClicked,
                            modifier = Modifier
                                .focusRequester(retryFocusRequester)
                                .weight(1f)
                        ) {
                            Text(stringResource(R.string.action_retry))
                        }

                        OutlinedButton(
                            onClick = onExitClicked,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.loading_exit_button))
                        }

                        LaunchedEffect(Unit) {
                            retryFocusRequester.requestFocus()
                        }
                    }
                }
                LoadingUiState.Completed -> {
                    val completeText = stringResource(R.string.loading_complete)
                    val completeDesc = stringResource(R.string.loading_completed_description)

                    Text(
                        completeText,
                        modifier = Modifier
                            .testTag("loading_completed")
                            .semantics { contentDescription = completeDesc }
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncProgressContent(
    syncState: SyncGlobalState,
    globalProgress: Float,
) {
    if (syncState.servers.size >= 2) {
        // Multi-server: Row with CurrentSyncBlock + ServerRecapSidebar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CurrentSyncBlock(
                syncState = syncState,
                globalProgress = globalProgress,
                modifier = Modifier.weight(0.55f),
            )

            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 2 }),
            ) {
                Row {
                    Spacer(Modifier.width(24.dp))
                    ServerRecapSidebar(
                        servers = syncState.servers,
                        currentIndex = syncState.currentServerIndex,
                        modifier = Modifier.weight(0.35f).heightIn(max = 400.dp),
                    )
                }
            }
        }
    } else {
        // Single server: vertical layout with library progress list below
        Column(
            modifier = Modifier.fillMaxWidth(0.7f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CurrentSyncBlock(
                syncState = syncState,
                globalProgress = globalProgress,
            )

            val libs = syncState.currentServer?.libraries.orEmpty()
            if (libs.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                LibraryProgressList(libraries = libs)
            }
        }
    }
}

@Composable
private fun CurrentSyncBlock(
    syncState: SyncGlobalState,
    globalProgress: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.testTag("loading_progress"),
        )

        Spacer(Modifier.height(16.dp))

        syncState.currentServer?.let { server ->
            Text(
                text = server.serverName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        syncState.currentLibrary?.let { lib ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${lib.name} (${lib.itemsSynced}/${lib.itemsTotal})",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Spacer(Modifier.height(16.dp))

        LinearProgressIndicator(
            progress = { globalProgress / 100f },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .testTag("sync_progress_bar"),
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "${globalProgress.toInt()}%",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ServerRecapSidebar(
    servers: List<SyncServerState>,
    currentIndex: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            // Header with counter
            val doneCount = servers.count {
                it.status == ServerStatus.Success || it.status == ServerStatus.PartialSuccess
            }
            Text(
                text = stringResource(R.string.sync_server_recap_title_count, doneCount, servers.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            // Scrollable server list (handles 20+ servers on Mi Box S)
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(
                    items = servers,
                    key = { _, server -> server.serverId },
                ) { index, server ->
                    ServerRecapItem(
                        server = server,
                        isCurrent = index == currentIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerRecapItem(
    server: SyncServerState,
    isCurrent: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(
                if (isCurrent) Modifier.background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    RoundedCornerShape(6.dp),
                ) else Modifier
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status icon
        when (server.status) {
            ServerStatus.Success -> Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF4CAF50),
            )
            ServerStatus.PartialSuccess -> Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFFFFA726),
            )
            ServerStatus.Error -> Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            ServerStatus.Running -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            ServerStatus.Pending -> Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }

        Spacer(Modifier.width(8.dp))

        // Server name
        Text(
            text = server.serverName,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Library count badge
        if (server.libraries.isNotEmpty() && server.status != ServerStatus.Pending) {
            Text(
                text = "${server.completedLibraryCount}/${server.libraries.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibraryProgressList(
    libraries: List<SyncLibraryState>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            val doneCount = libraries.count { it.status == LibraryStatus.Success }
            Text(
                text = stringResource(R.string.sync_library_progress_title, doneCount, libraries.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            libraries.forEach { lib ->
                LibraryProgressItem(library = lib)
            }
        }
    }
}

@Composable
private fun LibraryProgressItem(library: SyncLibraryState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(
                if (library.status == LibraryStatus.Running) Modifier.background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    RoundedCornerShape(6.dp),
                ) else Modifier
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (library.status) {
            LibraryStatus.Success -> Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF4CAF50),
            )
            LibraryStatus.Error -> Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            LibraryStatus.Running -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            LibraryStatus.Pending -> Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = library.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (library.status != LibraryStatus.Pending && library.itemsTotal > 0) {
            Text(
                text = "${library.itemsSynced}/${library.itemsTotal}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
