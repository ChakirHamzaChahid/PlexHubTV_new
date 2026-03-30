package com.chakir.plexhubtv.core.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.isRetryable
import kotlinx.coroutines.flow.Flow

/**
 * Composable that collects error events and displays them via a snackbar.
 * Optionally invokes [onRetry] when the user taps the retry action on a retryable error.
 */
@Composable
fun HandleErrors(
    errorFlow: Flow<AppError>,
    snackbarHostState: SnackbarHostState,
    onRetry: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    LaunchedEffect(errorFlow) {
        errorFlow.collect { error ->
            val result = snackbarHostState.showError(error, context)
            if (onRetry != null && result == SnackbarResult.ActionPerformed && error.isRetryable()) {
                onRetry()
            }
        }
    }
}
