package com.chakir.plexhubtv.data.repository

import androidx.room.withTransaction
import com.chakir.plexhubtv.core.database.BackendServerDao
import com.chakir.plexhubtv.core.database.BackendServerEntity
import com.chakir.plexhubtv.core.database.IdBridgeDao
import com.chakir.plexhubtv.core.database.IdBridgeEntity
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.database.PlexDatabase
import com.chakir.plexhubtv.core.di.IoDispatcher
import com.chakir.plexhubtv.core.model.BackendConnectionInfo
import com.chakir.plexhubtv.core.model.BackendServer
import com.chakir.plexhubtv.core.model.Category
import com.chakir.plexhubtv.core.model.CategoryConfig
import com.chakir.plexhubtv.core.model.CategorySelection
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.network.backend.BackendAccountCreate
import com.chakir.plexhubtv.core.network.backend.BackendApiClient
import com.chakir.plexhubtv.core.network.backend.BackendCategoryUpdate
import com.chakir.plexhubtv.core.network.backend.BackendCategoryUpdateRequest
import com.chakir.plexhubtv.core.network.backend.BackendSyncRequest
import com.chakir.plexhubtv.core.network.util.safeApiCall
import com.chakir.plexhubtv.data.mapper.BackendMediaMapper
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.repository.BackendRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendRepositoryImpl @Inject constructor(
    private val backendApiClient: BackendApiClient,
    private val backendServerDao: BackendServerDao,
    private val mediaDao: MediaDao,
    private val idBridgeDao: IdBridgeDao,
    private val database: PlexDatabase,
    private val mapper: BackendMediaMapper,
    private val mediaMapper: MediaMapper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BackendRepository {

    override fun observeServers(): Flow<List<BackendServer>> =
        backendServerDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun addServer(label: String, baseUrl: String): Result<BackendServer> =
        safeApiCall("BackendRepository.addServer") {
            withContext(ioDispatcher) {
                val service = backendApiClient.getService(baseUrl)
                val health = service.getHealth()
                require(health.status == "ok") { "Backend unhealthy: ${health.status}" }

                val id = UUID.randomUUID().toString().take(8)
                val entity = BackendServerEntity(
                    id = id,
                    label = label,
                    baseUrl = baseUrl.trimEnd('/'),
                    isActive = true,
                )
                backendServerDao.upsert(entity)
                entity.toDomain()
            }
        }

    override suspend fun removeServer(id: String): Unit =
        withContext(ioDispatcher) {
            val serverId = "backend_$id"
            // Delete all media synced from this backend
            mediaDao.deleteAllMediaByServerId(serverId)
            backendServerDao.delete(id)
            Timber.d("Removed backend server $id and its media")
        }

    override suspend fun testConnection(baseUrl: String): Result<BackendConnectionInfo> =
        safeApiCall("BackendRepository.testConnection") {
            withContext(ioDispatcher) {
                val service = backendApiClient.getService(baseUrl)
                val health = service.getHealth()
                BackendConnectionInfo(
                    status = health.status,
                    totalMedia = health.totalMedia,
                    enrichedMedia = health.enrichedMedia,
                    brokenStreams = health.brokenStreams,
                    accounts = health.accounts,
                    version = health.version,
                    lastSyncAt = health.lastSyncAt,
                )
            }
        }

    override suspend fun getHealthInfo(backendId: String): Result<BackendConnectionInfo> =
        safeApiCall("BackendRepository.getHealthInfo") {
            withContext(ioDispatcher) {
                val backend = backendServerDao.getById(backendId)
                    ?: throw IllegalStateException("Backend server $backendId not found")
                val service = backendApiClient.getService(backend.baseUrl)
                val health = service.getHealth()
                BackendConnectionInfo(
                    status = health.status,
                    totalMedia = health.totalMedia,
                    enrichedMedia = health.enrichedMedia,
                    brokenStreams = health.brokenStreams,
                    accounts = health.accounts,
                    version = health.version,
                    lastSyncAt = health.lastSyncAt,
                )
            }
        }

    override suspend fun syncMedia(backendId: String): Result<Int> =
        safeApiCall("BackendRepository.syncMedia") {
            withContext(ioDispatcher) {
                val backend = backendServerDao.getById(backendId)
                    ?: throw IllegalStateException("Backend server $backendId not found")
                val service = backendApiClient.getService(backend.baseUrl)
                val serverId = "backend_$backendId"
                var totalSynced = 0

                // Collect all synced ratingKeys for differential cleanup
                val syncedRatingKeys = mutableSetOf<String>()

                // Sync movies (paginated) — single transaction per page
                var offset = 0
                do {
                    val response = service.getMovies(limit = 500, offset = offset)
                    val entities = response.items.map { mapper.mapDtoToEntity(it, backendId) }
                    if (entities.isNotEmpty()) {
                        database.withTransaction {
                            mediaDao.upsertMedia(entities)
                            populateIdBridge(entities)
                        }
                        entities.forEach { syncedRatingKeys.add(it.ratingKey) }
                        totalSynced += entities.size
                    }
                    offset += 500
                } while (response.hasMore)

                // Sync shows (paginated) — single transaction per page
                offset = 0
                do {
                    val response = service.getShows(limit = 500, offset = offset)
                    val entities = response.items.map { mapper.mapDtoToEntity(it, backendId) }
                    if (entities.isNotEmpty()) {
                        database.withTransaction {
                            mediaDao.upsertMedia(entities)
                            populateIdBridge(entities)
                        }
                        entities.forEach { syncedRatingKeys.add(it.ratingKey) }
                        totalSynced += entities.size
                    }
                    offset += 500
                } while (response.hasMore)

                // Sync episodes for each show (needed for unified seasons query)
                // Accumulate across shows and flush every 2000 episodes to balance
                // memory usage vs InvalidationTracker notifications
                val syncedShows = mediaDao.getMediaByServerTypeFilter(serverId, "show", "all")
                val pendingEpisodes = mutableListOf<com.chakir.plexhubtv.core.database.MediaEntity>()
                for (showEntity in syncedShows) {
                    try {
                        var epOffset = 0
                        do {
                            val epResponse = service.getEpisodes(
                                parentRatingKey = showEntity.ratingKey,
                                limit = 500,
                                offset = epOffset,
                            )
                            val epEntities = epResponse.items.map { mapper.mapDtoToEntity(it, backendId) }
                            if (epEntities.isNotEmpty()) {
                                pendingEpisodes.addAll(epEntities)
                                totalSynced += epEntities.size
                            }
                            epOffset += 500
                        } while (epResponse.hasMore)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to sync episodes for show ${showEntity.title}")
                    }

                    // Flush every 2000 episodes to limit memory (~2KB per entity = ~4MB)
                    if (pendingEpisodes.size >= 2000) {
                        database.withTransaction {
                            mediaDao.upsertMedia(pendingEpisodes)
                            populateIdBridge(pendingEpisodes)
                        }
                        pendingEpisodes.clear()
                    }
                }
                // Final flush
                if (pendingEpisodes.isNotEmpty()) {
                    database.withTransaction {
                        mediaDao.upsertMedia(pendingEpisodes)
                        populateIdBridge(pendingEpisodes)
                    }
                    pendingEpisodes.clear()
                }

                // Differential cleanup: batch all deletes in a single transaction
                val existingMovies = mediaDao.getMediaByServerTypeFilter(serverId, "movie", "all")
                val existingShows = syncedShows
                val staleItems = (existingMovies + existingShows).filter { it.ratingKey !in syncedRatingKeys }
                if (staleItems.isNotEmpty()) {
                    database.withTransaction {
                        staleItems.forEach { mediaDao.deleteMedia(it.ratingKey, serverId) }
                    }
                }

                if (staleItems.isNotEmpty()) {
                    Timber.d("Cleaned up ${staleItems.size} stale backend items")
                }

                // Update last sync time
                backendServerDao.upsert(backend.copy(lastSyncedAt = System.currentTimeMillis()))

                Timber.i("BACKEND [${backend.label}] Synced $totalSynced items (${staleItems.size} removed)")
                totalSynced
            }
        }

    override suspend fun getStreamUrl(ratingKey: String, backendServerId: String): Result<String> =
        safeApiCall("BackendRepository.getStreamUrl") {
            withContext(ioDispatcher) {
                val backendId = backendServerId.removePrefix("backend_")
                val backend = backendServerDao.getById(backendId)
                    ?: throw IllegalStateException("Backend server $backendId not found")

                // Get original serverId from Room entity
                val entity = mediaDao.getMedia(ratingKey, backendServerId)
                Timber.d("BACKEND getStreamUrl: ratingKey=$ratingKey, backendServerId=$backendServerId, entity=$entity, sourceServerId=${entity?.sourceServerId}")
                val originalServerId = entity?.sourceServerId
                    ?: throw IllegalStateException("No sourceServerId for $ratingKey (entity=${if (entity == null) "NULL" else "found but sourceServerId=null"})")

                val service = backendApiClient.getService(backend.baseUrl)
                val response = service.getStreamUrl(ratingKey, originalServerId)
                Timber.d("BACKEND getStreamUrl: resolved url=${response.url}")
                response.url
            }
        }

    override suspend fun getEpisodes(parentRatingKey: String, backendServerId: String): Result<List<MediaItem>> =
        safeApiCall("BackendRepository.getEpisodes") {
            withContext(ioDispatcher) {
                val backendId = backendServerId.removePrefix("backend_")
                val backend = backendServerDao.getById(backendId)
                    ?: throw IllegalStateException("Backend server $backendId not found")

                val service = backendApiClient.getService(backend.baseUrl)
                val allEpisodes = mutableListOf<MediaItem>()
                var offset = 0

                do {
                    val response = service.getEpisodes(
                        parentRatingKey = parentRatingKey,
                        limit = 500,
                        offset = offset,
                    )
                    val entities = response.items.map { mapper.mapDtoToEntity(it, backendId) }
                    if (entities.isNotEmpty()) {
                        database.withTransaction {
                            mediaDao.upsertMedia(entities)
                            populateIdBridge(entities)
                        }
                        allEpisodes.addAll(entities.map { mediaMapper.mapEntityToDomain(it) })
                    }
                    offset += 500
                } while (response.hasMore)

                allEpisodes
            }
        }

    override suspend fun getMediaDetail(ratingKey: String, backendServerId: String): Result<MediaItem> =
        safeApiCall("BackendRepository.getMediaDetail") {
            withContext(ioDispatcher) {
                // Room first
                val localEntity = mediaDao.getMedia(ratingKey, backendServerId)
                if (localEntity != null) {
                    return@withContext mediaMapper.mapEntityToDomain(localEntity)
                }

                // Fallback: fetch from backend API
                val backendId = backendServerId.removePrefix("backend_")
                val backend = backendServerDao.getById(backendId)
                    ?: throw IllegalStateException("Backend server $backendId not found")

                val entity = mediaDao.getMedia(ratingKey, backendServerId)
                val originalServerId = entity?.sourceServerId ?: backendServerId

                val service = backendApiClient.getService(backend.baseUrl)
                val dto = service.getMediaDetail(ratingKey, originalServerId)
                val mappedEntity = mapper.mapDtoToEntity(dto, backendId)
                mediaDao.insertMedia(mappedEntity)
                mediaMapper.mapEntityToDomain(mappedEntity)
            }
        }

    override suspend fun createXtreamAccount(
        backendId: String,
        label: String,
        baseUrl: String,
        port: Int,
        username: String,
        password: String,
    ): Result<Unit> = safeApiCall("BackendRepository.createXtreamAccount") {
        withContext(ioDispatcher) {
            val backend = backendServerDao.getById(backendId)
                ?: throw IllegalStateException("Backend server $backendId not found")
            val service = backendApiClient.getService(backend.baseUrl)
            service.createAccount(
                BackendAccountCreate(
                    label = label,
                    baseUrl = baseUrl,
                    port = port,
                    username = username,
                    password = password,
                ),
            )
            Unit
        }
    }

    override suspend fun deleteXtreamAccount(backendId: String, accountId: String): Result<Unit> =
        safeApiCall("BackendRepository.deleteXtreamAccount") {
            withContext(ioDispatcher) {
                val backend = backendServerDao.getById(backendId)
                    ?: throw IllegalStateException("Backend server $backendId not found")
                val service = backendApiClient.getService(backend.baseUrl)
                service.deleteAccount(accountId)
            }
        }

    override suspend fun testXtreamAccount(backendId: String, accountId: String): Result<Unit> =
        safeApiCall("BackendRepository.testXtreamAccount") {
            withContext(ioDispatcher) {
                val backend = backendServerDao.getById(backendId)
                    ?: throw IllegalStateException("Backend server $backendId not found")
                val service = backendApiClient.getService(backend.baseUrl)
                service.testAccount(accountId)
                Unit
            }
        }

    override suspend fun syncAll(backendId: String): Result<String> =
        safeApiCall("BackendRepository.syncAll") {
            withContext(ioDispatcher) {
                val backend = backendServerDao.getById(backendId)
                    ?: throw IllegalStateException("Backend server $backendId not found")
                val service = backendApiClient.getService(backend.baseUrl)
                service.triggerSyncAll().jobId
            }
        }

    override suspend fun triggerAccountSync(backendId: String, accountId: String): Result<String> =
        safeApiCall("BackendRepository.triggerAccountSync") {
            withContext(ioDispatcher) {
                val backend = backendServerDao.getById(backendId)
                    ?: throw IllegalStateException("Backend server $backendId not found")
                val service = backendApiClient.getService(backend.baseUrl)
                service.triggerSync(BackendSyncRequest(accountId = accountId)).jobId
            }
        }

    override suspend fun getCategories(backendId: String, accountId: String): Result<CategoryConfig> =
        safeApiCall("BackendRepository.getCategories") {
            withContext(ioDispatcher) {
                val backend = backendServerDao.getById(backendId)
                    ?: throw IllegalStateException("Backend server $backendId not found")
                val service = backendApiClient.getService(backend.baseUrl)
                val response = service.getCategories(accountId)
                CategoryConfig(
                    filterMode = response.filterMode,
                    categories = response.items.map {
                        Category(
                            categoryId = it.categoryId,
                            categoryName = it.categoryName,
                            categoryType = it.categoryType,
                            isAllowed = it.isAllowed,
                        )
                    },
                )
            }
        }

    override suspend fun updateCategories(
        backendId: String,
        accountId: String,
        filterMode: String,
        categories: List<CategorySelection>,
    ): Result<Unit> = safeApiCall("BackendRepository.updateCategories") {
        withContext(ioDispatcher) {
            val backend = backendServerDao.getById(backendId)
                ?: throw IllegalStateException("Backend server $backendId not found")
            val service = backendApiClient.getService(backend.baseUrl)
            service.updateCategories(
                accountId,
                BackendCategoryUpdateRequest(
                    filterMode = filterMode,
                    categories = categories.map {
                        BackendCategoryUpdate(
                            categoryId = it.categoryId,
                            categoryType = it.categoryType,
                            isAllowed = it.isAllowed,
                        )
                    },
                ),
            )
        }
    }

    override suspend fun refreshCategories(backendId: String, accountId: String): Result<Unit> =
        safeApiCall("BackendRepository.refreshCategories") {
            withContext(ioDispatcher) {
                val backend = backendServerDao.getById(backendId)
                    ?: throw IllegalStateException("Backend server $backendId not found")
                val service = backendApiClient.getService(backend.baseUrl)
                service.refreshCategories(accountId)
                Unit
            }
        }

    private suspend fun populateIdBridge(entities: List<com.chakir.plexhubtv.core.database.MediaEntity>) {
        val bridgeEntries = entities.mapNotNull { entity ->
            val imdb = entity.imdbId?.takeIf { it.isNotBlank() }
            val tmdb = entity.tmdbId?.takeIf { it.isNotBlank() }
            if (imdb != null && tmdb != null) IdBridgeEntity(imdb, tmdb) else null
        }
        if (bridgeEntries.isNotEmpty()) idBridgeDao.upsertAll(bridgeEntries)
    }

    private fun BackendServerEntity.toDomain() = BackendServer(
        id = id,
        label = label,
        baseUrl = baseUrl,
        isActive = isActive,
        lastSyncedAt = lastSyncedAt,
    )
}
