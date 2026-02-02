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
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Écran principal des paramètres.
 * Gère la navigation et affiche les différentes sections de configuration (Compte, Lecture, Serveur, etc.).
 */
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
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onAction(SettingsAction.Back) }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp), // More horizontal padding
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- Account Section ---
            item {
                SettingsGroup("Account") {
                    SettingsButton(
                        title = "Switch Profile",
                        icon = Icons.Filled.Cached,
                        onClick = { onAction(SettingsAction.SwitchProfile) }
                    )
                    Spacer(Modifier.height(8.dp))
                    SettingsButton(
                        title = "Logout",
                        icon = Icons.Filled.Logout,
                        isDestructive = true,
                        onClick = { onAction(SettingsAction.Logout) }
                    )
                }
            }

            // --- Playback Section ---
            item {
                SettingsGroup("Playback") {
                     // Video Quality
                     SettingsOptionSelector(
                         title = "Video Quality",
                         currentValue = state.videoQuality,
                         options = listOf("Original", "20 Mbps 1080p", "12 Mbps 1080p", "8 Mbps 1080p", "4 Mbps 720p", "3 Mbps 720p"),
                         onSelect = { onAction(SettingsAction.ChangeVideoQuality(it)) }
                     )
                     Spacer(Modifier.height(16.dp))
                     // Player Engine
                     SettingsOptionSelector(
                         title = "Player Engine",
                         currentValue = state.playerEngine,
                         options = listOf("ExoPlayer", "MPV"),
                         onSelect = { onAction(SettingsAction.ChangePlayerEngine(it)) }
                     )
                }
            }

            // --- Languages Section ---
            item {
                SettingsGroup("Languages") {
                    // Audio
                    SettingsOptionSelector(
                        title = "Preferred Audio Language",
                        currentValue = state.preferredAudioLanguage ?: "Original",
                        options = listOf("Original", "English", "French", "German", "Spanish", "Italian", "Japanese", "Korean", "Russian", "Portuguese"),
                         onSelect = { lang ->
                             val value = if (lang == "Original") null else lang
                             onAction(SettingsAction.ChangePreferredAudioLanguage(value)) 
                         }
                    )
                    Spacer(Modifier.height(16.dp))
                    // Subtitle
                    SettingsOptionSelector(
                        title = "Preferred Subtitle Language",
                        currentValue = state.preferredSubtitleLanguage ?: "None",
                        options = listOf("None", "English", "French", "German", "Spanish", "Italian", "Japanese", "Korean", "Russian", "Portuguese"),
                         onSelect = { lang ->
                             val value = if (lang == "None") null else lang
                             onAction(SettingsAction.ChangePreferredSubtitleLanguage(value))
                         }
                    )
                }
            }

            // --- Server Section ---
            item {
                SettingsGroup("Server") {
                    SettingsOptionSelector(
                        title = "Default Server",
                        currentValue = state.defaultServer,
                        options = state.availableServers,
                        onSelect = { onAction(SettingsAction.SelectDefaultServer(it)) }
                    )
                    Spacer(Modifier.height(16.dp))
                    SettingsButton(
                        title = "Check Server Status",
                        icon = Icons.Filled.Info,
                        onClick = { onAction(SettingsAction.CheckServerStatus) }
                    )
                }
            }

            // --- Appearance ---
            item {
                SettingsGroup("Appearance") {
                     SettingsOptionSelector(
                        title = "App Theme",
                        currentValue = state.theme.name,
                        options = AppTheme.values().map { it.name },
                        onSelect = { onAction(SettingsAction.ChangeTheme(AppTheme.valueOf(it))) }
                    )
                }
            }

            // --- Storage & Cache ---
            item {
                SettingsGroup("Storage & Sync") {
                    SettingsToggle(
                        title = "Enable Cache",
                        subtitle = "${state.cacheSize} used",
                        isChecked = state.isCacheEnabled,
                        onCheckChanged = { onAction(SettingsAction.ToggleCache(it)) }
                    )
                    Spacer(Modifier.height(16.dp))
                    SettingsButton(
                        title = "Synchronise Now",
                        icon = Icons.Filled.Cached,
                        onClick = { onAction(SettingsAction.ForceSync) },
                        enabled = !state.isSyncing
                    )
                    Spacer(Modifier.height(8.dp))
                    SettingsButton(
                        title = "Sync Plex Watchlist",
                        icon = Icons.Filled.Cached,
                        onClick = { onAction(SettingsAction.SyncWatchlist) },
                        enabled = !state.isSyncing
                    )
                    
                    // Sync feedback
                    if (state.isSyncing) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Syncing...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    
                    state.syncMessage?.let { message ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        )
                    }
                    
                    state.syncError?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        )
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    SettingsButton(
                        title = "Clear Cache",
                        icon = Icons.Filled.Delete,
                        isDestructive = true,
                        onClick = { onAction(SettingsAction.ClearCache) }
                    )
                }
            }
            
            item {
                Text(
                    text = "Version ${state.appVersion}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

// --- Components ---

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SettingsButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var isFocused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    
    val backgroundColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        isFocused -> if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isFocused -> if (isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .onFocusChanged { if (enabled) isFocused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = contentColor)
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SettingsToggle(
    title: String,
    subtitle: String? = null,
    isChecked: Boolean,
    onCheckChanged: (Boolean) -> Unit
) {
    var isFocused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    
    val backgroundColor = if (isFocused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onCheckChanged(!isChecked) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = isChecked, onCheckedChange = null) // Switch handles its own color but we control click
    }
}

@Composable
fun SettingsOptionSelector(
    title: String,
    currentValue: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(options) { option ->
                val isSelected = option == currentValue
                var isFocused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                
                // High contrast focus for chips
                val containerColor = when {
                    isFocused -> MaterialTheme.colorScheme.primary // Focus takes precedence
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                
                val contentColor = when {
                    isFocused -> MaterialTheme.colorScheme.onPrimary
                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                        .background(containerColor)
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onSelect(option) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSettingsScreen() {
    com.chakir.plexhubtv.core.designsystem.PlexHubTheme {
        SettingsScreen(
            state = SettingsUiState(videoQuality = "Original", cacheSize = "150 MB"),
            onAction = {}
        )
    }
}
