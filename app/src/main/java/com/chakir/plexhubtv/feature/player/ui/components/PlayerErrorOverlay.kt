package com.chakir.plexhubtv.feature.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.feature.player.PlayerErrorType

/**
 * Overlay d'erreur pour le player avec options de retry et fallback MPV
 *
 * Affiche un message d'erreur contextualisé selon le type d'erreur et propose :
 * - Réessayer (pour erreurs réseau)
 * - Basculer vers MPV (après plusieurs échecs réseau, si pas déjà en MPV)
 * - Fermer le lecteur
 */
@Composable
fun PlayerErrorOverlay(
    errorMessage: String,
    errorType: PlayerErrorType,
    retryCount: Int,
    isMpvMode: Boolean,
    onRetry: () -> Unit,
    onSwitchToMpv: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryFocusRequester = remember { FocusRequester() }
    val maxRetries = 3
    val canSuggestMpv = errorType == PlayerErrorType.Network && retryCount >= maxRetries && !isMpvMode

    // Auto-focus le bouton Retry au montage
    LaunchedEffect(Unit) {
        try {
            retryFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .testTag("player_error_overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
            modifier =
                Modifier
                    .width(500.dp)
                    .padding(32.dp),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Icône d'erreur
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error,
                )

                // Titre selon type d'erreur
                Text(
                    text =
                        when (errorType) {
                            PlayerErrorType.Network -> stringResource(R.string.player_error_network_title)
                            PlayerErrorType.Codec -> stringResource(R.string.player_error_codec_title)
                            PlayerErrorType.Generic, PlayerErrorType.None -> stringResource(R.string.player_error_generic_title)
                        },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                // Message d'erreur
                Text(
                    text =
                        when (errorType) {
                            PlayerErrorType.Network -> stringResource(R.string.player_error_network_message)
                            PlayerErrorType.Codec -> stringResource(R.string.player_error_codec_message)
                            PlayerErrorType.Generic, PlayerErrorType.None -> errorMessage
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )

                // Retry count indicator si réseau
                if (errorType == PlayerErrorType.Network && retryCount > 0) {
                    Text(
                        text = stringResource(R.string.player_error_retry_count, retryCount, maxRetries),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Boutons d'action
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Bouton Retry (pour erreurs réseau principalement)
                    if (errorType == PlayerErrorType.Network) {
                        val retryInteractionSource = remember { MutableInteractionSource() }
                        val isRetryFocused by retryInteractionSource.collectIsFocusedAsState()

                        Button(
                            onClick = onRetry,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .scale(if (isRetryFocused) 1.05f else 1f)
                                    .focusRequester(retryFocusRequester)
                                    .testTag("player_error_retry_button"),
                            interactionSource = retryInteractionSource,
                            colors =
                                if (isRetryFocused) {
                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                } else {
                                    ButtonDefaults.buttonColors()
                                },
                        ) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }

                    // Bouton fallback MPV (si plusieurs échecs réseau)
                    if (canSuggestMpv) {
                        val mpvInteractionSource = remember { MutableInteractionSource() }
                        val isMpvFocused by mpvInteractionSource.collectIsFocusedAsState()

                        OutlinedButton(
                            onClick = onSwitchToMpv,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .scale(if (isMpvFocused) 1.05f else 1f)
                                    .testTag("player_error_mpv_button"),
                            interactionSource = mpvInteractionSource,
                            border =
                                androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isMpvFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                ),
                        ) {
                            Text(
                                stringResource(R.string.player_error_switch_to_mpv),
                                color = if (isMpvFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Bouton Fermer
                    val closeInteractionSource = remember { MutableInteractionSource() }
                    val isCloseFocused by closeInteractionSource.collectIsFocusedAsState()

                    OutlinedButton(
                        onClick = onClose,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .scale(if (isCloseFocused) 1.05f else 1f)
                                .testTag("player_error_close_button"),
                        interactionSource = closeInteractionSource,
                        border =
                            androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isCloseFocused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                            ),
                    ) {
                        Text(
                            stringResource(R.string.action_close),
                            color = if (isCloseFocused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
