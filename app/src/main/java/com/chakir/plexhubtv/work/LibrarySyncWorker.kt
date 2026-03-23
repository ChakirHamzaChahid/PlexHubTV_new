package com.chakir.plexhubtv.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.SyncRepository
import com.chakir.plexhubtv.core.database.LibrarySectionDao
import com.chakir.plexhubtv.core.di.IoDispatcher
import com.chakir.plexhubtv.core.model.isRetryable
import com.chakir.plexhubtv.core.model.toAppError
import com.chakir.plexhubtv.feature.loading.LibraryStatus
import com.chakir.plexhubtv.feature.loading.ServerStatus
import com.chakir.plexhubtv.feature.loading.SyncLibraryState
import com.chakir.plexhubtv.feature.loading.SyncServerState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Worker de synchronisation en arrière-plan des bibliothèques Plex.
 *
 * Fonctionnalités :
 * - Récupère toutes les bibliothèques de tous les serveurs connectés.
 * - Stocke les métadonnées dans la base de données locale (Room).
 * - Synchronise également la Watchlist Plex (Cloud) vers les Favoris locaux.
 * - S'exécute en mode Foreground Service pour éviter les limitations Android (10 min).
 * - Notifie l'utilisateur de la progression via des notifications.
 * - Expose un état granulaire par serveur/bibliothèque via WorkManager Data.
 *
 * Planification :
 * - Synchronisation initiale : Au premier démarrage de l'app.
 * - Synchronisation périodique : Toutes les 6 heures (configuré dans PlexHubApplication).
 */
@HiltWorker
class LibrarySyncWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val authRepository: AuthRepository,
        private val syncRepository: SyncRepository,
        private val settingsDataStore: com.chakir.plexhubtv.core.datastore.SettingsDataStore,
        private val syncWatchlistUseCase: com.chakir.plexhubtv.domain.usecase.SyncWatchlistUseCase,
        private val syncXtreamLibraryUseCase: com.chakir.plexhubtv.domain.usecase.SyncXtreamLibraryUseCase,
        private val syncJellyfinLibraryUseCase: com.chakir.plexhubtv.domain.usecase.SyncJellyfinLibraryUseCase,
        private val xtreamAccountRepository: com.chakir.plexhubtv.domain.repository.XtreamAccountRepository,
        private val backendRepository: com.chakir.plexhubtv.domain.repository.BackendRepository,
        private val librarySectionDao: LibrarySectionDao,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : CoroutineWorker(appContext, workerParams) {
        private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        private val channelId = "library_sync"

        override suspend fun doWork(): Result =
            withContext(ioDispatcher) {
                Timber.d("→ doWork() STARTED")

                try {
                    // 1. Wait for valid token and client ID (Avoid race condition with checkAuthentication)
                    Timber.d("→ Waiting for authentication...")
                    val token =
                        withTimeoutOrNull(15000) { // Wait up to 15s
                            settingsDataStore.plexToken.first { !it.isNullOrBlank() }
                        }
                    val clientId =
                        withTimeoutOrNull(5000) {
                            settingsDataStore.clientId.first { !it.isNullOrBlank() }
                        }

                    if (token == null || clientId == null) {
                        Timber.w("✗ Authentication timeout - Token: ${token != null}, ClientId: ${clientId != null}")
                        return@withContext Result.retry()
                    }

                    Timber.d("✓ Authenticated, proceeding with sync")

                    // Promote to Foreground to bypass 10-min limit
                    try {
                        setForeground(getForegroundInfo())
                        Timber.d("✓ Foreground service set")
                    } catch (e: Exception) {
                        Timber.e(e, "✗ Failed to set foreground: ${e.message}")
                    }

                    // Set up progress tracking variables
                    var lastNotificationTime = 0L
                    var completedLibraries = 0
                    var totalLibraries = 0

                    // Phase: discovering
                    setProgressAsync(workDataOf(
                        "progress" to 0f,
                        "message" to "Discovering servers...",
                        "phase" to "discovering",
                    ))

                    Timber.d("→ Fetching servers...")
                    val serversResult = authRepository.getServers()
                    val servers = serversResult.getOrNull() ?: emptyList()

                    val serverNames = servers.joinToString { it.name }
                    Timber.d("→ Syncing ${servers.size} Plex server(s): [$serverNames]")

                    // Initialize per-server state tracking
                    val selectedIds = settingsDataStore.selectedLibraryIds.first()
                    val serverStates = Array(servers.size) { i ->
                        val server = servers[i]
                        // Pre-fill libraries from cached Room data (local read, no network)
                        val cachedLibs = try {
                            librarySectionDao.getLibrarySections(server.clientIdentifier).first()
                                .filter { it.type == "movie" || it.type == "show" }
                                .filter { selectedIds.contains("${server.clientIdentifier}:${it.libraryKey}") }
                                .map { SyncLibraryState(key = it.libraryKey, name = it.title) }
                        } catch (e: Exception) {
                            Timber.w(e, "LibrarySync: Failed to load cached library sections for ${server.name}")
                            emptyList()
                        }
                        SyncServerState(
                            serverId = server.clientIdentifier,
                            serverName = server.name,
                            status = ServerStatus.Pending,
                            libraries = cachedLibs,
                        )
                    }
                    totalLibraries = serverStates.sumOf { it.libraries.size }
                        .coerceAtLeast(servers.size * 2) // Fallback estimate
                    var currentServerIdx = -1

                    // Set up enriched progress callback
                    syncRepository.onProgressUpdate = { current, total, libraryName ->
                        val now = System.currentTimeMillis()
                        // Throttle: Update only if 1 second has passed OR if finished
                        if (now - lastNotificationTime >= 1000 || current == total) {
                            lastNotificationTime = now
                            try {
                                updateNotification("Syncing $libraryName: $current / $total")
                            } catch (e: Exception) {
                                Timber.e("Failed to update notification: ${e.message}")
                            }

                            // Track per-library completion
                            if (current == total && total > 0) {
                                completedLibraries++
                            }

                            // Update library state in serverStates
                            val idx = currentServerIdx
                            if (idx >= 0) {
                                val serverState = serverStates[idx]
                                val libs = serverState.libraries.toMutableList()
                                val libIdx = libs.indexOfFirst { it.name == libraryName }
                                if (libIdx >= 0) {
                                    libs[libIdx] = libs[libIdx].copy(
                                        status = if (current >= total && total > 0)
                                            LibraryStatus.Success else LibraryStatus.Running,
                                        itemsSynced = current,
                                        itemsTotal = total,
                                    )
                                } else {
                                    // Library wasn't in the pre-filled list (cache miss) — add dynamically
                                    libs.add(SyncLibraryState(
                                        key = libraryName, // Fallback: use name as key
                                        name = libraryName,
                                        status = if (current >= total && total > 0)
                                            LibraryStatus.Success else LibraryStatus.Running,
                                        itemsSynced = current,
                                        itemsTotal = total,
                                    ))
                                }
                                serverStates[idx] = serverState.copy(libraries = libs)
                            }

                            // Global progress: weight library sync at 80% of total, remaining 20% for extras
                            val libraryFraction = if (totalLibraries > 0) {
                                val completedFraction = completedLibraries.toFloat() / totalLibraries
                                val currentLibFraction = if (total > 0) current.toFloat() / total else 0f
                                val inProgressFraction = currentLibFraction / totalLibraries
                                completedFraction + inProgressFraction
                            } else {
                                if (total > 0) current.toFloat() / total else 0f
                            }
                            val globalProgress = libraryFraction * 80f // 0-80% range

                            // Report enriched progress to UI
                            emitSyncProgress(
                                serverStates, currentServerIdx, globalProgress,
                                "library_sync", libraryName, completedLibraries, totalLibraries,
                            )
                        }
                    }

                    var failureCount = 0
                    servers.forEachIndexed { index, server ->
                        currentServerIdx = index
                        serverStates[index] = serverStates[index].copy(status = ServerStatus.Running)
                        emitSyncProgress(
                            serverStates, index, 0f,
                            "library_sync", "", completedLibraries, totalLibraries,
                        )

                        try {
                            Timber.d("→ [${server.name}] Starting sync (relay=${server.relay}, candidates=${server.connectionCandidates.size}, urls=${server.connectionCandidates.map { it.uri }})")
                            updateNotification("Syncing ${server.name}...")
                            val syncResult = syncRepository.syncServer(server)
                            if (syncResult.isFailure) {
                                Timber.w("✗ [${server.name}] Sync failed: ${syncResult.exceptionOrNull()?.message}")
                                val errorMsg = syncResult.exceptionOrNull()?.message ?: "Unknown error"
                                serverStates[index] = serverStates[index].copy(
                                    status = ServerStatus.Error,
                                    errorMessage = errorMsg.take(80),
                                )
                                failureCount++
                            } else {
                                Timber.d("✓ [${server.name}] Sync complete")
                                // Mark remaining Pending/Running libraries as Success
                                val libs = serverStates[index].libraries.map { lib ->
                                    if (lib.status == LibraryStatus.Running || lib.status == LibraryStatus.Pending)
                                        lib.copy(status = LibraryStatus.Success)
                                    else lib
                                }
                                val hasErrors = libs.any { it.status == LibraryStatus.Error }
                                serverStates[index] = serverStates[index].copy(
                                    libraries = libs,
                                    status = if (hasErrors) ServerStatus.PartialSuccess else ServerStatus.Success,
                                )
                            }
                        } catch (e: Exception) {
                            Timber.e("✗ [${server.name}] Exception: ${e.javaClass.simpleName} - ${e.message}")
                            serverStates[index] = serverStates[index].copy(
                                status = ServerStatus.Error,
                                errorMessage = e.message?.take(80),
                            )
                            failureCount++
                        }

                        // Emit after each server completes
                        emitSyncProgress(
                            serverStates, index, 0f,
                            "library_sync", "", completedLibraries, totalLibraries,
                        )
                    }

                    // Phase: extras (Xtream, Backend, Watchlist)
                    try {
                        setProgressAsync(workDataOf(
                            "progress" to 80f,
                            "message" to "Syncing extras...",
                            "phase" to "extras",
                            "serverStates" to Json.encodeToString(serverStates.toList()),
                            "currentServerIdx" to currentServerIdx,
                        ))
                    } catch (e: Exception) {
                        Timber.w(e, "LibrarySync: Failed to report extras progress")
                    }

                    // SYNC XTREAM ACCOUNTS (VOD + Series)
                    try {
                        val xtreamAccounts = xtreamAccountRepository.observeAccounts().first()
                        if (xtreamAccounts.isNotEmpty()) {
                            val selectedCatIds = settingsDataStore.selectedXtreamCategoryIds.first()
                            Timber.d("→ Syncing ${xtreamAccounts.size} Xtream account(s), selectedCategories=${selectedCatIds.size}")
                            xtreamAccounts.forEach { account ->
                                try {
                                    updateNotification("Syncing Xtream: ${account.label}...")
                                    val result = syncXtreamLibraryUseCase(account.id, selectedCatIds)
                                    if (result.isFailure) {
                                        Timber.w("✗ [Xtream:${account.label}] Sync failed: ${result.exceptionOrNull()?.message}")
                                    } else {
                                        Timber.d("✓ [Xtream:${account.label}] Sync complete")
                                    }
                                } catch (e: Exception) {
                                    Timber.e("✗ [Xtream:${account.label}] Exception: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e("✗ Xtream sync failed: ${e.message}")
                    }

                    // SYNC FROM PLEXHUB BACKEND SERVERS (best effort — does not affect Result)
                    try {
                        val backends = backendRepository.observeServers().first()
                        backends.filter { it.isActive }.forEach { backend ->
                            try {
                                updateNotification("Syncing Backend: ${backend.label}...")
                                val result = backendRepository.syncMedia(backend.id)
                                if (result.isFailure) {
                                    Timber.w("✗ [Backend:${backend.label}] Sync failed: ${result.exceptionOrNull()?.message}")
                                } else {
                                    Timber.d("✓ [Backend:${backend.label}] Synced ${result.getOrDefault(0)} items")
                                }
                            } catch (e: Exception) {
                                Timber.e("✗ [Backend:${backend.label}] Exception: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e("✗ Backend sync failed: ${e.message}")
                    }

                    // SYNC JELLYFIN SERVERS (best effort — does not affect Result)
                    try {
                        Timber.w("JELLYFIN_TRACE [LibrarySyncWorker] → Starting Jellyfin sync...")
                        updateNotification("Syncing Jellyfin servers...")
                        val jellyfinResult = syncJellyfinLibraryUseCase()
                        if (jellyfinResult.isFailure) {
                            Timber.e("JELLYFIN_TRACE [LibrarySyncWorker] ✗ Jellyfin sync FAILED: ${jellyfinResult.exceptionOrNull()?.message}", jellyfinResult.exceptionOrNull())
                        } else {
                            Timber.w("JELLYFIN_TRACE [LibrarySyncWorker] ✓ Jellyfin sync complete: ${jellyfinResult.getOrDefault(0)} items synced")
                        }
                    } catch (e: Exception) {
                        Timber.e("JELLYFIN_TRACE [LibrarySyncWorker] ✗ Jellyfin sync EXCEPTION: ${e.message}", e)
                    }

                    // AUTOMATE WATCHLIST SYNC
                    try {
                        updateNotification("Syncing Watchlist...")
                        syncWatchlistUseCase()
                    } catch (e: Exception) {
                        Timber.e("✗ Watchlist sync failed: ${e.message}")
                    }

                    // Clear callback
                    syncRepository.onProgressUpdate = null

                    // Phase: finalizing
                    try {
                        setProgressAsync(workDataOf(
                            "progress" to 95f,
                            "message" to "Finalizing...",
                            "phase" to "finalizing",
                            "serverStates" to Json.encodeToString(serverStates.toList()),
                            "currentServerIdx" to currentServerIdx,
                        ))
                    } catch (e: Exception) {
                        Timber.w(e, "LibrarySync: Failed to report finalizing progress")
                    }

                    if (failureCount == servers.size && servers.isNotEmpty()) {
                        Timber.w("✗ All ${servers.size} servers failed — scheduling retry: [$serverNames]")
                        settingsDataStore.saveFirstSyncComplete(true)
                        Result.retry()
                    } else {
                        // MARK SYNC AS COMPLETE
                        settingsDataStore.saveLastSyncTime(System.currentTimeMillis())
                        settingsDataStore.saveFirstSyncComplete(true)
                        Timber.i("✓ Sync complete! ${servers.size - failureCount}/${servers.size} servers OK [$serverNames]")

                        // TRIGGER POST-SYNC CHAIN: CollectionSync → UnifiedRebuildWorker
                        try {
                            Timber.d("→ Triggering post-sync chain: CollectionSync → UnifiedRebuild")
                            val collectionSyncRequest =
                                androidx.work.OneTimeWorkRequestBuilder<CollectionSyncWorker>()
                                    .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                                    .build()
                            val unifiedRebuildRequest =
                                androidx.work.OneTimeWorkRequestBuilder<UnifiedRebuildWorker>()
                                    .build()

                            androidx.work.WorkManager.getInstance(applicationContext)
                                .beginUniqueWork(
                                    "PostSyncChain",
                                    androidx.work.ExistingWorkPolicy.REPLACE,
                                    collectionSyncRequest,
                                )
                                .then(unifiedRebuildRequest)
                                .enqueue()
                            Timber.i("✓ Post-sync chain ENQUEUED: CollectionSync → UnifiedRebuild")
                        } catch (e: Exception) {
                            Timber.e("Failed to trigger post-sync chain: ${e.message}")
                        }

                        Result.success()
                    }
                } catch (e: Exception) {
                    Timber.e("═══════════════════════════════")
                    Timber.e("SYNC FAILED - Exception Details:")
                    Timber.e("Type: ${e.javaClass.name}")
                    Timber.e("Message: ${e.message}")
                    Timber.e(e, "Stack trace:")
                    Timber.e("═══════════════════════════════")

                    syncRepository.onProgressUpdate = null

                    // Mark as complete to avoid blocking the app
                    try {
                        settingsDataStore.saveFirstSyncComplete(true)
                        Timber.w("Marked sync as complete despite failure to unblock app")
                    } catch (ex: Exception) {
                        Timber.e("Failed to mark sync complete: ${ex.message}")
                    }

                    val appError = e.toAppError()
                    if (appError.isRetryable()) Result.retry() else Result.failure()
                }
            }

        private fun emitSyncProgress(
            states: Array<SyncServerState>,
            currentIdx: Int,
            progress: Float,
            phase: String,
            libraryName: String,
            completedLibs: Int,
            totalLibs: Int,
        ) {
            try {
                // Compute global progress from server states if not provided
                val effectiveProgress = if (progress > 0f) progress else {
                    if (states.isEmpty()) 0f
                    else states.sumOf { it.progress.toDouble() }.toFloat() / states.size * 80f
                }

                val currentServer = states.getOrNull(currentIdx)
                val currentLib = currentServer?.libraries?.firstOrNull { it.status == LibraryStatus.Running }
                val message = when {
                    currentServer != null && currentLib != null ->
                        "${currentServer.serverName} - ${currentLib.name} (${currentLib.itemsSynced}/${currentLib.itemsTotal})"
                    currentServer != null -> "Syncing ${currentServer.serverName}..."
                    else -> "Syncing..."
                }

                setProgressAsync(workDataOf(
                    "progress" to effectiveProgress,
                    "phase" to phase,
                    "message" to message,
                    "libraryName" to libraryName,
                    "serverStates" to Json.encodeToString(states.toList()),
                    "currentServerIdx" to currentIdx,
                    "completedLibs" to completedLibs,
                    "totalLibs" to totalLibs,
                ))
            } catch (e: Exception) {
                Timber.e("Failed to emit sync progress: ${e.message}")
            }
        }

        private fun updateNotification(text: String) {
            val notification =
                androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
                    .setContentTitle(applicationContext.getString(R.string.sync_notification_title))
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setOngoing(true)
                    .setProgress(0, 0, true) // Indeterminate progress
                    .build()
            notificationManager.notify(1, notification)
        }

        override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel =
                    android.app.NotificationChannel(
                        channelId,
                        applicationContext.getString(R.string.sync_library_channel_name),
                        android.app.NotificationManager.IMPORTANCE_LOW,
                    )
                notificationManager.createNotificationChannel(channel)
            }

            val notification =
                androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
                    .setContentTitle(applicationContext.getString(R.string.sync_notification_title))
                    .setContentText(applicationContext.getString(R.string.sync_library_in_progress))
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setOngoing(true)
                    .build()

            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                androidx.work.ForegroundInfo(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                androidx.work.ForegroundInfo(1, notification)
            }
        }
    }
