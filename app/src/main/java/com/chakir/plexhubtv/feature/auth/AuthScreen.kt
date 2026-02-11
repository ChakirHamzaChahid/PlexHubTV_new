package com.chakir.plexhubtv.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.BuildConfig

/**
 * Écran d'authentification principal.
 * Affiche l'état courant de l'authentification (PIN, Loading, Error, Success).
 */
@Composable
fun AuthRoute(
    viewModel: AuthViewModel = hiltViewModel(),
    onAuthSuccess: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onAuthSuccess()
        }
    }

    AuthScreen(
        state = uiState,
        onAction = viewModel::onEvent,
    )
}

@Composable
fun AuthScreen(
    state: AuthUiState,
    onAction: (AuthEvent) -> Unit,
) {
    Scaffold { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is AuthUiState.Idle -> IdleState(onAction)
                is AuthUiState.Authenticating -> AuthenticatingState(state, onAction)
                is AuthUiState.Error -> ErrorState(state.message, onAction)
                is AuthUiState.Success -> SuccessState()
            }
        }
    }
}

@Composable
fun IdleState(onAction: (AuthEvent) -> Unit) {
    var token by remember { mutableStateOf(BuildConfig.PLEX_TOKEN) }

    // Auto-login if test token is set
    LaunchedEffect(Unit) {
        if (BuildConfig.PLEX_TOKEN.isNotBlank()) {
            onAction(AuthEvent.SubmitToken(BuildConfig.PLEX_TOKEN))
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("PlexHubTV", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(32.dp))
        /*
        Button(onClick = { onAction(AuthEvent.StartAuth) }) {
            Text("Login with PIN")
        }
        Spacer(Modifier.height(16.dp))
        Text("OR")
        Spacer(Modifier.height(16.dp))
         */
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Plex Token") },
            maxLines = 1,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onAction(AuthEvent.SubmitToken(token)) },
            enabled = token.isNotBlank(),
        ) {
            Text("Login with Token")
        }
    }
}

@Composable
fun AuthenticatingState(
    state: AuthUiState.Authenticating,
    onAction: (AuthEvent) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Text("Link Account", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("Go to: ${state.authUrl}", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = state.pinCode,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(16.dp),
        )
        LinearProgressIndicator(
            progress = { state.progress ?: 0f },
            modifier = Modifier.fillMaxWidth().height(4.dp),
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { onAction(AuthEvent.Cancel) }) {
            Text("Cancel")
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onAction: (AuthEvent) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Text(text = message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onAction(AuthEvent.Retry) }) {
            Text("Retry")
        }
    }
}

@Composable
fun SuccessState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Authentication Successful! Loading...")
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewAuthIdle() {
    AuthScreen(state = AuthUiState.Idle, onAction = {})
}

@Preview(showBackground = true)
@Composable
fun PreviewAuthLoading() {
    AuthScreen(
        state = AuthUiState.Authenticating("ABCD", "123", 0.5f),
        onAction = {},
    )
}
