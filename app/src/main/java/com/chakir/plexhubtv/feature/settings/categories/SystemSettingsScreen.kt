package com.chakir.plexhubtv.feature.settings.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.BuildConfig
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.feature.settings.*

private const val PRIVACY_POLICY_URL = "https://chakir-elarram.github.io/PlexHubTV/privacy-policy-en.html"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToDebug: () -> Unit,
) {
    val listFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { listFocusRequester.requestFocus() } catch (_: Exception) { }
    }

    Scaffold(
        modifier = Modifier.padding(top = 56.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_category_system), fontWeight = FontWeight.Bold) },
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
            // --- Screensaver ---
            item {
                SettingsSection(stringResource(R.string.settings_section_screensaver)) {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_screensaver_enabled),
                        subtitle = stringResource(R.string.settings_screensaver_enabled_subtitle),
                        isChecked = state.screensaverEnabled,
                        onCheckedChange = { onAction(SettingsAction.ToggleScreensaver(it)) },
                    )
                    SettingsSwitch(
                        title = stringResource(R.string.settings_screensaver_show_clock),
                        subtitle = stringResource(R.string.settings_screensaver_show_clock_subtitle),
                        isChecked = state.screensaverShowClock,
                        onCheckedChange = { onAction(SettingsAction.ToggleScreensaverClock(it)) },
                    )
                    SettingsTile(
                        title = stringResource(R.string.settings_screensaver_interval),
                        subtitle = stringResource(R.string.settings_screensaver_interval_subtitle, state.screensaverIntervalSeconds),
                        onClick = {
                            val next = when (state.screensaverIntervalSeconds) {
                                10 -> 15; 15 -> 20; 20 -> 30; 30 -> 45; 45 -> 60; else -> 10
                            }
                            onAction(SettingsAction.ChangeScreensaverInterval(next))
                        },
                    )
                    // Button to open Android TV system screensaver settings
                    val context = LocalContext.current
                    SettingsTile(
                        title = stringResource(R.string.settings_screensaver_activate),
                        subtitle = stringResource(R.string.settings_screensaver_activate_subtitle),
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (e: Exception) {
                                timber.log.Timber.w(e, "ACTION_DREAM_SETTINGS not available on this device")
                            }
                        },
                    )
                }
            }

            // --- Updates ---
            item {
                SettingsSection(stringResource(R.string.settings_section_updates)) {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_auto_check_updates),
                        subtitle = stringResource(R.string.settings_auto_check_updates_subtitle),
                        isChecked = state.autoCheckUpdates,
                        onCheckedChange = { onAction(SettingsAction.ToggleAutoCheckUpdates(it)) },
                    )
                    SettingsTile(
                        title = stringResource(R.string.settings_check_now),
                        subtitle = if (state.isCheckingForUpdate) {
                            stringResource(R.string.settings_syncing_message)
                        } else {
                            state.updateCheckMessage ?: stringResource(R.string.settings_check_now_subtitle)
                        },
                        onClick = { if (!state.isCheckingForUpdate) onAction(SettingsAction.CheckForUpdates) },
                        trailingContent = if (state.isCheckingForUpdate) {
                            { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
                        } else {
                            null
                        },
                    )
                }
            }

            // --- Legal ---
            item {
                val uriHandler = LocalUriHandler.current
                SettingsSection(stringResource(R.string.settings_section_legal)) {
                    SettingsTile(
                        title = stringResource(R.string.settings_privacy_policy),
                        subtitle = stringResource(R.string.settings_privacy_policy_subtitle),
                        onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) },
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
        }
    }
}
