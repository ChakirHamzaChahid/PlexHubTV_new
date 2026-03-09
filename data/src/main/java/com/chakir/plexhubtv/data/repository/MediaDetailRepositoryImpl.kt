package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.database.MediaEntity
import com.chakir.plexhubtv.core.model.Collection
import com.chakir.plexhubtv.core.model.EpisodeSource
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.UnifiedEpisode
import com.chakir.plexhubtv.core.model.UnifiedSeason
import com.chakir.plexhubtv.core.util.MediaUrlResolver
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.data.source.MediaSourceResolver
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaDetailRepositoryImpl
    @Inject
    constructor(
        private val serverClientResolver: ServerClientResolver,
        private val mediaDao: MediaDao,
        private val collectionDao: com.chakir.plexhubtv.core.database.CollectionDao,
        private val mapper: MediaMapper,
        private val mediaUrlResolver: MediaUrlResolver,
        private val mediaSourceResolver: MediaSourceResolver,
        private val serverNameResolver: ServerNameResolver,
        @com.chakir.plexhubtv.core.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : MediaDetailRepository {
        override suspend fun getMediaDetail(
            ratingKey: String,
            serverId: String,
        ): Result<MediaItem> =
            mediaSourceResolver.resolve(serverId).getDetail(ratingKey, serverId)

        override suspend fun getSeasonEpisodes(
            ratingKey: String,
            serverId: String,
        ): Result<List<MediaItem>> =
            mediaSourceResolver.resolve(serverId).getEpisodes(ratingKey, serverId)

        override suspend fun findRemoteSources(item: MediaItem): List<MediaItem> {
            val entities = if (item.type == com.chakir.plexhubtv.core.model.MediaType.Episode) {
                val seasonIndex = item.parentIndex
                val episodeIndex = item.episodeIndex
                if (seasonIndex != null && episodeIndex != null) {
                    // Primary: match via parent show's unificationId (handles title variations across servers)
                    val grandparentRatingKey = item.grandparentRatingKey
                    val showUnificationId = if (grandparentRatingKey != null) {
                        mediaDao.getMedia(grandparentRatingKey, item.serverId)?.unificationId
                    } else null

                    if (!showUnificationId.isNullOrBlank()) {
                        mediaDao.findRemoteEpisodesByShowUnificationId(
                            showUnificationId, seasonIndex, episodeIndex, item.serverId
                        )
                    } else {
                        // Fallback: exact grandparentTitle match
                        val showTitle = item.grandparentTitle
                        if (showTitle != null) {
                            mediaDao.findRemoteEpisodeSources(showTitle, seasonIndex, episodeIndex, item.serverId)
                        } else emptyList()
                    }
                } else {
                    emptyList()
                }
            } else {
                // For movies/shows: match by unificationId
                val unificationId = item.unificationId
                if (unificationId.isNullOrBlank()) return emptyList()
                mediaDao.findRemoteSources(unificationId, item.serverId)
            }
            return entities.map { entity ->
                val client = serverClientResolver.getClient(entity.serverId)
                val baseUrl = client?.baseUrl
                val token = client?.server?.accessToken
                val domain = mapper.mapEntityToDomain(entity)
                if (baseUrl != null && token != null) {
                    mediaUrlResolver.resolveUrls(domain, baseUrl, token).copy(
                        baseUrl = baseUrl, accessToken = token
                    )
                } else domain
            }
        }

        override suspend fun getUnifiedSeasons(
            showUnificationId: String,
            enabledServerIds: List<String>,
        ): List<UnifiedSeason> {
            val allEpisodes = mediaDao.getUnifiedEpisodesByShowId(showUnificationId, enabledServerIds)
            if (allEpisodes.isEmpty()) return emptyList()

            val serverNames = serverNameResolver.getServerNameMap()

            return allEpisodes
                .filter { it.parentIndex != null && it.index != null }
                .groupBy { it.parentIndex!! }
                .toSortedMap()
                .map { (seasonIdx, seasonEpisodes) ->
                    val byEpIndex = seasonEpisodes.groupBy { it.index!! }
                    val unifiedEps = byEpIndex.toSortedMap().map { (epIdx, entities) ->
                        val best = pickBestEntity(entities)
                        UnifiedEpisode(
                            episodeIndex = epIdx,
                            title = best.title,
                            duration = best.duration,
                            thumbUrl = best.thumbUrl ?: best.parentThumb,
                            summary = best.summary,
                            bestRatingKey = best.ratingKey,
                            bestServerId = best.serverId,
                            sources = entities.map { entity ->
                                EpisodeSource(
                                    serverId = entity.serverId,
                                    serverName = serverNames[entity.serverId] ?: entity.serverId,
                                    ratingKey = entity.ratingKey,
                                )
                            },
                        )
                    }
                    val allServerIds = unifiedEps.flatMap { ep -> ep.sources.map { it.serverId } }.toSet()
                    val bestSeasonEntity = seasonEpisodes.maxByOrNull { metadataScore(it) }
                    UnifiedSeason(
                        seasonIndex = seasonIdx,
                        title = bestSeasonEntity?.parentTitle ?: "Season $seasonIdx",
                        thumbUrl = bestSeasonEntity?.parentThumb,
                        episodes = unifiedEps,
                        availableServerIds = allServerIds,
                        bestSeasonRatingKey = bestSeasonEntity?.parentRatingKey,
                        bestSeasonServerId = bestSeasonEntity?.serverId,
                    )
                }
        }

        private fun pickBestEntity(entities: List<MediaEntity>): MediaEntity =
            entities.maxByOrNull { metadataScore(it) } ?: entities.first()

        private fun metadataScore(entity: MediaEntity): Int {
            var score = 0
            if (!entity.summary.isNullOrBlank()) score += 2
            if (!entity.thumbUrl.isNullOrBlank()) score += 2
            if (!entity.imdbId.isNullOrBlank()) score += 1
            if (!entity.tmdbId.isNullOrBlank()) score += 1
            val year = entity.year
            if (year != null && year > 0) score += 1
            if (!entity.genres.isNullOrBlank()) score += 1
            score += mediaSourceResolver.resolve(entity.serverId).metadataScoreBonus()
            return score
        }


        override suspend fun updateMediaParts(item: MediaItem) {
            // Persist mediaParts to Room for future sessions (lazy progressive cache)
            try {
                val existing = mediaDao.getMedia(item.ratingKey, item.serverId)
                if (existing != null && item.mediaParts.isNotEmpty()) {
                    // Update entity with mediaParts while preserving filter/sortOrder/pageOffset
                    val updated = existing.copy(mediaParts = item.mediaParts)
                    mediaDao.insertMedia(updated)
                    Timber.d("MediaParts cached in Room for ${item.title} (${item.ratingKey})")
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to cache mediaParts for ${item.ratingKey}")
            }
        }

        override suspend fun getParentShowIds(ratingKey: String, serverId: String): com.chakir.plexhubtv.domain.repository.ParentShowInfo? {
            val entity = mediaDao.getMedia(ratingKey, serverId) ?: return null
            return com.chakir.plexhubtv.domain.repository.ParentShowInfo(
                imdbId = entity.imdbId,
                tmdbId = entity.tmdbId,
                unificationId = entity.unificationId,
            )
        }

        override suspend fun findRemoteShowByUnificationId(unificationId: String, serverId: String): MediaItem? {
            val entity = mediaDao.findShowByUnificationIdAndServer(unificationId, serverId)
                ?: return null
            val client = serverClientResolver.getClient(serverId)
            val baseUrl = client?.baseUrl
            val token = client?.server?.accessToken
            val domain = mapper.mapEntityToDomain(entity)
            return if (baseUrl != null && token != null) {
                mediaUrlResolver.resolveUrls(domain, baseUrl, token).copy(
                    baseUrl = baseUrl, accessToken = token
                )
            } else domain
        }

        override suspend fun findServersWithShow(unificationId: String, excludeServerId: String): Map<String, String> =
            mediaDao.findServersWithShow(unificationId, excludeServerId)

        override suspend fun getShowSeasons(
            ratingKey: String,
            serverId: String,
        ): Result<List<MediaItem>> =
            mediaSourceResolver.resolve(serverId).getSeasons(ratingKey, serverId)

        override suspend fun getSimilarMedia(
            ratingKey: String,
            serverId: String,
        ): Result<List<MediaItem>> =
            mediaSourceResolver.resolve(serverId).getSimilarMedia(ratingKey, serverId)

        override fun getMediaCollections(
            ratingKey: String,
            serverId: String,
        ): Flow<List<Collection>> {
            return collectionDao.getCollectionsForMedia(ratingKey, serverId)
                .map { collectionEntities ->
                    if (collectionEntities.isEmpty()) {
                        Timber.d("Collections: No collections found in DB for $ratingKey")
                        emptyList()
                    } else {
                        Timber.d("Collections: Found ${collectionEntities.size} collections in DB for $ratingKey")

                        // ✅ OPTIMIZED: Batch query to eliminate N+1 problem
                        // Fetch ALL media for ALL collections in a single query
                        val collectionIds = collectionEntities.map { it.id }
                        val allMediaWithCollection = collectionDao.getMediaForCollectionsBatch(collectionIds, serverId)

                        // Group media by collection ID
                        val mediaByCollection = allMediaWithCollection.groupBy { it.collectionId }

                        // Map to domain objects
                        collectionEntities.map { collEntity ->
                            val mediaList = mediaByCollection[collEntity.id] ?: emptyList()
                            val items = mediaList.map { mediaWithCol ->
                                val client = serverClientResolver.getClient(mediaWithCol.media.serverId)
                                val baseUrl = client?.baseUrl
                                val token = client?.server?.accessToken

                                val domain = mapper.mapEntityToDomain(mediaWithCol.media)
                                if (baseUrl != null && token != null) {
                                    mediaUrlResolver.resolveUrls(domain, baseUrl, token).copy(
                                        baseUrl = baseUrl,
                                        accessToken = token,
                                    )
                                } else {
                                    domain
                                }
                            }

                            Collection(
                                id = collEntity.id,
                                serverId = collEntity.serverId,
                                title = collEntity.title,
                                items = items,
                            )
                        }
                    }
                }
                .flowOn(ioDispatcher)
        }

        override fun getCollection(
            collectionId: String,
            serverId: String,
        ): Flow<Collection?> {
            return collectionDao.getCollection(collectionId, serverId)
                .map { collectionEntity ->
                    if (collectionEntity == null) {
                        Timber.w("Collection: Not found in DB - id=$collectionId server=$serverId")
                        null
                    } else {
                        Timber.d("Collection: Found '${collectionEntity.title}' in DB")
                        
                        // Get collection items from database
                        val items = collectionDao.getMediaInCollection(collectionId, serverId)
                            .map { mediaEntities ->
                                mediaEntities.map { entity ->
                                    val client = serverClientResolver.getClient(entity.serverId)
                                    val baseUrl = client?.baseUrl
                                    val token = client?.server?.accessToken
                                    
                                    val domain = mapper.mapEntityToDomain(entity)
                                    if (baseUrl != null && token != null) {
                                        mediaUrlResolver.resolveUrls(domain, baseUrl, token).copy(
                                            baseUrl = baseUrl,
                                            accessToken = token,
                                        )
                                    } else {
                                        domain
                                    }
                                }
                            }
                            .first()
                        
                        Collection(
                            id = collectionEntity.id,
                            serverId = collectionEntity.serverId,
                            title = collectionEntity.title,
                            items = items,
                        )
                    }
                }
                .flowOn(ioDispatcher)
        }

    }
