package com.chakir.plexhubtv.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToServerStatus: () -> Unit,
    onNavigateToProfiles: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val events = viewModel.navigationEvents

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is SettingsNavigationEvent.NavigateBack -> onNavigateBack()
                is SettingsNavigationEvent.NavigateToLogin -> onNavigateToLogin()
                is SettingsNavigationEvent.NavigateToServerStatus -> onNavigateToServerStatus()
                is SettingsNavigationEvent.NavigateToProfiles -> onNavigateToProfiles()
            }
        }
    }

    SettingsScreen(
        state = uiState,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { onAction(SettingsAction.Back) }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                SettingsSectionHeader("Server")
                SettingsDropdown(
                    title = "Default Server",
                    currentValue = state.defaultServer,
                    options = state.availableServers,
                    onOptionSelected = { onAction(SettingsAction.SelectDefaultServer(it)) }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onAction(SettingsAction.CheckServerStatus) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Check Server Status")
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
            }
            
            item {
                SettingsSectionHeader("Appearance")
                SettingsDropdown(
                    title = "App Theme",
                    currentValue = state.theme.name,
                    options = AppTheme.values().map { it.name },
                    onOptionSelected = { onAction(SettingsAction.ChangeTheme(AppTheme.valueOf(it))) }
                )
                HorizontalDivider()
            }

            item {
                SettingsSectionHeader("Playback")
                SettingsDropdown(
                    title = "Video Quality",
                    currentValue = state.videoQuality,
                    options = listOf("Original", "20 Mbps 1080p", "12 Mbps 1080p", "8 Mbps 1080p", "4 Mbps 720p", "3 Mbps 720p"),
                    onOptionSelected = { onAction(SettingsAction.ChangeVideoQuality(it)) }
                )
                SettingsDropdown(
                    title = "Player Engine",
                    currentValue = state.playerEngine,
                    options = listOf("ExoPlayer", "MPV"),
                    onOptionSelected = { onAction(SettingsAction.ChangePlayerEngine(it)) }
                )
                HorizontalDivider()
            }

            item {
                SettingsSectionHeader("Storage & Cache")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Enable Cache", style = MaterialTheme.typography.titleMedium)
                        Text(state.cacheSize, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = state.isCacheEnabled,
                        onCheckedChange = { onAction(SettingsAction.ToggleCache(it)) }
                    )
                }
                
                Button(
                    onClick = { onAction(SettingsAction.ForceSync) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Cached, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Synchroniser maintenant")
                }
                
                Spacer(Modifier.height(8.dp))
                
                Button(
                    onClick = { onAction(SettingsAction.ClearCache) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear Cache")
                }
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
            }

            item {
                SettingsSectionHeader("Account")
                Button(
                    onClick = { onAction(SettingsAction.SwitchProfile) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Cached, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Changer de profil")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onAction(SettingsAction.Logout) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Logout")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Version ${state.appVersion}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 16.dp)
    )
}

@Composable
fun SettingsDropdown(
    title: String,
    currentValue: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    // Simplified dropdown using a Row with Text and a simple callback for now
    // In a real TV app, this would likely be a focused item that opens a dialog
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
         Text(title, style = MaterialTheme.typography.titleMedium)
         Spacer(Modifier.height(4.dp))
         LazyRow(
             modifier = Modifier.fillMaxWidth(),
             horizontalArrangement = Arrangement.spacedBy(8.dp),
             contentPadding = PaddingValues(horizontal = 4.dp)
         ) {
             items(options) { option ->
                 FilterChip(
                     selected = option == currentValue,
                     onClick = { onOptionSelected(option) },
                     label = { Text(option) }
                 )
             }
         }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSettingsScreen() {
    SettingsScreen(
        state = SettingsUiState(videoQuality = "Original", cacheSize = "150 MB"),
        onAction = {}
    )
}
