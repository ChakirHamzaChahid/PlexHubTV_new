package com.chakir.plexhubtv.feature.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import com.chakir.plexhubtv.domain.model.Marker

/** Bouton animé qui apparaît pendant les intros ou les crédits pour les passer. */
@Composable
fun SkipMarkerButton(
    marker: Marker?,
    markerType: String,
    isVisible: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayText =
        when (markerType) {
            "intro" -> "Passer l'intro"
            "credits" -> "Passer les crédits"
            else -> "Passer"
        }

    val buttonColor =
        when (markerType) {
            "intro" -> Color(0xFF4CAF50)
            "credits" -> Color(0xFFFF9800)
            else -> Color(0xFF2196F3)
        }

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isFocused) 1.1f else 1f)

    AnimatedVisibility(
        visible = isVisible && marker != null,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = modifier
    ) {
        androidx.compose.material3.Surface(
            onClick = onSkip,
            shape = RoundedCornerShape(8.dp),
            color = if (isFocused) buttonColor else buttonColor.copy(alpha = 0.8f),
            modifier = Modifier
                .scale(scale),
            interactionSource = interactionSource
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = displayText,
                    tint = Color.White,
                    modifier = Modifier
                )
                Text(
                    text = displayText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
