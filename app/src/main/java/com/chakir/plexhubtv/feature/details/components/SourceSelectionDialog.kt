package com.chakir.plexhubtv.feature.details.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chakir.plexhubtv.core.designsystem.NetflixDarkGray
import com.chakir.plexhubtv.core.designsystem.NetflixLightGray
import com.chakir.plexhubtv.core.designsystem.NetflixRed
import com.chakir.plexhubtv.core.model.MediaSource
import timber.log.Timber

// Accent colors for technical badges
private val VideoColor = Color(0xFF64B5F6) // Light blue
private val AudioColor = Color(0xFFCE93D8) // Light purple
private val ContainerColor = Color(0xFF90A4AE) // Blue-gray
private val FileSizeColor = Color(0xFF81C784) // Light green
private val HdrGold = Color(0xFFFFD700)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceSelectionDialog(
    sources: List<MediaSource>,
    onDismiss: () -> Unit,
    onSourceSelected: (MediaSource) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    val sortedSources = remember(sources) {
        sources.sortedByDescending { it.fileSize ?: 0L }
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            Timber.e(e, "Error showing source selection dialog")
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Dark scrim overlay — NOT focusable (no clickable) so D-pad goes to content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .shadow(24.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1A1A1A),
                tonalElevation = 8.dp,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Dns,
                                contentDescription = null,
                                tint = NetflixRed,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Select Server",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Close",
                                tint = NetflixLightGray,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Source list
                    LazyColumn {
                        sortedSources.forEachIndexed { index, source ->
                            item {
                                val interactionSource = remember { MutableInteractionSource() }
                                val isFocused by interactionSource.collectIsFocusedAsState()
                                val bgColor by animateColorAsState(
                                    targetValue = if (isFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                                    animationSpec = tween(200),
                                    label = "sourceBg",
                                )
                                val borderColor by animateColorAsState(
                                    targetValue = if (isFocused) NetflixRed else Color.Transparent,
                                    animationSpec = tween(200),
                                    label = "sourceBorder",
                                )

                                val baseModifier = if (index == 0) {
                                    Modifier.focusRequester(focusRequester)
                                } else {
                                    Modifier
                                }

                                Column(
                                    modifier = baseModifier
                                        .fillMaxWidth()
                                        .background(bgColor, RoundedCornerShape(12.dp))
                                        .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                            onClick = { onSourceSelected(source) },
                                        )
                                        .padding(16.dp),
                                ) {
                                    // Server name + resolution/HDR badges
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            source.serverName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.weight(1f),
                                        )

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (source.hasHDR) {
                                                TechBadge(text = "HDR", color = HdrGold, filled = true)
                                            }
                                            source.resolution?.let { resolution ->
                                                val is4K = resolution.contains("4k", ignoreCase = true) || resolution.startsWith("2160")
                                                TechBadge(
                                                    text = resolution.uppercase(),
                                                    color = if (is4K) NetflixRed else NetflixLightGray,
                                                    filled = is4K,
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Technical badges row
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        // Container badge
                                        source.container?.let {
                                            IconTechBadge(
                                                icon = Icons.Outlined.Storage,
                                                text = it.uppercase(),
                                                color = ContainerColor,
                                            )
                                        }

                                        // Video codec badge
                                        source.videoCodec?.let {
                                            IconTechBadge(
                                                icon = Icons.Outlined.Videocam,
                                                text = it.uppercase(),
                                                color = VideoColor,
                                            )
                                        }

                                        // Audio codec + channels badge
                                        val audioText = buildString {
                                            source.audioCodec?.let { append(it.uppercase()) }
                                            source.audioChannels?.let { ch ->
                                                if (isNotEmpty()) append(" ")
                                                append(
                                                    when (ch) {
                                                        8 -> "7.1"
                                                        6 -> "5.1"
                                                        2 -> "Stereo"
                                                        1 -> "Mono"
                                                        else -> "${ch}ch"
                                                    }
                                                )
                                            }
                                        }
                                        if (audioText.isNotEmpty()) {
                                            IconTechBadge(
                                                icon = Icons.Outlined.GraphicEq,
                                                text = audioText,
                                                color = AudioColor,
                                            )
                                        }
                                    }

                                    // Languages + file size row
                                    val languages = source.languages.joinToString(", ")
                                    val hasLang = languages.isNotEmpty()
                                    val hasSize = source.fileSize != null

                                    if (hasLang || hasSize) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (hasLang) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Language,
                                                        contentDescription = null,
                                                        tint = NetflixLightGray.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        languages,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = NetflixLightGray.copy(alpha = 0.8f),
                                                    )
                                                }
                                            }

                                            if (hasSize) {
                                                source.fileSize?.let { size ->
                                                    val sizeInGb = size.toDouble() / (1024 * 1024 * 1024)
                                                    val sizeText = if (sizeInGb >= 1.0) {
                                                        String.format("%.2f GB", sizeInGb)
                                                    } else {
                                                        String.format("%.0f MB", size.toDouble() / (1024 * 1024))
                                                    }
                                                    Text(
                                                        sizeText,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = FileSizeColor,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (index < sortedSources.size - 1) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.08f),
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TechBadge(
    text: String,
    color: Color,
    filled: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (filled) color else Color.Transparent,
        border = if (!filled) androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.6f)) else null,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (filled) Color.Black else color,
        )
    }
}

@Composable
private fun IconTechBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
    }
}
