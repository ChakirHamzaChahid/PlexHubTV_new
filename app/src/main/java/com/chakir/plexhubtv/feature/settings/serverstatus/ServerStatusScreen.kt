package com.chakir.plexhubtv.feature.settings.serverstatus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Écran affichant l'état des serveurs configurés (En ligne/Hors ligne, Latence).
 * Permet de rafraîchir manuellement le statut.
 */
@Composable
fun ServerStatusRoute(
    viewModel: ServerStatusViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    ServerStatusScreen(
        state = uiState,
        onNavigateBack = onNavigateBack,
        onRefresh = viewModel::refresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerStatusScreen(
    state: ServerStatusUiState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server Status") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading && state.servers.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.servers, key = { it.identifier }) { server ->
                        ServerStatusCard(server = server)
                    }
                }
            }

            if (state.error != null) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
fun ServerStatusCard(server: ServerStatusUiModel) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .focusable(interactionSource = interactionSource),
        colors =
            CardDefaults.cardColors(
                containerColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ),
        border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = server.details,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (isFocused) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                alpha = 0.8f,
                            )
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                if (server.address.isNotBlank() && server.address != "Scanning...") {
                    Text(
                        text = server.address,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (isFocused) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                    alpha = 0.6f,
                                )
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                    )
                }
            }

            if (server.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                )
            } else {
                StatusIndicator(isOnline = server.isOnline)
            }
        }
    }
}

@Composable
fun StatusIndicator(isOnline: Boolean) {
    val color = if (isOnline) Color.Green else Color.Red
    Box(
        modifier =
            Modifier
                .size(16.dp)
                .background(color, CircleShape),
    )
}
