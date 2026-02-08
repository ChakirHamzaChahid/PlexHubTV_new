package com.chakir.plexhubtv.data.repository.aggregation

import com.chakir.plexhubtv.core.model.AudioStream
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaSource
import com.chakir.plexhubtv.core.model.Server
import com.chakir.plexhubtv.core.model.VideoStream
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.util.MediaUrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface MediaDeduplicator {
    suspend fun deduplicate(
        items: List<MediaItem>,
        ownedServerIds: Set<String>,
        servers: List<Server>,
    ): List<MediaItem>
}

class DefaultMediaDeduplicator
    @Inject
    constructor(
        private val connectionManager: ConnectionManager,
        private val mediaUrlResolver: MediaUrlResolver,
    ) : MediaDeduplicator {
        override suspend fun deduplicate(
            items: List<MediaItem>,
            ownedServerIds: Set<String>,
            servers: List<Server>,
        ): List<MediaItem> =
            withContext(Dispatchers.Default) {
                // Advanced Grouping Logic
                // Priority: IMDB ID -> TMDB ID -> Title+Year
                items.groupBy { item ->
                    when {
                        !item.imdbId.isNullOrBlank() -> "imdb://${item.imdbId}"
                        !item.tmdbId.isNullOrBlank() -> "tmdb://${item.tmdbId}"
                        // Fallback to Title + Year (Normalized)
                        else -> "${item.title.lowercase().trim().replace(Regex("[^a-z0-9 ]"), "")}_${item.year ?: 0}"
                    }
                }.map { (_, group) ->
                    // Sort by Owned first, then by Last Updated (descending)
                    val prioritizedItem =
                        group.sortedWith(
                            compareByDescending<MediaItem> { it.serverId in ownedServerIds }
                                .thenByDescending { it.updatedAt ?: 0L },
                        ).first()

                    // Calculate Average Rating
                    val ratings = group.mapNotNull { it.rating }
                    val averageRating =
                        if (ratings.isNotEmpty()) {
                            ratings.average()
                        } else {
                            null
                        }

                    // Calculate Average Audience Rating
                    val audienceRatings = group.mapNotNull { it.audienceRating }
                    val averageAudienceRating =
                        if (audienceRatings.isNotEmpty()) {
                            audienceRatings.average()
                        } else {
                            null
                        }

                    // Populate Remote Sources
                    val sources =
                        group.map { item ->
                            val server = servers.find { it.clientIdentifier == item.serverId }
                            val serverName = server?.name ?: "Unknown"

                            // Extract resolution from mediaParts if available
                            val bestMedia = item.mediaParts.firstOrNull()
                            val videoStream = bestMedia?.streams?.filterIsInstance<VideoStream>()?.firstOrNull()
                            val audioStream = bestMedia?.streams?.filterIsInstance<AudioStream>()?.firstOrNull()
                            val languages =
                                bestMedia?.streams?.filterIsInstance<AudioStream>()
                                    ?.mapNotNull { it.language }?.distinct() ?: emptyList()

                            val resolution = videoStream?.let { "${it.height}p" }
                            val hasHDR = videoStream?.hasHDR ?: false

                            // Calculate full URLs for the alternative source using Resolver
                            val baseUrl = if (server != null) connectionManager.getCachedUrl(server.clientIdentifier) ?: server.address else null
                            val token = server?.accessToken ?: ""

                            // Use resolver for cleaner code
                            val fullThumb =
                                if (baseUrl != null) {
                                    mediaUrlResolver.resolveImageUrl(item.thumbUrl, baseUrl, token, 300, 450) ?: item.thumbUrl
                                } else {
                                    item.thumbUrl
                                }

                            val fullArt =
                                if (baseUrl != null) {
                                    mediaUrlResolver.resolveImageUrl(item.artUrl, baseUrl, token, 1280, 720) ?: item.artUrl
                                } else {
                                    item.artUrl
                                }

                            MediaSource(
                                serverId = item.serverId,
                                ratingKey = item.ratingKey,
                                serverName = serverName,
                                resolution = resolution,
                                container = bestMedia?.container,
                                videoCodec = videoStream?.codec,
                                audioCodec = audioStream?.codec,
                                audioChannels = audioStream?.channels,
                                fileSize = bestMedia?.size,
                                bitrate = videoStream?.bitrate,
                                hasHDR = hasHDR,
                                languages = languages,
                                thumbUrl = fullThumb,
                                artUrl = fullArt,
                            )
                        }

                    prioritizedItem.copy(
                        rating = averageRating,
                        audienceRating = averageAudienceRating,
                        remoteSources = sources,
                    )
                }
            }
    }
