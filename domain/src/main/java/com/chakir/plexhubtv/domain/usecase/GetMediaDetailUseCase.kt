package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

                coroutineScope {
                    // 1. Fetch primary detail + speculatively fetch children in TRUE parallel
                    // Both getMediaDetail and getChildren are Room-first (~5ms), so launching both
                    // simultaneously saves the sequential wait even if children aren't needed.
                    val itemDeferred = async { mediaDetailRepository.getMediaDetail(ratingKey, serverId) }
                    val childrenDeferred = async { mediaDetailRepository.getShowSeasons(ratingKey, serverId) }

                    val itemResult = itemDeferred.await()

                    if (itemResult.isSuccess) {
                        val item = itemResult.getOrThrow()

                        // 2. Use speculative children only for Show/Season types
                        val children = when (item.type) {
                            MediaType.Show, MediaType.Season -> childrenDeferred.await().getOrDefault(emptyList())
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
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }
