package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.common.exception.AuthException
import com.chakir.plexhubtv.core.common.exception.MediaNotFoundException
import com.chakir.plexhubtv.core.common.exception.NetworkException
import com.chakir.plexhubtv.core.common.exception.ServerUnavailableException
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.model.Collection
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.PlexApiCache
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.core.network.PlexClient
import com.chakir.plexhubtv.core.util.MediaUrlResolver
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

class MediaDetailRepositoryImpl
    @Inject
    constructor(
        private val api: PlexApiService,
        private val authRepository: AuthRepository,
        private val connectionManager: ConnectionManager,
        private val mediaDao: MediaDao,
        private val collectionDao: com.chakir.plexhubtv.core.database.CollectionDao,
        private val plexApiCache: PlexApiCache,
        private val mapper: MediaMapper,
        private val mediaUrlResolver: MediaUrlResolver,
        private val gson: com.google.gson.Gson,
        @com.chakir.plexhubtv.core.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : MediaDetailRepository {
        override suspend fun getMediaDetail(
            ratingKey: String,
            serverId: String,
        ): Result<MediaItem> {
            return try {
                val client = getClient(serverId) ?: return Result.failure(ServerUnavailableException(serverId))

                // 1. Try Cache First for performance
                val cacheKey = "$serverId:/library/metadata/$ratingKey"
                val cachedJson = plexApiCache.get(cacheKey)
                if (cachedJson != null) {
                    try {
                        val cachedResponse = gson.fromJson(cachedJson, com.chakir.plexhubtv.core.network.model.PlexResponse::class.java)
                        val cachedMetadata = cachedResponse?.mediaContainer?.metadata?.firstOrNull()
                        if (cachedMetadata != null) {
                            val cachedItem = mapper.mapDtoToDomain(cachedMetadata, serverId, client.baseUrl, client.server.accessToken)
                            return Result.success(cachedItem)
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to parse cached metadata for $ratingKey")
                    }
                }

                val response = client.getMetadata(ratingKey)
                if (response.isSuccessful) {
                    val metadata = response.body()?.mediaContainer?.metadata?.firstOrNull()
                    if (metadata != null) {
                        val item = mapper.mapDtoToDomain(metadata, serverId, client.baseUrl, client.server.accessToken)
                        return Result.success(item)
                    }
                }

                // 2. If 404 or Failure, attempt Fallback via GUID
                val localEntity = mediaDao.getMedia(ratingKey, serverId)
                val guid = localEntity?.guid
                if (guid != null) {
                    val otherServers = authRepository.getServers().getOrNull()?.filter { it.clientIdentifier != serverId } ?: emptyList()
                    for (altServer in otherServers) {
                        val altBaseUrl = connectionManager.findBestConnection(altServer)
                        if (altBaseUrl != null) {
                            val altClient = PlexClient(altServer, api, altBaseUrl)
                            val altResponse = altClient.getMetadataByGuid(guid)
                            if (altResponse.isSuccessful) {
                                val altMetadata = altResponse.body()?.mediaContainer?.metadata?.firstOrNull()
                                if (altMetadata != null) {
                                    return Result.success(
                                        mapper.mapDtoToDomain(altMetadata, altServer.clientIdentifier, altBaseUrl, altServer.accessToken),
                                    )
                                }
                            }
                        }
                    }
                }

                Result.failure(MediaNotFoundException("Media $ratingKey not found on server $serverId"))
            } catch (e: IOException) {
                Timber.e(e, "Network error fetching media detail $ratingKey")
                // Fallback to Room if network fails
                val localEntity = mediaDao.getMedia(ratingKey, serverId)
                if (localEntity != null) {
                    return Result.success(mapper.mapEntityToDomain(localEntity))
                }
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
            // 1. Cache-first: Load from Room immediately
            val localEntities = mediaDao.getChildren(ratingKey, serverId)
            if (localEntities.isNotEmpty()) {
                val client = getClient(serverId)
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
                // If we have local data and no network, return immediately
                val networkClient = getClient(serverId)
                if (networkClient == null) return Result.success(cachedItems)
            }

            // 2. Attempt network fetch for refresh
            try {
                val client = getClient(serverId)
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

            // 3. Fallback: Return cache (already loaded above)
            if (localEntities.isNotEmpty()) {
                val client = getClient(serverId)
                val items = localEntities.map {
                    val domain = mapper.mapEntityToDomain(it)
                    val baseUrl = client?.baseUrl
                    val token = client?.server?.accessToken
                    if (baseUrl != null && token != null) {
                        mediaUrlResolver.resolveUrls(domain, baseUrl, token).copy(
                            baseUrl = baseUrl, accessToken = token
                        )
                    } else domain
                }
                return Result.success(items)
            }

            return Result.failure(MediaNotFoundException("Episodes for $ratingKey not found"))
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
            return try {
                val client = getClient(serverId) ?: return Result.failure(ServerUnavailableException(serverId))
                val response = client.getRelated(ratingKey)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        // The /related endpoint returns HUBS, not direct metadata
                        // Usually the first hub is "Similar" or "More like this"
                        val hubs = body.mediaContainer?.hubs ?: emptyList()
                        Timber.d("Similar: Received ${hubs.size} hubs for $ratingKey")
                        
                        val similarHub = hubs.find { it.hubIdentifier == "similar" } ?: hubs.firstOrNull()
                        val metadata = similarHub?.metadata ?: emptyList()
                        
                        Timber.d("Similar: Found ${metadata.size} items in similar hub")
                        
                        val items = metadata.map {
                            mapper.mapDtoToDomain(it, serverId, client.baseUrl, client.server.accessToken)
                        }
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
                                val client = getClient(mediaWithCol.media.serverId)
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
                                    val client = getClient(entity.serverId)
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

        private suspend fun getClient(serverId: String): PlexClient? {
            val servers = authRepository.getServers(forceRefresh = false).getOrNull() ?: return null
            val server = servers.find { it.clientIdentifier == serverId } ?: return null
            val baseUrl = connectionManager.findBestConnection(server) ?: return null
            return PlexClient(server, api, baseUrl)
        }
    }
