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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.feature.player.PlayerStats

@Composable
fun PerformanceOverlay(
    stats: PlayerStats,
    modifier: Modifier = Modifier,
) {
    val statsDesc = stringResource(R.string.player_stats_description)

    Box(
        modifier =
            modifier
                .testTag("player_performance_overlay")
                .semantics { contentDescription = statsDesc }
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(12.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.player_nerd_stats),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            StatRow(stringResource(R.string.player_stat_bitrate), stats.bitrate)
            StatRow(stringResource(R.string.player_stat_resolution), stats.resolution)
            StatRow(stringResource(R.string.player_stat_video_codec), stats.videoCodec)
            StatRow(stringResource(R.string.player_stat_audio_codec), stats.audioCodec)
            StatRow(stringResource(R.string.player_stat_dropped_frames), stats.droppedFrames.toString())
            StatRow(stringResource(R.string.player_stat_fps), if (stats.fps > 0) String.format("%.2f", stats.fps) else "N/A")
            // Convert cache duration from milliseconds to seconds with 2 decimals
            StatRow(stringResource(R.string.player_stat_cache), if (stats.cacheDuration > 0) String.format("%.2fs", stats.cacheDuration / 1000.0) else "0s")
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
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), // Increased from 11sp for TV readability
            color = Color.White.copy(alpha = 0.7f),
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), // Increased from 11sp for TV readability
            color = Color.Green,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
}
