package com.chakir.plexhubtv.feature.settings.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.feature.settings.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesSettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    var showApiKeysDialog by remember { mutableStateOf(false) }
    var showAddBackendDialog by remember { mutableStateOf(false) }
    var showRemoveBackendDialog by remember { mutableStateOf<String?>(null) }
    val listFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { listFocusRequester.requestFocus() } catch (_: Exception) { }
    }

    Scaffold(
        modifier = Modifier.padding(top = 56.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_category_services), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back_description))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .focusRequester(listFocusRequester),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // --- External API Keys ---
            item {
                SettingsSection(stringResource(R.string.settings_section_external)) {
                    SettingsTile(
                        title = stringResource(R.string.settings_api_keys),
                        subtitle = stringResource(R.string.settings_api_keys_subtitle),
                        icon = Icons.Default.Star,
                        onClick = { showApiKeysDialog = true },
                    )
                }
            }

            // --- PlexHub Backend ---
            item {
                SettingsSection(stringResource(R.string.settings_backend_section)) {
                    state.backendServers.forEach { server ->
                        SettingsTile(
                            title = server.label,
                            subtitle = server.baseUrl,
                            icon = Icons.Filled.Cloud,
                            onClick = { showRemoveBackendDialog = server.id },
                        )
                    }

                    SettingsTile(
                        title = stringResource(R.string.settings_add_backend),
                        icon = Icons.Filled.AddCircle,
                        onClick = { showAddBackendDialog = true },
                    )

                    if (state.backendServers.isNotEmpty()) {
                        SettingsTile(
                            title = stringResource(R.string.settings_backend_sync),
                            subtitle = if (state.isSyncingBackend) {
                                stringResource(R.string.settings_syncing_message)
                            } else {
                                state.backendSyncMessage ?: stringResource(R.string.settings_backend_sync_subtitle)
                            },
                            icon = Icons.Filled.Cached,
                            onClick = { if (!state.isSyncingBackend) onAction(SettingsAction.SyncBackend) },
                            trailingContent = if (state.isSyncingBackend) {
                                { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
                            } else {
                                null
                            },
                        )

                        SettingsTile(
                            title = stringResource(R.string.settings_backend_trigger_sync),
                            subtitle = if (state.isTriggeringBackendSync) {
                                stringResource(R.string.settings_syncing_message)
                            } else {
                                state.backendTriggerSyncMessage ?: stringResource(R.string.settings_backend_trigger_sync_subtitle)
                            },
                            icon = Icons.Filled.Sync,
                            onClick = { if (!state.isTriggeringBackendSync) onAction(SettingsAction.TriggerBackendXtreamSync) },
                            trailingContent = if (state.isTriggeringBackendSync) {
                                { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
                            } else {
                                null
                            },
                        )

                        SettingsTile(
                            title = stringResource(R.string.settings_backend_health),
                            subtitle = if (state.isCheckingBackendHealth) {
                                stringResource(R.string.settings_syncing_message)
                            } else {
                                state.backendHealthMessage ?: stringResource(R.string.settings_backend_health_subtitle)
                            },
                            icon = Icons.Filled.Info,
                            onClick = { if (!state.isCheckingBackendHealth) onAction(SettingsAction.CheckBackendHealth) },
                            trailingContent = if (state.isCheckingBackendHealth) {
                                { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
                            } else {
                                null
                            },
                        )

                        // Backend-managed Xtream accounts — category management
                        val backendAccounts = state.xtreamAccounts.filter { it.isBackendManaged }
                        if (backendAccounts.isNotEmpty()) {
                            backendAccounts.forEach { account ->
                                val summary = state.xtreamCategorySummaries[account.id]
                                val subtitle = if (summary != null && (summary.first > 0 || summary.second > 0)) {
                                    stringResource(R.string.xtream_category_summary, summary.first, summary.second)
                                } else {
                                    stringResource(R.string.settings_xtream_categories_subtitle)
                                }
                                SettingsTile(
                                    title = "${account.label} - ${stringResource(R.string.settings_xtream_categories)}",
                                    subtitle = subtitle,
                                    icon = Icons.Filled.LiveTv,
                                    onClick = { onAction(SettingsAction.ManageXtreamCategories(account.id)) },
                                )
                            }
                        }
                    }

                    state.backendConfigMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // --- Jellyfin ---
            item {
                SettingsSection("Jellyfin Servers") {
                    state.jellyfinServers.forEach { server ->
                        SettingsTile(
                            title = server.name,
                            subtitle = "${server.baseUrl} (${server.userName})",
                            icon = Icons.Filled.Dns,
                            onClick = {},
                        )
                    }

                    SettingsTile(
                        title = "Manage Jellyfin Servers",
                        subtitle = if (state.jellyfinServers.isEmpty()) "Add a Jellyfin server" else "${state.jellyfinServers.size} server(s) configured",
                        icon = Icons.Filled.AddCircle,
                        onClick = { onAction(SettingsAction.ManageJellyfinServers) },
                    )

                    if (state.jellyfinServers.isNotEmpty()) {
                        SettingsTile(
                            title = "Sync Jellyfin",
                            subtitle = if (state.isSyncingJellyfin) {
                                stringResource(R.string.settings_syncing_message)
                            } else {
                                state.jellyfinSyncMessage ?: "Sync all Jellyfin libraries"
                            },
                            icon = Icons.Filled.Cached,
                            onClick = { if (!state.isSyncingJellyfin) onAction(SettingsAction.SyncJellyfin) },
                            trailingContent = if (state.isSyncingJellyfin) {
                                { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            // --- IPTV & Xtream (direct accounts only, no backend) ---
            item {
                SettingsSection(stringResource(R.string.settings_section_iptv)) {
                    SettingsTile(
                        title = stringResource(R.string.settings_xtream_accounts),
                        subtitle = stringResource(R.string.settings_xtream_accounts_subtitle),
                        icon = Icons.Filled.LiveTv,
                        onClick = { onAction(SettingsAction.ManageXtreamAccounts) },
                    )

                    // Only show direct Xtream accounts here (not backend-managed ones)
                    val directAccounts = state.xtreamAccounts.filter { !it.isBackendManaged }
                    directAccounts.forEach { account ->
                        val summary = state.xtreamCategorySummaries[account.id]
                        val subtitle = if (summary != null && (summary.first > 0 || summary.second > 0)) {
                            stringResource(R.string.xtream_category_summary, summary.first, summary.second)
                        } else {
                            stringResource(R.string.settings_xtream_categories_subtitle)
                        }
                        SettingsTile(
                            title = "${account.label} - ${stringResource(R.string.settings_xtream_categories)}",
                            subtitle = subtitle,
                            icon = Icons.Filled.LiveTv,
                            onClick = { onAction(SettingsAction.ManageXtreamCategories(account.id)) },
                        )
                    }

                    SettingsTile(
                        title = stringResource(R.string.settings_sync_xtream),
                        subtitle = if (state.isSyncingXtream) {
                            stringResource(R.string.settings_syncing_message)
                        } else {
                            state.xtreamSyncMessage ?: stringResource(R.string.settings_sync_xtream_subtitle)
                        },
                        icon = Icons.Filled.Cached,
                        onClick = { if (!state.isSyncingXtream) onAction(SettingsAction.SyncXtream) },
                        trailingContent = if (state.isSyncingXtream) {
                            { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
                        } else {
                            null
                        },
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_iptv_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        var iptvUrl by remember(state.iptvPlaylistUrl) { mutableStateOf(state.iptvPlaylistUrl) }
                        OutlinedTextField(
                            value = iptvUrl,
                            onValueChange = { iptvUrl = it },
                            label = { Text(stringResource(R.string.settings_iptv_url_label)) },
                            placeholder = { Text(stringResource(R.string.settings_iptv_url_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (iptvUrl != state.iptvPlaylistUrl) {
                                    IconButton(onClick = { onAction(SettingsAction.SaveIptvPlaylistUrl(iptvUrl)) }) {
                                        Icon(Icons.Default.Done, contentDescription = stringResource(R.string.settings_save_description))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // --- Dialogs ---
    if (showApiKeysDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeysDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_api_keys),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_api_keys_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    var tmdbKey by remember(state.tmdbApiKey) { mutableStateOf(state.tmdbApiKey) }
                    OutlinedTextField(
                        value = tmdbKey,
                        onValueChange = { tmdbKey = it },
                        label = { Text(stringResource(R.string.settings_tmdb_api_key)) },
                        placeholder = { Text(stringResource(R.string.settings_tmdb_api_key_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (tmdbKey != state.tmdbApiKey && tmdbKey.isNotBlank()) {
                                IconButton(onClick = { onAction(SettingsAction.SaveTmdbApiKey(tmdbKey)) }) {
                                    Icon(Icons.Default.Done, contentDescription = stringResource(R.string.settings_save_tmdb_description))
                                }
                            }
                        },
                    )
                    Text(
                        text = stringResource(R.string.settings_tmdb_api_key_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    var omdbKey by remember(state.omdbApiKey) { mutableStateOf(state.omdbApiKey) }
                    OutlinedTextField(
                        value = omdbKey,
                        onValueChange = { omdbKey = it },
                        label = { Text(stringResource(R.string.settings_omdb_api_key)) },
                        placeholder = { Text(stringResource(R.string.settings_omdb_api_key_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (omdbKey != state.omdbApiKey && omdbKey.isNotBlank()) {
                                IconButton(onClick = { onAction(SettingsAction.SaveOmdbApiKey(omdbKey)) }) {
                                    Icon(Icons.Default.Done, contentDescription = stringResource(R.string.settings_save_omdb_description))
                                }
                            }
                        },
                    )
                    Text(
                        text = stringResource(R.string.settings_omdb_api_key_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showApiKeysDialog = false }) {
                    Text(stringResource(R.string.settings_close_description))
                }
            },
        )
    }

    if (showAddBackendDialog) {
        var label by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddBackendDialog = false },
            title = { Text(stringResource(R.string.settings_add_backend)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(stringResource(R.string.settings_backend_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.settings_backend_url)) },
                        placeholder = { Text(stringResource(R.string.settings_backend_url_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (label.isNotBlank() && url.isNotBlank()) {
                            onAction(SettingsAction.AddBackendServer(label, url))
                            showAddBackendDialog = false
                        }
                    },
                    enabled = !state.isTestingBackend,
                ) {
                    if (state.isTestingBackend) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBackendDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    showRemoveBackendDialog?.let { serverId ->
        AlertDialog(
            onDismissRequest = { showRemoveBackendDialog = null },
            title = { Text(stringResource(R.string.settings_backend_remove_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onAction(SettingsAction.RemoveBackendServer(serverId))
                    showRemoveBackendDialog = null
                }) {
                    Text(stringResource(android.R.string.ok), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveBackendDialog = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
