package com.chakir.plexhubtv.feature.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import java.text.SimpleDateFormat
import java.util.*

/**
 * Route pour l'écran de debug.
 */
@Composable
fun DebugRoute(
    viewModel: DebugViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    DebugScreen(
        state = uiState,
        onAction = { action ->
            if (action == DebugAction.Back) {
                onNavigateBack()
            } else {
                viewModel.onAction(action)
            }
        }
    )
}

/**
 * Écran de debug complet affichant toutes les informations système, réseau, cache, etc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    state: DebugUiState,
    onAction: (DebugAction) -> Unit
) {
    Scaffold(
        modifier = Modifier.padding(top = 56.dp),
        topBar = {
            TopAppBar(
                title = { Text("Debug Information", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onAction(DebugAction.Back) }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onAction(DebugAction.Refresh) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val listState = rememberTvLazyListState()
            TvLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                pivotOffsets = PivotOffsets(parentFraction = 0.0f)
            ) {
                // System Information
                item {
                    DebugSection(title = "System Information") {
                        DebugInfoRow("Device", "${state.systemInfo.deviceManufacturer} ${state.systemInfo.deviceModel}")
                        DebugInfoRow("Android", "${state.systemInfo.androidVersion} (API ${state.systemInfo.apiLevel})")
                        DebugInfoRow("Architecture", state.systemInfo.cpuArchitecture)
                        DebugInfoRow("RAM", "${state.systemInfo.availableMemoryMb} / ${state.systemInfo.totalMemoryMb} MB")
                        DebugInfoRow("Storage", "${state.systemInfo.availableStorageGb} / ${state.systemInfo.totalStorageGb} GB")
                    }
                }

                // App Information
                item {
                    DebugSection(title = "Application") {
                        DebugInfoRow("Version", "${state.appInfo.appVersion} (${state.appInfo.versionCode})")
                        DebugInfoRow("Build", state.appInfo.buildType)
                        DebugInfoRow("Package", state.appInfo.packageName)
                        DebugInfoRow("Installed", formatDate(state.appInfo.installTime))
                        DebugInfoRow("Updated", formatDate(state.appInfo.lastUpdateTime))
                    }
                }

                // Database Information
                item {
                    DebugSection(title = "Database") {
                        DebugInfoRow("Size", "${state.databaseInfo.databaseSizeMb} MB")
                        DebugInfoRow("Media Items", "${state.databaseInfo.mediaItemsCount}")
                        DebugInfoRow("Hubs", "${state.databaseInfo.hubsCount}")
                        DebugInfoRow("Libraries", "${state.databaseInfo.librariesCount}")
                        DebugInfoRow("Version", "v${state.databaseInfo.databaseVersion}")
                        state.databaseInfo.lastSyncTime?.let {
                            DebugInfoRow("Last Sync", formatDate(it))
                        }
                    }
                }

                // Cache Information
                item {
                    DebugSection(title = "Cache") {
                        DebugInfoRow("Total Cache", "${state.cacheInfo.totalCacheSizeMb} MB")
                        DebugInfoRow("Image Cache", "${state.cacheInfo.imageCacheSizeMb} MB")
                        DebugInfoRow("Metadata Cache", "${state.cacheInfo.metadataCacheSizeMb} MB")

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onAction(DebugAction.ClearImageCache) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear Images", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { onAction(DebugAction.ClearAllCache) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear All", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Network Information
                item {
                    DebugSection(title = "Network") {
                        DebugInfoRow(
                            "Status",
                            if (state.networkInfo.isConnected) "Connected" else "Disconnected",
                            valueColor = if (state.networkInfo.isConnected) Color.Green else Color.Red
                        )
                        DebugInfoRow("Type", state.networkInfo.connectionType)
                        if (state.networkInfo.downloadSpeedMbps > 0) {
                            DebugInfoRow("Download", "${state.networkInfo.downloadSpeedMbps} Mbps")
                        }
                    }
                }

                // Playback Information
                item {
                    DebugSection(title = "Playback") {
                        DebugInfoRow("Player Engine", state.playbackInfo.playerEngine)
                        if (state.playbackInfo.currentCodec != "N/A") {
                            DebugInfoRow("Codec", state.playbackInfo.currentCodec)
                            DebugInfoRow("Resolution", state.playbackInfo.currentResolution)
                            DebugInfoRow("Bitrate", "${state.playbackInfo.currentBitrateMbps} Mbps")
                        }
                    }
                }

                // Server Information
                if (state.serverInfo.connectedServers.isNotEmpty()) {
                    item {
                        DebugSection(title = "Servers (${state.serverInfo.totalServers})") {
                            state.serverInfo.connectedServers.forEach { server ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    DebugInfoRow("Name", server.name)
                                    DebugInfoRow("Version", server.version)
                                    DebugInfoRow(
                                        "Status",
                                        if (server.isConnected) "Connected" else "Disconnected",
                                        valueColor = if (server.isConnected) Color.Green else Color.Red
                                    )
                                    DebugInfoRow("Response Time", "${server.responseTimeMs}ms")
                                    DebugInfoRow("Libraries", "${server.librariesCount}")
                                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }
                        }
                    }
                }

                // Actions
                item {
                    DebugSection(title = "Actions") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onAction(DebugAction.ForceSync) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Sync, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Force Re-Sync")
                            }
                            Button(
                                onClick = { onAction(DebugAction.ExportLogs) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Storage, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export Logs")
                            }
                        }
                    }
                }

                // Bottom Spacer
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun DebugSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
private fun DebugInfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
}
