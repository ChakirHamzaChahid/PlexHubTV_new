package com.chakir.plexhubtv.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.chakir.plexhubtv.core.model.toUserMessage

/**
 * Host pour afficher les snackbars d'erreur de manière cohérente dans l'app.
 * Adapté pour Android TV avec focus et design approprié.
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
 * Snackbar personnalisé pour afficher les erreurs avec icône et style approprié
 */
@Composable
private fun ErrorSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector
    val containerColor: Color
    val contentColor: Color

    // Déterminer l'icône et les couleurs selon le type de message
    when {
        snackbarData.visuals.message.contains("critique", ignoreCase = true) ||
        snackbarData.visuals.message.contains("expiré", ignoreCase = true) -> {
            icon = Icons.Default.Error
            containerColor = MaterialTheme.colorScheme.error
            contentColor = MaterialTheme.colorScheme.onError
        }
        snackbarData.visuals.message.contains("attention", ignoreCase = true) ||
        snackbarData.visuals.message.contains("impossible", ignoreCase = true) -> {
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
                text = snackbarData.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        }
    }
}

/**
 * Extension pour afficher une erreur AppError via un SnackbarHostState
 */
suspend fun SnackbarHostState.showError(
    error: AppError,
    actionLabel: String? = null
): androidx.compose.material3.SnackbarResult {
    val message = error.toUserMessage()
    val action = when {
        actionLabel != null -> actionLabel
        error.isRetryable() -> "Réessayer"
        error.isCritical() -> "OK"
        else -> null
    }

    return showSnackbar(
        message = message,
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
 * Extension pour afficher un message d'erreur simple
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
