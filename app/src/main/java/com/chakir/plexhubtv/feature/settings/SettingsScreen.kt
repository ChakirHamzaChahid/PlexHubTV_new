package com.chakir.plexhubtv.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.core.designsystem.PlexHubTheme

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
) {
    val uiState by viewModel.uiState.collectAsState()
    val events = viewModel.navigationEvents

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is SettingsNavigationEvent.NavigateBack -> onNavigateBack()
                is SettingsNavigationEvent.NavigateToLogin -> onNavigateToLogin()
                is SettingsNavigationEvent.NavigateToServerStatus -> onNavigateToServerStatus()
            }
        }
    }

    SettingsScreen(
        state = uiState,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    // Dialog States
    var showQualityDialog by remember { mutableStateOf(false) }
    var showPlayerEngineDialog by remember { mutableStateOf(false) }
    var showAudioLangDialog by remember { mutableStateOf(false) }
    var showSubtitleLangDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onAction(SettingsAction.Back) }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background, // Match background for seamless look
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // --- Appearance ---
            item {
                SettingsSection("Appearance") {
                    SettingsTile(
                        title = "App Theme",
                        subtitle = state.theme.name,
                        onClick = { showThemeDialog = true },
                    )
                }
            }

            // --- Playback ---
            item {
                SettingsSection("Playback") {
                    SettingsTile(
                        title = "Video Quality",
                        subtitle = state.videoQuality,
                        onClick = { showQualityDialog = true },
                    )
                    SettingsTile(
                        title = "Player Engine",
                        subtitle = state.playerEngine,
                        onClick = { showPlayerEngineDialog = true },
                    )
                }
            }

            // --- Languages ---
            item {
                SettingsSection("Languages") {
                    // Find display name for stored code
                    val audioOptions = getAudioLanguageOptions()
                    val currentAudioDisplay =
                        audioOptions.find { it.second == state.preferredAudioLanguage }?.first
                            ?: state.preferredAudioLanguage ?: "Original"

                    SettingsTile(
                        title = "Preferred Audio Language",
                        subtitle = currentAudioDisplay,
                        onClick = { showAudioLangDialog = true },
                    )

                    val subtitleOptions = getSubtitleLanguageOptions()
                    val currentSubtitleDisplay =
                        subtitleOptions.find { it.second == state.preferredSubtitleLanguage }?.first
                            ?: state.preferredSubtitleLanguage ?: "None"

                    SettingsTile(
                        title = "Preferred Subtitle Language",
                        subtitle = currentSubtitleDisplay,
                        onClick = { showSubtitleLangDialog = true },
                    )
                }
            }

            // --- Server ---
            item {
                SettingsSection("Server") {
                    SettingsTile(
                        title = "Default Server",
                        subtitle = state.defaultServer,
                        onClick = {
                            if (state.availableServers.isNotEmpty()) {
                                showServerDialog = true
                            }
                        },
                        trailingContent =
                            if (state.availableServers.isEmpty()) {
                                {
                                    Text("Scanning...", style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                null
                            },
                    )
                    SettingsTile(
                        title = "Check Server Status",
                        subtitle = "View connection details and latency",
                        icon = Icons.Filled.Info,
                        onClick = { onAction(SettingsAction.CheckServerStatus) },
                    )
                }
            }

            // --- Server Visibility ---
            item {
                SettingsSection("Server Visibility") {
                    if (state.availableServersMap.isEmpty()) {
                        Text(
                            text = "No servers found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        state.availableServersMap.entries.forEach { (name, id) ->
                            SettingsSwitch(
                                title = name,
                                isChecked = !state.excludedServerIds.contains(id),
                                onCheckedChange = { onAction(SettingsAction.ToggleServerExclusion(id)) },
                            )
                        }
                    }
                }
                Text(
                    text = "Uncheck servers to hide them from unified libraries.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            // --- Data & Sync ---
            item {
                SettingsSection("Data & Sync") {
                    SettingsTile(
                        title = "Synchronise Library",
                        subtitle = if (state.isSyncing) state.syncMessage ?: "Syncing..." else "Update local database from Plex",
                        icon = Icons.Filled.Cached,
                        onClick = { onAction(SettingsAction.ForceSync) },
                        trailingContent =
                            if (state.isSyncing) {
                                {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            } else {
                                null
                            },
                    )

                    SettingsTile(
                        title = "Sync Watchlist",
                        subtitle = "Import Plex watchlist favorites",
                        icon = Icons.Filled.Cached,
                        onClick = { onAction(SettingsAction.SyncWatchlist) },
                    )

                    SettingsTile(
                        title = "Sync Media Ratings",
                        subtitle = if (state.isSyncingRatings) "Syncing ratings..." else state.ratingSyncMessage ?: "Update IMDb/TMDb ratings",
                        icon = Icons.Default.Star,
                        onClick = { onAction(SettingsAction.SyncRatings) },
                        trailingContent =
                            if (state.isSyncingRatings) {
                                {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            } else {
                                null
                            },
                    )

                    SettingsTile(
                        title = "Clear Cache & Data",
                        subtitle = "Used: ${state.cacheSize}",
                        icon = Icons.Filled.Delete,
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { onAction(SettingsAction.ClearCache) },
                    )
                }
                if (state.syncError != null) {
                    Text(
                        text = state.syncError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }

            // --- External API Keys ---
            item {
                SettingsSection("External API Keys") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Configure API keys for rating sync feature",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // TMDb API Key
                        var tmdbKey by remember(state.tmdbApiKey) { mutableStateOf(state.tmdbApiKey) }
                        OutlinedTextField(
                            value = tmdbKey,
                            onValueChange = { tmdbKey = it },
                            label = { Text("TMDb API Key") },
                            placeholder = { Text("Enter TMDb API key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (tmdbKey != state.tmdbApiKey) {
                                    IconButton(onClick = { onAction(SettingsAction.SaveTmdbApiKey(tmdbKey)) }) {
                                        Icon(Icons.Default.Done, contentDescription = "Save")
                                    }
                                }
                            },
                        )

                        Text(
                            text = "Get your key at themoviedb.org/settings/api",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                        )

                        // OMDb API Key
                        var omdbKey by remember(state.omdbApiKey) { mutableStateOf(state.omdbApiKey) }
                        OutlinedTextField(
                            value = omdbKey,
                            onValueChange = { omdbKey = it },
                            label = { Text("OMDb API Key") },
                            placeholder = { Text("Enter OMDb API key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (omdbKey != state.omdbApiKey) {
                                    IconButton(onClick = { onAction(SettingsAction.SaveOmdbApiKey(omdbKey)) }) {
                                        Icon(Icons.Default.Done, contentDescription = "Save")
                                    }
                                }
                            },
                        )

                        Text(
                            text = "Get your key at omdbapi.com/apikey.aspx",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                        )
                    }
                }
            }

            // --- Account ---
            item {
                SettingsSection("Account") {
                    SettingsTile(
                        title = "Logout",
                        icon = Icons.Filled.Logout,
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { onAction(SettingsAction.Logout) },
                    )
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Version ${state.appVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }

    // --- Dialogs ---

    if (showQualityDialog) {
        val options = listOf("Original", "20 Mbps 1080p", "12 Mbps 1080p", "8 Mbps 1080p", "4 Mbps 720p", "3 Mbps 720p")
        SettingsDialog(
            title = "Video Quality",
            options = options,
            currentValue = state.videoQuality,
            onDismissRequest = { showQualityDialog = false },
            onOptionSelected = {
                onAction(SettingsAction.ChangeVideoQuality(it))
                showQualityDialog = false
            },
        )
    }

    if (showPlayerEngineDialog) {
        val options = listOf("ExoPlayer", "MPV")
        SettingsDialog(
            title = "Player Engine",
            options = options,
            currentValue = state.playerEngine,
            onDismissRequest = { showPlayerEngineDialog = false },
            onOptionSelected = {
                onAction(SettingsAction.ChangePlayerEngine(it))
                showPlayerEngineDialog = false
            },
        )
    }

    if (showThemeDialog) {
        val options = AppTheme.values().map { it.name }
        SettingsDialog(
            title = "App Theme",
            options = options,
            currentValue = state.theme.name,
            onDismissRequest = { showThemeDialog = false },
            onOptionSelected = {
                onAction(SettingsAction.ChangeTheme(AppTheme.valueOf(it)))
                showThemeDialog = false
            },
        )
    }

    if (showAudioLangDialog) {
        val audioOptions = getAudioLanguageOptions()
        SettingsDialog(
            title = "Preferred Audio Language",
            options = audioOptions.map { it.first },
            currentValue = audioOptions.find { it.second == state.preferredAudioLanguage }?.first ?: "Original",
            onDismissRequest = { showAudioLangDialog = false },
            onOptionSelected = { selectedName ->
                val isoCode = audioOptions.find { it.first == selectedName }?.second
                onAction(SettingsAction.ChangePreferredAudioLanguage(isoCode))
                showAudioLangDialog = false
            },
        )
    }

    if (showSubtitleLangDialog) {
        val subtitleOptions = getSubtitleLanguageOptions()
        SettingsDialog(
            title = "Preferred Subtitle Language",
            options = subtitleOptions.map { it.first },
            currentValue = subtitleOptions.find { it.second == state.preferredSubtitleLanguage }?.first ?: "None",
            onDismissRequest = { showSubtitleLangDialog = false },
            onOptionSelected = { selectedName ->
                val isoCode = subtitleOptions.find { it.first == selectedName }?.second
                onAction(SettingsAction.ChangePreferredSubtitleLanguage(isoCode))
                showSubtitleLangDialog = false
            },
        )
    }

    if (showServerDialog) {
        SettingsDialog(
            title = "Default Server",
            options = state.availableServers,
            currentValue = state.defaultServer,
            onDismissRequest = { showServerDialog = false },
            onOptionSelected = {
                onAction(SettingsAction.SelectDefaultServer(it))
                showServerDialog = false
            },
        )
    }
}

// Helpers for Language Options
private fun getAudioLanguageOptions() =
    listOf(
        "Original" to null,
        "English" to "eng",
        "French" to "fra",
        "German" to "deu",
        "Spanish" to "spa",
        "Italian" to "ita",
        "Japanese" to "jpn",
        "Korean" to "kor",
        "Russian" to "rus",
        "Portuguese" to "por",
    )

private fun getSubtitleLanguageOptions() =
    listOf(
        "None" to null,
        "English" to "eng",
        "French" to "fra",
        "German" to "deu",
        "Spanish" to "spa",
        "Italian" to "ita",
        "Japanese" to "jpn",
        "Korean" to "kor",
        "Russian" to "rus",
        "Portuguese" to "por",
    )

@Preview(showBackground = true)
@Composable
fun PreviewSettingsScreen() {
    PlexHubTheme {
        SettingsScreen(
            state = SettingsUiState(videoQuality = "Original", cacheSize = "150 MB", availableServers = listOf("Plex Server 1")),
            onAction = {},
        )
    }
}
