package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.database.MediaEntity
import com.chakir.plexhubtv.core.di.ApplicationScope
import com.chakir.plexhubtv.core.di.IoDispatcher
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.core.network.PlexClient
import com.chakir.plexhubtv.core.network.model.MetadataDTO
import com.chakir.plexhubtv.core.util.MediaUrlResolver
import com.chakir.plexhubtv.core.util.getOptimizedImageUrl
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.data.repository.aggregation.MediaDeduplicator
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.OnDeckRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

class OnDeckRepositoryImpl
    @Inject
    constructor(
        private val mediaDao: MediaDao,
        private val homeContentDao: com.chakir.plexhubtv.core.database.HomeContentDao,
        private val serverClientResolver: ServerClientResolver,
        private val authRepository: AuthRepository,
        private val connectionManager: ConnectionManager,
        private val api: PlexApiService,
        private val mapper: MediaMapper,
        private val mediaUrlResolver: MediaUrlResolver,
        private val mediaDeduplicator: MediaDeduplicator,
        @ApplicationScope private val applicationScope: CoroutineScope,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : OnDeckRepository {
        override fun getUnifiedOnDeck(): Flow<List<MediaItem>> =
            flow {
                val serversResult = Result.success(serverClientResolver.getServers())
                val servers = serversResult.getOrNull() ?: emptyList()

                if (servers.isEmpty()) {
                    emit(emptyList())
                } else {
                    val ownedServerIds = servers.filter { it.isOwned }.map { it.clientIdentifier }.toSet()

                    // 1. Emit Cache
                    val cachedEntities = homeContentDao.getHomeMediaItems("onDeck", "onDeck")
                    if (cachedEntities.isNotEmpty()) {
                        val items = mapEntities(cachedEntities, servers)
                        val deduplicated = mediaDeduplicator.deduplicate(items, ownedServerIds, servers)
                        emit(deduplicated)
                    }

                    // 2. Refresh Network & Update DB
                    // ApplicationScope launch is for fire-and-forget, but here we want to emit the result AFTER refresh.
                    // So we should suspend.
                    refreshOnDeck()

                    // 3. Emit Fresh
                    val freshEntities = homeContentDao.getHomeMediaItems("onDeck", "onDeck")
                    val freshItems = mapEntities(freshEntities, servers)
                    val freshDeduplicated = mediaDeduplicator.deduplicate(freshItems, ownedServerIds, servers)
                    emit(freshDeduplicated)
                }
            }

        private suspend fun mapEntities(
            entities: List<com.chakir.plexhubtv.core.database.MediaEntity>,
            servers: List<com.chakir.plexhubtv.core.model.Server>,
        ): List<MediaItem> {
            return entities.map { entity ->
                val server = servers.find { it.clientIdentifier == entity.serverId }
                val baseUrl = if (server != null) connectionManager.getCachedUrl(server.clientIdentifier) ?: server.address else null
                val token = server?.accessToken

                val domain = mapper.mapEntityToDomain(entity)
                if (server != null && baseUrl != null) {
                    mediaUrlResolver.resolveUrls(domain, baseUrl, token).copy(
                        baseUrl = baseUrl,
                        accessToken = token,
                    )
                } else {
                    domain
                }
            }
        }

        private suspend fun refreshOnDeck() {
            val clients = getActiveClients()
            if (clients.isEmpty()) return

            // Use Race to get fastest response? No, we need all OnDecks.
            // But we should prioritize faster servers?
            // For now, simpler implementation: Fetch all in parallel.

            try {
                val deferredResults =
                    clients.map { client: PlexClient ->
                        val token = client.server.accessToken ?: ""
                        applicationScope.async<List<MediaEntity>>(ioDispatcher) {
                            try {
                                val baseUrl = client.baseUrl
                                val accessToken = client.server.accessToken ?: ""
                                val response = client.getOnDeck()
                                response.body()?.mediaContainer?.metadata?.map { dto: MetadataDTO ->
                                    val entity = mapper.mapDtoToEntity(dto, client.server.clientIdentifier, dto.librarySectionID ?: "")
                                    entity.copy(
                                        resolvedThumbUrl = entity.thumbUrl?.let { path ->
                                            getOptimizedImageUrl("$baseUrl$path?X-Plex-Token=$accessToken", 300, 450)
                                                ?: "$baseUrl$path?X-Plex-Token=$accessToken"
                                        },
                                        resolvedArtUrl = entity.artUrl?.let { path ->
                                            getOptimizedImageUrl("$baseUrl$path?X-Plex-Token=$accessToken", 1280, 720)
                                                ?: "$baseUrl$path?X-Plex-Token=$accessToken"
                                        },
                                        resolvedBaseUrl = baseUrl,
                                    )
                                } ?: emptyList()
                            } catch (e: Exception) {
                                Timber.w(e, "OnDeck fetch failed for server=${client.server.name}")
                                emptyList()
                            }
                        }
                    }

                val allEntities: List<MediaEntity> = deferredResults.awaitAll().flatten()

                // Update DB transactionally
                mediaDao.upsertMedia(allEntities)

                // Update HomeContent for ordering
                val homeContent =
                    allEntities.mapIndexed { index, entity ->
                        com.chakir.plexhubtv.core.database.HomeContentEntity(
                            type = "onDeck",
                            hubIdentifier = "onDeck",
                            title = "On Deck",
                            itemServerId = entity.serverId,
                            itemRatingKey = entity.ratingKey,
                            orderIndex = index,
                        )
                    }
                homeContentDao.insertHomeContent(homeContent)
            } catch (e: Exception) {
                Timber.e(e, "OnDeck refresh failed")
            }
        }

        // Helper duplicated from MediaRepositoryImpl logic (or extracted later)
        private suspend fun getActiveClients(): List<PlexClient> =
            coroutineScope {
                val servers = authRepository.getServers(forceRefresh = false).getOrNull() ?: return@coroutineScope emptyList()

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
