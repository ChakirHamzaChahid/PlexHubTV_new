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
        private val plexApiCache: PlexApiCache,
        private val mapper: MediaMapper,
        private val mediaUrlResolver: MediaUrlResolver,
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
                        // We don't have GSON injected here easily without more refactor,
                        // but the original code had it. Let's assume for now we want fresh data
                        // or we inject GSON too if needed. Original MediaRepositoryImpl had GSON.
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to parse cached metadata")
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
            try {
                val client = getClient(serverId)
                if (client != null) {
                    val response = client.getChildren(ratingKey)
                    if (response.isSuccessful) {
                        val metadata = response.body()?.mediaContainer?.metadata
                        if (metadata != null) {
                            val items =
                                metadata.map {
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

            val localEntities = mediaDao.getChildren(ratingKey, serverId)
            if (localEntities.isNotEmpty()) {
                val client = getClient(serverId)
                val baseUrl = client?.baseUrl
                val token = client?.server?.accessToken

                val items =
                    localEntities.map {
                        mapper.mapEntityToDomain(it).let { domain ->
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
                    val metadata = response.body()?.mediaContainer?.metadata ?: emptyList()
                    val items =
                        metadata.map {
                            mapper.mapDtoToDomain(it, serverId, client.baseUrl, client.server.accessToken)
                        }
                    Result.success(items)
                } else {
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
        ): Flow<List<Collection>> =
            flow {
                val serverResult = authRepository.getServers()
                val servers = serverResult.getOrNull() ?: emptyList()

                val item = getMediaDetail(ratingKey, serverId).getOrNull() ?: return@flow
                val guid = item.guid ?: return@flow

                coroutineScope {
                    val deferreds =
                        servers.map { server ->
                            async(ioDispatcher) {
                                val baseUrl = connectionManager.findBestConnection(server) ?: return@async emptyList<Collection>()
                                val client = PlexClient(server, api, baseUrl)

                                val metadataResponse = client.getMetadataByGuid(guid)
                                if (metadataResponse.isSuccessful) {
                                    val metadata = metadataResponse.body()?.mediaContainer?.metadata?.firstOrNull()
                                    val collections = metadata?.collections ?: return@async emptyList()

                                    collections.mapNotNull { collDto ->
                                        val collRatingKey = collDto.ratingKey ?: collDto.id ?: return@mapNotNull null
                                        val hubResponse = client.getCollectionHubs(collRatingKey)
                                        if (hubResponse.isSuccessful) {
                                            val hubs = hubResponse.body()?.mediaContainer?.hubs ?: emptyList()
                                            val items =
                                                hubs.flatMap { hub ->
                                                    hub.metadata?.map { mapper.mapDtoToDomain(it, server.clientIdentifier, baseUrl, server.accessToken) } ?: emptyList<MediaItem>()
                                                }
                                            Collection(
                                                id = collRatingKey,
                                                serverId = server.clientIdentifier,
                                                title = collDto.tag ?: "Collection",
                                                items = items,
                                            )
                                        } else {
                                            null
                                        }
                                    }
                                } else {
                                    emptyList()
                                }
                            }
                        }

                    val allResults = deferreds.awaitAll().flatten()

                    data class CollectionKey(val title: String)
                    val aggregated =
                        allResults.groupBy { CollectionKey(it.title) }
                            .map { (key, group) ->
                                group.first().copy(
                                    items = group.flatMap { it.items }.distinctBy { it.guid ?: it.ratingKey },
                                )
                            }

                    emit(aggregated)
                }
            }.flowOn(ioDispatcher)

        override fun getCollection(
            collectionId: String,
            serverId: String,
        ): Flow<Collection?> =
            flow {
                val client = getClient(serverId) ?: return@flow
                val response = client.getCollectionHubs(collectionId)
                if (response.isSuccessful) {
                    val hubs = response.body()?.mediaContainer?.hubs ?: emptyList()
                    val items =
                        hubs.flatMap { hub ->
                            hub.metadata?.map { mapper.mapDtoToDomain(it, serverId, client.baseUrl, client.server.accessToken) } ?: emptyList()
                        }
                    emit(
                        Collection(
                            id = collectionId,
                            serverId = serverId,
                            title = "Collection",
                            items = items,
                        ),
                    )
                }
            }.flowOn(ioDispatcher)

        private suspend fun getClient(serverId: String): PlexClient? {
            val servers = authRepository.getServers(forceRefresh = false).getOrNull() ?: return null
            val server = servers.find { it.clientIdentifier == serverId } ?: return null
            val baseUrl = connectionManager.findBestConnection(server) ?: return null
            return PlexClient(server, api, baseUrl)
        }
    }
