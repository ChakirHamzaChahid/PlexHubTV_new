package com.chakir.plexhubtv.core.ui

// === CINEMA GOLD REFONTE — Composant bouton thémé centralisé ===

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Bouton thémé centralisé pour PlexHubTV.
 *
 * Lit les couleurs depuis [MaterialTheme.colorScheme] pour un raccord automatique
 * avec tous les thèmes (Plex, Netflix, CinemaGold, etc.).
 *
 * Gère le focus D-pad Android TV nativement.
 */
@Composable
fun ThemedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector? = null,
    variant: ThemedButtonVariant = ThemedButtonVariant.PRIMARY,
    enabled: Boolean = true,
) {
    var isFocused by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val primaryColor = colors.primary
    val onPrimaryColor = colors.onPrimary
    val surfaceColor = colors.surfaceVariant
    val onSurfaceColor = colors.onSurfaceVariant
    val outlineColor = colors.outline

    val bgColor by animateColorAsState(
        targetValue = when {
            !enabled -> surfaceColor.copy(alpha = 0.4f)
            isFocused && variant == ThemedButtonVariant.PRIMARY -> primaryColor
            isFocused && variant == ThemedButtonVariant.SECONDARY -> surfaceColor
            isFocused && variant == ThemedButtonVariant.GHOST -> primaryColor.copy(alpha = 0.15f)
            variant == ThemedButtonVariant.PRIMARY -> primaryColor.copy(alpha = 0.85f)
            variant == ThemedButtonVariant.SECONDARY -> surfaceColor.copy(alpha = 0.6f)
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "btnBg",
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> onSurfaceColor.copy(alpha = 0.4f)
            variant == ThemedButtonVariant.PRIMARY -> onPrimaryColor
            isFocused && variant == ThemedButtonVariant.GHOST -> primaryColor
            else -> if (isFocused) colors.onSurface else onSurfaceColor
        },
        animationSpec = tween(180),
        label = "btnContent",
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> outlineColor.copy(alpha = 0.3f)
            isFocused -> primaryColor
            variant == ThemedButtonVariant.SECONDARY -> outlineColor
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "btnBorder",
    )

    val scale by animateFloatAsState(
        targetValue = if (isFocused && enabled) 1.06f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "btnScale",
    )

    val borderWidth = if (isFocused || variant == ThemedButtonVariant.SECONDARY) 2.dp else 0.dp

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(8.dp),
            )
            .onFocusChanged { focusState -> isFocused = focusState.isFocused }
            .onKeyEvent { keyEvent ->
                if (enabled &&
                    keyEvent.type == KeyEventType.KeyUp &&
                    (keyEvent.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        keyEvent.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_ENTER)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable(enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
            )
        }
    }
}

enum class ThemedButtonVariant {
    /** Fond plein couleur primaire du thème */
    PRIMARY,

    /** Fond transparent + bordure */
    SECONDARY,

    /** Fond très léger, pas de bordure au repos */
    GHOST,
}
