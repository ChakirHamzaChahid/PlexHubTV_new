package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.data.model.MetadataDTO
import com.chakir.plexhubtv.domain.model.Hub
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.model.MediaType
import com.chakir.plexhubtv.domain.repository.MediaRepository
import com.chakir.plexhubtv.domain.model.Server
import javax.inject.Inject

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.chakir.plexhubtv.core.util.getOptimizedImageUrl
import kotlinx.coroutines.launch
import com.chakir.plexhubtv.core.database.CollectionEntity

/**
 * Implémentation du dépôt central pour les données multimédias.
 *
 * Stratégie de données :
 * 1. Cache-First : On renvoie toujours onDeck/OnDeck localement en premier pour la réactivité.
 * 2. Network-Refresh : On lance ensuite un appel réseau pour mettre à jour la BDD locale.
 * 3. Aggregation : On fusionne les données de multiples serveurs Plex.
 * 4. Deduplication : On regroupe les médias identiques (même film sur 2 serveurs) via [deduplicateMediaItems].
 */
class MediaRepositoryImpl @Inject constructor(
    private val api: PlexApiService,
    private val mediaDao: MediaDao,
    private val apiCacheDao: com.chakir.plexhubtv.core.database.ApiCacheDao,
    private val homeContentDao: com.chakir.plexhubtv.core.database.HomeContentDao,
    private val mapper: MediaMapper,
    private val authRepository: com.chakir.plexhubtv.domain.repository.AuthRepository,
    private val connectionManager: com.chakir.plexhubtv.core.network.ConnectionManager,
    private val favoriteDao: com.chakir.plexhubtv.core.database.FavoriteDao,
    private val collectionDao: com.chakir.plexhubtv.core.database.CollectionDao,
    private val settingsDataStore: com.chakir.plexhubtv.core.datastore.SettingsDataStore,
    private val plexApiCache: com.chakir.plexhubtv.core.network.PlexApiCache,
    private val gson: com.google.gson.Gson
) : MediaRepository {

    /**
     * Récupère la section "On Deck" (En cours / À voir) unifiée.
     *
     * Logique :
     * 1. Émet immédiatement le cache local (rapide).
     * 2. Lance en parallèle les requêtes vers tous les serveurs connectés.
     * 3. Met à jour la BDD locale (MediaDao + HomeContentDao).
     * 4. Émet la nouvelle liste dédoublonnée une fois le réseau terminé.
     */
    override fun getUnifiedOnDeck(): Flow<List<MediaItem>> = flow {
        // 1. Emit Cache First (Fast Path)
        val cachedItems = getCachedOnDeck()
        emit(cachedItems)
        
        // 2. Refresh from Network
        val clients = getActiveClients()
        if (clients.isNotEmpty()) {
            coroutineScope {
                val servers = authRepository.getServers().getOrNull() ?: emptyList()
                val ownedServerIds = servers.filter { it.isOwned }.map { it.clientIdentifier }.toSet()

                val deferreds = clients.map { client ->
                    async(Dispatchers.IO) {
                        try {
                            val serverId = client.server.clientIdentifier
                            val cacheKey = "$serverId:/library/onDeck"
                            
                            // 1. Try Cache First
                            val cachedJson = plexApiCache.get(cacheKey)
                            if (cachedJson != null) {
                                try {
                                    val cachedBody = gson.fromJson(cachedJson, com.chakir.plexhubtv.data.model.GenericPlexResponse::class.java)
                                    val metadata = cachedBody.mediaContainer?.metadata ?: emptyList()
                                    android.util.Log.i("METRICS", "REPO [OnDeck] CACHE HIT: server=$serverId items=${metadata.size}")
                                    
                                    val entities = metadata.filter { mapper.isQualityMetadata(it) }.map { dto ->
                                        mapper.mapDtoToEntity(dto, client.server.clientIdentifier, dto.librarySectionID ?: "0")
                                            .copy(filter = "ondeck", sortOrder = "default", pageOffset = 0)
                                    }
                                    
                                    mediaDao.upsertMedia(entities)
                                    
                                    val homeContent = entities.mapIndexed { index, entity ->
                                        com.chakir.plexhubtv.core.database.HomeContentEntity(
                                            type = "onDeck",
                                            hubIdentifier = "onDeck",
                                            title = "On Deck",
                                            itemServerId = entity.serverId,
                                            itemRatingKey = entity.ratingKey,
                                            orderIndex = index
                                        )
                                    }
                                    homeContentDao.insertHomeContent(homeContent)
                                    
                                    return@async entities.map { entity ->
                                        val domain = mapper.mapEntityToDomain(entity)
                                        val baseUrl = client.baseUrl
                                        val token = client.server.accessToken
                                        
                                        val rawThumb = if (entity.thumbUrl != null && !entity.thumbUrl.startsWith("http")) "$baseUrl${entity.thumbUrl}?X-Plex-Token=$token" else entity.thumbUrl
                                        val rawArt = if (entity.artUrl != null && !entity.artUrl.startsWith("http")) "$baseUrl${entity.artUrl}?X-Plex-Token=$token" else entity.artUrl
                                        val rawParentThumb = if (domain.parentThumb != null && !domain.parentThumb.startsWith("http")) "$baseUrl${domain.parentThumb}?X-Plex-Token=$token" else domain.parentThumb
                                        val rawGrandparentThumb = if (domain.grandparentThumb != null && !domain.grandparentThumb.startsWith("http")) "$baseUrl${domain.grandparentThumb}?X-Plex-Token=$token" else domain.grandparentThumb
                                        
                                        val fullThumb = getOptimizedImageUrl(rawThumb, 300, 450) ?: rawThumb
                                        val fullArt = getOptimizedImageUrl(rawArt, 1280, 720) ?: rawArt
                                        val fullParentThumb = getOptimizedImageUrl(rawParentThumb, 300, 450) ?: rawParentThumb
                                        val fullGrandparentThumb = getOptimizedImageUrl(rawGrandparentThumb, 300, 450) ?: rawGrandparentThumb

                                        domain.copy(
                                            baseUrl = baseUrl,
                                            accessToken = token,
                                            thumbUrl = fullThumb,
                                            artUrl = fullArt,
                                            parentThumb = fullParentThumb,
                                            grandparentThumb = fullGrandparentThumb
                                        )
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("MediaRepository", "OnDeck cache parse failed, fetching fresh: ${e.message}")
                                }
                            }

                            val response = client.getOnDeck()
                            if (response.isSuccessful) {
                                val body = response.body() ?: return@async emptyList()
                                android.util.Log.i("METRICS", "REPO [OnDeck] NETWORK SUCCESS: server=$serverId")

                                // Save to cache
                                plexApiCache.put(
                                    cacheKey = cacheKey,
                                    data = gson.toJson(body),
                                    ttlSeconds = 1800  // 30 min
                                )
                                
                                val metadata = body.mediaContainer?.metadata ?: emptyList()
                                
                                val entities = metadata.filter { mapper.isQualityMetadata(it) }.map { dto ->
                                    mapper.mapDtoToEntity(dto, client.server.clientIdentifier, dto.librarySectionID ?: "0")
                                        .copy(filter = "ondeck", sortOrder = "default", pageOffset = 0)
                                }
                                
                                mediaDao.upsertMedia(entities)
                                
                                val homeContent = entities.mapIndexed { index, entity ->
                                    com.chakir.plexhubtv.core.database.HomeContentEntity(
                                        type = "onDeck",
                                        hubIdentifier = "onDeck",
                                        title = "On Deck",
                                        itemServerId = entity.serverId,
                                        itemRatingKey = entity.ratingKey,
                                        orderIndex = index
                                    )
                                }
                                homeContentDao.insertHomeContent(homeContent)

                                entities.map { entity ->
                                    val domain = mapper.mapEntityToDomain(entity)
                                    val baseUrl = client.baseUrl
                                    val token = client.server.accessToken
                                    
                                    val rawThumb = if (entity.thumbUrl != null && !entity.thumbUrl.startsWith("http")) "$baseUrl${entity.thumbUrl}?X-Plex-Token=$token" else entity.thumbUrl
                                    val rawArt = if (entity.artUrl != null && !entity.artUrl.startsWith("http")) "$baseUrl${entity.artUrl}?X-Plex-Token=$token" else entity.artUrl
                                    val rawParentThumb = if (domain.parentThumb != null && !domain.parentThumb.startsWith("http")) "$baseUrl${domain.parentThumb}?X-Plex-Token=$token" else domain.parentThumb
                                    val rawGrandparentThumb = if (domain.grandparentThumb != null && !domain.grandparentThumb.startsWith("http")) "$baseUrl${domain.grandparentThumb}?X-Plex-Token=$token" else domain.grandparentThumb
                                    
                                    val fullThumb = getOptimizedImageUrl(rawThumb, 300, 450) ?: rawThumb
                                    val fullArt = getOptimizedImageUrl(rawArt, 1280, 720) ?: rawArt
                                    val fullParentThumb = getOptimizedImageUrl(rawParentThumb, 300, 450) ?: rawParentThumb
                                    val fullGrandparentThumb = getOptimizedImageUrl(rawGrandparentThumb, 300, 450) ?: rawGrandparentThumb

                                    domain.copy(
                                        baseUrl = baseUrl,
                                        accessToken = token,
                                        thumbUrl = fullThumb,
                                        artUrl = fullArt,
                                        parentThumb = fullParentThumb,
                                        grandparentThumb = fullGrandparentThumb
                                    )
                                }
                            } else emptyList()
                        } catch (e: Exception) { emptyList() }
                    }
                }
                val allItems = deferreds.awaitAll().flatten()
                if (allItems.isNotEmpty()) {
                    val result = deduplicateMediaItems(allItems, ownedServerIds, servers)
                    emit(result)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun getCachedOnDeck(): List<MediaItem> {
        val servers = authRepository.getServers().getOrNull() ?: return emptyList()
// ... (Lines 96-127 remain mostly same, implied unchanged by replace tool matching context)
        val ownedServerIds = servers.filter { it.isOwned }.map { it.clientIdentifier }.toSet()
        
        // Fetch from DB
        val entities = homeContentDao.getHomeMediaItems("onDeck", "onDeck")
        val items = entities.map { entity ->
            val server = servers.find { it.clientIdentifier == entity.serverId }
            val baseUrl = if (server != null) connectionManager.getCachedUrl(server.clientIdentifier) ?: server.address else null
            
            val domain = mapper.mapEntityToDomain(entity)
            if (server != null && baseUrl != null) {
                // Reconstruct full URLs from relative paths
                val fullThumb = if (entity.thumbUrl != null && !entity.thumbUrl.startsWith("http")) "$baseUrl${entity.thumbUrl}?X-Plex-Token=${server.accessToken}" else entity.thumbUrl
                val fullArt = if (entity.artUrl != null && !entity.artUrl.startsWith("http")) "$baseUrl${entity.artUrl}?X-Plex-Token=${server.accessToken}" else entity.artUrl
                val fullParentThumb = if (domain.parentThumb != null && !domain.parentThumb.startsWith("http")) "$baseUrl${domain.parentThumb}?X-Plex-Token=${server.accessToken}" else domain.parentThumb
                val fullGrandparentThumb = if (domain.grandparentThumb != null && !domain.grandparentThumb.startsWith("http")) "$baseUrl${domain.grandparentThumb}?X-Plex-Token=${server.accessToken}" else domain.grandparentThumb

                domain.copy(
                    baseUrl = baseUrl, 
                    accessToken = server.accessToken,
                    thumbUrl = fullThumb,
                    artUrl = fullArt,
                    parentThumb = fullParentThumb,
                    grandparentThumb = fullGrandparentThumb
                )
            } else {
                domain
            }
        }
        
        return deduplicateMediaItems(items, ownedServerIds, servers)
    }

    override fun getUnifiedHubs(): Flow<List<Hub>> = flow {
        // 1. Emit Cache First
        val cachedHubs = getCachedHubs()
        emit(cachedHubs)

        val clients = getActiveClients()
        if (clients.isNotEmpty()) {
            coroutineScope {
                val servers = authRepository.getServers().getOrNull() ?: emptyList()
                val ownedServerIds = servers.filter { it.isOwned }.map { it.clientIdentifier }.toSet()

                val deferreds = clients.map { client ->
                     async(Dispatchers.IO) {
                         try {
                            val serverId = client.server.clientIdentifier
                            val cacheKey = "$serverId:/hubs"
                            
                            // 1. Try Cache First
                            val cachedJson = plexApiCache.get(cacheKey)
                            if (cachedJson != null) {
                                try {
                                    val cachedBody = gson.fromJson(cachedJson, com.chakir.plexhubtv.data.model.GenericPlexResponse::class.java)
                                    val hubs = cachedBody.mediaContainer?.hubs ?: emptyList()
                                    android.util.Log.i("METRICS", "REPO [Hubs] CACHE HIT: server=$serverId hubs=${hubs.size}")
                                    
                                    return@async hubs.mapNotNull { hubDto ->
                                        val hubIdentifier = hubDto.hubIdentifier ?: hubDto.title ?: "unknown"
                                        val hubTitle = hubDto.title ?: "Unknown"
                                        
                                        val metadata = hubDto.metadata ?: emptyList()
                                        val entities = metadata.filter { mapper.isQualityMetadata(it) }.map { dto ->
                                            mapper.mapDtoToEntity(dto, client.server.clientIdentifier, dto.librarySectionID ?: "0")
                                                .copy(filter = "hub", sortOrder = hubDto.hubIdentifier ?: "default", pageOffset = 0)
                                        }
                                        
                                        if (entities.isNotEmpty()) {
                                            mediaDao.upsertMedia(entities)
                                            val homeContent = entities.mapIndexed { index, entity ->
                                                com.chakir.plexhubtv.core.database.HomeContentEntity(
                                                    type = "hub",
                                                    hubIdentifier = hubIdentifier,
                                                    title = hubTitle,
                                                    itemServerId = entity.serverId,
                                                    itemRatingKey = entity.ratingKey,
                                                    orderIndex = index
                                                )
                                            }
                                            homeContentDao.insertHomeContent(homeContent)
                                            
                                            com.chakir.plexhubtv.domain.model.Hub(
                                                key = hubDto.key ?: "",
                                                title = hubTitle,
                                                type = hubDto.type ?: "mixed",
                                                hubIdentifier = hubIdentifier,
                                                items = entities.map { entity ->
                                                    val domain = mapper.mapEntityToDomain(entity)
                                                    val baseUrl = client.baseUrl
                                                    val token = client.server.accessToken
                                                    
                                                    val rawThumb = if (entity.thumbUrl != null && !entity.thumbUrl.startsWith("http")) "$baseUrl${entity.thumbUrl}?X-Plex-Token=$token" else entity.thumbUrl
                                                    val rawArt = if (entity.artUrl != null && !entity.artUrl.startsWith("http")) "$baseUrl${entity.artUrl}?X-Plex-Token=$token" else entity.artUrl
                                                    val rawParentThumb = if (domain.parentThumb != null && !domain.parentThumb.startsWith("http")) "$baseUrl${domain.parentThumb}?X-Plex-Token=$token" else domain.parentThumb
                                                    val rawGrandparentThumb = if (domain.grandparentThumb != null && !domain.grandparentThumb.startsWith("http")) "$baseUrl${domain.grandparentThumb}?X-Plex-Token=$token" else domain.grandparentThumb
    
                                                    val fullThumb = getOptimizedImageUrl(rawThumb, 300, 450) ?: rawThumb
                                                    val fullArt = getOptimizedImageUrl(rawArt, 1280, 720) ?: rawArt
                                                    val fullParentThumb = getOptimizedImageUrl(rawParentThumb, 300, 450) ?: rawParentThumb
                                                    val fullGrandparentThumb = getOptimizedImageUrl(rawGrandparentThumb, 300, 450) ?: rawGrandparentThumb
    
                                                    domain.copy(
                                                        baseUrl = baseUrl,
                                                        accessToken = token,
                                                        thumbUrl = fullThumb,
                                                        artUrl = fullArt,
                                                        parentThumb = fullParentThumb,
                                                        grandparentThumb = fullGrandparentThumb
                                                    )
                                                },
                                                serverId = client.server.clientIdentifier
                                            )
                                        } else null
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("MediaRepository", "Hubs cache parse failed: ${e.message}")
                                }
                            }

                            val response = client.getHubs()
                            if (response.isSuccessful) {
                                val body = response.body() ?: return@async emptyList()
                                android.util.Log.i("METRICS", "REPO [Hubs] NETWORK SUCCESS: server=$serverId")
                                
                                // Save to cache
                                plexApiCache.put(
                                    cacheKey = cacheKey,
                                    data = gson.toJson(body),
                                    ttlSeconds = 3600  // 1 hour for hubs
                                )

                                val hubs = body.mediaContainer?.hubs ?: emptyList()
                                
                                hubs.mapNotNull { hubDto ->
                                    val hubIdentifier = hubDto.hubIdentifier ?: hubDto.title ?: "unknown"
                                    val hubTitle = hubDto.title ?: "Unknown"
                                    
                                    val metadata = hubDto.metadata ?: emptyList()
                                    val entities = metadata.filter { mapper.isQualityMetadata(it) }.map { dto ->
                                        mapper.mapDtoToEntity(dto, client.server.clientIdentifier, dto.librarySectionID ?: "0")
                                            .copy(filter = "hub", sortOrder = hubDto.hubIdentifier ?: "default", pageOffset = 0)
                                    }
                                    
                                    if (entities.isNotEmpty()) {
                                        mediaDao.upsertMedia(entities)
                                        val homeContent = entities.mapIndexed { index, entity ->
                                            com.chakir.plexhubtv.core.database.HomeContentEntity(
                                                type = "hub",
                                                hubIdentifier = hubIdentifier,
                                                title = hubTitle,
                                                itemServerId = entity.serverId,
                                                itemRatingKey = entity.ratingKey,
                                                orderIndex = index
                                            )
                                        }
                                        homeContentDao.insertHomeContent(homeContent)
                                        
                                        com.chakir.plexhubtv.domain.model.Hub(
                                            key = hubDto.key ?: "",
                                            title = hubTitle,
                                            type = hubDto.type ?: "mixed",
                                            hubIdentifier = hubIdentifier,
                                            items = entities.map { entity ->
                                                val domain = mapper.mapEntityToDomain(entity)
                                                val baseUrl = client.baseUrl
                                                val token = client.server.accessToken
                                                
                                                val rawThumb = if (entity.thumbUrl != null && !entity.thumbUrl.startsWith("http")) "$baseUrl${entity.thumbUrl}?X-Plex-Token=$token" else entity.thumbUrl
                                                val rawArt = if (entity.artUrl != null && !entity.artUrl.startsWith("http")) "$baseUrl${entity.artUrl}?X-Plex-Token=$token" else entity.artUrl
                                                val rawParentThumb = if (domain.parentThumb != null && !domain.parentThumb.startsWith("http")) "$baseUrl${domain.parentThumb}?X-Plex-Token=$token" else domain.parentThumb
                                                val rawGrandparentThumb = if (domain.grandparentThumb != null && !domain.grandparentThumb.startsWith("http")) "$baseUrl${domain.grandparentThumb}?X-Plex-Token=$token" else domain.grandparentThumb

                                                val fullThumb = getOptimizedImageUrl(rawThumb, 300, 450) ?: rawThumb
                                                val fullArt = getOptimizedImageUrl(rawArt, 1280, 720) ?: rawArt
                                                val fullParentThumb = getOptimizedImageUrl(rawParentThumb, 300, 450) ?: rawParentThumb
                                                val fullGrandparentThumb = getOptimizedImageUrl(rawGrandparentThumb, 300, 450) ?: rawGrandparentThumb

                                                domain.copy(
                                                    baseUrl = baseUrl,
                                                    accessToken = token,
                                                    thumbUrl = fullThumb,
                                                    artUrl = fullArt,
                                                    parentThumb = fullParentThumb,
                                                    grandparentThumb = fullGrandparentThumb
                                                )
                                            },
                                            serverId = client.server.clientIdentifier
                                        )
                                    } else null
                                }
                            } else emptyList()
                         } catch (e: Exception) { emptyList() }
                     }
                }
                
                val allHubs = deferreds.awaitAll().flatten()
                if (allHubs.isNotEmpty()) {
                    val result = aggregateHubs(allHubs, ownedServerIds, servers)
                    emit(result)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun getCachedHubs(): List<Hub> {
        val servers = authRepository.getServers().getOrNull() ?: return emptyList()
        val ownedServerIds = servers.filter { it.isOwned }.map { it.clientIdentifier }.toSet()

        // Fetch distinct hubs from DB
        val hubInfos = homeContentDao.getHubsList()
        
        val allHubs = hubInfos.map { hubInfo ->
            val entities = homeContentDao.getHomeMediaItems("hub", hubInfo.hubIdentifier)
            val items = entities.map { entity ->
                val server = servers.find { it.clientIdentifier == entity.serverId }
                val baseUrl = if (server != null) connectionManager.getCachedUrl(server.clientIdentifier) ?: server.address else null
                
                val domain = mapper.mapEntityToDomain(entity)
                if (server != null && baseUrl != null) {
                    // Reconstruct full URLs from relative paths
                    val rawThumb = if (entity.thumbUrl != null && !entity.thumbUrl.startsWith("http")) "$baseUrl${entity.thumbUrl}?X-Plex-Token=${server.accessToken}" else entity.thumbUrl
                    val rawArt = if (entity.artUrl != null && !entity.artUrl.startsWith("http")) "$baseUrl${entity.artUrl}?X-Plex-Token=${server.accessToken}" else entity.artUrl
                    val rawParentThumb = if (domain.parentThumb != null && !domain.parentThumb.startsWith("http")) "$baseUrl${domain.parentThumb}?X-Plex-Token=${server.accessToken}" else domain.parentThumb
                    val rawGrandparentThumb = if (domain.grandparentThumb != null && !domain.grandparentThumb.startsWith("http")) "$baseUrl${domain.grandparentThumb}?X-Plex-Token=${server.accessToken}" else domain.grandparentThumb

                    val fullThumb = getOptimizedImageUrl(rawThumb, 300, 450) ?: rawThumb
                    val fullArt = getOptimizedImageUrl(rawArt, 1280, 720) ?: rawArt
                    val fullParentThumb = getOptimizedImageUrl(rawParentThumb, 300, 450) ?: rawParentThumb
                    val fullGrandparentThumb = getOptimizedImageUrl(rawGrandparentThumb, 300, 450) ?: rawGrandparentThumb

                    domain.copy(
                        baseUrl = baseUrl, 
                        accessToken = server.accessToken,
                        thumbUrl = fullThumb,
                        artUrl = fullArt,
                        parentThumb = fullParentThumb,
                        grandparentThumb = fullGrandparentThumb
                    )
                } else {
                    domain
                }
            }
            
            com.chakir.plexhubtv.domain.model.Hub(
                key = "", // Not needed for offline display
                title = hubInfo.title,
                type = "mixed",
                hubIdentifier = hubInfo.hubIdentifier,
                items = items,
                serverId = null 
            )
        }
        return aggregateHubs(allHubs, ownedServerIds, servers)
    }

    /**
     * Récupère les détails d'un média spécifique.
     *
     * Gestion d'erreur & Fallback :
     * - Si le serveur principal échoue (404/Offline), on tente le **Fallback**.
     * - Fallback : On utilise le GUID du média (ex: plex://movie/...) pour trouver
     *   une copie de ce même média sur un AUTRE serveur accessible.
     */
    override suspend fun getMediaDetail(ratingKey: String, serverId: String): Result<MediaItem> {
        // 1. Try Cache First
        val cacheKey = "$serverId:/library/metadata/$ratingKey"
        val cachedJson = plexApiCache.get(cacheKey)
        if (cachedJson != null) {
            try {
                // Deserialize cached JSON
                val metadata = gson.fromJson(cachedJson, MetadataDTO::class.java)
                if (metadata != null) {
                    val client = getClient(serverId)
                    val baseUrl = client?.baseUrl ?: ""
                    val token = client?.server?.accessToken ?: ""
                    android.util.Log.i("METRICS", "REPO [Detail] CACHE HIT: key=$ratingKey")
                    return Result.success(mapper.mapDtoToDomain(metadata, serverId, baseUrl, token))
                }
            } catch (e: Exception) {
                // Cache corruption or format change, ignore and fetch fresh
            }
        }

        // 2. Try API (Network)
        try {
            val client = getClient(serverId)
            if (client != null) {
                // Add includeOnDeck=1 for parity with Plezy as per plan
                val response = client.getMetadata(ratingKey, includeChildren = true)
                if (response.isSuccessful) {
                    val metadata = response.body()?.mediaContainer?.metadata?.firstOrNull()
                    if (metadata != null) {
                        // Validate Quality Metadata
                        val entity = mapper.mapDtoToEntity(
                            dto = metadata, 
                            serverId = serverId, 
                            libraryKey = metadata.librarySectionID ?: "0"
                        )
                        mediaDao.insertMedia(entity)

                        // Update Cache (TTL 60 mins for metadata)
                        val json = gson.toJson(metadata)
                        plexApiCache.put(cacheKey, json, ttlSeconds = 3600)

                        return Result.success(mapper.mapDtoToDomain(metadata, serverId, client.baseUrl, client.server.accessToken))
                    }
                }
            }
        } catch (e: Exception) {
            // Ignorer l'échec primaire, procéder au fallback
        }

        // 2. Fallback: Recherche de doublons via GUID
        // On a besoin de l'élément local pour récupérer son GUID
        val local = mediaDao.getMedia(ratingKey, serverId) ?: return Result.failure(Exception("Item not found and no local cache to enable fallback."))
        val guid = local.guid ?: return Result.failure(Exception("Item failed to load and has no GUID for fallback."))

        // Trouver des alternatives sur d'autres serveurs
        val alternatives = mediaDao.getMediaByGuid(guid, excludeServerId = serverId)
        
        for (alt in alternatives) {
            try {
                val altClient = getClient(alt.serverId) ?: continue
                val response = altClient.getMetadata(alt.ratingKey, includeChildren = true)
                if (response.isSuccessful) {
                    val metadata = response.body()?.mediaContainer?.metadata?.firstOrNull()
                    if (metadata != null) {
                        // Persist the fresh data from the alternative source
                        val entity = mapper.mapDtoToEntity(
                            dto = metadata, 
                            serverId = alt.serverId, 
                            libraryKey = metadata.librarySectionID ?: "0"
                        )
                        mediaDao.insertMedia(entity)
                        
                        // Return the ALTERNATIVE item as the source of truth
                        return Result.success(mapper.mapDtoToDomain(metadata, alt.serverId, altClient.baseUrl, altClient.server.accessToken))
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }

        return Result.failure(Exception("Media not found on any available server."))
    }

    override suspend fun getSeasonEpisodes(ratingKey: String, serverId: String): Result<List<MediaItem>> {
        // 1. Try API First
        try {
             val client = getClient(serverId)
             if (client != null) {
                 val response = client.getChildren(ratingKey)
                 if (response.isSuccessful) {
                     val metadata = response.body()?.mediaContainer?.metadata
                     if (metadata != null) {
                         // Cache these items? Maybe later. For now, strict fix.
                         val items = metadata.map {
                             mapper.mapDtoToDomain(it, serverId, client.baseUrl, client.server.accessToken)
                         }
                         return Result.success(items)
                     }
                 }
             }
        } catch (e: Exception) {
            // Fallthrough to DB
        }
        
        // 2. Fallback to DB
        val localEntities = mediaDao.getChildren(ratingKey, serverId)
        if (localEntities.isNotEmpty()) {
             // We need to resolve base URL for caching if possible, or just use what we have
             val client = getClient(serverId)
             val baseUrl = client?.baseUrl
             val token = client?.server?.accessToken
             
             val items = localEntities.map { 
                 mapper.mapEntityToDomain(it).let { domain ->
                     // Attempt to fix URLs
                     if (baseUrl != null && token != null) {
                         val fullThumb = if (domain.thumbUrl != null && !domain.thumbUrl.startsWith("http")) "$baseUrl${domain.thumbUrl}?X-Plex-Token=$token" else domain.thumbUrl
                         domain.copy(thumbUrl = fullThumb, baseUrl = baseUrl, accessToken = token)
                     } else domain
                 }
             }
             return Result.success(items)
        }
        
        // 3. Final Failure
        return Result.failure(Exception("Episodes not found (API failed and DB empty)"))
    }

    override suspend fun getShowSeasons(ratingKey: String, serverId: String): Result<List<MediaItem>> {
        return getSeasonEpisodes(ratingKey, serverId) // Same endpoint logically for children
    }

    override suspend fun getSimilarMedia(ratingKey: String, serverId: String): Result<List<MediaItem>> {
        try {
            val client = getClient(serverId) ?: return Result.failure(Exception("Server not found"))
            val response = client.getRelated(ratingKey)
            if (response.isSuccessful) {
                val body = response.body() ?: return Result.success(emptyList())
                val hubs = body.mediaContainer?.hubs ?: emptyList()
                
                // Usually the first hub is "Similar" or "More like this"
                val similarHub = hubs.find { it.hubIdentifier == "similar" } ?: hubs.firstOrNull()
                val metadata = similarHub?.metadata ?: emptyList() // Don't fall back to flatMap here to avoid mixing Collections
                
                if (metadata.isNotEmpty()) {
                    val servers = authRepository.getServers().getOrNull() ?: emptyList()
                    val items = metadata.filter { mapper.isQualityMetadata(it) }.map {
                        mapper.mapDtoToDomain(it, serverId, client.baseUrl, client.server.accessToken)
                    }
                    val ownedServerIds = servers.filter { it.isOwned }.map { it.clientIdentifier }.toSet()
                    return Result.success(deduplicateMediaItems(items, ownedServerIds, servers))
                }
            }
            return Result.success(emptyList())
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun getMediaCollections(ratingKey: String, serverId: String): Flow<List<com.chakir.plexhubtv.domain.model.Collection>> = flow {
        android.util.Log.d("CollectionSync", "═══════════════════════════════")
        android.util.Log.d("CollectionSync", "Repo: Requesting ALL collections for MEDIA")
        android.util.Log.d("CollectionSync", "  - ratingKey: $ratingKey")
        android.util.Log.d("CollectionSync", "  - serverId: $serverId")
        android.util.Log.d("CollectionSync", "═══════════════════════════════")
        
        // Step 1: Get unificationId for this media
        val unificationId = mediaDao.getUnificationId(ratingKey, serverId)
        if (unificationId.isNullOrEmpty()) {
            android.util.Log.w("CollectionSync", "❌ No unificationId found for this media")
            emit(emptyList())
            return@flow
        }
        
        android.util.Log.d("CollectionSync", "→ UnificationId: $unificationId")
        
        // Step 2: Get ALL duplicates across all servers
        val duplicates = mediaDao.getAllDuplicates(unificationId)
        android.util.Log.d("CollectionSync", "→ Found ${duplicates.size} duplicate(s) across servers")
        
        if (duplicates.isEmpty()) {
            android.util.Log.w("CollectionSync", "❌ No duplicates found (unusual, should at least include self)")
            emit(emptyList())
            return@flow
        }
        
        // Step 3: For each duplicate, retrieve ALL its collections (not just first one)
        data class CollectionKey(val id: String, val serverId: String)
        val allCollections = mutableMapOf<CollectionKey, Pair<CollectionEntity, String>>()
        
        for (duplicate in duplicates) {
            val collections = collectionDao.getCollectionsForMedia(duplicate.ratingKey, duplicate.serverId).first()
            android.util.Log.d("CollectionSync", "  → Server ${duplicate.serverId.take(8)}: Found ${collections.size} collection(s)")
            
            collections.forEach { collection ->
                val key = CollectionKey(collection.id, duplicate.serverId)
                // Deduplicate: same collection on same server should only appear once
                if (!allCollections.containsKey(key)) {
                    allCollections[key] = collection to duplicate.serverId
                    android.util.Log.d("CollectionSync", "     - '${collection.title}' (id=${collection.id})")
                }
            }
        }
        
        if (allCollections.isEmpty()) {
            android.util.Log.d("CollectionSync", "VM: No collections found across any server (Item not in any collection)")
            emit(emptyList())
            return@flow
        }
        
        android.util.Log.i("CollectionSync", "✅ Found ${allCollections.size} unique collection(s) total")
        
        // Step 4: Build domain models for ALL collections
        val result = allCollections.values.map { (collectionEntity, sId) ->
            // Get items for this collection
            val entities = collectionDao.getMediaInCollection(collectionEntity.id, sId).first()
            val client = getClient(sId)
            val baseUrl = client?.baseUrl
            val token = client?.server?.accessToken
            
            val items = entities.map { entity ->
                val domain = mapper.mapEntityToDomain(entity)
                if (baseUrl != null && token != null) {
                    val rawThumb = if (entity.thumbUrl != null && !entity.thumbUrl.startsWith("http")) "$baseUrl${entity.thumbUrl}?X-Plex-Token=$token" else entity.thumbUrl
                    val rawArt = if (entity.artUrl != null && !entity.artUrl.startsWith("http")) "$baseUrl${entity.artUrl}?X-Plex-Token=$token" else entity.artUrl
                    
                    val fullThumb = getOptimizedImageUrl(rawThumb, 300, 450) ?: rawThumb
                    val fullArt = getOptimizedImageUrl(rawArt, 1280, 720) ?: rawArt
                    
                    domain.copy(thumbUrl = fullThumb, artUrl = fullArt, baseUrl = baseUrl, accessToken = token)
                } else {
                    domain
                }
            }
            
            com.chakir.plexhubtv.domain.model.Collection(
                id = collectionEntity.id,
                serverId = sId,
                title = collectionEntity.title,
                summary = collectionEntity.summary,
                thumbUrl = collectionEntity.thumbUrl,
                items = items
            )
        }
        
        android.util.Log.i("CollectionSync", "→ Returning ${result.size} collection(s)")
        emit(result)
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun getCollection(collectionId: String, serverId: String): Flow<com.chakir.plexhubtv.domain.model.Collection?> = 
        collectionDao.getCollection(collectionId, serverId).flatMapLatest { entity ->
            if (entity == null) return@flatMapLatest flowOf<com.chakir.plexhubtv.domain.model.Collection?>(null)
            
            collectionDao.getMediaInCollection(collectionId, serverId).map { entities ->
                val client = getClient(serverId)
                val baseUrl = client?.baseUrl
                val token = client?.server?.accessToken
                
                val items = entities.map { itemEntity ->
                    val domain = mapper.mapEntityToDomain(itemEntity)
                    if (baseUrl != null && token != null) {
                        val rawThumb = if (itemEntity.thumbUrl != null && !itemEntity.thumbUrl.startsWith("http")) "$baseUrl${itemEntity.thumbUrl}?X-Plex-Token=$token" else itemEntity.thumbUrl
                        val rawArt = if (itemEntity.artUrl != null && !itemEntity.artUrl.startsWith("http")) "$baseUrl${itemEntity.artUrl}?X-Plex-Token=$token" else itemEntity.artUrl
                        
                        val fullThumb = getOptimizedImageUrl(rawThumb, 300, 450) ?: rawThumb
                        val fullArt = getOptimizedImageUrl(rawArt, 1280, 720) ?: rawArt
                        
                        domain.copy(thumbUrl = fullThumb, artUrl = fullArt, baseUrl = baseUrl, accessToken = token)
                    } else {
                        domain
                    }
                }
                
                com.chakir.plexhubtv.domain.model.Collection(
                    id = entity.id,
                    serverId = serverId,
                    title = entity.title,
                    summary = entity.summary,
                    thumbUrl = entity.thumbUrl,
                    items = items
                )
            }
        }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    override suspend fun toggleWatchStatus(media: MediaItem, isWatched: Boolean): Result<Unit> {
        val client = getClient(media.serverId) ?: return Result.failure(Exception("Server not found"))
        val response = if (isWatched) client.scrobble(media.ratingKey) else client.unscrobble(media.ratingKey)
        
        if (response.isSuccessful) {
            // Invalidate cache to reflect new watch status immediately
            val cacheKey = "${media.serverId}:/library/metadata/${media.ratingKey}"
            plexApiCache.evict(cacheKey)
            return Result.success(Unit)
        }
        return Result.failure(Exception("API Error"))
    }

    override suspend fun updatePlaybackProgress(media: MediaItem, positionMs: Long): Result<Unit> {
        val client = getClient(media.serverId) ?: return Result.failure(Exception("Server not found"))
        val response = client.updateTimeline(
            ratingKey = media.ratingKey,
            state = "playing", // Should be passed from UI
            timeMs = positionMs,
            durationMs = media.durationMs ?: 0L
        )
        
        if (response.isSuccessful) {
             // Invalidate cache so that "Resume" position is updated when user revisits details
            val cacheKey = "${media.serverId}:/library/metadata/${media.ratingKey}"
            plexApiCache.evict(cacheKey)
            return Result.success(Unit)
        }
        return Result.failure(Exception("API Error"))
    }

    override suspend fun getNextMedia(currentItem: MediaItem): MediaItem? {
        if (currentItem.type != MediaType.Episode) return null
        val episodes = getSeasonEpisodes(currentItem.parentRatingKey ?: "", currentItem.serverId).getOrNull() ?: return null
        val currentIndex = episodes.indexOfFirst { it.ratingKey == currentItem.ratingKey }
        return if (currentIndex != -1 && currentIndex < episodes.size - 1) episodes[currentIndex + 1] else null
    }

    override suspend fun getPreviousMedia(currentItem: MediaItem): MediaItem? {
        if (currentItem.type != MediaType.Episode) return null
        val episodes = getSeasonEpisodes(currentItem.parentRatingKey ?: "", currentItem.serverId).getOrNull() ?: return null
        val currentIndex = episodes.indexOfFirst { it.ratingKey == currentItem.ratingKey }
        return if (currentIndex > 0) episodes[currentIndex - 1] else null
    }

    override fun getFavorites(): kotlinx.coroutines.flow.Flow<List<MediaItem>> {
        return favoriteDao.getAllFavorites().map { entities ->
             val servers = authRepository.getServers().getOrNull() ?: emptyList()
             val ownedServerIds = servers.filter { it.isOwned }.map { it.clientIdentifier }.toSet()
             
             val items = entities.map { entity ->
                 val server = servers.find { it.clientIdentifier == entity.serverId }
                 val baseUrl = if (server != null) connectionManager.getCachedUrl(server.clientIdentifier) ?: server.address else null
                 val token = server?.accessToken

                 val mediaEntity = mediaDao.getMedia(entity.ratingKey, entity.serverId)
                 val domain = if (mediaEntity != null) {
                     mapper.mapEntityToDomain(mediaEntity)
                 } else {
                      MediaItem(
                             id = "${entity.serverId}_${entity.ratingKey}",
                             ratingKey = entity.ratingKey,
                             serverId = entity.serverId,
                             title = entity.title,
                             type = when(entity.type) { "movie" -> MediaType.Movie; "show" -> MediaType.Show; "episode" -> MediaType.Episode; else -> MediaType.Movie },
                             thumbUrl = entity.thumbUrl,
                             artUrl = entity.artUrl,
                             year = entity.year
                        )
                 }
                 
                 if (server != null && baseUrl != null) {
                        val fullThumb = if (domain.thumbUrl != null && !domain.thumbUrl.startsWith("http")) 
                            "$baseUrl${domain.thumbUrl}?X-Plex-Token=$token" else domain.thumbUrl
                        val fullArt = if (domain.artUrl != null && !domain.artUrl.startsWith("http")) 
                            "$baseUrl${domain.artUrl}?X-Plex-Token=$token" else domain.artUrl
                        
                        // Optimized images
                        val optimizedThumb = getOptimizedImageUrl(fullThumb, 300, 450) ?: fullThumb
                        val optimizedArt = getOptimizedImageUrl(fullArt, 1280, 720) ?: fullArt

                        domain.copy(
                            baseUrl = baseUrl,
                            accessToken = token,
                            thumbUrl = optimizedThumb,
                            artUrl = optimizedArt
                        )
                 } else {
                     domain
                 }
             }
             
             deduplicateMediaItems(items, ownedServerIds, servers)
        }
    }

    override fun isFavorite(ratingKey: String, serverId: String): kotlinx.coroutines.flow.Flow<Boolean> {
        return favoriteDao.isFavorite(ratingKey, serverId)
    }

    override suspend fun toggleFavorite(media: MediaItem): Result<Boolean> {
        return try {
            val isFav = favoriteDao.isFavorite(media.ratingKey, media.serverId).first()
            if (isFav) {
                favoriteDao.deleteFavorite(media.ratingKey, media.serverId)
                // Sync removal to Plex watchlist in background
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val token = settingsDataStore.plexToken.first()
                        val clientId = settingsDataStore.clientId.first()
                        // Use GUID for global watchlist sync if available, fallback to ratingKey logic if needed (but usually GUID is safer for Plex Discover)
                        val idToSync = media.guid ?: media.ratingKey 
                        
                        if (token != null && clientId != null) {
                            if (idToSync.startsWith("plex://")) {
                                api.removeFromWatchlist(idToSync, token, clientId)
                            } else {
                                // If no global GUID, we can't reliably sync to Plex Watchlist (which is global)
                                // But maybe the user wants to remove by ratingKey? Plex API usually expects `ratingKey` param to be the GUID string for Watchlist actions on metadata.provider.plex.tv
                                android.util.Log.w("MediaRepository", "Skipping Watchlist sync: No valid GUID for ${media.title} ($idToSync)")
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("MediaRepository", "Failed to sync removal to Plex: ${e.message}")
                    }
                }
                Result.success(false)
            } else {
                favoriteDao.insertFavorite(com.chakir.plexhubtv.core.database.FavoriteEntity(
                    ratingKey = media.ratingKey,
                    serverId = media.serverId,
                    title = media.title,
                    type = media.type.name.lowercase(),
                    thumbUrl = media.thumbUrl,
                    artUrl = media.artUrl,
                    year = media.year
                ))
                // Sync addition to Plex watchlist in background
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val token = settingsDataStore.plexToken.first()
                        val clientId = settingsDataStore.clientId.first()
                        val idToSync = media.guid ?: media.ratingKey
                        
                        if (token != null && clientId != null) {
                            if (idToSync.startsWith("plex://")) {
                                api.addToWatchlist(idToSync, token, clientId)
                            } else {
                                android.util.Log.w("MediaRepository", "Skipping Watchlist sync: No valid GUID for ${media.title} ($idToSync)")
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("MediaRepository", "Failed to sync addition to Plex: ${e.message}")
                    }
                }
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncWatchlist(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = settingsDataStore.plexToken.first() ?: return@withContext Result.failure(Exception("No token"))
            val clientId = settingsDataStore.clientId.first() ?: return@withContext Result.failure(Exception("No client ID"))

            val response = api.getWatchlist(token, clientId)
            if (response.isSuccessful) {
                val metadata = response.body()?.mediaContainer?.metadata ?: emptyList()
                
                // For each item in watchlist, check if we have it locally matching by GUID
                metadata.forEach { item ->
                    val guid = item.guid // This is the Plex GUID e.g. plex://movie/5d77682...
                    if (guid != null) {
                        // Find local item(s) that match this GUID
                        val localItems = mediaDao.getAllMediaByGuid(guid) // Returns List<MediaEntity>
                        
                        // Mark all matching local instances as Favorites
                        localItems.forEach { local ->
                             favoriteDao.insertFavorite(com.chakir.plexhubtv.core.database.FavoriteEntity(
                                ratingKey = local.ratingKey,
                                serverId = local.serverId,
                                title = local.title,
                                type = local.type,
                                thumbUrl = local.thumbUrl,
                                artUrl = local.artUrl,
                                year = local.year,
                                addedAt = System.currentTimeMillis() // Or use item.addedAt from Plex?
                            ))
                        }
                    }
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to fetch watchlist: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getWatchHistory(limit: Int, offset: Int): kotlinx.coroutines.flow.Flow<List<MediaItem>> {
        return mediaDao.getHistory(limit, offset).map { entities ->
             // Fetch servers to resolve base URLs
             val servers = authRepository.getServers().getOrNull() ?: emptyList()
             
             entities.map { entity ->
                 val server = servers.find { it.clientIdentifier == entity.serverId }
                 val baseUrl = if (server != null) connectionManager.getCachedUrl(server.clientIdentifier) ?: server.address else null
                 
                 val domain = mapper.mapEntityToDomain(entity)
                 if (server != null && baseUrl != null) {
                        val fullThumb = if (entity.thumbUrl != null && !entity.thumbUrl.startsWith("http")) 
                            "$baseUrl${entity.thumbUrl}?X-Plex-Token=${server.accessToken}" else entity.thumbUrl
                        val fullArt = if (entity.artUrl != null && !entity.artUrl.startsWith("http")) 
                            "$baseUrl${entity.artUrl}?X-Plex-Token=${server.accessToken}" else entity.artUrl
                        
                        domain.copy(
                            baseUrl = baseUrl,
                            accessToken = server.accessToken,
                            thumbUrl = fullThumb,
                            artUrl = fullArt
                        )
                 } else {
                     domain
                 }
             }
        }
    }

    // --- Aggregation Logic ---

    private suspend fun getActiveClients(): List<com.chakir.plexhubtv.core.network.PlexClient> = coroutineScope {
        val servers = authRepository.getServers(forceRefresh = true).getOrNull() ?: return@coroutineScope emptyList()
        
        servers.map { server ->
            async {
                val baseUrl = connectionManager.findBestConnection(server)
                if (baseUrl != null) {
                    com.chakir.plexhubtv.core.network.PlexClient(server, api, baseUrl)
                } else null
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun getClient(serverId: String): com.chakir.plexhubtv.core.network.PlexClient? {
        val servers = authRepository.getServers(forceRefresh = false).getOrNull() ?: return null
        val server = servers.find { it.clientIdentifier == serverId } ?: return null
        val baseUrl = connectionManager.findBestConnection(server) ?: return null
        return com.chakir.plexhubtv.core.network.PlexClient(server, api, baseUrl)
    }

    /**
     * Dédoublonne les éléments provenant de plusieurs serveurs.
     *
     * Algorithme de dédoublonnage :
     * 1. Groupement par ID unique externe (IMDB, TMDB).
     * 2. Si pas d'ID externe, fallback sur "Titre + Année" normalisé.
     * 3. Sélection du "meilleur" candidat dans chaque groupe :
     *    - Priorité au serveur "Propriétaire" (Owned) vs Partagé.
     *    - Puis au plus récemment mis à jour.
     * 4. Fusion des métadonnées (Note moyenne) et des sources (MediaSource) pour le lecteur.
     */
    private suspend fun deduplicateMediaItems(items: List<MediaItem>, ownedServerIds: Set<String>, servers: List<Server>): List<MediaItem> = withContext(Dispatchers.Default) {
        // Advanced Grouping Logic (Matching Python Script Strictly)
        // Priority: IMDB ID -> TMDB ID -> Title+Year
        // We INTENTIONALLY SKIP item.guid to avoid splitting items from different agents (Legacy vs Plex Movie)
        items.groupBy { item ->
            when {
                !item.imdbId.isNullOrBlank() -> "imdb://${item.imdbId}"
                !item.tmdbId.isNullOrBlank() -> "tmdb://${item.tmdbId}"
                // Fallback to Title + Year (Normalized)
                else -> "${item.title.lowercase().trim().replace(Regex("[^a-z0-9 ]"), "")}_${item.year ?: 0}"
            }
        }.map { (_, group) ->
            // Sort by Owned first, then by Last Updated (descending)
            val prioritizedItem = group.sortedWith(
                compareByDescending<MediaItem> { it.serverId in ownedServerIds }
                    .thenByDescending { it.updatedAt ?: 0L }
            ).first()
            
            // Calculate Average Rating
            val ratings = group.mapNotNull { it.rating }
            val averageRating = if (ratings.isNotEmpty()) {
                ratings.average()
            } else null

            // Calculate Average Audience Rating
            val audienceRatings = group.mapNotNull { it.audienceRating }
            val averageAudienceRating = if (audienceRatings.isNotEmpty()) {
                audienceRatings.average()
            } else null

            // Populate Remote Sources
            val sources = group.map { item ->
                val serverName = servers.find { it.clientIdentifier == item.serverId }?.name ?: "Unknown"
                // Extract resolution from mediaParts if available
                val bestMedia = item.mediaParts.firstOrNull()
                val videoStream = bestMedia?.streams?.filterIsInstance<com.chakir.plexhubtv.domain.model.VideoStream>()?.firstOrNull()
                val audioStream = bestMedia?.streams?.filterIsInstance<com.chakir.plexhubtv.domain.model.AudioStream>()?.firstOrNull()
                val languages = bestMedia?.streams?.filterIsInstance<com.chakir.plexhubtv.domain.model.AudioStream>()
                    ?.mapNotNull { it.language }?.distinct() ?: emptyList()

                val resolution = videoStream?.let { "${it.height}p" }
                val hasHDR = videoStream?.hasHDR ?: false
                
                // Calculate full URLs for the alternative source
                val server = servers.find { it.clientIdentifier == item.serverId }
                val baseUrl = if (server != null) connectionManager.getCachedUrl(server.clientIdentifier) ?: server.address else null
                
                val rawThumb = if (item.thumbUrl != null && !item.thumbUrl.startsWith("http")) {
                    if (baseUrl != null && server?.accessToken != null) "$baseUrl${item.thumbUrl}?X-Plex-Token=${server.accessToken}" else null
                } else item.thumbUrl

                val rawArt = if (item.artUrl != null && !item.artUrl.startsWith("http")) {
                    if (baseUrl != null && server?.accessToken != null) "$baseUrl${item.artUrl}?X-Plex-Token=${server.accessToken}" else null
                } else item.artUrl

                val fullThumb = getOptimizedImageUrl(rawThumb, 300, 450) ?: rawThumb
                val fullArt = getOptimizedImageUrl(rawArt, 1280, 720) ?: rawArt
                
                com.chakir.plexhubtv.domain.model.MediaSource(
                    serverId = item.serverId,
                    ratingKey = item.ratingKey,
                    serverName = serverName,
                    resolution = resolution,
                    container = bestMedia?.container,
                    videoCodec = videoStream?.codec,
                    audioCodec = audioStream?.codec,
                    audioChannels = audioStream?.channels,
                    fileSize = bestMedia?.size,
                    bitrate = videoStream?.bitrate,
                    hasHDR = hasHDR,
                    languages = languages,
                    thumbUrl = fullThumb,
                    artUrl = fullArt
                )
            }
            
            prioritizedItem.copy(
                rating = averageRating, // Use average critic rating
                audienceRating = averageAudienceRating, // Use average audience rating
                remoteSources = sources
            )
        }
    }

    override suspend fun getUnifiedLibrary(mediaType: String): Result<List<MediaItem>> = coroutineScope {
        try {
            // 1. Fetch All Raw Data (No SQL Aggregation)
            // We use flow.first() to get the current snapshot of the DB
            val entities = mediaDao.getAllMediaByType(mediaType).first()
            
            // 2. Fetch Servers for Context (Owned vs Shared, BaseURLs)
            val servers = authRepository.getServers().getOrNull() ?: emptyList()
            val ownedServerIds = servers.filter { it.isOwned }.map { it.clientIdentifier }.toSet()

            // 3. Map to Domain & Reconstruct URLs
            val items = entities.map { entity ->
                val server = servers.find { it.clientIdentifier == entity.serverId }
                val baseUrl = if (server != null) connectionManager.getCachedUrl(server.clientIdentifier) ?: server.address else null
                
                val domain = mapper.mapEntityToDomain(entity)
                
                if (server != null && baseUrl != null) {
                    val rawThumb = if (entity.thumbUrl != null && !entity.thumbUrl.startsWith("http")) 
                        "$baseUrl${entity.thumbUrl}?X-Plex-Token=${server.accessToken}" else entity.thumbUrl
                    val rawArt = if (entity.artUrl != null && !entity.artUrl.startsWith("http")) 
                        "$baseUrl${entity.artUrl}?X-Plex-Token=${server.accessToken}" else entity.artUrl
                    
                    val fullThumb = getOptimizedImageUrl(rawThumb, 300, 450) ?: rawThumb
                    val fullArt = getOptimizedImageUrl(rawArt, 1280, 720) ?: rawArt

                    domain.copy(
                        baseUrl = baseUrl,
                        accessToken = server.accessToken,
                        thumbUrl = fullThumb,
                        artUrl = fullArt
                    )
                } else {
                    domain
                }
            }

            // 4. Apply Robust Deduplication (Kotlin)
            // This prioritizes IMDB > TMDB > GUID > Title+Year
            val unifiedItems = deduplicateMediaItems(items, ownedServerIds, servers)
            
            Result.success(unifiedItems)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun aggregateHubs(hubs: List<com.chakir.plexhubtv.domain.model.Hub>, ownedServerIds: Set<String>, servers: List<Server>): List<com.chakir.plexhubtv.domain.model.Hub> = coroutineScope {
        // Group hubs by identifier (e.g. "recentlyAdded")
        hubs.groupBy { it.hubIdentifier ?: it.title }.map { (identifier, group) ->
            async {
                val first = group.first()
                com.chakir.plexhubtv.domain.model.Hub(
                    key = first.key,
                    title = first.title,
                    type = first.type,
                    hubIdentifier = identifier,
                    items = deduplicateMediaItems(group.flatMap { it.items }, ownedServerIds, servers),
                    serverId = null // Aggregate hubs are not server-specific
                )
            }
        }.awaitAll().sortedBy { it.title } // Or use a predefined order for hubs
    }
    override suspend fun searchMedia(query: String): Result<List<MediaItem>> = coroutineScope {
        try {
            // Search Movies
            val movies = async { mediaDao.searchMedia(query, "movie") }
            // Search Shows
            val shows = async { mediaDao.searchMedia(query, "show") }
            
            val allEntities = movies.await() + shows.await()
            
            val servers = authRepository.getServers().getOrNull() ?: emptyList()
            
            val items = allEntities.map { entity ->
                 val server = servers.find { it.clientIdentifier == entity.serverId }
                 val baseUrl = if (server != null) connectionManager.getCachedUrl(server.clientIdentifier) ?: server.address else null
                 val token = server?.accessToken

                 val domain = mapper.mapEntityToDomain(entity)
                 if (server != null && baseUrl != null) {
                        val rawThumb = if (entity.thumbUrl != null && !entity.thumbUrl.startsWith("http")) 
                            "$baseUrl${entity.thumbUrl}?X-Plex-Token=$token" else entity.thumbUrl
                        val rawArt = if (entity.artUrl != null && !entity.artUrl.startsWith("http")) 
                            "$baseUrl${entity.artUrl}?X-Plex-Token=$token" else entity.artUrl
                        
                        val fullThumb = getOptimizedImageUrl(rawThumb, 300, 450) ?: rawThumb
                        val fullArt = getOptimizedImageUrl(rawArt, 1280, 720) ?: rawArt

                        domain.copy(
                            baseUrl = baseUrl,
                            accessToken = token,
                            thumbUrl = fullThumb,
                            artUrl = fullArt
                        )
                 } else {
                     domain
                 }
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateStreamSelection(
        serverId: String,
        partId: String,
        audioStreamId: String?,
        subtitleStreamId: String?
    ): Result<Unit> {
        return try {
            val client = getClient(serverId) ?: return Result.failure(Exception("Server not found"))
            val url = "${client.baseUrl}library/parts/$partId"
            val response = api.putStreamSelection(
                url = url,
                audioStreamID = audioStreamId,
                subtitleStreamID = subtitleStreamId,
                token = client.server.accessToken ?: ""
            )
            if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("API Error: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
