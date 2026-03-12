package com.chakir.plexhubtv.core.model

data class UnifiedSeason(
    val seasonIndex: Int,
    val title: String,
    val thumbUrl: String?,
    val episodes: List<UnifiedEpisode>,
    val availableServerIds: Set<String>,
    val bestSeasonRatingKey: String? = null,
    val bestSeasonServerId: String? = null,
)

data class UnifiedEpisode(
    val episodeIndex: Int,
    val title: String,
    val duration: Long?,
    val thumbUrl: String?,
    val summary: String?,
    val bestRatingKey: String,
    val bestServerId: String,
    val sources: List<EpisodeSource>,
)

data class EpisodeSource(
    val serverId: String,
    val serverName: String,
    val ratingKey: String,
)
