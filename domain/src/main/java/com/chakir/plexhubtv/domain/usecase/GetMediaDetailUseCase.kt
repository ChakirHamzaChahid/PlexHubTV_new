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
                coroutineScope {
                    // 1. Start fetching primary detail
                    val itemDeferred = async { mediaDetailRepository.getMediaDetail(ratingKey, serverId) }

                    val itemResult = itemDeferred.await()

                    if (itemResult.isSuccess) {
                        val item = itemResult.getOrThrow()

                        // 2. Parallelize Children Fetch
                        val childrenDeferred =
                            async {
                                when (item.type) {
                                    MediaType.Show -> mediaDetailRepository.getShowSeasons(ratingKey, serverId)
                                    MediaType.Season -> mediaDetailRepository.getSeasonEpisodes(ratingKey, serverId)
                                    else -> Result.success(emptyList())
                                }
                            }

                        val childrenResult = childrenDeferred.await()

                        emit(
                            Result.success(
                                MediaDetail(
                                    item = item,
                                    children = childrenResult.getOrDefault(emptyList()),
                                ),
                            ),
                        )
                    } else {
                        emit(Result.failure(itemResult.exceptionOrNull() ?: Exception("Failed to load details")))
                    }
                }
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }
