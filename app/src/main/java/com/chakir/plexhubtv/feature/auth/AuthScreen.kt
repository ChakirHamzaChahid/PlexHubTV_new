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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.BuildConfig
import com.chakir.plexhubtv.R

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
    val screenDescription = stringResource(R.string.auth_screen_description)

    Scaffold { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag("screen_login")
                    .semantics { contentDescription = screenDescription }
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
    var showAdvancedLogin by remember { mutableStateOf(false) }
    val pinButtonFocusRequester = remember { FocusRequester() }
    val tokenFieldDescription = stringResource(R.string.auth_token_field_description)
    val loginButtonDescription = stringResource(R.string.auth_login_button_description)

    // Auto-login if test token is set
    LaunchedEffect(Unit) {
        if (BuildConfig.PLEX_TOKEN.isNotBlank()) {
            onAction(AuthEvent.SubmitToken(BuildConfig.PLEX_TOKEN))
        } else {
            // Request focus on PIN button for TV navigation
            pinButtonFocusRequester.requestFocus()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(48.dp)
    ) {
        Text(
            text = stringResource(R.string.auth_title),
            style = MaterialTheme.typography.headlineLarge,
            fontSize = 36.sp
        )
        Spacer(Modifier.height(48.dp))

        // Primary action: PIN login (TV-friendly, no keyboard required)
        Button(
            onClick = { onAction(AuthEvent.StartAuth) },
            modifier = Modifier
                .height(56.dp)
                .widthIn(min = 280.dp)
                .focusRequester(pinButtonFocusRequester)
                .testTag("auth_pin_button")
                .semantics { contentDescription = "Login with PIN code" }
        ) {
            Text(
                text = stringResource(R.string.auth_login_with_pin),
                style = MaterialTheme.typography.titleLarge,
                fontSize = 20.sp
            )
        }

        Spacer(Modifier.height(24.dp))

        // Advanced login option (for dev/testing)
        if (showAdvancedLogin) {
            Text(
                text = stringResource(R.string.auth_advanced_login),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(stringResource(R.string.auth_plex_token_label)) },
                maxLines = 1,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                modifier = Modifier
                    .testTag("auth_token_field")
                    .semantics { contentDescription = tokenFieldDescription }
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onAction(AuthEvent.SubmitToken(token)) },
                enabled = token.isNotBlank(),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("auth_login_button")
                    .semantics { contentDescription = loginButtonDescription }
            ) {
                Text(stringResource(R.string.auth_login_with_token))
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { showAdvancedLogin = false }) {
                Text(stringResource(R.string.auth_hide_advanced_login))
            }
        } else {
            TextButton(
                onClick = { showAdvancedLogin = true },
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = stringResource(R.string.auth_advanced_login_token),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun AuthenticatingState(
    state: AuthUiState.Authenticating,
    onAction: (AuthEvent) -> Unit,
) {
    val cancelButtonFocusRequester = remember { FocusRequester() }
    val pinScreenDescription = stringResource(R.string.auth_pin_screen_description)
    val pinDisplayDescription = stringResource(R.string.auth_pin_display_description, state.pinCode)

    LaunchedEffect(Unit) {
        // Request focus on cancel button for D-Pad navigation
        cancelButtonFocusRequester.requestFocus()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(48.dp)
            .testTag("screen_pin_input")
            .semantics { contentDescription = pinScreenDescription }
    ) {
        Text(
            text = stringResource(R.string.auth_link_account),
            style = MaterialTheme.typography.headlineMedium,
            fontSize = 32.sp
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.auth_go_to_url, state.authUrl),
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = state.pinCode,
            style = MaterialTheme.typography.displayLarge,
            fontSize = 72.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(24.dp)
                .testTag("pin_display")
                .semantics { contentDescription = pinDisplayDescription },
            letterSpacing = 8.sp
        )
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { state.progress ?: 0f },
            modifier = Modifier
                .widthIn(max = 400.dp)
                .height(6.dp),
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { onAction(AuthEvent.Cancel) },
            modifier = Modifier
                .height(56.dp)
                .widthIn(min = 200.dp)
                .focusRequester(cancelButtonFocusRequester)
                .testTag("auth_cancel_button")
        ) {
            Text(
                text = stringResource(R.string.action_cancel),
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onAction: (AuthEvent) -> Unit,
) {
    val retryButtonFocusRequester = remember { FocusRequester() }
    val errorDescription = stringResource(R.string.auth_error_description, message)

    LaunchedEffect(Unit) {
        // Request focus on retry button for D-Pad navigation
        retryButtonFocusRequester.requestFocus()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(48.dp)
            .testTag("auth_error_state")
            .semantics { contentDescription = errorDescription }
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            fontSize = 18.sp,
            modifier = Modifier.widthIn(max = 600.dp)
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { onAction(AuthEvent.Retry) },
            modifier = Modifier
                .height(56.dp)
                .widthIn(min = 200.dp)
                .focusRequester(retryButtonFocusRequester)
                .testTag("auth_retry_button")
        ) {
            Text(
                text = stringResource(R.string.action_retry),
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun SuccessState() {
    val successDescription = stringResource(R.string.auth_success_description)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .testTag("auth_success_state")
            .semantics { contentDescription = successDescription }
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.auth_success_message))
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
