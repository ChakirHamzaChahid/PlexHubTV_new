package com.chakir.plexhubtv.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.BuildConfig
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
    onNavigateToDebug: () -> Unit = {},
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
        onNavigateToDebug = onNavigateToDebug,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onNavigateToDebug: () -> Unit = {},
) {
    // Dialog States
    var showQualityDialog by remember { mutableStateOf(false) }
    var showPlayerEngineDialog by remember { mutableStateOf(false) }
    var showAudioLangDialog by remember { mutableStateOf(false) }
    var showSubtitleLangDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showApiKeysDialog by remember { mutableStateOf(false) }
    var showRatingSyncSourceDialog by remember { mutableStateOf(false) }
    var showRatingSyncDelayDialog by remember { mutableStateOf(false) }
    var showRatingSyncDailyLimitDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.padding(top = 56.dp), // Clear Netflix TopBar overlay
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
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .testTag("screen_settings")
                    .semantics { contentDescription = "Écran des paramètres" },
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

            // --- External API Keys (Submenu) ---
            item {
                SettingsSection("External Services") {
                    SettingsTile(
                        title = "API Keys Configuration",
                        subtitle = "Configure TMDb and OMDb API keys",
                        icon = Icons.Default.Star,
                        onClick = { showApiKeysDialog = true },
                    )
                }
            }

            // --- Rating Sync Configuration ---
            item {
                SettingsSection("Rating Sync Configuration") {
                    SettingsTile(
                        title = "Movie Rating Source",
                        subtitle = if (state.ratingSyncSource == "tmdb") "TMDb (Recommended)" else "OMDb (IMDb ratings)",
                        onClick = { showRatingSyncSourceDialog = true },
                    )
                    SettingsTile(
                        title = "Request Delay",
                        subtitle = "${state.ratingSyncDelay}ms between requests",
                        onClick = { showRatingSyncDelayDialog = true },
                    )
                    SettingsSwitch(
                        title = "Enable Batching (Multi-day sync)",
                        subtitle = "Sync large libraries over multiple days",
                        isChecked = state.ratingSyncBatchingEnabled,
                        onCheckedChange = { onAction(SettingsAction.ToggleRatingSyncBatching(it)) },
                    )
                    if (state.ratingSyncBatchingEnabled) {
                        SettingsTile(
                            title = "Daily Limit",
                            subtitle = "${state.ratingSyncDailyLimit} requests/day",
                            onClick = { showRatingSyncDailyLimitDialog = true },
                        )
                        if (state.ratingSyncProgressSeries > 0 || state.ratingSyncProgressMovies > 0) {
                            SettingsTile(
                                title = "Reset Progress",
                                subtitle = "Series: ${state.ratingSyncProgressSeries}, Movies: ${state.ratingSyncProgressMovies}",
                                titleColor = MaterialTheme.colorScheme.error,
                                onClick = { onAction(SettingsAction.ResetRatingSyncProgress) },
                            )
                        }
                    }
                }
                Text(
                    text = "Configure how ratings are scraped from external APIs. " +
                        if (state.ratingSyncBatchingEnabled) {
                            "Batching enabled: syncs ${state.ratingSyncDailyLimit} items per day to respect API limits."
                        } else {
                            "TMDb has generous limits (50 req/sec), OMDb free tier has 1000 req/day."
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            // --- IPTV ---
            item {
                SettingsSection("IPTV") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Configure your IPTV playlist URL",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        var iptvUrl by remember(state.iptvPlaylistUrl) { mutableStateOf(state.iptvPlaylistUrl) }
                        OutlinedTextField(
                            value = iptvUrl,
                            onValueChange = { iptvUrl = it },
                            label = { Text("IPTV Playlist URL") },
                            placeholder = { Text("http://example.com/playlist.m3u") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (iptvUrl != state.iptvPlaylistUrl) {
                                    IconButton(onClick = { onAction(SettingsAction.SaveIptvPlaylistUrl(iptvUrl)) }) {
                                        Icon(Icons.Default.Done, contentDescription = "Save")
                                    }
                                }
                            },
                        )
                    }
                }
            }

            // --- Debug (DEBUG builds only) ---
            if (BuildConfig.DEBUG) {
                item {
                    SettingsSection("Debug") {
                        SettingsTile(
                            title = "Debug Information",
                            subtitle = "View system diagnostics and logs",
                            icon = Icons.Filled.BugReport,
                            onClick = onNavigateToDebug,
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

    if (showApiKeysDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showApiKeysDialog = false },
            title = {
                Text(
                    text = "API Keys Configuration",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Configure your TMDb and OMDb API keys to enable enhanced metadata and ratings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // TMDb API Key
                    var tmdbKey by remember(state.tmdbApiKey) { mutableStateOf(state.tmdbApiKey) }
                    OutlinedTextField(
                        value = tmdbKey,
                        onValueChange = { tmdbKey = it },
                        label = { Text("TMDb API Key") },
                        placeholder = { Text("Enter your TMDb API key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (tmdbKey != state.tmdbApiKey && tmdbKey.isNotBlank()) {
                                IconButton(onClick = {
                                    onAction(SettingsAction.SaveTmdbApiKey(tmdbKey))
                                }) {
                                    Icon(Icons.Default.Done, contentDescription = "Save TMDb Key")
                                }
                            }
                        }
                    )
                    Text(
                        text = "Get your API key from: https://www.themoviedb.org/settings/api",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // OMDb API Key
                    var omdbKey by remember(state.omdbApiKey) { mutableStateOf(state.omdbApiKey) }
                    OutlinedTextField(
                        value = omdbKey,
                        onValueChange = { omdbKey = it },
                        label = { Text("OMDb API Key") },
                        placeholder = { Text("Enter your OMDb API key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (omdbKey != state.omdbApiKey && omdbKey.isNotBlank()) {
                                IconButton(onClick = {
                                    onAction(SettingsAction.SaveOmdbApiKey(omdbKey))
                                }) {
                                    Icon(Icons.Default.Done, contentDescription = "Save OMDb Key")
                                }
                            }
                        }
                    )
                    Text(
                        text = "Get your API key from: https://www.omdbapi.com/apikey.aspx",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showApiKeysDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Rating Sync Source Dialog
    if (showRatingSyncSourceDialog) {
        SettingsDialog(
            title = "Movie Rating Source",
            options = listOf("TMDb (Recommended)", "OMDb (IMDb ratings)"),
            currentValue = if (state.ratingSyncSource == "tmdb") "TMDb (Recommended)" else "OMDb (IMDb ratings)",
            onDismissRequest = { showRatingSyncSourceDialog = false },
            onOptionSelected = {
                val source = if (it.startsWith("TMDb")) "tmdb" else "omdb"
                onAction(SettingsAction.ChangeRatingSyncSource(source))
                showRatingSyncSourceDialog = false
            },
        )
    }

    // Rating Sync Delay Dialog
    if (showRatingSyncDelayDialog) {
        SettingsDialog(
            title = "Request Delay",
            options = listOf("100ms (Fast)", "250ms (Default)", "500ms (Safe)", "1000ms (Very Safe)"),
            currentValue = when (state.ratingSyncDelay) {
                100L -> "100ms (Fast)"
                250L -> "250ms (Default)"
                500L -> "500ms (Safe)"
                1000L -> "1000ms (Very Safe)"
                else -> "${state.ratingSyncDelay}ms"
            },
            onDismissRequest = { showRatingSyncDelayDialog = false },
            onOptionSelected = {
                val delay = when (it) {
                    "100ms (Fast)" -> 100L
                    "250ms (Default)" -> 250L
                    "500ms (Safe)" -> 500L
                    "1000ms (Very Safe)" -> 1000L
                    else -> 250L
                }
                onAction(SettingsAction.ChangeRatingSyncDelay(delay))
                showRatingSyncDelayDialog = false
            },
        )
    }

    // Rating Sync Daily Limit Dialog
    if (showRatingSyncDailyLimitDialog) {
        SettingsDialog(
            title = "Daily Request Limit",
            options = listOf("500 req/day", "900 req/day (Default)", "1500 req/day", "2500 req/day"),
            currentValue = when (state.ratingSyncDailyLimit) {
                500 -> "500 req/day"
                900 -> "900 req/day (Default)"
                1500 -> "1500 req/day"
                2500 -> "2500 req/day"
                else -> "${state.ratingSyncDailyLimit} req/day"
            },
            onDismissRequest = { showRatingSyncDailyLimitDialog = false },
            onOptionSelected = {
                val limit = when (it) {
                    "500 req/day" -> 500
                    "900 req/day (Default)" -> 900
                    "1500 req/day" -> 1500
                    "2500 req/day" -> 2500
                    else -> 900
                }
                onAction(SettingsAction.ChangeRatingSyncDailyLimit(limit))
                showRatingSyncDailyLimitDialog = false
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
