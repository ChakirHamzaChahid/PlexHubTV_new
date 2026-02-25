package com.chakir.plexhubtv.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * OverscanSafeArea wrapper for Android TV screens.
 *
 * Applies consistent horizontal and vertical padding to ensure content stays within
 * the TV-safe display area and avoids overscan issues.
 *
 * Recommended padding: 48dp (standard TV safe area).
 *
 * Usage:
 * ```kotlin
 * OverscanSafeArea {
 *     // Your screen content here
 * }
 * ```
 *
 * @param modifier Optional modifier for the container
 * @param content The content to be displayed within the safe area
 */
@Composable
fun OverscanSafeArea(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.padding(horizontal = 48.dp, vertical = 48.dp)
    ) {
        content()
    }
}
