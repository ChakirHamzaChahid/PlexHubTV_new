package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.HomeContentDao
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.Server
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.PlexApiCache
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.core.network.PlexClient
import com.chakir.plexhubtv.core.network.model.GenericPlexResponse
import com.chakir.plexhubtv.core.util.MediaUrlResolver
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.data.repository.aggregation.MediaDeduplicator
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.HubsRepository
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject

class HubsRepositoryImpl
    @Inject
    constructor(
        private val api: PlexApiService,
        private val mediaDao: MediaDao,
        private val homeContentDao: HomeContentDao,
        private val plexApiCache: PlexApiCache,
        private val mapper: MediaMapper,
        private val authRepository: AuthRepository,
        private val connectionManager: ConnectionManager,
        private val gson: Gson,
        private val mediaUrlResolver: MediaUrlResolver,
        private val mediaDeduplicator: MediaDeduplicator,
        @com.chakir.plexhubtv.core.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : HubsRepository {
        override fun getUnifiedHubs(): Flow<List<Hub>> =
            flow {
                // 1. Emit Cache First
                val cachedHubs = getCachedHubs()
                emit(cachedHubs)

                val clients = getActiveClients()
                if (clients.isNotEmpty()) {
                    coroutineScope {
                        val servers = authRepository.getServers().getOrNull() ?: emptyList()
                        val ownedServerIds = servers.filter { it.isOwned }.map { it.clientIdentifier }.toSet()

                        val deferreds =
                            clients.map { client ->
                                async(ioDispatcher) {
                                    try {
                                        val serverId = client.server.clientIdentifier
                                        val cacheKey = "$serverId:/hubs"

                                        // 1. Try Cache First
                                        val cachedJson = plexApiCache.get(cacheKey)
                                        if (cachedJson != null) {
                                            try {
                                                val cachedBody = gson.fromJson(cachedJson, GenericPlexResponse::class.java)
                                                val hubs = cachedBody.mediaContainer?.hubs ?: emptyList()
                                                Timber.i("REPO [Hubs] CACHE HIT: server=$serverId hubs=${hubs.size}")

                                                return@async hubs.mapNotNull { hubDto ->
                                                    val hubIdentifier = hubDto.hubIdentifier ?: hubDto.title ?: "unknown"
                                                    val hubTitle = hubDto.title ?: "Unknown"

                                                    val metadata = hubDto.metadata ?: emptyList()
                                                    val entities =
                                                        metadata.filter { mapper.isQualityMetadata(it) && !it.ratingKey.isNullOrEmpty() }.map {
                                                                dto ->
                                                            mapper.mapDtoToEntity(
                                                                dto,
                                                                client.server.clientIdentifier,
                                                                dto.librarySectionID ?: "0",
                                                            )
                                                                .copy(
                                                                    filter = "hub",
                                                                    sortOrder = hubDto.hubIdentifier ?: "default",
                                                                    pageOffset = 0,
                                                                )
                                                        }

                                                    if (entities.isNotEmpty()) {
                                                        mediaDao.upsertMedia(entities)
                                                        val homeContent =
                                                            entities.mapIndexed { index, entity ->
                                                                com.chakir.plexhubtv.core.database.HomeContentEntity(
                                                                    type = "hub",
                                                                    hubIdentifier = hubIdentifier,
                                                                    title = hubTitle,
                                                                    itemServerId = entity.serverId,
                                                                    itemRatingKey = entity.ratingKey,
                                                                    orderIndex = index,
                                                                )
                                                            }
                                                        homeContentDao.insertHomeContent(homeContent)

                                                        Hub(
                                                            key = hubDto.key ?: "",
                                                            title = hubTitle,
                                                            type = hubDto.type ?: "mixed",
                                                            hubIdentifier = hubIdentifier,
                                                            items =
                                                                entities.map { entity ->
                                                                    val domain = mapper.mapEntityToDomain(entity)
                                                                    val baseUrl = client.baseUrl
                                                                    val token = client.server.accessToken

                                                                    mediaUrlResolver.resolveUrls(domain, baseUrl, token ?: "").copy(
                                                                        baseUrl = baseUrl,
                                                                        accessToken = token,
                                                                    )
                                                                },
                                                            serverId = client.server.clientIdentifier,
                                                        )
                                                    } else {
                                                        null
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Timber.e(e, "REPO [Hubs] CACHE PARSE FAILED: server=$serverId")
                                            }
                                        }

                                        val response = client.getHubs()
                                        if (response.isSuccessful) {
                                            val body = response.body() ?: return@async emptyList()
                                            Timber.i("REPO [Hubs] NETWORK SUCCESS: server=$serverId")

                                            // Save to cache
                                            plexApiCache.put(
                                                cacheKey = cacheKey,
                                                data = gson.toJson(body),
                                                ttlSeconds = 3600, // 1 hour for hubs
                                            )

                                            val hubs = body.mediaContainer?.hubs ?: emptyList()

                                            hubs.mapNotNull { hubDto ->
                                                val hubIdentifier = hubDto.hubIdentifier ?: hubDto.title ?: "unknown"
                                                val hubTitle = hubDto.title ?: "Unknown"

                                                val metadata = hubDto.metadata ?: emptyList()
                                                val entities =
                                                    metadata.filter { mapper.isQualityMetadata(it) && !it.ratingKey.isNullOrEmpty() }.map {
                                                            dto ->
                                                        mapper.mapDtoToEntity(
                                                            dto,
                                                            client.server.clientIdentifier,
                                                            dto.librarySectionID ?: "0",
                                                        )
                                                            .copy(
                                                                filter = "hub",
                                                                sortOrder = hubDto.hubIdentifier ?: "default",
                                                                pageOffset = 0,
                                                            )
                                                    }

                                                if (entities.isNotEmpty()) {
                                                    mediaDao.upsertMedia(entities)
                                                    val homeContent =
                                                        entities.mapIndexed { index, entity ->
                                                            com.chakir.plexhubtv.core.database.HomeContentEntity(
                                                                type = "hub",
                                                                hubIdentifier = hubIdentifier,
                                                                title = hubTitle,
                                                                itemServerId = entity.serverId,
                                                                itemRatingKey = entity.ratingKey,
                                                                orderIndex = index,
                                                            )
                                                        }
                                                    homeContentDao.insertHomeContent(homeContent)

                                                    Hub(
                                                        key = hubDto.key ?: "",
                                                        title = hubTitle,
                                                        type = hubDto.type ?: "mixed",
                                                        hubIdentifier = hubIdentifier,
                                                        items =
                                                            entities.map { entity ->
                                                                val domain = mapper.mapEntityToDomain(entity)
                                                                val baseUrl = client.baseUrl
                                                                val token = client.server.accessToken

                                                                mediaUrlResolver.resolveUrls(domain, baseUrl, token ?: "").copy(
                                                                    baseUrl = baseUrl,
                                                                    accessToken = token,
                                                                )
                                                            },
                                                        serverId = client.server.clientIdentifier,
                                                    )
                                                } else {
                                                    null
                                                }
                                            }
                                        } else {
                                            emptyList()
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "REPO [Hubs] SERVER FAILED: server=${client.server.name}")
                                        emptyList()
                                    }
                                }
                            }

                        val allHubs = deferreds.awaitAll().flatten()
                        if (allHubs.isNotEmpty()) {
                            val result = aggregateHubs(allHubs, ownedServerIds, servers)
                            emit(result)
                        }
                    }
                }
            }.flowOn(ioDispatcher)

        private suspend fun getCachedHubs(): List<Hub> {
            val servers = authRepository.getServers().getOrNull() ?: return emptyList()
            val ownedServerIds = servers.filter { it.isOwned }.map { it.clientIdentifier }.toSet()

            // Fetch distinct hubs from DB
            val hubInfos = homeContentDao.getHubsList()

            val allHubs =
                hubInfos.map { hubInfo ->
                    val entities = homeContentDao.getHomeMediaItems("hub", hubInfo.hubIdentifier)
                    val items =
                        entities.map { entity ->
                            val server = servers.find { it.clientIdentifier == entity.serverId }
                            val baseUrl = if (server != null) connectionManager.getCachedUrl(server.clientIdentifier) ?: server.address else null

                            val domain = mapper.mapEntityToDomain(entity)
                            if (server != null && baseUrl != null) {
                                // Reconstruct full URLs from relative paths
                                mediaUrlResolver.resolveUrls(domain, baseUrl, server.accessToken ?: "").copy(
                                    baseUrl = baseUrl,
                                    accessToken = server.accessToken,
                                )
                            } else {
                                domain
                            }
                        }

                    Hub(
                        key = "", // Not needed for offline display
                        title = hubInfo.title,
                        type = "mixed",
                        hubIdentifier = hubInfo.hubIdentifier,
                        items = items,
                        serverId = null,
                    )
                }
            return aggregateHubs(allHubs, ownedServerIds, servers)
        }

        private suspend fun aggregateHubs(
            hubs: List<Hub>,
            ownedServerIds: Set<String>,
            servers: List<Server>,
        ): List<Hub> =
            coroutineScope {
                // Group hubs by identifier (e.g. "recentlyAdded")
                hubs.groupBy { it.hubIdentifier ?: it.title }.map { (identifier, group) ->
                    async {
                        val first = group.first()
                        Hub(
                            key = first.key,
                            title = first.title,
                            type = first.type,
                            hubIdentifier = identifier,
                            items = mediaDeduplicator.deduplicate(group.flatMap { it.items }, ownedServerIds, servers),
                            serverId = null, // Aggregate hubs are not server-specific
                        )
                    }
                }.awaitAll().sortedBy { it.title } // Or use a predefined order for hubs
            }

        private suspend fun getActiveClients(): List<PlexClient> =
            coroutineScope {
                val servers = authRepository.getServers(forceRefresh = true).getOrNull() ?: return@coroutineScope emptyList()

                servers.map { server ->
                    async {
                        val baseUrl = connectionManager.findBestConnection(server)
                        if (baseUrl != null) {
                            PlexClient(server, api, baseUrl)
                        } else {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }
    }
