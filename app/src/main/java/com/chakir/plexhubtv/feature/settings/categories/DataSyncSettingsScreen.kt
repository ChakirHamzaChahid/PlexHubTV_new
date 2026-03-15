package com.chakir.plexhubtv.feature.settings.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
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
fun DataSyncSettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    var showRatingSyncSourceDialog by remember { mutableStateOf(false) }
    var showRatingSyncDelayDialog by remember { mutableStateOf(false) }
    var showRatingSyncDailyLimitDialog by remember { mutableStateOf(false) }
    val listFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { listFocusRequester.requestFocus() } catch (_: Exception) { }
    }

    Scaffold(
        modifier = Modifier.padding(top = 56.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_category_data_sync), fontWeight = FontWeight.Bold) },
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
            // --- Data & Sync ---
            item {
                SettingsSection(stringResource(R.string.settings_section_data_sync)) {
                    SettingsTile(
                        title = stringResource(R.string.settings_synced_libraries),
                        subtitle = stringResource(R.string.settings_synced_libraries_subtitle),
                        icon = Icons.Filled.Cached,
                        onClick = { onAction(SettingsAction.ManageLibrarySelection) },
                    )
                    SettingsTile(
                        title = stringResource(R.string.settings_sync_library),
                        subtitle = if (state.isSyncing) state.syncMessage ?: stringResource(R.string.settings_syncing_message) else stringResource(R.string.settings_sync_library_subtitle),
                        icon = Icons.Filled.Cached,
                        onClick = { if (!state.isSyncing) onAction(SettingsAction.ForceSync) },
                        trailingContent = if (state.isSyncing) {
                            { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
                        } else {
                            null
                        },
                    )
                    SettingsTile(
                        title = stringResource(R.string.settings_sync_watchlist),
                        subtitle = if (state.isSyncingWatchlist) stringResource(R.string.settings_syncing_message) else stringResource(R.string.settings_sync_watchlist_subtitle),
                        icon = Icons.Filled.Cached,
                        onClick = { if (!state.isSyncingWatchlist) onAction(SettingsAction.SyncWatchlist) },
                        trailingContent = if (state.isSyncingWatchlist) {
                            { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
                        } else {
                            null
                        },
                    )
                    SettingsTile(
                        title = stringResource(R.string.settings_sync_ratings),
                        subtitle = if (state.isSyncingRatings) stringResource(R.string.settings_syncing_ratings) else state.ratingSyncMessage ?: stringResource(R.string.settings_sync_ratings_subtitle),
                        icon = Icons.Default.Star,
                        onClick = { if (!state.isSyncingRatings) onAction(SettingsAction.SyncRatings) },
                        trailingContent = if (state.isSyncingRatings) {
                            { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
                        } else {
                            null
                        },
                    )
                    SettingsTile(
                        title = stringResource(R.string.settings_clear_cache),
                        subtitle = stringResource(R.string.settings_clear_cache_subtitle, state.cacheSize),
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

            // --- Rating Sync Configuration ---
            item {
                SettingsSection(stringResource(R.string.settings_section_rating_sync)) {
                    SettingsTile(
                        title = stringResource(R.string.settings_rating_source),
                        subtitle = if (state.ratingSyncSource == "tmdb") stringResource(R.string.settings_rating_tmdb) else stringResource(R.string.settings_rating_omdb),
                        onClick = { showRatingSyncSourceDialog = true },
                    )
                    SettingsTile(
                        title = stringResource(R.string.settings_request_delay),
                        subtitle = stringResource(R.string.settings_request_delay_subtitle, state.ratingSyncDelay.toInt()),
                        onClick = { showRatingSyncDelayDialog = true },
                    )
                    SettingsSwitch(
                        title = stringResource(R.string.settings_enable_batching),
                        subtitle = stringResource(R.string.settings_enable_batching_subtitle),
                        isChecked = state.ratingSyncBatchingEnabled,
                        onCheckedChange = { onAction(SettingsAction.ToggleRatingSyncBatching(it)) },
                    )
                    if (state.ratingSyncBatchingEnabled) {
                        SettingsTile(
                            title = stringResource(R.string.settings_daily_limit),
                            subtitle = stringResource(R.string.settings_daily_limit_subtitle, state.ratingSyncDailyLimit),
                            onClick = { showRatingSyncDailyLimitDialog = true },
                        )
                        if (state.ratingSyncProgressSeries > 0 || state.ratingSyncProgressMovies > 0) {
                            SettingsTile(
                                title = stringResource(R.string.settings_reset_progress),
                                subtitle = stringResource(R.string.settings_reset_progress_subtitle, state.ratingSyncProgressSeries, state.ratingSyncProgressMovies),
                                titleColor = MaterialTheme.colorScheme.error,
                                onClick = { onAction(SettingsAction.ResetRatingSyncProgress) },
                            )
                        }
                    }
                }
                Text(
                    text = if (state.ratingSyncBatchingEnabled) {
                        stringResource(R.string.settings_rating_hint_batching, state.ratingSyncDailyLimit)
                    } else {
                        stringResource(R.string.settings_rating_hint_no_batching)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }

    // --- Dialogs ---
    if (showRatingSyncSourceDialog) {
        SettingsDialog(
            title = stringResource(R.string.settings_rating_source),
            options = listOf(stringResource(R.string.settings_rating_tmdb), stringResource(R.string.settings_rating_omdb)),
            currentValue = if (state.ratingSyncSource == "tmdb") stringResource(R.string.settings_rating_tmdb) else stringResource(R.string.settings_rating_omdb),
            onDismissRequest = { showRatingSyncSourceDialog = false },
            onOptionSelected = {
                val source = if (it.startsWith("TMDb")) "tmdb" else "omdb"
                onAction(SettingsAction.ChangeRatingSyncSource(source))
                showRatingSyncSourceDialog = false
            },
        )
    }

    if (showRatingSyncDelayDialog) {
        val delay100 = stringResource(R.string.settings_delay_100)
        val delay250 = stringResource(R.string.settings_delay_250)
        val delay500 = stringResource(R.string.settings_delay_500)
        val delay1000 = stringResource(R.string.settings_delay_1000)
        SettingsDialog(
            title = stringResource(R.string.settings_request_delay),
            options = listOf(delay100, delay250, delay500, delay1000),
            currentValue = when (state.ratingSyncDelay) {
                100L -> delay100; 250L -> delay250; 500L -> delay500; 1000L -> delay1000
                else -> "${state.ratingSyncDelay}ms"
            },
            onDismissRequest = { showRatingSyncDelayDialog = false },
            onOptionSelected = {
                val delay = when (it) {
                    delay100 -> 100L; delay250 -> 250L; delay500 -> 500L; delay1000 -> 1000L; else -> 250L
                }
                onAction(SettingsAction.ChangeRatingSyncDelay(delay))
                showRatingSyncDelayDialog = false
            },
        )
    }

    if (showRatingSyncDailyLimitDialog) {
        val limit500 = stringResource(R.string.settings_limit_500)
        val limit900 = stringResource(R.string.settings_limit_900)
        val limit1500 = stringResource(R.string.settings_limit_1500)
        val limit2500 = stringResource(R.string.settings_limit_2500)
        SettingsDialog(
            title = stringResource(R.string.settings_daily_request_limit),
            options = listOf(limit500, limit900, limit1500, limit2500),
            currentValue = when (state.ratingSyncDailyLimit) {
                500 -> limit500; 900 -> limit900; 1500 -> limit1500; 2500 -> limit2500
                else -> "${state.ratingSyncDailyLimit} req/day"
            },
            onDismissRequest = { showRatingSyncDailyLimitDialog = false },
            onOptionSelected = {
                val limit = when (it) {
                    limit500 -> 500; limit900 -> 900; limit1500 -> 1500; limit2500 -> 2500; else -> 900
                }
                onAction(SettingsAction.ChangeRatingSyncDailyLimit(limit))
                showRatingSyncDailyLimitDialog = false
            },
        )
    }
}
