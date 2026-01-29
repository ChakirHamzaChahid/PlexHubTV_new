package com.chakir.plexhubtv.core.util

import java.util.Locale
import java.util.concurrent.TimeUnit

object FormatUtils {

    /**
     * Format bytes to human-readable string (e.g., "1.5 GB", "256.3 MB")
     */
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    /**
     * Format bitrate in bps to human-readable string
     */
    fun formatBitrate(bps: Int): String {
        val kbps = bps / 1000.0
        if (kbps < 1000) return String.format(Locale.US, "%.1f Kbps", kbps)
        val mbps = kbps / 1000.0
        return String.format(Locale.US, "%.1f Mbps", mbps)
    }

    /**
     * Formats a duration in timestamp format (e.g., "1:23:45" or "23:45").
     */
    fun formatDurationTimestamp(milliseconds: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    fun formatDuration(milliseconds: Long): String = formatDurationTimestamp(milliseconds)

    /**
     * Formats a duration in textual format (e.g., "1h 23m").
     */
    fun formatDurationTextual(milliseconds: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
        
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    fun toBulletedString(parts: List<String>): String {
        return parts.filter { it.isNotBlank() }.joinToString(" · ")
    }
}
