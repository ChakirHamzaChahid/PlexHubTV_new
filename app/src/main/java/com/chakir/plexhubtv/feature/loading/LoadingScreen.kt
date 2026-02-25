package com.chakir.plexhubtv.feature.loading

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.R

@Composable
fun LoadingRoute(
    viewModel: LoadingViewModel = hiltViewModel(),
    onNavigateToMain: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToLibrarySelection: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                LoadingNavigationEvent.NavigateToMain -> onNavigateToMain()
                LoadingNavigationEvent.NavigateToAuth -> onNavigateToAuth()
                LoadingNavigationEvent.NavigateToLibrarySelection -> onNavigateToLibrarySelection()
            }
        }
    }

    LoadingScreen(
        state = uiState,
        onRetryClicked = { viewModel.onRetry() },
        onExitClicked = { viewModel.onExit() }
    )
}

@Composable
fun LoadingScreen(
    state: LoadingUiState,
    onRetryClicked: () -> Unit = {},
    onExitClicked: () -> Unit = {},
) {
    val screenDesc = stringResource(R.string.loading_screen_description)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_loading")
            .semantics { contentDescription = screenDesc },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Logo or App Name
            Text(
                text = stringResource(R.string.loading_welcome),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.loading_please_wait),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(48.dp))

            when (state) {
                is LoadingUiState.Loading -> {
                    val progressDesc = stringResource(R.string.loading_progress_description, state.progress.toInt())
                    val syncBarDesc = stringResource(R.string.loading_sync_bar_description)

                    CircularProgressIndicator(
                        modifier = Modifier
                            .testTag("loading_progress")
                            .semantics { contentDescription = progressDesc }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { state.progress / 100f }, // progress is 0..100
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .testTag("sync_progress_bar")
                            .semantics { contentDescription = syncBarDesc },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${state.progress.toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                is LoadingUiState.Error -> {
                    val errorIconDesc = stringResource(R.string.loading_error_icon_description)
                    val errorDesc = stringResource(R.string.loading_error_description, state.message)

                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                        contentDescription = errorIconDesc,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("loading_error")
                            .semantics { contentDescription = errorDesc }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Boutons d'action
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 48.dp)
                    ) {
                        // Bouton Réessayer (focus principal)
                        val retryFocusRequester = remember { FocusRequester() }
                        Button(
                            onClick = onRetryClicked,
                            modifier = Modifier
                                .focusRequester(retryFocusRequester)
                                .weight(1f)
                        ) {
                            Text(stringResource(R.string.action_retry))
                        }

                        // Bouton Quitter
                        OutlinedButton(
                            onClick = onExitClicked,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.loading_exit_button))
                        }

                        LaunchedEffect(Unit) {
                            retryFocusRequester.requestFocus()
                        }
                    }
                }
                LoadingUiState.Completed -> {
                    val completeText = stringResource(R.string.loading_complete)
                    val completeDesc = stringResource(R.string.loading_completed_description)

                    Text(
                        completeText,
                        modifier = Modifier
                            .testTag("loading_completed")
                            .semantics { contentDescription = completeDesc }
                    )
                }
            }
        }
    }
}
