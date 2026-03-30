package com.chakir.plexhubtv.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class LiveChannel(
    val streamId: Int,              // Backend stream ID
    val serverId: String,           // "xtream_{accountId}" or "m3u"
    val name: String,
    val logoUrl: String?,
    val categoryId: String?,
    val categoryName: String?,
    val epgChannelId: String?,
    val containerExtension: String?, // ts, m3u8
    val tvArchive: Boolean = false,
    val tvArchiveDuration: Int = 0,
    val isAdult: Boolean = false,
    val nowPlaying: EpgEntry? = null,  // Current program
    val addedAt: Long = 0,
    val updatedAt: Long = 0,
    val accountLabel: String? = null,  // Human-readable label of the Xtream account
)

@Immutable
data class EpgEntry(
    val id: Int,
    val streamId: Int,
    val title: String,
    val description: String?,
    val startTime: Long,            // ms
    val endTime: Long,              // ms
    val lang: String?,
) {
    val progress: Float
        get() {
            val now = System.currentTimeMillis()
            if (now < startTime || now > endTime) return 0f
            return ((now - startTime).toFloat() / (endTime - startTime)).coerceIn(0f, 1f)
        }
}
