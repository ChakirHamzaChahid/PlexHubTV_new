package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.BackendServerDao
import com.chakir.plexhubtv.core.database.BackendServerEntity
import com.chakir.plexhubtv.core.database.MediaDao
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
    private val mapper: BackendMediaMapper,
    private val mediaMapper: MediaMapper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BackendRepository {

    override fun observeServers(): Flow<List<BackendServer>> =
        backendServerDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun addServer(label: String, baseUrl: String): Result<BackendServer> =
        withContext(ioDispatcher) {
            runCatching {
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
        withContext(ioDispatcher) {
            runCatching {
                val service = backendApiClient.getService(baseUrl)
                val health = service.getHealth()
                BackendConnectionInfo(
                    totalMedia = health.totalMedia,
                    enrichedMedia = health.enrichedMedia,
                    version = health.version,
                )
            }
        }

    override suspend fun syncMedia(backendId: String): Result<Int> =
        withContext(ioDispatcher) {
            runCatching {
                val backend = backendServerDao.getById(backendId)
                    ?: throw IllegalStateException("Backend server $backendId not found")
                val service = backendApiClient.getService(backend.baseUrl)
                val serverId = "backend_$backendId"
                var totalSynced = 0

                // Collect all synced ratingKeys for differential cleanup
                val syncedRatingKeys = mutableSetOf<String>()

                // Sync movies (paginated)
                var offset = 0
                do {
                    val response = service.getMovies(limit = 500, offset = offset)
                    val entities = response.items.map { mapper.mapDtoToEntity(it, backendId) }
                    if (entities.isNotEmpty()) {
                        mediaDao.upsertMedia(entities)
                        entities.forEach { syncedRatingKeys.add(it.ratingKey) }
                        totalSynced += entities.size
                    }
                    offset += 500
                } while (response.hasMore)

                // Sync shows (paginated)
                offset = 0
                do {
                    val response = service.getShows(limit = 500, offset = offset)
                    val entities = response.items.map { mapper.mapDtoToEntity(it, backendId) }
                    if (entities.isNotEmpty()) {
                        mediaDao.upsertMedia(entities)
                        entities.forEach { syncedRatingKeys.add(it.ratingKey) }
                        totalSynced += entities.size
                    }
                    offset += 500
                } while (response.hasMore)

                // Differential cleanup: remove items no longer in backend
                val existingMovies = mediaDao.getMediaByServerTypeFilter(serverId, "movie", "all")
                val existingShows = mediaDao.getMediaByServerTypeFilter(serverId, "show", "all")
                val staleItems = (existingMovies + existingShows).filter { it.ratingKey !in syncedRatingKeys }
                staleItems.forEach { mediaDao.deleteMedia(it.ratingKey, serverId) }

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
        withContext(ioDispatcher) {
            runCatching {
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
                response.url
            }
        }

    override suspend fun getEpisodes(parentRatingKey: String, backendServerId: String): Result<List<MediaItem>> =
        withContext(ioDispatcher) {
            runCatching {
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
                        mediaDao.upsertMedia(entities)
                        allEpisodes.addAll(entities.map { mediaMapper.mapEntityToDomain(it) })
                    }
                    offset += 500
                } while (response.hasMore)

                allEpisodes
            }
        }

    override suspend fun getMediaDetail(ratingKey: String, backendServerId: String): Result<MediaItem> =
        withContext(ioDispatcher) {
            runCatching {
                // Room first
                val localEntity = mediaDao.getMedia(ratingKey, backendServerId)
                if (localEntity != null) {
                    return@runCatching mediaMapper.mapEntityToDomain(localEntity)
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
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
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
        withContext(ioDispatcher) {
            runCatching {
                val backend = backendServerDao.getById(backendId)
                    ?: throw IllegalStateException("Backend server $backendId not found")
                val service = backendApiClient.getService(backend.baseUrl)
                service.deleteAccount(accountId)
            }
        }

    override suspend fun testXtreamAccount(backendId: String, accountId: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val backend = backendServerDao.getById(backendId)
                    ?: throw IllegalStateException("Backend server $backendId not found")
                val service = backendApiClient.getService(backend.baseUrl)
                service.testAccount(accountId)
                Unit
            }
        }

    override suspend fun syncAll(backendId: String): Result<String> =
        withContext(ioDispatcher) {
            runCatching {
                val backend = backendServerDao.getById(backendId)
                    ?: throw IllegalStateException("Backend server $backendId not found")
                val service = backendApiClient.getService(backend.baseUrl)
                service.triggerSyncAll().jobId
            }
        }

    override suspend fun triggerAccountSync(backendId: String, accountId: String): Result<String> =
        withContext(ioDispatcher) {
            runCatching {
                val backend = backendServerDao.getById(backendId)
                    ?: throw IllegalStateException("Backend server $backendId not found")
                val service = backendApiClient.getService(backend.baseUrl)
                service.triggerSync(BackendSyncRequest(accountId = accountId)).jobId
            }
        }

    override suspend fun getCategories(backendId: String, accountId: String): Result<CategoryConfig> =
        withContext(ioDispatcher) {
            runCatching {
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
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
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
        withContext(ioDispatcher) {
            runCatching {
                val backend = backendServerDao.getById(backendId)
                    ?: throw IllegalStateException("Backend server $backendId not found")
                val service = backendApiClient.getService(backend.baseUrl)
                service.refreshCategories(accountId)
                Unit
            }
        }

    private fun BackendServerEntity.toDomain() = BackendServer(
        id = id,
        label = label,
        baseUrl = baseUrl,
        isActive = isActive,
        lastSyncedAt = lastSyncedAt,
    )
}
