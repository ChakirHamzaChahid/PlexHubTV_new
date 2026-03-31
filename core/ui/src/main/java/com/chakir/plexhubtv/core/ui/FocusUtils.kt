package com.chakir.plexhubtv.core.ui

import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.chakir.plexhubtv.core.designsystem.Dims

/**
 * Animates a scale factor based on focus state.
 * Each composable applies this value to its own `graphicsLayer { scaleX = s; scaleY = s }`.
 *
 * Usage:
 * ```
 * val scale = animateFocusScale(isFocused, targetScale = 1.06f)
 * Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
 * ```
 */
@Composable
fun animateFocusScale(
    isFocused: Boolean,
    targetScale: Float = Dims.FocusScale,
): Float {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) targetScale else 1f,
        animationSpec = tween(200, easing = EaseOutQuart),
        label = "focus_scale",
    )
    return scale
}
