package com.chakir.plexhubtv.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chakir.plexhubtv.core.database.*
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.repository.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class CollectionSyncWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val authRepository: AuthRepository,
        private val collectionDao: CollectionDao,
        private val mediaDao: MediaDao,
        private val mediaMapper: MediaMapper,
        private val api: PlexApiService,
        private val connectionManager: ConnectionManager,
    ) : CoroutineWorker(appContext, workerParams) {
        private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        private val channelId = "collection_sync"

        override suspend fun doWork(): androidx.work.ListenableWorker.Result =
            withContext(Dispatchers.IO) {
                Timber.d("Starting Collection Sync (Worker)...")

                try {
                    // Promote to Foreground to bypass 10-min limit
                    try {
                        setForeground(getForegroundInfo())
                        Timber.d("✓ Foreground service set")
                    } catch (e: Exception) {
                        Timber.e(e, "✗ Failed to set foreground: ${e.message}")
                    }

                    // Force refresh to get all connection candidates (Crucial for ConnectionManager to find best URL)
                    updateNotification("Refreshing servers...")
                    val result = authRepository.getServers(forceRefresh = true)
                    val servers = result.getOrNull() ?: emptyList()
                    Timber.d("Sync found ${servers.size} server(s) to process (including shared servers)")
                    val now = System.currentTimeMillis()

                    servers.forEachIndexed { index, server ->
                        Timber.d(">>> Processing server: ${server.name} (ID: ${server.clientIdentifier})")
                        updateNotification("Syncing collections for ${server.name} (${index + 1}/${servers.size})...")

                        try {
                            val baseUrl = connectionManager.findBestConnection(server)
                            if (baseUrl == null) {
                                Timber.e("No connection available for server ${server.name}")
                                return@forEachIndexed
                            }
                            Timber.d("Using base URL: $baseUrl")

                            val sectionsUrl = "$baseUrl/library/sections?X-Plex-Token=${server.accessToken}"
                            val response = api.getSections(sectionsUrl)
                            if (!response.isSuccessful) {
                                Timber.e(
                                    "Failed to fetch sections for ${server.name}: code=${response.code()} error=${response.errorBody()?.string()}",
                                )
                                return@forEachIndexed
                            }
                            val libraries = response.body()?.mediaContainer?.directory ?: emptyList()
                            Timber.d("Found ${libraries.size} libraries on ${server.name}")

                            libraries.forEach { lib ->
                                val libKey = lib.key
                                val libTitle = lib.title ?: "Unknown"
                                val libType = lib.type ?: "unknown"

                                // Only process movie and show libraries for collections
                                if (libKey != null && (libType == "movie" || libType == "show")) {
                                    // Update Notification occasionally
                                    updateNotification("Syncing ${server.name}: $libTitle...")
                                    Timber.i(">>> Syncing Collections for Library: '$libTitle' (key=$libKey, type=$libType)")

                                    val baseUrl = connectionManager.findBestConnection(server) ?: return@forEach
                                    val collectionsUrl = "$baseUrl/library/sections/$libKey/collections?X-Plex-Token=${server.accessToken}"
                                    Timber.d("  -> Fetching collections from: $collectionsUrl")

                                    val collectionsResponse = api.getCollections(collectionsUrl)
                                    if (!collectionsResponse.isSuccessful) {
                                        Timber.e("     ERROR: Failed to fetch collections: code=${collectionsResponse.code()} body=${collectionsResponse.errorBody()?.string()}")
                                        return@forEach
                                    }

                                    val collectionsContainer = collectionsResponse.body()?.mediaContainer
                                    val collections = collectionsContainer?.metadata ?: emptyList()
                                    Timber.i("     SUCCESS: Found ${collections.size} potential collections in '$libTitle'")

                                    collections.forEach { collectionDto ->
                                        if (collectionDto.type != "collection") {
                                            Timber.v(
                                                "     SKIP: Item '${collectionDto.title}' is type ${collectionDto.type} (not collection)",
                                            )
                                            return@forEach
                                        }

                                        Timber.d("     PROCESS: Collection '${collectionDto.title}' (ratingKey=${collectionDto.ratingKey})")

                                        val collectionEntity =
                                            CollectionEntity(
                                                id = collectionDto.ratingKey,
                                                serverId = server.clientIdentifier,
                                                title = collectionDto.title,
                                                summary = collectionDto.summary,
                                                thumbUrl = collectionDto.thumb,
                                                lastSync = now,
                                            )

                                        val baseUrl = connectionManager.findBestConnection(server) ?: return@forEach
                                        val itemsUrl = "$baseUrl/library/collections/${collectionDto.ratingKey}/children?X-Plex-Token=${server.accessToken}"
                                        val itemsResponse = api.getCollectionItems(itemsUrl)
                                        if (!itemsResponse.isSuccessful) {
                                            Timber.e(
                                                "       ERROR: Failed to fetch items for '${collectionDto.title}': code=${itemsResponse.code()}",
                                            )
                                            return@forEach
                                        }

                                        val items = itemsResponse.body()?.mediaContainer?.metadata ?: emptyList()
                                        Timber.d("       SUCCESS: '${collectionDto.title}' has ${items.size} items")

                                        val crossRefs =
                                            items.map { itemDto ->
                                                // FORCE DISTINCT FILTER & OFFSET to avoid Unique Constraint Violation
                                                // Constraint: (serverId, librarySectionId, filter, sortOrder, pageOffset) is UNIQUE
                                                // Previous Bug: pageOffset=0 for all items caused cascade deletion of library items!
                                                val mediaEntity =
                                                    mediaMapper.mapDtoToEntity(itemDto, server.clientIdentifier, libKey)
                                                        .copy(
                                                            filter = "collection_sync",
                                                            sortOrder = "default",
                                                            // Use ratingKey as offset to ensure uniqueness within this filter bucket
                                                            pageOffset = itemDto.ratingKey.toIntOrNull() ?: itemDto.ratingKey.hashCode(),
                                                        )
                                                // Log insertion attempt
                                                // Timber.v("       - Inserting Media: ${itemDto.title} (${itemDto.ratingKey})")
                                                mediaDao.insertMedia(mediaEntity)

                                                MediaCollectionCrossRef(
                                                    mediaRatingKey = itemDto.ratingKey,
                                                    collectionId = collectionEntity.id,
                                                    serverId = server.clientIdentifier,
                                                )
                                            }

                                        Timber.d("       → Inserting ${crossRefs.size} cross-references")
                                        collectionDao.upsertCollectionWithItems(collectionEntity, crossRefs)
                                        Timber.i("       DONE: Synced '${collectionDto.title}' (items=${crossRefs.size})")
                                    }
                                } else {
                                    Timber.v("  -> Skipping library '$libTitle' (type=$libType)")
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "CRITICAL: Error processing server ${server.name}")
                        }
                    }

                    Timber.d("Sync cleanup: Deleting collections older than ${now - 7 * 24 * 3600 * 1000}")
                    collectionDao.deleteOldCollections(now - 7 * 24 * 3600 * 1000)

                    updateNotification("Collection sync complete!")
                    Timber.i("=== Collection sync COMPLETED successfully ===")
                    androidx.work.ListenableWorker.Result.success()
                } catch (e: Exception) {
                    Timber.e("Worker failed: ${e.message}")
                    if (e is kotlinx.coroutines.CancellationException) {
                        // If cancelled (e.g. by user or system), don't retry locally if it was a system kill.
                        // But generally clean exit.
                        throw e
                    }
                    androidx.work.ListenableWorker.Result.retry()
                }
            }

        private fun updateNotification(text: String) {
            val notification =
                androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
                    .setContentTitle("PlexHubTV")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build()
            notificationManager.notify(2, notification) // ID 2 for CollectionSync to allow parallel notification with LibrarySync (ID 1)
        }

        override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel =
                    android.app.NotificationChannel(
                        channelId,
                        "Synchronisation des Collections",
                        android.app.NotificationManager.IMPORTANCE_LOW,
                    )
                notificationManager.createNotificationChannel(channel)
            }

            val notification =
                androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
                    .setContentTitle("PlexHubTV")
                    .setContentText("Synchronisation des collections en cours...")
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setOngoing(true)
                    .build()

            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                androidx.work.ForegroundInfo(2, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                androidx.work.ForegroundInfo(2, notification)
            }
        }
    }
