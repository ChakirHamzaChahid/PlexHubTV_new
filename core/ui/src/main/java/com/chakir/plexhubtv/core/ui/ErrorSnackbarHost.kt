package com.chakir.plexhubtv.core.ui

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.isCritical
import com.chakir.plexhubtv.core.model.isRetryable

/**
 * Host for displaying error snackbars consistently across the app.
 * Adapted for Android TV with focus and appropriate design.
 */
@Composable
fun ErrorSnackbarHost(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier,
        snackbar = { snackbarData ->
            ErrorSnackbar(snackbarData = snackbarData)
        }
    )
}

/**
 * Custom snackbar for displaying errors with icon and appropriate style.
 * Uses snackbar metadata prefix to determine severity without language-dependent matching.
 */
@Composable
private fun ErrorSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector
    val containerColor: Color
    val contentColor: Color

    // Determine icon and colors based on metadata prefix (language-independent)
    val message = snackbarData.visuals.message
    when {
        message.startsWith(SEVERITY_CRITICAL) -> {
            icon = Icons.Default.Error
            containerColor = MaterialTheme.colorScheme.error
            contentColor = MaterialTheme.colorScheme.onError
        }
        message.startsWith(SEVERITY_WARNING) -> {
            icon = Icons.Default.Warning
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        }
        else -> {
            icon = Icons.Default.Info
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    // Strip the severity prefix before displaying
    val displayMessage = message
        .removePrefix(SEVERITY_CRITICAL)
        .removePrefix(SEVERITY_WARNING)

    Snackbar(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth(0.9f),
        shape = RoundedCornerShape(8.dp),
        containerColor = containerColor,
        contentColor = contentColor,
        action = {
            snackbarData.visuals.actionLabel?.let { actionLabel ->
                TextButton(
                    onClick = { snackbarData.performAction() }
                ) {
                    Text(
                        text = actionLabel,
                        color = contentColor,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = displayMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        }
    }
}

private const val SEVERITY_CRITICAL = "[CRITICAL]"
private const val SEVERITY_WARNING = "[WARNING]"

/**
 * Extension to show an [AppError] via a [SnackbarHostState] with localized message.
 */
suspend fun SnackbarHostState.showError(
    error: AppError,
    context: Context,
    actionLabel: String? = null
): androidx.compose.material3.SnackbarResult {
    val resolvedMessage = error.resolveMessage(context)

    // Prefix with severity marker for the snackbar UI to style appropriately
    val prefixedMessage = when {
        error.isCritical() -> "$SEVERITY_CRITICAL$resolvedMessage"
        error.isRetryable() -> "$SEVERITY_WARNING$resolvedMessage"
        else -> resolvedMessage
    }

    val action = when {
        actionLabel != null -> actionLabel
        error.isRetryable() -> context.getString(R.string.error_action_retry)
        error.isCritical() -> context.getString(R.string.error_action_ok)
        else -> null
    }

    return showSnackbar(
        message = prefixedMessage,
        actionLabel = action,
        withDismissAction = true,
        duration = if (error.isCritical()) {
            androidx.compose.material3.SnackbarDuration.Indefinite
        } else {
            androidx.compose.material3.SnackbarDuration.Long
        }
    )
}

/**
 * Extension to show a simple error message string.
 */
suspend fun SnackbarHostState.showErrorMessage(
    message: String,
    actionLabel: String? = "OK"
): androidx.compose.material3.SnackbarResult {
    return showSnackbar(
        message = message,
        actionLabel = actionLabel,
        withDismissAction = true,
        duration = androidx.compose.material3.SnackbarDuration.Short
    )
}
