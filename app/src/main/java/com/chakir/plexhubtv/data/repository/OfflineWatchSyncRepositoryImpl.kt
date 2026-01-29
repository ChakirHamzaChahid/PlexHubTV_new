package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.OfflineWatchProgressDao
import com.chakir.plexhubtv.core.database.OfflineWatchProgressEntity
import com.chakir.plexhubtv.core.database.ApiCacheDao
import com.chakir.plexhubtv.core.database.DownloadDao
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.core.network.PlexClient
import com.chakir.plexhubtv.domain.model.Server
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.OfflineWatchSyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineWatchSyncRepositoryImpl @Inject constructor(
    private val offlineWatchDao: OfflineWatchProgressDao,
    private val apiCacheDao: ApiCacheDao,
    private val downloadDao: DownloadDao,
    private val authRepository: AuthRepository,
    private val connectionManager: ConnectionManager,
    private val apiService: PlexApiService
) : OfflineWatchSyncRepository {

    private var lastSyncTime: Long? = null

    companion object {
        private const val MIN_SYNC_INTERVAL_MS = 2 * 60 * 1000L // 2 minutes
        private const val MAX_SYNC_ATTEMPTS = 5
    }

    override val MAX_SYNC_ATTEMPTS = OfflineWatchSyncRepositoryImpl.MAX_SYNC_ATTEMPTS

    override fun isWatchedByProgress(viewOffset: Long, duration: Long): Boolean {
        if (duration <= 0) return false
        return (viewOffset.toFloat() / duration.toFloat()) > 0.9f
    }

    override suspend fun queueProgressUpdate(
        serverId: String,
        ratingKey: String,
        viewOffset: Long,
        duration: Long
    ) {
        val globalKey = "$serverId:$ratingKey"
        val shouldMarkWatched = isWatchedByProgress(viewOffset, duration)

        val entity = OfflineWatchProgressEntity(
            serverId = serverId,
            ratingKey = ratingKey,
            globalKey = globalKey,
            actionType = "progress",
            viewOffset = viewOffset,
            duration = duration,
            shouldMarkWatched = shouldMarkWatched,
            updatedAt = System.currentTimeMillis()
        )

        offlineWatchDao.upsertProgressAction(entity)
    }

    override suspend fun queueMarkWatched(serverId: String, ratingKey: String) {
        queueWatchStatusAction(serverId, ratingKey, "watched")
    }

    override suspend fun queueMarkUnwatched(serverId: String, ratingKey: String) {
        queueWatchStatusAction(serverId, ratingKey, "unwatched")
    }

    private suspend fun queueWatchStatusAction(
        serverId: String,
        ratingKey: String,
        actionType: String
    ) {
        val globalKey = "$serverId:$ratingKey"

        val entity = OfflineWatchProgressEntity(
            serverId = serverId,
            ratingKey = ratingKey,
            globalKey = globalKey,
            actionType = actionType,
            updatedAt = System.currentTimeMillis()
        )

        offlineWatchDao.insertWatchAction(entity)
    }

    override suspend fun getLocalWatchStatus(globalKey: String): Boolean? {
        val action = offlineWatchDao.getLatestWatchAction(globalKey) ?: return null

        return when (action.actionType) {
            "watched" -> true
            "unwatched" -> false
            "progress" -> action.shouldMarkWatched
            else -> null
        }
    }

    override suspend fun getLocalWatchStatusesBatched(globalKeys: Set<String>): Map<String, Boolean?> {
        if (globalKeys.isEmpty()) return emptyMap()

        val actions = offlineWatchDao.getLatestWatchActionsForKeys(globalKeys.toList())
            .associateBy { it.globalKey }
        val result = mutableMapOf<String, Boolean?>()

        for (key in globalKeys) {
            val action = actions[key]
            result[key] = when (action?.actionType) {
                "watched" -> true
                "unwatched" -> false
                "progress" -> action.shouldMarkWatched
                else -> null
            }
        }

        return result
    }

    override suspend fun getLocalViewOffset(globalKey: String): Long? {
        val action = offlineWatchDao.getLatestWatchAction(globalKey) ?: return null
        return if (action.actionType == "progress") action.viewOffset else null
    }

    override suspend fun getPendingSyncCount(): Int {
        return offlineWatchDao.getPendingSyncCount()
    }

    override fun observePendingSyncCount(): Flow<Int> {
        return offlineWatchDao.observePendingSyncCount()
    }

    override suspend fun syncPendingItems(): Result<Int> {
        return try {
            val pendingActions = offlineWatchDao.getPendingWatchActions()

            if (pendingActions.isEmpty()) {
                return Result.success(0)
            }

            // Group actions by server
            val actionsByServer = pendingActions.groupBy { it.serverId }
            var syncedCount = 0

            for ((serverId, actions) in actionsByServer) {
                // Check if server still exists
                val server = authRepository.getServers().getOrNull()
                    ?.find { it.clientIdentifier == serverId }

                if (server == null) {
                    // Server no longer exists, delete all actions for it
                    actions.forEach { offlineWatchDao.deleteWatchAction(it.id) }
                    continue
                }

                // Process each action for this server
                val client = getOnlineClient(server) ?: continue

                for (action in actions) {
                    // Check retry limit
                    if (action.syncAttempts >= MAX_SYNC_ATTEMPTS) {
                        offlineWatchDao.deleteWatchAction(action.id)
                        continue
                    }

                    try {
                        syncAction(client, action)
                        offlineWatchDao.deleteWatchAction(action.id)
                        syncedCount++
                    } catch (e: Exception) {
                        offlineWatchDao.updateSyncAttempt(
                            action.id,
                            e.message ?: "Unknown error"
                        )
                    }
                }
            }

            Result.success(syncedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun syncAction(client: PlexClient, action: OfflineWatchProgressEntity) {
        when (action.actionType) {
            "watched" -> client.scrobble(action.ratingKey)
            "unwatched" -> client.unscrobble(action.ratingKey)
            "progress" -> {
                // Update timeline
                if (action.viewOffset != null && action.duration != null) {
                    client.updateTimeline(
                        ratingKey = action.ratingKey,
                        state = "stopped",
                        timeMs = action.viewOffset,
                        durationMs = action.duration
                    )
                }
                // Also mark as watched if threshold exceeded
                if (action.shouldMarkWatched) {
                    client.scrobble(action.ratingKey)
                }
            }
        }
    }

    override suspend fun syncWatchStatesFromServer(): Result<Int> {
        return try {
            val downloadedItems = downloadDao.getAllDownloads().first()

            if (downloadedItems.isEmpty()) {
                return Result.success(0)
            }

            // Separate episodes from other items
            val episodesByServerAndSeason = mutableMapOf<String, MutableMap<String, MutableSet<String>>>()
            val nonEpisodeItems = mutableMapOf<String, MutableList<String>>()

            for (item in downloadedItems) {
                if (item.type == "episode" && !item.parentRatingKey.isNullOrBlank()) {
                    episodesByServerAndSeason
                        .getOrPut(item.serverId) { mutableMapOf() }
                        .getOrPut(item.parentRatingKey) { mutableSetOf() }
                        .add(item.ratingKey)
                } else {
                    nonEpisodeItems
                        .getOrPut(item.serverId) { mutableListOf() }
                        .add(item.ratingKey)
                }
            }

            var syncedCount = 0

            // Fetch episodes by season (batch)
            for ((serverId, seasonMap) in episodesByServerAndSeason) {
                val server = authRepository.getServers().getOrNull()
                    ?.find { it.clientIdentifier == serverId } ?: continue

                val client = getOnlineClient(server) ?: continue

                for ((seasonRatingKey, downloadedEpisodeKeys) in seasonMap) {
                    try {
                        val response = client.getChildren(seasonRatingKey)
                        if (response.isSuccessful) {
                            val episodes = response.body()?.mediaContainer?.metadata ?: emptyList()

                            for (episode in episodes) {
                                if (downloadedEpisodeKeys.contains(episode.ratingKey)) {
                                    // Cache the updated metadata
                                    apiCacheDao.insertCache(
                                        com.chakir.plexhubtv.core.database.ApiCacheEntity(
                                            cacheKey = "$serverId:/library/metadata/${episode.ratingKey}",
                                            data = com.google.gson.Gson().toJson(mapOf(
                                                "MediaContainer" to mapOf(
                                                    "Metadata" to listOf(episode)
                                                )
                                            ))
                                        )
                                    )
                                    syncedCount++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Continue with other seasons
                    }
                }
            }

            // Fetch non-episode items individually
            for ((serverId, ratingKeys) in nonEpisodeItems) {
                val server = authRepository.getServers().getOrNull()
                    ?.find { it.clientIdentifier == serverId } ?: continue

                val client = getOnlineClient(server) ?: continue

                for (ratingKey in ratingKeys) {
                    try {
                        val response = client.getMetadata(ratingKey, includeChildren = false)
                        if (response.isSuccessful) {
                            val metadata = response.body()?.mediaContainer?.metadata?.firstOrNull()

                            if (metadata != null) {
                                apiCacheDao.insertCache(
                                    com.chakir.plexhubtv.core.database.ApiCacheEntity(
                                        cacheKey = "$serverId:/library/metadata/$ratingKey",
                                        data = com.google.gson.Gson().toJson(mapOf(
                                            "MediaContainer" to mapOf(
                                                "Metadata" to listOf(metadata)
                                            )
                                        ))
                                    )
                                )
                                syncedCount++
                            }
                        }
                    } catch (e: Exception) {
                        // Continue with other items
                    }
                }
            }

            Result.success(syncedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun performBidirectionalSync(force: Boolean): Result<Unit> {
        return try {
            // Always push local changes
            syncPendingItems()

            // Only pull if not throttled or forced
            if (!force && lastSyncTime != null) {
                val elapsed = System.currentTimeMillis() - lastSyncTime!!
                if (elapsed < MIN_SYNC_INTERVAL_MS) {
                    return Result.success(Unit)
                }
            }

            // Pull latest states from server
            syncWatchStatesFromServer()
            lastSyncTime = System.currentTimeMillis()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearAll() {
        offlineWatchDao.clearAllWatchActions()
    }

    private suspend fun getOnlineClient(server: Server): PlexClient? {
        val baseUrl = connectionManager.findBestConnection(server) ?: return null
        return PlexClient(server, apiService, baseUrl)
    }
}
