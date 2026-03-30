package com.chakir.plexhubtv.feature.xtream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.R

@Composable
fun XtreamSetupRoute(
    viewModel: XtreamSetupViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToCategorySelection: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    XtreamSetupScreen(
        state = state,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        onNavigateToCategorySelection = onNavigateToCategorySelection,
    )
}

@Composable
fun XtreamSetupScreen(
    state: XtreamSetupUiState,
    onAction: (XtreamSetupAction) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToCategorySelection: (String) -> Unit = {},
) {
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(48.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.xtream_setup_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Form fields
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
                        value = state.baseUrl,
                        onValueChange = { onAction(XtreamSetupAction.UpdateBaseUrl(it)) },
                        label = { Text(stringResource(R.string.xtream_server_url_label)) },
                        placeholder = { Text("http://example.com") },
                        modifier = Modifier.weight(2f),
                        singleLine = true,
                        colors = textFieldColors,
                    )
                    OutlinedTextField(
                        value = state.port,
                        onValueChange = { onAction(XtreamSetupAction.UpdatePort(it)) },
                        label = { Text(stringResource(R.string.xtream_port_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = { onAction(XtreamSetupAction.UpdateUsername(it)) },
                        label = { Text(stringResource(R.string.xtream_username_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = textFieldColors,
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { onAction(XtreamSetupAction.UpdatePassword(it)) },
                        label = { Text(stringResource(R.string.xtream_password_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = textFieldColors,
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = state.label,
                    onValueChange = { onAction(XtreamSetupAction.UpdateLabel(it)) },
                    label = { Text(stringResource(R.string.xtream_label_label)) },
                    placeholder = { Text(stringResource(R.string.xtream_label_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = textFieldColors,
                )
            }

            // Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Button(
                        onClick = { onAction(XtreamSetupAction.TestConnection) },
                        enabled = !state.isTesting &&
                            state.baseUrl.isNotBlank() &&
                            state.username.isNotBlank() &&
                            state.password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        if (state.isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp).width(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.isTesting) stringResource(R.string.xtream_testing) else stringResource(R.string.xtream_test_save))
                    }

                    Button(
                        onClick = { onAction(XtreamSetupAction.ClearForm) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Test result
            state.testResult?.let { result ->
                item {
                    when (result) {
                        is XtreamTestResult.Success -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        MaterialTheme.shapes.medium,
                                    )
                                    .padding(16.dp)
                            ) {
                                Text(
                                    stringResource(R.string.xtream_connected),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    stringResource(R.string.xtream_status, result.status),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                result.expiration?.let {
                                    Text(
                                        stringResource(R.string.xtream_expires, it),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                state.savedAccount?.let { account ->
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = { onNavigateToCategorySelection(account.id) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(R.string.xtream_choose_categories))
                                    }
                                }
                            }
                        }
                        is XtreamTestResult.Error -> {
                            Text(
                                stringResource(R.string.xtream_error, result.message),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // Existing accounts
            if (state.accounts.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.xtream_configured_accounts),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                items(state.accounts, key = { it.id }) { account ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.shapes.medium,
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                account.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "${account.username} @ ${account.baseUrl}:${account.port}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.xtream_status, account.status.name),
                                style = MaterialTheme.typography.bodySmall,
                                color = when (account.status) {
                                    com.chakir.plexhubtv.core.model.XtreamAccountStatus.Active ->
                                        MaterialTheme.colorScheme.primary
                                    com.chakir.plexhubtv.core.model.XtreamAccountStatus.Expired ->
                                        MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(
                            onClick = { onAction(XtreamSetupAction.RemoveAccount(account.id)) },
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.xtream_remove_account),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
