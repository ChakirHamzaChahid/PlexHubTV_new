package com.chakir.plexhubtv.feature.jellyfin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.R

@Composable
fun JellyfinSetupRoute(
    viewModel: JellyfinSetupViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    JellyfinSetupScreen(
        state = state,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
fun JellyfinSetupScreen(
    state: JellyfinSetupUiState,
    onAction: (JellyfinSetupAction) -> Unit,
    onNavigateBack: () -> Unit,
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
                    stringResource(R.string.jellyfin_setup_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Server URL
            item {
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = { onAction(JellyfinSetupAction.UpdateBaseUrl(it)) },
                    label = { Text(stringResource(R.string.jellyfin_server_url_label)) },
                    placeholder = { Text("http://192.168.1.100:8096") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = textFieldColors,
                )
            }

            // Username & Password
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = { onAction(JellyfinSetupAction.UpdateUsername(it)) },
                        label = { Text(stringResource(R.string.jellyfin_username_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = textFieldColors,
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { onAction(JellyfinSetupAction.UpdatePassword(it)) },
                        label = { Text(stringResource(R.string.jellyfin_password_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = textFieldColors,
                    )
                }
            }

            // Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val isEnabled = !state.isTesting &&
                        state.baseUrl.isNotBlank() &&
                        state.username.isNotBlank() &&
                        state.password.isNotBlank()

                    TVButton(
                        onClick = { if (isEnabled) onAction(JellyfinSetupAction.TestAndAdd) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        enabled = isEnabled,
                    ) {
                        if (state.isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (state.isTesting) stringResource(R.string.jellyfin_connecting) else stringResource(R.string.jellyfin_test_add),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    TVButton(
                        onClick = { onAction(JellyfinSetupAction.ClearForm) },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Text(stringResource(R.string.action_clear), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Test result
            state.testResult?.let { result ->
                item {
                    when (result) {
                        is JellyfinTestResult.Success -> {
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
                                    stringResource(R.string.jellyfin_connected),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    stringResource(R.string.jellyfin_server_name, result.serverName),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    stringResource(R.string.jellyfin_version, result.version),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        is JellyfinTestResult.Error -> {
                            Text(
                                stringResource(R.string.jellyfin_error, result.message),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // Existing servers
            if (state.servers.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.jellyfin_configured_servers),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                items(state.servers, key = { it.id }) { server ->
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
                                server.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "${server.userName} @ ${server.baseUrl}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (server.version.isNotBlank()) {
                                Text(
                                    stringResource(R.string.jellyfin_version, server.version),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TVIconButton(
                            onClick = { onAction(JellyfinSetupAction.ConfirmRemove(server.id)) },
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.jellyfin_remove_server),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    // Remove confirmation dialog
    state.showRemoveDialog?.let {
        AlertDialog(
            onDismissRequest = { onAction(JellyfinSetupAction.DismissRemoveDialog(false)) },
            title = { Text(stringResource(R.string.jellyfin_remove_dialog_title)) },
            text = { Text(stringResource(R.string.jellyfin_remove_confirm)) },
            confirmButton = {
                TextButton(onClick = { onAction(JellyfinSetupAction.DismissRemoveDialog(true)) }) {
                    Text(stringResource(R.string.jellyfin_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(JellyfinSetupAction.DismissRemoveDialog(false)) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * TV-friendly button with D-pad focus support:
 * - Scale animation on focus
 * - Visible border on focus
 * - Background brightness change on focus
 */
@Composable
private fun TVButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "buttonScale",
    )

    val effectiveAlpha = if (enabled) 1f else 0.4f
    val bgColor = if (isFocused && enabled) containerColor else containerColor.copy(alpha = 0.7f * effectiveAlpha)
    val borderColor = if (isFocused && enabled) MaterialTheme.colorScheme.onPrimary else Color.Transparent

    Row(
        modifier = modifier
            .scale(scale)
            .background(
                color = bgColor,
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = 12.dp, horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor.copy(alpha = effectiveAlpha),
        ) {
            content()
        }
    }
}

/**
 * TV-friendly icon button with D-pad focus support:
 * - Scale animation on focus
 * - Circular highlight background on focus
 */
@Composable
private fun TVIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        label = "iconButtonScale",
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .scale(scale)
            .background(
                color = if (isFocused) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else Color.Transparent,
                shape = RoundedCornerShape(50),
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.error else Color.Transparent,
                shape = RoundedCornerShape(50),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
