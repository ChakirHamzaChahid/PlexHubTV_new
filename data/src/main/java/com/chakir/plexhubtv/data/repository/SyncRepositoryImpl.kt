package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.model.Server
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.core.network.PlexClient
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.repository.LibraryRepository
import com.chakir.plexhubtv.domain.repository.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        private val api: PlexApiService,
        private val connectionManager: ConnectionManager,
        private val mediaDao: MediaDao,
        private val mediaMapper: MediaMapper,
        private val libraryRepository: LibraryRepository,
    ) : SyncRepository {
        // Callback for progress updates (set by LibrarySyncWorker)
        override var onProgressUpdate: ((current: Int, total: Int, libraryName: String) -> Unit)? = null

        override suspend fun syncServer(server: Server): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    Timber.d("Starting sync for server: ${server.name}")
                    // 1. Get Libraries
                    val librariesResult = libraryRepository.getLibraries(server.clientIdentifier)
                    val libraries = librariesResult.getOrNull() ?: return@withContext Result.failure(Exception("Failed to fetch libraries for ${server.name}"))

                    // 2. Filter to syncable libraries (movies and shows)
                    libraries.forEach { Timber.d("Found library: ${it.title} (type=${it.type})") }
                    val syncableLibraries = libraries.filter { it.type == "movie" || it.type == "show" }

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
                    Result.failure(e)
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
            withContext(Dispatchers.IO) {
                try {
                    val baseUrl = connectionManager.findBestConnection(server) ?: return@withContext Result.failure(Exception("No connection to ${server.name}"))
                    val client = PlexClient(server, api, baseUrl)

                    var start = 0
                    val size = 500 // Batch size
                    var hasMore = true
                    var totalSize = 0

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

                                val entities =
                                    validMetadata.mapIndexed { index, dto ->
                                        val dtoWithLib = dto.copy(librarySectionID = libraryKey)
                                        val entity = mediaMapper.mapDtoToEntity(dtoWithLib, server.clientIdentifier, libraryKey)

                                        // Restore scrapedRating and recompute displayRating
                                        val restoredScrapedRating = existingRatingsMap[dto.ratingKey]
                                        entity.copy(
                                            filter = "all",
                                            sortOrder = "default",
                                            pageOffset = start + index,
                                            scrapedRating = restoredScrapedRating,
                                            displayRating = restoredScrapedRating
                                                ?: entity.displayRating,
                                        )
                                    }

                                if (entities.isNotEmpty()) {
                                    mediaDao.upsertMedia(entities)
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
                            return@withContext Result.failure(Exception("Failed to fetch page: ${response.code()}"))
                        }
                    }

                    Timber.d("Finished syncing library $libraryName: $totalSize total items")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Timber.e("Error in syncLibrary $libraryKey: ${e.message}")
                    Result.failure(e)
                }
            }
    }
