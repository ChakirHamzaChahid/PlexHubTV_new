package com.chakir.plexhubtv.data.repository

import androidx.room.withTransaction
import com.chakir.plexhubtv.core.database.IdBridgeDao
import com.chakir.plexhubtv.core.database.IdBridgeEntity
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.database.PlexDatabase
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.Server
import com.chakir.plexhubtv.core.model.toAppError
import com.chakir.plexhubtv.core.network.util.getOptimizedImageUrl
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.repository.LibraryRepository
import com.chakir.plexhubtv.domain.repository.SyncRepository
import com.chakir.plexhubtv.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Implémentation de la synchronisation complète des bibliothèques.
 *
 * Stratégie de Sync :
 * - **Semi-Parallèle** : Synchronise 2 bibliothèques à la fois pour équilibrer charge réseau et écriture DB.
 * - **Batching** : Récupère les items par pages de 500 pour éviter les OOM.
 * - **Logs Métriques** : Tracking précis des temps API vs DB pour le debugging de performance.
 */
class SyncRepositoryImpl
    @Inject
    constructor(
        private val serverClientResolver: ServerClientResolver,
        private val mediaDao: MediaDao,
        private val idBridgeDao: IdBridgeDao,
        private val database: PlexDatabase,
        private val mediaMapper: MediaMapper,
        private val libraryRepository: LibraryRepository,
        private val settingsDataStore: SettingsDataStore,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : SyncRepository {
        // Callback for progress updates (set by LibrarySyncWorker)
        override var onProgressUpdate: ((current: Int, total: Int, libraryName: String) -> Unit)? = null

        override suspend fun syncServer(server: Server): Result<Unit> =
            withContext(ioDispatcher) {
                try {
                    Timber.d("Starting sync for server: ${server.name}")
                    // 1. Get Libraries
                    val librariesResult = libraryRepository.getLibraries(server.clientIdentifier)
                    val libraries = librariesResult.getOrNull()
                        ?: return@withContext Result.failure(AppError.Network.ServerError("Failed to fetch libraries for ${server.name}"))

                    // 2. Filter to syncable libraries (movies and shows) + user selection
                    libraries.forEach { Timber.d("Found library: ${it.title} (type=${it.type})") }
                    val selectedIds = settingsDataStore.selectedLibraryIds.first()
                    Timber.d("SYNC FILTER: selectedIds = $selectedIds (size=${selectedIds.size})")

                    val syncableLibraries = libraries
                        .filter { it.type == "movie" || it.type == "show" }
                        .filter { lib ->
                            val compositeId = "${server.clientIdentifier}:${lib.key}"
                            val isSelected = selectedIds.contains(compositeId)
                            Timber.d("SYNC FILTER: Library '${lib.title}' (id=$compositeId) -> selected=$isSelected")
                            isSelected
                        }
                    Timber.d("Syncable libraries after selection filter: ${syncableLibraries.map { it.title }}")

                    // 3. SEMI-PARALLEL SYNC: Limit to 2 concurrent libraries to prevent DB lock contention
                    val results =
                        syncableLibraries.chunked(2).flatMap { chunk ->
                            coroutineScope {
                                chunk.map { library ->
                                    async {
                                        try {
                                            Timber.d("Syncing library: ${library.title} (${library.type})")
                                            syncLibrary(server, library.key, library.title).getOrThrow()
                                            Timber.d("Successfully synced library: ${library.title}")
                                            true
                                        } catch (e: Exception) {
                                            Timber.e("Failed to sync library ${library.title}: ${e.message}")
                                            false
                                        }
                                    }
                                }.awaitAll()
                            }
                        }

                    val successCount = results.count { it }
                    Timber.d("Sync complete: $successCount/${syncableLibraries.size} libraries synced")

                    Result.success(Unit)
                } catch (e: Exception) {
                    Timber.e("Critical failure syncing server ${server.name}: ${e.message}")
                    Result.failure(e.toAppError())
                }
            }

        override suspend fun syncLibrary(
            server: Server,
            libraryKey: String,
        ): Result<Unit> {
            return syncLibrary(server, libraryKey, "Library")
        }

        private suspend fun syncLibrary(
            server: Server,
            libraryKey: String,
            libraryName: String,
        ): Result<Unit> =
            withContext(ioDispatcher) {
                try {
                    val client = serverClientResolver.resolveClient(server)
                        ?: return@withContext Result.failure(AppError.Network.NoConnection("No connection to ${server.name}"))
                    val baseUrl = client.baseUrl

                    var start = 0
                    val size = 500 // Batch size
                    var hasMore = true
                    var totalSize = 0
                    val syncedRatingKeys = mutableSetOf<String>()

                    while (hasMore) {
                        val startTime = System.currentTimeMillis()
                        Timber.d("--------------------------------------------------")
                        Timber.d("SYNC [Page Request]: start=$start, size=$size for $libraryName")

                        // 1. API Fetch
                        val apiStartTime = System.currentTimeMillis()
                        val response = client.getLibraryContents(libraryKey, start, size)
                        val apiDuration = System.currentTimeMillis() - apiStartTime

                        if (response.isSuccessful) {
                            val container = response.body()?.mediaContainer
                            val metadata = container?.metadata ?: emptyList()
                            totalSize = container?.totalSize ?: 0

                            if (metadata.isNotEmpty()) {
                                // 2. Filter, Mapping & DB Insert
                                val dbStartTime = System.currentTimeMillis()

                                // FILTER: Only keep quality metadata (Movies MUST have IMDb/TMDB)
                                val validMetadata = metadata.filter { mediaMapper.isQualityMetadata(it) }

                                // PERSISTENCE: Retrieve existing scraped ratings before they get overwritten
                                val ratingKeys = validMetadata.map { it.ratingKey }
                                val existingRatingsMap = mediaDao.getScrapedRatings(ratingKeys, server.clientIdentifier)

                                // PERSISTENCE: Preserve soft-hidden state across upserts (INSERT OR REPLACE resets isHidden)
                                val hiddenKeys = mediaDao.getHiddenRatingKeys(server.clientIdentifier)

                                // PERSISTENCE: Preserve TMDB manual overrides (summary + poster)
                                val overriddenMap = mediaDao.getOverriddenMetadata(ratingKeys, server.clientIdentifier)
                                    .associateBy { it.ratingKey }

                                val accessToken = server.accessToken ?: ""
                                val entities =
                                    validMetadata.mapIndexed { index, dto ->
                                        val dtoWithLib = dto.copy(librarySectionID = libraryKey)
                                        val entity = mediaMapper.mapDtoToEntity(dtoWithLib, server.clientIdentifier, libraryKey, isOwned = server.isOwned)

                                        // Restore scrapedRating and recompute displayRating
                                        val restoredScrapedRating = existingRatingsMap[dto.ratingKey]
                                        val wasHidden = dto.ratingKey in hiddenKeys
                                        val overrides = overriddenMap[dto.ratingKey]
                                        val plexThumbUrl = entity.thumbUrl?.let { path ->
                                            getOptimizedImageUrl("$baseUrl$path?X-Plex-Token=$accessToken", 300, 450)
                                                ?: "$baseUrl$path?X-Plex-Token=$accessToken"
                                        }
                                        entity.copy(
                                            filter = "all",
                                            sortOrder = "default",
                                            pageOffset = start + index,
                                            scrapedRating = restoredScrapedRating,
                                            displayRating = restoredScrapedRating
                                                ?: entity.displayRating,
                                            resolvedThumbUrl = overrides?.overriddenThumbUrl ?: plexThumbUrl,
                                            resolvedArtUrl = entity.artUrl?.let { path ->
                                                getOptimizedImageUrl("$baseUrl$path?X-Plex-Token=$accessToken", 1280, 720)
                                                    ?: "$baseUrl$path?X-Plex-Token=$accessToken"
                                            },
                                            resolvedBaseUrl = baseUrl,
                                            isHidden = wasHidden,
                                            hiddenAt = if (wasHidden) System.currentTimeMillis() else 0,
                                            overriddenSummary = overrides?.overriddenSummary,
                                            overriddenThumbUrl = overrides?.overriddenThumbUrl,
                                            summary = overrides?.overriddenSummary ?: entity.summary,
                                        )
                                    }

                                syncedRatingKeys.addAll(entities.map { it.ratingKey })

                                if (entities.isNotEmpty()) {
                                    // Single transaction: Room fires InvalidationTracker only ONCE
                                    // instead of separately for media + id_bridge writes
                                    database.withTransaction {
                                        mediaDao.upsertMedia(entities)
                                        val bridgeEntries = entities.mapNotNull { entity ->
                                            val imdb = entity.imdbId?.takeIf { it.isNotBlank() }
                                            val tmdb = entity.tmdbId?.takeIf { it.isNotBlank() }
                                            if (imdb != null && tmdb != null) IdBridgeEntity(imdb, tmdb) else null
                                        }
                                        if (bridgeEntries.isNotEmpty()) idBridgeDao.upsertAll(bridgeEntries)
                                        // Solution C: compute groupKey post-bridge
                                        mediaDao.updateGroupKeys(server.clientIdentifier, entities.map { it.ratingKey })
                                    }
                                }
                                val dbDuration = System.currentTimeMillis() - dbStartTime
                                val skippedCount = metadata.size - entities.size

                                val totalIterationDuration = System.currentTimeMillis() - startTime
                                Timber.i("SYNC SUCCESS: lib=$libraryName items=${entities.size} (skipped=$skippedCount)")
                                Timber.i(" -> API Latency: ${apiDuration}ms")
                                Timber.i(" -> DB Ingestion: ${dbDuration}ms")
                                Timber.i(" -> Total Page Process: ${totalIterationDuration}ms")
                                Timber.d("--------------------------------------------------")

                                start += metadata.size

                                // PROGRESS UPDATE
                                onProgressUpdate?.invoke(start, totalSize, libraryName)
                            } else {
                                hasMore = false
                            }

                            if (start >= totalSize) {
                                hasMore = false
                            }
                        } else {
                            val errorDuration = System.currentTimeMillis() - startTime
                            Timber.e("SYNC FAILED: lib=$libraryKey code=${response.code()} duration=${errorDuration}ms")
                            return@withContext Result.failure(AppError.Network.ServerError("Failed to fetch page: ${response.code()}"))
                        }
                    }

                    // Differential cleanup: remove items no longer present on the server
                    if (syncedRatingKeys.isNotEmpty()) {
                        val existingKeys = mediaDao.getRatingKeysByLibrary(server.clientIdentifier, libraryKey)
                        val staleKeys = existingKeys.filter { it !in syncedRatingKeys }
                        if (staleKeys.isNotEmpty()) {
                            database.withTransaction {
                                staleKeys.chunked(500).forEach { chunk ->
                                    mediaDao.deleteMediaByKeys(server.clientIdentifier, chunk)
                                }
                            }
                            Timber.i("SYNC CLEANUP: Removed ${staleKeys.size} stale items from $libraryName")
                        }
                    }

                    Timber.d("Finished syncing library $libraryName: $totalSize total items")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Timber.e("Error in syncLibrary $libraryKey: ${e.message}")
                    Result.failure(e.toAppError())
                }
            }
    }
