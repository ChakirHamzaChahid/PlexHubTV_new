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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.BuildConfig
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.designsystem.PlexHubTheme

// TODO: Replace with your hosted privacy policy URL (e.g. GitHub Pages)
private const val PRIVACY_POLICY_URL = "https://github.com/chakir-elarram/PlexHubTV/blob/main/docs/privacy-policy-en.md"

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
    val isTvChannelsEnabled by viewModel.isTvChannelsEnabled.collectAsState()

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
        isTvChannelsEnabled = isTvChannelsEnabled,
        onTvChannelsEnabledChange = viewModel::setTvChannelsEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onNavigateToDebug: () -> Unit = {},
    isTvChannelsEnabled: Boolean = true,
    onTvChannelsEnabledChange: (Boolean) -> Unit = {},
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
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onAction(SettingsAction.Back) }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back_description))
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
        val screenDescription = stringResource(R.string.settings_screen_description)
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .testTag("screen_settings")
                    .semantics { contentDescription = screenDescription },
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // --- Appearance ---
            item {
                SettingsSection(stringResource(R.string.settings_section_appearance)) {
                    SettingsTile(
                        title = stringResource(R.string.settings_app_theme),
                        subtitle = state.theme.name,
                        onClick = { showThemeDialog = true },
                    )
                }
            }

            // --- Playback ---
            item {
                SettingsSection(stringResource(R.string.settings_section_playback)) {
                    SettingsTile(
                        title = stringResource(R.string.settings_video_quality),
                        subtitle = state.videoQuality,
                        onClick = { showQualityDialog = true },
                    )
                    SettingsTile(
                        title = stringResource(R.string.settings_player_engine),
                        subtitle = state.playerEngine,
                        onClick = { showPlayerEngineDialog = true },
                    )
                }
            }

            // --- Languages ---
            item {
                SettingsSection(stringResource(R.string.settings_section_languages)) {
                    // Find display name for stored code
                    val audioOptions = getAudioLanguageOptions()
                    val currentAudioDisplay =
                        audioOptions.find { it.second == state.preferredAudioLanguage }?.first
                            ?: state.preferredAudioLanguage ?: stringResource(R.string.settings_lang_original)

                    SettingsTile(
                        title = stringResource(R.string.settings_preferred_audio),
                        subtitle = currentAudioDisplay,
                        onClick = { showAudioLangDialog = true },
                    )

                    val subtitleOptions = getSubtitleLanguageOptions()
                    val currentSubtitleDisplay =
                        subtitleOptions.find { it.second == state.preferredSubtitleLanguage }?.first
                            ?: state.preferredSubtitleLanguage ?: stringResource(R.string.settings_lang_none)

                    SettingsTile(
                        title = stringResource(R.string.settings_preferred_subtitle),
                        subtitle = currentSubtitleDisplay,
                        onClick = { showSubtitleLangDialog = true },
                    )
                }
            }

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
                        trailingContent =
                            if (state.availableServers.isEmpty()) {
                                {
                                    Text(stringResource(R.string.settings_scanning), style = MaterialTheme.typography.bodySmall)
                                }
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
                        onCheckedChange = onTvChannelsEnabledChange
                    )
                }
            }

            // --- Data & Sync ---
            item {
                SettingsSection(stringResource(R.string.settings_section_data_sync)) {
                    SettingsTile(
                        title = stringResource(R.string.settings_sync_library),
                        subtitle = if (state.isSyncing) state.syncMessage ?: stringResource(R.string.settings_syncing_message) else stringResource(R.string.settings_sync_library_subtitle),
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
                        title = stringResource(R.string.settings_sync_watchlist),
                        subtitle = stringResource(R.string.settings_sync_watchlist_subtitle),
                        icon = Icons.Filled.Cached,
                        onClick = { onAction(SettingsAction.SyncWatchlist) },
                    )

                    SettingsTile(
                        title = stringResource(R.string.settings_sync_ratings),
                        subtitle = if (state.isSyncingRatings) stringResource(R.string.settings_syncing_ratings) else state.ratingSyncMessage ?: stringResource(R.string.settings_sync_ratings_subtitle),
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

            // --- External API Keys (Submenu) ---
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

            // --- IPTV ---
            item {
                SettingsSection(stringResource(R.string.settings_section_iptv)) {
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

            // --- Legal ---
            item {
                val uriHandler = LocalUriHandler.current
                SettingsSection(stringResource(R.string.settings_section_legal)) {
                    SettingsTile(
                        title = stringResource(R.string.settings_privacy_policy),
                        subtitle = stringResource(R.string.settings_privacy_policy_subtitle),
                        onClick = {
                            uriHandler.openUri(PRIVACY_POLICY_URL)
                        },
                    )
                }
            }

            // --- Debug (DEBUG builds only) ---
            if (BuildConfig.DEBUG) {
                item {
                    SettingsSection(stringResource(R.string.settings_section_debug)) {
                        SettingsTile(
                            title = stringResource(R.string.settings_debug_info),
                            subtitle = stringResource(R.string.settings_debug_info_subtitle),
                            icon = Icons.Filled.BugReport,
                            onClick = onNavigateToDebug,
                        )
                    }
                }
            }

            // --- Account ---
            item {
                SettingsSection(stringResource(R.string.settings_section_account)) {
                    SettingsTile(
                        title = stringResource(R.string.settings_logout),
                        icon = Icons.Filled.Logout,
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { onAction(SettingsAction.Logout) },
                    )
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.settings_version, state.appVersion),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }

    // --- Dialogs ---

    if (showQualityDialog) {
        val options = listOf(stringResource(R.string.settings_quality_original), stringResource(R.string.settings_quality_1080p_20), stringResource(R.string.settings_quality_1080p_12), stringResource(R.string.settings_quality_1080p_8), stringResource(R.string.settings_quality_720p_4), stringResource(R.string.settings_quality_720p_3))
        SettingsDialog(
            title = stringResource(R.string.settings_video_quality),
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
        val options = listOf(stringResource(R.string.settings_player_exoplayer), stringResource(R.string.settings_player_mpv))
        SettingsDialog(
            title = stringResource(R.string.settings_player_engine),
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
            title = stringResource(R.string.settings_app_theme),
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
            title = stringResource(R.string.settings_preferred_audio),
            options = audioOptions.map { it.first },
            currentValue = audioOptions.find { it.second == state.preferredAudioLanguage }?.first ?: stringResource(R.string.settings_lang_original),
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
            title = stringResource(R.string.settings_preferred_subtitle),
            options = subtitleOptions.map { it.first },
            currentValue = subtitleOptions.find { it.second == state.preferredSubtitleLanguage }?.first ?: stringResource(R.string.settings_lang_none),
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

    if (showApiKeysDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showApiKeysDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_api_keys),
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
                        text = stringResource(R.string.settings_api_keys_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // TMDb API Key
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
                                IconButton(onClick = {
                                    onAction(SettingsAction.SaveTmdbApiKey(tmdbKey))
                                }) {
                                    Icon(Icons.Default.Done, contentDescription = stringResource(R.string.settings_save_tmdb_description))
                                }
                            }
                        }
                    )
                    Text(
                        text = stringResource(R.string.settings_tmdb_api_key_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // OMDb API Key
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
                                IconButton(onClick = {
                                    onAction(SettingsAction.SaveOmdbApiKey(omdbKey))
                                }) {
                                    Icon(Icons.Default.Done, contentDescription = stringResource(R.string.settings_save_omdb_description))
                                }
                            }
                        }
                    )
                    Text(
                        text = stringResource(R.string.settings_omdb_api_key_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showApiKeysDialog = false }) {
                    Text(stringResource(R.string.settings_close_description))
                }
            }
        )
    }

    // Rating Sync Source Dialog
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

    // Rating Sync Delay Dialog
    if (showRatingSyncDelayDialog) {
        val delay100 = stringResource(R.string.settings_delay_100)
        val delay250 = stringResource(R.string.settings_delay_250)
        val delay500 = stringResource(R.string.settings_delay_500)
        val delay1000 = stringResource(R.string.settings_delay_1000)

        SettingsDialog(
            title = stringResource(R.string.settings_request_delay),
            options = listOf(delay100, delay250, delay500, delay1000),
            currentValue = when (state.ratingSyncDelay) {
                100L -> delay100
                250L -> delay250
                500L -> delay500
                1000L -> delay1000
                else -> "${state.ratingSyncDelay}ms"
            },
            onDismissRequest = { showRatingSyncDelayDialog = false },
            onOptionSelected = {
                val delay = when (it) {
                    delay100 -> 100L
                    delay250 -> 250L
                    delay500 -> 500L
                    delay1000 -> 1000L
                    else -> 250L
                }
                onAction(SettingsAction.ChangeRatingSyncDelay(delay))
                showRatingSyncDelayDialog = false
            },
        )
    }

    // Rating Sync Daily Limit Dialog
    if (showRatingSyncDailyLimitDialog) {
        val limit500 = stringResource(R.string.settings_limit_500)
        val limit900 = stringResource(R.string.settings_limit_900)
        val limit1500 = stringResource(R.string.settings_limit_1500)
        val limit2500 = stringResource(R.string.settings_limit_2500)

        SettingsDialog(
            title = stringResource(R.string.settings_daily_request_limit),
            options = listOf(limit500, limit900, limit1500, limit2500),
            currentValue = when (state.ratingSyncDailyLimit) {
                500 -> limit500
                900 -> limit900
                1500 -> limit1500
                2500 -> limit2500
                else -> "${state.ratingSyncDailyLimit} req/day"
            },
            onDismissRequest = { showRatingSyncDailyLimitDialog = false },
            onOptionSelected = {
                val limit = when (it) {
                    limit500 -> 500
                    limit900 -> 900
                    limit1500 -> 1500
                    limit2500 -> 2500
                    else -> 900
                }
                onAction(SettingsAction.ChangeRatingSyncDailyLimit(limit))
                showRatingSyncDailyLimitDialog = false
            },
        )
    }
}

// Helpers for Language Options
@Composable
private fun getAudioLanguageOptions() =
    listOf(
        stringResource(R.string.settings_lang_original) to null,
        stringResource(R.string.settings_lang_english) to "eng",
        stringResource(R.string.settings_lang_french) to "fra",
        stringResource(R.string.settings_lang_german) to "deu",
        stringResource(R.string.settings_lang_spanish) to "spa",
        stringResource(R.string.settings_lang_italian) to "ita",
        stringResource(R.string.settings_lang_japanese) to "jpn",
        stringResource(R.string.settings_lang_korean) to "kor",
        stringResource(R.string.settings_lang_russian) to "rus",
        stringResource(R.string.settings_lang_portuguese) to "por",
    )

@Composable
private fun getSubtitleLanguageOptions() =
    listOf(
        stringResource(R.string.settings_lang_none) to null,
        stringResource(R.string.settings_lang_english) to "eng",
        stringResource(R.string.settings_lang_french) to "fra",
        stringResource(R.string.settings_lang_german) to "deu",
        stringResource(R.string.settings_lang_spanish) to "spa",
        stringResource(R.string.settings_lang_italian) to "ita",
        stringResource(R.string.settings_lang_japanese) to "jpn",
        stringResource(R.string.settings_lang_korean) to "kor",
        stringResource(R.string.settings_lang_russian) to "rus",
        stringResource(R.string.settings_lang_portuguese) to "por",
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
