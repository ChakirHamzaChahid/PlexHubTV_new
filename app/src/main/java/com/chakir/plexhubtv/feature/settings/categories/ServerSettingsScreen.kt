package com.chakir.plexhubtv.feature.settings.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
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
fun ServerSettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onNavigateBack: () -> Unit,
    isTvChannelsEnabled: Boolean,
    onTvChannelsEnabledChange: (Boolean) -> Unit,
) {
    var showServerDialog by remember { mutableStateOf(false) }
    val listFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { listFocusRequester.requestFocus() } catch (_: Exception) { }
    }

    Scaffold(
        modifier = Modifier.padding(top = 56.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_category_server), fontWeight = FontWeight.Bold) },
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
            // --- Server ---
            item {
                SettingsSection(stringResource(R.string.settings_section_server)) {
                    SettingsTile(
                        title = stringResource(R.string.settings_default_server),
                        subtitle = state.defaultServer,
                        onClick = {
                            if (state.availableServers.isNotEmpty()) {
                                showServerDialog = true
                            }
                        },
                        trailingContent = if (state.availableServers.isEmpty()) {
                            { Text(stringResource(R.string.settings_scanning), style = MaterialTheme.typography.bodySmall) }
                        } else {
                            null
                        },
                    )
                    SettingsTile(
                        title = stringResource(R.string.settings_check_server),
                        subtitle = stringResource(R.string.settings_check_server_subtitle),
                        icon = Icons.Filled.Info,
                        onClick = { onAction(SettingsAction.CheckServerStatus) },
                    )
                }
            }

            // --- Server Visibility ---
            item {
                SettingsSection(stringResource(R.string.settings_section_server_visibility)) {
                    if (state.availableServersMap.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_no_servers_found),
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
                    text = stringResource(R.string.settings_server_visibility_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            // --- TV Channels ---
            item {
                SettingsSection(stringResource(R.string.settings_tv_channels_title)) {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_tv_channels_title),
                        subtitle = stringResource(R.string.settings_tv_channels_summary),
                        isChecked = isTvChannelsEnabled,
                        onCheckedChange = onTvChannelsEnabledChange,
                    )
                }
            }
        }
    }

    // --- Dialogs ---
    if (showServerDialog) {
        SettingsDialog(
            title = stringResource(R.string.settings_default_server),
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
