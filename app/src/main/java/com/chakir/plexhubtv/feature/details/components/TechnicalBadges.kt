package com.chakir.plexhubtv.feature.details.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.domain.model.AudioStream
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.model.VideoStream

@Composable
fun TechnicalBadges(
    media: MediaItem,
    modifier: Modifier = Modifier
) {
    // Basic logic to extract best tracks
    // Typically the first video track is the main one.
    // We want to show: [4K/1080p] [HDR/Dolby Vision] [Audio Codec] [Audio Channels]

    val part = media.mediaParts.firstOrNull() ?: return
    val videoStream = part.streams.filterIsInstance<VideoStream>().firstOrNull()
    val audioStream = part.streams.filterIsInstance<AudioStream>().firstOrNull()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Resolution Badge
        videoStream?.let { video ->
            val resTitle = when {
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
                 Badge(text = "HDR", color = Color(0xFFFFD700)) // Gold
            }
            if (video.displayTitle?.contains("Dolby Vision", ignoreCase = true) == true || video.displayTitle?.contains("DoVi", ignoreCase = true) == true) {
                 Badge(text = "DOLBY VISION", color = Color(0xFFE91E63)) // Pinkish
            }
        }

        // Audio Badge
        audioStream?.let { audio ->
            // Codec
            val codec = audio.codec?.uppercase() ?: "AUDIO"
            val displayCodec = when(codec) {
                "AC3" -> "DOLBY DIGITAL"
                "EAC3" -> "DOLBY DIGITAL PLUS"
                "DCA" -> "DTS"
                "AAC" -> "AAC"
                "MP3" -> "MP3"
                else -> codec
            }
            Badge(text = displayCodec)

            // Channels
            val channels = audio.channels ?: 2
            val channelText = when(channels) {
                8 -> "7.1"
                6 -> "5.1"
                2 -> "STEREO" // Or 2.0
                1 -> "MONO"
                else -> "$channels CH"
            }
            Badge(text = channelText)
            
            // Atmos Check
             if (audio.displayTitle?.contains("Atmos", ignoreCase = true) == true) {
                 Badge(text = "ATMOS", color = Color(0xFF00B0FF)) // Blue
            }
        }
    }
}

@Composable
fun Badge(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = color.copy(alpha = 0.5f)
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        color = Color.Transparent
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = color
        )
    }
}
