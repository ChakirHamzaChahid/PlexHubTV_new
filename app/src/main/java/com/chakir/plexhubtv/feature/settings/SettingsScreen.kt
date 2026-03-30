package com.chakir.plexhubtv.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.R

/**
 * Settings entry point — shows a grid of category cards.
 * Each card navigates to a dedicated sub-screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToServerStatus: () -> Unit,
    onNavigateToDebug: () -> Unit = {},
    onNavigateToPlexHomeSwitch: () -> Unit = {},
    onNavigateToAppProfiles: () -> Unit = {},
    onNavigateToLibrarySelection: () -> Unit = {},
    onNavigateToJellyfinSetup: () -> Unit = {},
    onNavigateToXtreamSetup: () -> Unit = {},
    onNavigateToXtreamCategorySelection: (String) -> Unit = {},
    onNavigateToSubtitleStyle: () -> Unit = {},
    onNavigateToSettingsCategory: (SettingsCategory) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val events = viewModel.navigationEvents

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is SettingsNavigationEvent.NavigateBack -> onNavigateBack()
                is SettingsNavigationEvent.NavigateToLogin -> onNavigateToLogin()
                is SettingsNavigationEvent.NavigateToServerStatus -> onNavigateToServerStatus()
                is SettingsNavigationEvent.NavigateToPlexHomeSwitch -> onNavigateToPlexHomeSwitch()
                is SettingsNavigationEvent.NavigateToAppProfiles -> onNavigateToAppProfiles()
                is SettingsNavigationEvent.NavigateToLibrarySelection -> onNavigateToLibrarySelection()
                is SettingsNavigationEvent.NavigateToJellyfinSetup -> onNavigateToJellyfinSetup()
                is SettingsNavigationEvent.NavigateToXtreamSetup -> onNavigateToXtreamSetup()
                is SettingsNavigationEvent.NavigateToXtreamCategorySelection -> onNavigateToXtreamCategorySelection(event.accountId)
                is SettingsNavigationEvent.NavigateToSubtitleStyle -> onNavigateToSubtitleStyle()
            }
        }
    }

    Scaffold(
        modifier = Modifier.padding(top = 56.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onNavigateBack() }) {
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
        SettingsGridScreen(
            appVersion = uiState.appVersion,
            onCategorySelected = { category ->
                if (category == SettingsCategory.Account) {
                    // Account is a direct action (logout), not a sub-screen
                    viewModel.onAction(SettingsAction.Logout)
                } else {
                    onNavigateToSettingsCategory(category)
                }
            },
            modifier = Modifier.padding(padding),
        )
    }
}
