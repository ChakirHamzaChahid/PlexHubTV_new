package com.chakir.plexhubtv.feature.settings.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.ui.ParentalPinDialog
import com.chakir.plexhubtv.core.ui.PinDialogMode
import com.chakir.plexhubtv.feature.settings.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showParentalPinDialog by remember { mutableStateOf(false) }
    val listFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { listFocusRequester.requestFocus() } catch (_: Exception) { }
    }

    Scaffold(
        modifier = Modifier.padding(top = 56.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_category_general), fontWeight = FontWeight.Bold) },
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
            // --- Profiles & Users ---
            item {
                SettingsSection(stringResource(R.string.settings_section_profiles)) {
                    SettingsTile(
                        title = stringResource(R.string.settings_switch_plex_user),
                        subtitle = stringResource(R.string.settings_switch_plex_user_desc),
                        icon = Icons.Default.SwitchAccount,
                        onClick = { onAction(SettingsAction.SwitchPlexUser) },
                    )
                    SettingsTile(
                        title = stringResource(R.string.settings_manage_profiles),
                        subtitle = stringResource(R.string.settings_manage_profiles_desc),
                        icon = Icons.Default.ManageAccounts,
                        onClick = { onAction(SettingsAction.ManageAppProfiles) },
                    )
                }
            }

            // --- Appearance ---
            item {
                SettingsSection(stringResource(R.string.settings_section_appearance)) {
                    SettingsTile(
                        title = stringResource(R.string.settings_app_theme),
                        subtitle = state.theme.name,
                        onClick = { showThemeDialog = true },
                    )
                    SettingsSwitch(
                        title = stringResource(R.string.settings_show_year_on_cards),
                        subtitle = stringResource(R.string.settings_show_year_on_cards_subtitle),
                        isChecked = state.showYearOnCards,
                        onCheckedChange = { onAction(SettingsAction.ToggleShowYearOnCards(it)) },
                    )
                    SettingsTile(
                        title = stringResource(R.string.settings_grid_columns),
                        subtitle = stringResource(R.string.settings_grid_columns_subtitle, state.gridColumnsCount),
                        onClick = {
                            val newCount = if (state.gridColumnsCount == 6) 7 else 6
                            onAction(SettingsAction.ChangeGridColumnsCount(newCount))
                        },
                    )
                }
            }

            // --- Parental Controls ---
            item {
                SettingsSection(stringResource(R.string.settings_section_parental)) {
                    SettingsTile(
                        title = if (state.hasParentalPin) {
                            stringResource(R.string.settings_parental_pin_change)
                        } else {
                            stringResource(R.string.settings_parental_pin_set)
                        },
                        subtitle = stringResource(R.string.settings_parental_pin_subtitle),
                        icon = Icons.Default.Lock,
                        onClick = { showParentalPinDialog = true },
                    )
                    if (state.hasParentalPin) {
                        SettingsTile(
                            title = stringResource(R.string.settings_parental_pin_remove),
                            subtitle = stringResource(R.string.settings_parental_pin_remove_subtitle),
                            titleColor = MaterialTheme.colorScheme.error,
                            onClick = { onAction(SettingsAction.ClearParentalPin) },
                        )
                    }
                }
            }
        }
    }

    // --- Dialogs ---
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

    if (showParentalPinDialog) {
        ParentalPinDialog(
            mode = PinDialogMode.SetPin,
            onPinSubmit = { pin ->
                onAction(SettingsAction.SetParentalPin(pin))
                showParentalPinDialog = false
            },
            onDismiss = { showParentalPinDialog = false },
        )
    }
}
