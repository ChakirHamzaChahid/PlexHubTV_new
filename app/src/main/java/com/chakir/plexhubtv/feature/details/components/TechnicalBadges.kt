package com.chakir.plexhubtv.feature.details.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.model.AudioStream
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.VideoStream

/**
 * Composant UI affichant les badges techniques (4K, HDR, Dolby Vision, Audio).
 * Extrait les informations des MediaParts et Streams pour afficher les capacités du média.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TechnicalBadges(
    media: MediaItem,
    modifier: Modifier = Modifier,
) {
    // Basic logic to extract best tracks
    // Typically the first video track is the main one.
    // We want to show: [4K/1080p] [HDR/Dolby Vision] [Audio Codec] [Audio Channels]

    val part = media.mediaParts.firstOrNull() ?: return
    val videoStream = part.streams.filterIsInstance<VideoStream>().firstOrNull()
    val audioStream = part.streams.filterIsInstance<AudioStream>().firstOrNull()

    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 4,
    ) {
        // Resolution Badge
        videoStream?.let { video ->
            val resTitle =
                when {
                    (video.width ?: 0) >= 3800 -> "4K"
                    (video.height ?: 0) >= 1080 -> "1080p"
                    (video.height ?: 0) >= 720 -> "720p"
                    else -> "SD"
                }
            Badge(text = resTitle)

            // HDR Logic (Simplified, depends on codec info usually in 'displayTitle' or specific fields)
            // Plex usually puts "HDR" or "Dolby Vision" in displayTitle or sometimes 'colorSpace' but we don't have that yet?
            // Let's assume if displayTitle string contains HDR.
            if (video.displayTitle?.contains("HDR", ignoreCase = true) == true) {
                Badge(text = stringResource(R.string.badge_hdr), color = Color(0xFFFFD700)) // Gold
            }
            if (video.displayTitle?.contains("Dolby Vision", ignoreCase = true) == true || video.displayTitle?.contains("DoVi", ignoreCase = true) == true) {
                Badge(text = stringResource(R.string.badge_dolby_vision), color = Color(0xFFE91E63)) // Pinkish
            }
        }

        // Audio Badge
        audioStream?.let { audio ->
            // Codec
            val codec = audio.codec?.uppercase() ?: stringResource(R.string.badge_audio)
            val displayCodec =
                when (codec) {
                    "AC3" -> stringResource(R.string.badge_dolby_digital)
                    "EAC3" -> stringResource(R.string.badge_dolby_digital_plus)
                    "DCA" -> stringResource(R.string.badge_dts)
                    "AAC" -> stringResource(R.string.badge_aac)
                    "MP3" -> "MP3"
                    else -> codec
                }
            Badge(text = displayCodec)

            // Channels
            val channels = audio.channels ?: 2
            val channelText =
                when (channels) {
                    8 -> "7.1"
                    6 -> "5.1"
                    2 -> stringResource(R.string.badge_stereo) // Or 2.0
                    1 -> stringResource(R.string.badge_mono)
                    else -> "$channels CH"
                }
            Badge(text = channelText)

            // Atmos Check
            if (audio.displayTitle?.contains("Atmos", ignoreCase = true) == true) {
                Badge(text = stringResource(R.string.badge_atmos), color = Color(0xFF00B0FF)) // Blue
            }
        }
    }
}

@Composable
fun Badge(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = color.copy(alpha = 0.5f),
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        color = Color.Transparent,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = color,
        )
    }
}
