package com.chakir.plexhubtv.feature.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chakir.plexhubtv.feature.player.PlayerStats

@Composable
fun PerformanceOverlay(
    stats: PlayerStats,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .testTag("player_performance_overlay")
                .semantics { contentDescription = "Statistiques de performance" }
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(12.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "NERD STATS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            StatRow("Bitrate", stats.bitrate)
            StatRow("Resolution", stats.resolution)
            StatRow("Video Codec", stats.videoCodec)
            StatRow("Audio Codec", stats.audioCodec)
            StatRow("Dropped Frames", stats.droppedFrames.toString())
            StatRow("FPS", if (stats.fps > 0) String.format("%.2f", stats.fps) else "N/A")
            // Convert cache duration from milliseconds to seconds with 2 decimals
            StatRow("Cache", if (stats.cacheDuration > 0) String.format("%.2fs", stats.cacheDuration / 1000.0) else "0s")
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = Color.White.copy(alpha = 0.7f),
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = Color.Green,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
}
