package com.chakir.plexhubtv.feature.loading

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoadingRoute(
    viewModel: LoadingViewModel = hiltViewModel(),
    onNavigateToMain: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                LoadingNavigationEvent.NavigateToMain -> onNavigateToMain()
            }
        }
    }

    LoadingScreen(state = uiState)
}

@Composable
fun LoadingScreen(state: LoadingUiState) {
    Surface(
        modifier = Modifier.fillMaxSize(),
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
                text = "Welcome to PlexHub TV",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Veuillez patienter pendant le chargement de vos médias...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(48.dp))

            when (state) {
                is LoadingUiState.Loading -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { state.progress / 100f }, // progress is 0..100
                        modifier = Modifier.fillMaxWidth(0.6f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${state.progress.toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                is LoadingUiState.Error -> {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                }
                LoadingUiState.Completed -> {
                    Text("Chargement terminé !")
                }
            }
        }
    }
}
