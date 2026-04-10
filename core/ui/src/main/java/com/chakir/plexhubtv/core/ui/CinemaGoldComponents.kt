package com.chakir.plexhubtv.core.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chakir.plexhubtv.core.designsystem.CinemaTypo
import com.chakir.plexhubtv.core.designsystem.Dims

// === CINEMA GOLD — Composants atomiques réutilisables ===

/**
 * Titre de section avec trait doré vertical.
 *
 * ```
 * ┃ Films    847 films
 * ```
 */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Trait doré vertical
        Box(
            modifier = Modifier
                .width(Dims.SectionBarW)
                .height(Dims.SectionBarH)
                .clip(RoundedCornerShape(2.dp))
                .background(cs.primary),
        )
        Text(
            text = title.uppercase(),
            style = CinemaTypo.SectionTitle,
            color = cs.onBackground,
        )
        if (count != null) {
            Text(
                text = count,
                style = CinemaTypo.Badge,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * Pill filtre focusable pour D-pad TV.
 * Sélectionnée = fond accent + texte noir. Non-sélectionnée = fond transparent + bordure.
 */
@Composable
fun PillFilter(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    val bgColor by animateColorAsState(
        targetValue = when {
            selected -> cs.primary
            isFocused -> cs.surfaceVariant
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "pillBg",
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> cs.primary
            selected -> Color.Transparent
            else -> cs.outline
        },
        animationSpec = tween(180),
        label = "pillBorder",
    )

    val textColor = when {
        selected -> cs.onPrimary
        isFocused -> cs.onSurface
        else -> cs.onSurfaceVariant
    }

    val scale = animateFocusScale(isFocused, targetScale = 1.04f)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(
                width = if (selected && !isFocused) 0.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp &&
                    (keyEvent.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        keyEvent.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_ENTER)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CinemaTypo.Label,
            color = textColor,
        )
    }
}

/**
 * Badge source avec pastille de statut.
 *
 * @param label Nom de la source (ex: "Plex", "Jellyfin")
 * @param color Couleur identitaire de la source
 * @param isConnected Statut de connexion
 */
@Composable
fun SourceBadge(
    label: String,
    color: Color,
    isConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(cs.surface)
            .border(1.dp, cs.outline, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Pastille de statut
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (isConnected) cs.tertiary else cs.onSurfaceVariant),
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

/**
 * Pill info technique non-focusable (lecture seule).
 * Ex: "HEVC", "DTS-HD", "1080p"
 */
@Composable
fun TechPill(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val cs = MaterialTheme.colorScheme
    Text(
        text = label,
        style = CinemaTypo.Badge,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(cs.surface)
            .border(1.dp, cs.outline, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * Animated "LIVE" badge — red background with blinking white dot.
 */
@Composable
fun LiveBadge(
    modifier: Modifier = Modifier,
    text: String = "EN DIRECT",
) {
    val cs = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "liveBlink")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotAlpha",
    )

    Row(
        modifier = modifier
            .background(cs.error, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .graphicsLayer { alpha = dotAlpha }
                .clip(CircleShape)
                .background(Color.White),
        )
        Text(
            text = text,
            style = CinemaTypo.BadgeSmall,
            color = Color.White,
        )
    }
}
