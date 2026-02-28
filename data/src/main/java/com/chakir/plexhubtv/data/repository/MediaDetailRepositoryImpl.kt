package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.network.util.safeApiCall
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.database.MediaEntity
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.Collection
import com.chakir.plexhubtv.core.model.EpisodeSource
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.UnifiedEpisode
import com.chakir.plexhubtv.core.model.UnifiedSeason
import com.chakir.plexhubtv.core.util.MediaUrlResolver
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.BackendRepository
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import com.chakir.plexhubtv.domain.repository.XtreamSeriesRepository
import com.chakir.plexhubtv.domain.repository.XtreamVodRepository
import com.chakir.plexhubtv.domain.usecase.MediaDetail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
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
        private val authRepository: AuthRepository,
        private val xtreamSeriesRepository: XtreamSeriesRepository,
        private val xtreamVodRepository: XtreamVodRepository,
        private val backendRepository: BackendRepository,
        @com.chakir.plexhubtv.core.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : MediaDetailRepository {
        // In-memory cache for similar items: "ratingKey:serverId" → (timestampMs, items)
        private val similarCache = ConcurrentHashMap<String, Pair<Long, List<MediaItem>>>()
        private val similarCacheTtlMs = 10 * 60 * 1000L // 10 minutes

        // In-memory cache for Xtream series detail (avoids double API call from parallel fetches)
        private val xtreamSeriesDetailCache = ConcurrentHashMap<String, Pair<Long, MediaDetail>>()
        private val xtreamSeriesDetailCacheTtlMs = 60 * 1000L // 1 minute
        override suspend fun getMediaDetail(
            ratingKey: String,
            serverId: String,
        ): Result<MediaItem> {
            // Backend: pre-enriched content from PlexHub Backend
            if (serverId.startsWith("backend_")) {
                return getBackendMediaDetail(ratingKey, serverId)
            }
            // Xtream: direct-play content — no mediaParts/streams needed
            if (serverId.startsWith("xtream_")) {
                return getXtreamMediaDetail(ratingKey, serverId)
            }

            // 1. BDD first — synced every 6h, instant access
            val localEntity = mediaDao.getMedia(ratingKey, serverId)
            if (localEntity != null) {
                // CRITICAL FIX: Check if mediaParts have streams (audio/subtitle tracks)
                // LibrarySyncWorker doesn't sync streams (endpoint /library/sections/{id}/all doesn't return them)
                // So we must fetch from network if streams are missing, otherwise audio/subtitle selection will be empty
                val hasStreams = localEntity.mediaParts.isNotEmpty() &&
                                localEntity.mediaParts.first().streams.isNotEmpty()

                if (hasStreams) {
                    val client = serverClientResolver.getClient(serverId)
                    val baseUrl = client?.baseUrl
                    val token = client?.server?.accessToken
                    val domain = mapper.mapEntityToDomain(localEntity)
                    val resolved = if (baseUrl != null && token != null) {
                        mediaUrlResolver.resolveUrls(domain, baseUrl, token).copy(
                            baseUrl = baseUrl, accessToken = token
                        )
                    } else domain
                    return Result.success(resolved)
                }
                // If no streams, fallthrough to network fetch
                Timber.d("MediaDetail: Room cache missing streams for $ratingKey, fetching from network")
            }

            // 2. API fallback — if not in BDD (new media, not yet synced)
            val client = serverClientResolver.getClient(serverId)
                ?: return Result.failure(AppError.Network.ServerError("Server $serverId unavailable"))

            return safeApiCall("getMediaDetail") {
                val response = client.getMetadata(ratingKey)
                if (response.isSuccessful) {
                    val metadata = response.body()?.mediaContainer?.metadata?.firstOrNull()
                    if (metadata != null) {
                        return@safeApiCall mapper.mapDtoToDomain(metadata, serverId, client.baseUrl, client.server.accessToken)
                    }
                }
                throw AppError.Media.NotFound("Media $ratingKey not found on server $serverId")
            }
        }

        override suspend fun getSeasonEpisodes(
            ratingKey: String,
            serverId: String,
        ): Result<List<MediaItem>> {
            // 1. Cache-first: Return Room data immediately when available (~5ms)
            val localEntities = mediaDao.getChildren(ratingKey, serverId)
            if (localEntities.isNotEmpty()) {
                // Xtream/Backend: no URL resolution needed (direct-play URLs built at playback time)
                if (serverId.startsWith("xtream_") || serverId.startsWith("backend_")) {
                    return Result.success(localEntities.map { mapper.mapEntityToDomain(it) })
                }

                val client = serverClientResolver.getClient(serverId)
                val baseUrl = client?.baseUrl
                val token = client?.server?.accessToken
                val cachedItems = localEntities.map {
                    mapper.mapEntityToDomain(it).let { domain ->
                        if (baseUrl != null && token != null) {
                            mediaUrlResolver.resolveUrls(domain, baseUrl, token).copy(
                                baseUrl = baseUrl, accessToken = token
                            )
                        } else domain
                    }
                }
                return Result.success(cachedItems)
            }

            // Backend: if no episodes in Room, fetch from backend API
            if (serverId.startsWith("backend_")) {
                return getBackendEpisodes(ratingKey, serverId)
            }

            // Xtream: if no episodes in Room, fetch series detail (which persists episodes) and re-query
            if (serverId.startsWith("xtream_") && ratingKey.startsWith("season_")) {
                val parts = ratingKey.removePrefix("season_").split("_", limit = 2)
                if (parts.size == 2) {
                    val seriesRatingKey = "series_${parts[0]}"
                    getCachedXtreamSeriesDetail(seriesRatingKey, serverId)
                    // Re-query after episodes were persisted by getSeriesDetail
                    val refreshed = mediaDao.getChildren(ratingKey, serverId)
                    if (refreshed.isNotEmpty()) {
                        return Result.success(refreshed.map { mapper.mapEntityToDomain(it) })
                    }
                }
                return Result.success(emptyList())
            }
            if (serverId.startsWith("xtream_")) {
                return Result.success(emptyList())
            }

            // 2. API fallback: Only if not in cache (new season, not yet synced)
            return safeApiCall("getSeasonEpisodes") {
                val client = serverClientResolver.getClient(serverId)
                if (client != null) {
                    val response = client.getChildren(ratingKey)
                    if (response.isSuccessful) {
                        val metadata = response.body()?.mediaContainer?.metadata
                        if (metadata != null) {
                            return@safeApiCall metadata.map {
                                mapper.mapDtoToDomain(it, serverId, client.baseUrl, client.server.accessToken)
                            }
                        }
                    }
                }
                throw AppError.Media.NotFound("Episodes for $ratingKey not found")
            }
        }

        override suspend fun findRemoteSources(item: MediaItem): List<MediaItem> {
            // For episodes: match by show title + season index + episode index (unificationId unreliable across servers)
            val entities = if (item.type == com.chakir.plexhubtv.core.model.MediaType.Episode) {
                val showTitle = item.grandparentTitle
                val seasonIndex = item.parentIndex
                val episodeIndex = item.episodeIndex
                if (showTitle != null && seasonIndex != null && episodeIndex != null) {
                    mediaDao.findRemoteEpisodeSources(showTitle, seasonIndex, episodeIndex, item.serverId)
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
            showTitle: String,
            enabledServerIds: List<String>,
        ): List<UnifiedSeason> {
            val allEpisodes = mediaDao.getUnifiedEpisodes(showTitle, enabledServerIds)
            if (allEpisodes.isEmpty()) return emptyList()

            val serverNames = buildServerNameMap()

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
            if (!entity.serverId.startsWith("xtream_") && !entity.serverId.startsWith("backend_")) score += 3
            return score
        }

        private suspend fun buildServerNameMap(): Map<String, String> {
            val map = mutableMapOf<String, String>()
            authRepository.getServers().getOrNull()?.forEach { server ->
                map[server.clientIdentifier] = server.name
            }
            return map
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

        override suspend fun getShowSeasons(
            ratingKey: String,
            serverId: String,
        ): Result<List<MediaItem>> {
            // Backend shows: fetch episodes from backend, group by parentIndex to build virtual seasons
            if (serverId.startsWith("backend_") && ratingKey.startsWith("series_")) {
                return getBackendSeasons(ratingKey, serverId)
            }

            // Xtream shows: seasons come from series info API (not stored in Room)
            if (serverId.startsWith("xtream_") && ratingKey.startsWith("series_")) {
                val detail = getCachedXtreamSeriesDetail(ratingKey, serverId)
                return detail?.let { Result.success(it.children) }
                    ?: Result.success(emptyList())
            }

            return getSeasonEpisodes(ratingKey, serverId) // Plex uses getChildren for both seasons of show and episodes of season
        }

        override suspend fun getSimilarMedia(
            ratingKey: String,
            serverId: String,
        ): Result<List<MediaItem>> {
            // Xtream/Backend: no similar media API available
            if (serverId.startsWith("xtream_") || serverId.startsWith("backend_")) {
                return Result.success(emptyList())
            }

            // Cache-first: return cached similar items if fresh (avoids 300-1000ms API call)
            val cacheKey = "$ratingKey:$serverId"
            val cached = similarCache[cacheKey]
            if (cached != null && System.currentTimeMillis() - cached.first < similarCacheTtlMs) {
                Timber.d("Similar: Cache hit for $ratingKey (${cached.second.size} items)")
                return Result.success(cached.second)
            }

            val client = serverClientResolver.getClient(serverId)
                ?: return Result.failure(AppError.Network.ServerError("Server $serverId unavailable"))

            return safeApiCall("getSimilarMedia") {
                val response = client.getRelated(ratingKey)
                if (!response.isSuccessful) {
                    throw AppError.Network.ServerError("Similar: API returned ${response.code()} for $ratingKey")
                }

                val body = response.body()
                if (body == null) {
                    Timber.w("Similar: Response body is null for $ratingKey")
                    return@safeApiCall emptyList<MediaItem>()
                }

                val hubs = body.mediaContainer?.hubs ?: emptyList()
                Timber.d("Similar: Received ${hubs.size} hubs for $ratingKey")

                val similarHub = hubs.find { it.hubIdentifier == "similar" } ?: hubs.firstOrNull()
                val metadata = similarHub?.metadata ?: emptyList()
                Timber.d("Similar: Found ${metadata.size} items in similar hub")

                val items = metadata.map {
                    mapper.mapDtoToDomain(it, serverId, client.baseUrl, client.server.accessToken)
                }
                similarCache[cacheKey] = System.currentTimeMillis() to items
                items
            }
        }

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

        // --- Backend detail helpers ---

        private suspend fun getBackendMediaDetail(ratingKey: String, serverId: String): Result<MediaItem> {
            // Room first: backend content is synced during library sync
            val localEntity = mediaDao.getMedia(ratingKey, serverId)
            if (localEntity != null) {
                return Result.success(mapper.mapEntityToDomain(localEntity))
            }

            // For seasons: parse seriesId, fetch episodes from backend, find matching season
            if (ratingKey.startsWith("season_")) {
                val parts = ratingKey.removePrefix("season_").split("_", limit = 2)
                if (parts.size == 2) {
                    val seriesRatingKey = "series_${parts[0]}"
                    val seasonNumber = parts[1].toIntOrNull()
                    // Fetch episodes and build virtual season
                    val seasons = getBackendSeasons(seriesRatingKey, serverId).getOrNull()
                    if (seasons != null && seasonNumber != null) {
                        val season = seasons.find { it.seasonIndex == seasonNumber }
                        if (season != null) return Result.success(season)
                    }
                }
            }

            // Fallback: fetch from backend API
            return backendRepository.getMediaDetail(ratingKey, serverId)
        }

        private suspend fun getBackendEpisodes(ratingKey: String, serverId: String): Result<List<MediaItem>> {
            // For season ratingKeys (season_<seriesId>_<seasonNum>), extract the parent show key
            val parentRatingKey = if (ratingKey.startsWith("season_")) {
                val parts = ratingKey.removePrefix("season_").split("_", limit = 2)
                if (parts.size == 2) "series_${parts[0]}" else ratingKey
            } else {
                ratingKey
            }

            val seasonNumber = if (ratingKey.startsWith("season_")) {
                ratingKey.removePrefix("season_").split("_", limit = 2).getOrNull(1)?.toIntOrNull()
            } else null

            return backendRepository.getEpisodes(parentRatingKey, serverId).map { episodes ->
                if (seasonNumber != null) {
                    episodes.filter { it.parentIndex == seasonNumber }
                } else {
                    episodes
                }
            }
        }

        private suspend fun getBackendSeasons(ratingKey: String, serverId: String): Result<List<MediaItem>> {
            // Fetch all episodes for this series, group by parentIndex to build virtual season items
            val result = backendRepository.getEpisodes(ratingKey, serverId)
            return result.map { episodes ->
                episodes.groupBy { it.parentIndex ?: 0 }
                    .toSortedMap()
                    .map { (seasonNum, seasonEpisodes) ->
                        val firstEp = seasonEpisodes.first()
                        val seriesId = ratingKey.removePrefix("series_")
                        val seasonRatingKey = "season_${seriesId}_$seasonNum"
                        MediaItem(
                            id = "$serverId:$seasonRatingKey",
                            ratingKey = seasonRatingKey,
                            serverId = serverId,
                            title = firstEp.parentTitle ?: "Season $seasonNum",
                            type = com.chakir.plexhubtv.core.model.MediaType.Season,
                            parentRatingKey = ratingKey,
                            seasonIndex = seasonNum,
                            thumbUrl = firstEp.thumbUrl,
                            parentTitle = firstEp.grandparentTitle,
                        )
                    }
            }
        }

        // --- Xtream detail helpers ---

        private suspend fun getXtreamMediaDetail(ratingKey: String, serverId: String): Result<MediaItem> {
            // For movies/episodes: return from Room directly (synced during library sync)
            val localEntity = mediaDao.getMedia(ratingKey, serverId)
            if (localEntity != null) {
                // VOD movies: enrich on first access (fetch plot, cast, genre, tmdb_id from get_vod_info)
                if (ratingKey.startsWith("vod_") && localEntity.summary == null) {
                    val vodId = ratingKey.removePrefix("vod_").substringBefore(".").toIntOrNull()
                    if (vodId != null) {
                        val accountId = serverId.removePrefix("xtream_")
                        xtreamVodRepository.enrichMovieDetail(accountId, vodId, ratingKey)
                        val enriched = mediaDao.getMedia(ratingKey, serverId)
                        if (enriched != null) {
                            return Result.success(mapper.mapEntityToDomain(enriched))
                        }
                    }
                }
                return Result.success(mapper.mapEntityToDomain(localEntity))
            }

            // For shows not in Room: fetch full detail from Xtream API
            if (ratingKey.startsWith("series_")) {
                val detail = getCachedXtreamSeriesDetail(ratingKey, serverId)
                if (detail != null) return Result.success(detail.item)
            }

            // For seasons: parse seriesId, fetch series detail, find matching season
            if (ratingKey.startsWith("season_")) {
                // Format: "season_<seriesId>_<seasonNumber>"
                val parts = ratingKey.removePrefix("season_").split("_", limit = 2)
                if (parts.size == 2) {
                    val seriesRatingKey = "series_${parts[0]}"
                    val seasonNumber = parts[1].toIntOrNull()
                    val detail = getCachedXtreamSeriesDetail(seriesRatingKey, serverId)
                    if (detail != null && seasonNumber != null) {
                        val season = detail.children.find { it.seasonIndex == seasonNumber }
                        if (season != null) return Result.success(season)
                    }
                }
            }

            return Result.failure(AppError.Media.NotFound("Xtream media $ratingKey not found"))
        }

        /**
         * Fetch Xtream series detail with a brief cache to avoid double API calls
         * from parallel getMediaDetail() + getShowSeasons() in GetMediaDetailUseCase.
         */
        private suspend fun getCachedXtreamSeriesDetail(ratingKey: String, serverId: String): MediaDetail? {
            val cacheKey = "$ratingKey:$serverId"
            val cached = xtreamSeriesDetailCache[cacheKey]
            if (cached != null && System.currentTimeMillis() - cached.first < xtreamSeriesDetailCacheTtlMs) {
                return cached.second
            }

            val accountId = serverId.removePrefix("xtream_")
            val seriesId = ratingKey.removePrefix("series_").toIntOrNull() ?: return null

            return xtreamSeriesRepository.getSeriesDetail(accountId, seriesId)
                .onSuccess { detail ->
                    xtreamSeriesDetailCache[cacheKey] = System.currentTimeMillis() to detail
                }
                .getOrNull()
        }
    }
