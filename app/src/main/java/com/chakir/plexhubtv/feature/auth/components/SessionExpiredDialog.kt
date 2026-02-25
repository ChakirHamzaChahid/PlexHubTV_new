package com.chakir.plexhubtv.feature.auth.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.chakir.plexhubtv.R

/**
 * Dialog displayed when user's Plex session has expired (401 error).
 *
 * Shows localized message explaining session expiration and provides
 * a "Reconnect" button to navigate back to authentication flow.
 */
@Composable
fun SessionExpiredDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.session_expired_title))
        },
        text = {
            Text(text = stringResource(R.string.session_expired_message))
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = stringResource(R.string.reconnect))
            }
        },
        modifier = modifier
    )
}
