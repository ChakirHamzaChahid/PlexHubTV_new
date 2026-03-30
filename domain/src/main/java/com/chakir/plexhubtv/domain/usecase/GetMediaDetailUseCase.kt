package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.di.IoDispatcher
import com.chakir.plexhubtv.core.model.AudioStream
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.SourcePrefix
import com.chakir.plexhubtv.core.model.VideoStream
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

data class MediaDetail(
    val item: MediaItem,
    val children: List<MediaItem> = emptyList(), // Seasons or Episodes
)

/**
 * Retrieves media details from the primary server.
 *
 * Optimized for speed:
 * 1. Fetches primary metadata.
 * 2. Fetches children (Seasons or Episodes) if applicable.
 *
 * Does NOT perform cross-server aggregation or search. Use [EnrichMediaItemUseCase] for that.
 */
class GetMediaDetailUseCase
    @Inject
    constructor(
        private val mediaDetailRepository: MediaDetailRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        /**
         * @param ratingKey ID de l'élément sur le serveur principal.
         * @param serverId ID du serveur principal.
         */
        operator fun invoke(
            ratingKey: String,
            serverId: String,
        ): Flow<Result<MediaDetail>> =
            flow {
                // Special case for IPTV channels: they don't need server lookup
                // ratingKey contains the stream URL, serverId is "iptv"
                if (serverId == "iptv") {
                    // For IPTV, ratingKey is actually the stream URL
                    val iptvItem =
                        MediaItem(
                            id = "iptv:$ratingKey",
                            ratingKey = ratingKey,
                            serverId = "iptv",
                            title = ratingKey, // Will be overridden by navigation args if available
                            type = MediaType.Clip, // Use Clip for IPTV streams
                            mediaParts =
                                listOf(
                                    com.chakir.plexhubtv.core.model.MediaPart(
                                        id = "iptv-part-0",
                                        key = ratingKey, // Stream URL
                                        duration = null,
                                        file = ratingKey,
                                        size = null,
                                        container = "mpegts", // Common for IPTV
                                        streams = emptyList(),
                                    ),
                                ),
                        )
                    emit(Result.success(MediaDetail(item = iptvItem, children = emptyList())))
                    return@flow
                }

                supervisorScope {
                    // 1. Fetch primary detail + speculatively fetch children in TRUE parallel
                    // Both getMediaDetail and getChildren are Room-first (~5ms), so launching both
                    // simultaneously saves the sequential wait even if children aren't needed.
                    val itemDeferred = async { mediaDetailRepository.getMediaDetail(ratingKey, serverId) }
                    val childrenDeferred = async { mediaDetailRepository.getShowSeasons(ratingKey, serverId) }
                    
                    val itemResult = try { itemDeferred.await() } catch (e: Exception) { Result.failure(e) }

                    if (itemResult.isSuccess) {
                        val item = itemResult.getOrThrow()

                        // 2. Use speculative children only for Show/Season types
                        val children = when (item.type) {
                            MediaType.Show -> childrenDeferred.await().getOrDefault(emptyList())
                            MediaType.Season -> {
                                val primaryEpisodes = childrenDeferred.await().getOrDefault(emptyList())
                                // For seasons with multi-source, merge episodes from all sources
                                if (item.remoteSources.size > 1) {
                                    mergeEpisodesFromAllSources(item, primaryEpisodes)
                                } else {
                                    primaryEpisodes
                                }
                            }
                            else -> {
                                childrenDeferred.cancel()
                                emptyList()
                            }
                        }

                        emit(Result.success(MediaDetail(item = item, children = children)))
                    } else {
                        childrenDeferred.cancel()
                        emit(Result.failure(itemResult.exceptionOrNull() ?: Exception("Failed to load details")))
                    }
                }
            }.flowOn(ioDispatcher)

    /**
     * Merges episodes from all available sources for a multi-source season.
     * Returns the MAXIMUM set of episodes across all sources (union, not intersection).
     *
     * Example: Plex has 6 episodes, Xtream has 4 episodes
     * Result: 6 episodes total (4 with multi-source, 2 with Plex-only)
     */
    private suspend fun mergeEpisodesFromAllSources(
        season: MediaItem,
        primaryEpisodes: List<MediaItem>
    ): List<MediaItem> = supervisorScope {
        try {
            // Load episodes from all remote sources in parallel
            val allSourceEpisodes = season.remoteSources.map { source ->
                async {
                    try {
                        val result = mediaDetailRepository.getSeasonEpisodes(source.ratingKey, source.serverId)
                        result.getOrNull()?.map { episode ->
                            episode to source
                        } ?: emptyList()
                    } catch (e: Exception) {
                        timber.log.Timber.w(e, "Failed to load episodes from ${source.serverName}")
                        emptyList()
                    }
                }
            }.awaitAll().flatten()

            // Group episodes by index (S01E01, S01E02, etc.)
            val episodesByIndex = allSourceEpisodes.groupBy { (episode, _) ->
                episode.episodeIndex ?: -1
            }

            // Merge: for each episode index, create a MediaItem with all available sources
            val mergedEpisodes = episodesByIndex.mapNotNull { (episodeIndex, episodesWithSources) ->
                if (episodeIndex == -1) return@mapNotNull null // Skip episodes without index

                // Pick the "best" episode (from Plex if available) as the base
                val (baseEpisode, baseSources) = episodesWithSources
                    .find { (ep, src) -> !SourcePrefix.isNonPlex(src.serverId) }
                    ?: episodesWithSources.firstOrNull()
                    ?: return@mapNotNull null

                // Build remoteSources list from all available sources for this episode
                val episodeSources = episodesWithSources.map { (episode, source) ->
                    // Extract technical details from episode's mediaParts/streams
                    val bestMedia = episode.mediaParts.firstOrNull()
                    val videoStream = bestMedia?.streams?.filterIsInstance<VideoStream>()?.firstOrNull()
                    val audioStream = bestMedia?.streams?.filterIsInstance<AudioStream>()?.firstOrNull()

                    com.chakir.plexhubtv.core.model.MediaSource(
                        serverId = source.serverId,
                        ratingKey = episode.ratingKey,
                        serverName = source.serverName,
                        resolution = videoStream?.let { "${it.height}p" },
                        container = bestMedia?.container,
                        videoCodec = videoStream?.codec,
                        audioCodec = audioStream?.codec,
                        audioChannels = audioStream?.channels,
                        fileSize = bestMedia?.size,
                        bitrate = videoStream?.bitrate,
                        hasHDR = videoStream?.hasHDR ?: false,
                        languages = bestMedia?.streams?.filterIsInstance<AudioStream>()
                            ?.mapNotNull { it.language }?.distinct() ?: emptyList(),
                        thumbUrl = episode.thumbUrl,
                        artUrl = episode.artUrl,
                        viewOffset = episode.viewOffset,
                    )
                }

                // Return merged episode with all sources
                baseEpisode.copy(remoteSources = episodeSources)
            }.sortedBy { it.episodeIndex }

            timber.log.Timber.d("Episode merge: ${primaryEpisodes.size} primary → ${mergedEpisodes.size} merged (${season.remoteSources.size} sources)")
            mergedEpisodes
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to merge episodes from all sources, falling back to primary")
            primaryEpisodes
        }
    }
}