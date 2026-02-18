package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.common.exception.AuthException
import com.chakir.plexhubtv.core.common.exception.MediaNotFoundException
import com.chakir.plexhubtv.core.common.exception.NetworkException
import com.chakir.plexhubtv.core.common.exception.ServerUnavailableException
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.model.Collection
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.util.MediaUrlResolver
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
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
        @com.chakir.plexhubtv.core.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : MediaDetailRepository {
        // In-memory cache for similar items: "ratingKey:serverId" → (timestampMs, items)
        private val similarCache = ConcurrentHashMap<String, Pair<Long, List<MediaItem>>>()
        private val similarCacheTtlMs = 10 * 60 * 1000L // 10 minutes
        override suspend fun getMediaDetail(
            ratingKey: String,
            serverId: String,
        ): Result<MediaItem> {
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
            return try {
                val client = serverClientResolver.getClient(serverId)
                    ?: return Result.failure(ServerUnavailableException(serverId))

                val response = client.getMetadata(ratingKey)
                if (response.isSuccessful) {
                    val metadata = response.body()?.mediaContainer?.metadata?.firstOrNull()
                    if (metadata != null) {
                        return Result.success(
                            mapper.mapDtoToDomain(metadata, serverId, client.baseUrl, client.server.accessToken)
                        )
                    }
                }

                Result.failure(MediaNotFoundException("Media $ratingKey not found on server $serverId"))
            } catch (e: IOException) {
                Timber.e(e, "Network error fetching media detail $ratingKey")
                Result.failure(NetworkException("Network error", e))
            } catch (e: HttpException) {
                Timber.e(e, "HTTP error ${e.code()} fetching media detail $ratingKey")
                if (e.code() == 401) {
                    Result.failure(AuthException("Unauthorized", e))
                } else {
                    Result.failure(e)
                }
            } catch (e: Exception) {
                Timber.e(e, "Unknown error fetching media detail $ratingKey")
                Result.failure(e)
            }
        }

        override suspend fun getSeasonEpisodes(
            ratingKey: String,
            serverId: String,
        ): Result<List<MediaItem>> {
            // 1. Cache-first: Return Room data immediately when available (~5ms)
            val localEntities = mediaDao.getChildren(ratingKey, serverId)
            if (localEntities.isNotEmpty()) {
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

            // 2. API fallback: Only if not in cache (new season, not yet synced)
            try {
                val client = serverClientResolver.getClient(serverId)
                if (client != null) {
                    val response = client.getChildren(ratingKey)
                    if (response.isSuccessful) {
                        val metadata = response.body()?.mediaContainer?.metadata
                        if (metadata != null) {
                            val items = metadata.map {
                                mapper.mapDtoToDomain(it, serverId, client.baseUrl, client.server.accessToken)
                            }
                            return Result.success(items)
                        }
                    }
                }
            } catch (e: IOException) {
                Timber.w(e, "Network error fetching episodes for $ratingKey")
            } catch (e: HttpException) {
                Timber.w(e, "HTTP error ${e.code()} fetching episodes for $ratingKey")
            } catch (e: Exception) {
                Timber.e(e, "Error fetching episodes for $ratingKey")
            }

            return Result.failure(MediaNotFoundException("Episodes for $ratingKey not found"))
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
                if (item.unificationId.isNullOrBlank()) return emptyList()
                mediaDao.findRemoteSources(item.unificationId!!, item.serverId)
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
            return getSeasonEpisodes(ratingKey, serverId) // Plex uses getChildren for both seasons of show and episodes of season
        }

        override suspend fun getSimilarMedia(
            ratingKey: String,
            serverId: String,
        ): Result<List<MediaItem>> {
            // Cache-first: return cached similar items if fresh (avoids 300-1000ms API call)
            val cacheKey = "$ratingKey:$serverId"
            val cached = similarCache[cacheKey]
            if (cached != null && System.currentTimeMillis() - cached.first < similarCacheTtlMs) {
                Timber.d("Similar: Cache hit for $ratingKey (${cached.second.size} items)")
                return Result.success(cached.second)
            }

            return try {
                val client = serverClientResolver.getClient(serverId) ?: return Result.failure(ServerUnavailableException(serverId))
                val response = client.getRelated(ratingKey)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        val hubs = body.mediaContainer?.hubs ?: emptyList()
                        Timber.d("Similar: Received ${hubs.size} hubs for $ratingKey")

                        val similarHub = hubs.find { it.hubIdentifier == "similar" } ?: hubs.firstOrNull()
                        val metadata = similarHub?.metadata ?: emptyList()

                        Timber.d("Similar: Found ${metadata.size} items in similar hub")

                        val items = metadata.map {
                            mapper.mapDtoToDomain(it, serverId, client.baseUrl, client.server.accessToken)
                        }
                        similarCache[cacheKey] = System.currentTimeMillis() to items
                        Result.success(items)
                    } else {
                        Timber.w("Similar: Response body is null for $ratingKey")
                        Result.success(emptyList())
                    }
                } else {
                    Timber.w("Similar: API returned ${response.code()} for $ratingKey")
                    Result.failure(Exception("API Error: ${response.code()}"))
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error fetching similar media for $ratingKey")
                Result.failure(NetworkException("Network error", e))
            } catch (e: HttpException) {
                Timber.e(e, "HTTP error ${e.code()} fetching similar media for $ratingKey")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "Error fetching similar media for $ratingKey")
                Result.failure(e)
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
    }
