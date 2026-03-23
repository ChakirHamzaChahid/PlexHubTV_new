package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.IdBridgeDao
import com.chakir.plexhubtv.core.database.IdBridgeEntity
import com.chakir.plexhubtv.core.database.JellyfinServerDao
import com.chakir.plexhubtv.core.database.JellyfinServerEntity
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.JellyfinServer
import com.chakir.plexhubtv.core.model.SourcePrefix
import com.chakir.plexhubtv.core.datastore.SecurePreferencesManager
import com.chakir.plexhubtv.core.network.jellyfin.JellyfinApiService
import com.chakir.plexhubtv.core.network.jellyfin.JellyfinApiService.Companion.ITEM_FIELDS_SYNC
import com.chakir.plexhubtv.core.network.jellyfin.JellyfinAuthRequest
import com.chakir.plexhubtv.core.network.jellyfin.JellyfinConnectionTester
import com.chakir.plexhubtv.data.mapper.JellyfinMapper
import com.chakir.plexhubtv.domain.repository.JellyfinServerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinServerRepositoryImpl @Inject constructor(
    private val dao: JellyfinServerDao,
    private val mediaDao: MediaDao,
    private val idBridgeDao: IdBridgeDao,
    private val api: JellyfinApiService,
    private val connectionTester: JellyfinConnectionTester,
    private val securePrefs: SecurePreferencesManager,
    private val aggregationService: AggregationService,
    private val clientResolver: JellyfinClientResolver,
    private val jellyfinMapper: JellyfinMapper,
) : JellyfinServerRepository {

    override fun observeServers(): Flow<List<JellyfinServer>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getServers(): List<JellyfinServer> =
        dao.getAll().map { it.toDomain() }

    override suspend fun getServer(id: String): JellyfinServer? =
        dao.getById(id)?.toDomain()

    override suspend fun addServer(
        baseUrl: String,
        username: String,
        password: String,
    ): Result<JellyfinServer> {
        val cleanUrl = baseUrl.trimEnd('/')

        // Step 1: Test connection
        val publicInfo = connectionTester.test(cleanUrl)
            ?: return Result.failure(
                AppError.Network.ServerError("Cannot reach Jellyfin server at $cleanUrl")
            )

        val serverId = publicInfo.id
            ?: return Result.failure(
                AppError.Network.ServerError("Server did not return an ID")
            )

        // Step 2: Authenticate
        // Build minimal authorization header for auth endpoint (no token yet)
        val deviceName = android.os.Build.MODEL ?: "Android TV"
        val deviceId = android.os.Build.FINGERPRINT ?: "PlexHubTV"
        val authHeader = "MediaBrowser Client=\"PlexHubTV\", Device=\"$deviceName\", " +
            "DeviceId=\"$deviceId\", Version=\"1.0.0\""

        val authResponse = try {
            val response = api.authenticateByName(
                "$cleanUrl/Users/AuthenticateByName",
                authHeader,
                JellyfinAuthRequest(username, password),
            )
            if (!response.isSuccessful) {
                return Result.failure(
                    AppError.Auth.InvalidCredentials(
                        "Authentication failed: HTTP ${response.code()}"
                    )
                )
            }
            response.body() ?: return Result.failure(
                AppError.Auth.InvalidCredentials("Empty auth response")
            )
        } catch (e: Exception) {
            Timber.e(e, "Jellyfin auth failed for $cleanUrl")
            return Result.failure(
                AppError.Network.ServerError("Authentication error: ${e.message}")
            )
        }

        val token = authResponse.accessToken
            ?: return Result.failure(
                AppError.Auth.InvalidCredentials("No access token in response")
            )
        val user = authResponse.user
            ?: return Result.failure(
                AppError.Auth.InvalidCredentials("No user in response")
            )

        // Step 3: Persist token securely
        securePrefs.putSecret("jellyfin_token_$serverId", token)

        // Step 4: Persist server entity
        val entity = JellyfinServerEntity(
            id = serverId,
            name = publicInfo.serverName ?: "Jellyfin",
            baseUrl = cleanUrl,
            userId = user.id,
            userName = user.name ?: username,
            version = publicInfo.version ?: "",
        )
        dao.upsert(entity)

        val server = entity.toDomain()
        Timber.i("Jellyfin server added: ${server.name} (${server.id}) at $cleanUrl")
        return Result.success(server)
    }

    override suspend fun removeServer(id: String) {
        Timber.i("Removing Jellyfin server $id")

        // 1. Delete all media entities for this server
        val prefixedId = "${SourcePrefix.JELLYFIN}$id"
        mediaDao.deleteAllMediaByServerId(prefixedId)

        // 2. Delete token from secure storage
        securePrefs.removeSecret("jellyfin_token_$id")

        // 3. Delete server entity
        dao.delete(id)

        // 4. Rebuild unified view to remove ghost entries
        aggregationService.rebuildAll()

        Timber.i("Jellyfin server $id removed and unified view rebuilt")
    }

    override suspend fun updateLastSyncedAt(id: String, timestamp: Long) {
        dao.updateLastSyncedAt(id, timestamp)
    }

    // ========================================
    // Library Sync
    // ========================================

    override suspend fun syncLibrary(serverId: String): Result<Int> {
        val prefixedId = "${SourcePrefix.JELLYFIN}$serverId"
        Timber.w("JELLYFIN_TRACE [syncLibrary] START serverId=$serverId prefixedId=$prefixedId")

        val client = clientResolver.getClient(serverId)
        if (client == null) {
            Timber.e("JELLYFIN_TRACE [syncLibrary] ABORT: clientResolver.getClient($serverId) returned null")
            return Result.failure(
                AppError.Network.ServerError("Jellyfin server $serverId unavailable")
            )
        }
        Timber.w("JELLYFIN_TRACE [syncLibrary] client resolved: baseUrl=${client.baseUrl}")

        val baseUrl = client.baseUrl

        return runCatching {
            // 1. Get library views, filter to movie/tvshow collections
            val viewsResponse = client.getUserViews()
            Timber.w("JELLYFIN_TRACE [syncLibrary] getUserViews: code=${viewsResponse.code()} success=${viewsResponse.isSuccessful}")
            if (!viewsResponse.isSuccessful) {
                throw AppError.Network.ServerError(
                    "Failed to get library views: HTTP ${viewsResponse.code()}"
                )
            }
            val allViews = viewsResponse.body()?.items ?: emptyList()
            Timber.w("JELLYFIN_TRACE [syncLibrary] raw views: ${allViews.size} total, collectionTypes=${allViews.map { "${it.name}:${it.collectionType}" }}")

            val views = allViews.filter {
                it.collectionType in listOf("movies", "tvshows")
            }
            Timber.w("JELLYFIN_TRACE [syncLibrary] filtered views (movies/tvshows): ${views.size}")

            if (views.isEmpty()) {
                Timber.e("JELLYFIN_TRACE [syncLibrary] ABORT: No movie/tvshow libraries found!")
                return@runCatching 0
            }

            // 2. Preserve hidden state upfront (single query)
            val hiddenKeys = mediaDao.getHiddenRatingKeys(prefixedId).toSet()

            var totalSynced = 0
            val allFetchedKeys = mutableListOf<String>()

            for (view in views) {
                val viewId = view.id
                val viewName = view.name ?: "Library"
                val itemTypes = when (view.collectionType) {
                    "movies" -> "Movie"
                    "tvshows" -> "Series"
                    else -> continue
                }

                Timber.w("JELLYFIN_TRACE [syncLibrary] → Syncing view: $viewName (type=$itemTypes, viewId=$viewId)")

                var startIndex = 0
                var totalCount = Int.MAX_VALUE

                while (startIndex < totalCount) {
                    val response = client.getItems(
                        parentId = viewId,
                        includeItemTypes = itemTypes,
                        startIndex = startIndex,
                        limit = PAGE_SIZE,
                        recursive = true,
                        fields = ITEM_FIELDS_SYNC,
                    )

                    Timber.w("JELLYFIN_TRACE [syncLibrary] getItems response: code=${response.code()}")
                    val body = response.body() ?: run {
                        Timber.e("JELLYFIN_TRACE [syncLibrary] getItems body is NULL, breaking pagination")
                        break
                    }
                    totalCount = body.totalRecordCount ?: 0
                    val items = body.items.orEmpty()
                    Timber.w("JELLYFIN_TRACE [syncLibrary] page: startIndex=$startIndex, totalCount=$totalCount, itemsInPage=${items.size}")
                    if (items.isEmpty()) break

                    val ratingKeys = items.map { it.id }

                    // Fetch preserved state for this batch
                    val scrapedRatings = mediaDao.getScrapedRatings(ratingKeys, prefixedId)
                    val overriddenMap = mediaDao.getOverriddenMetadata(ratingKeys, prefixedId)
                        .associateBy { it.ratingKey }

                    // Map to entities with state preservation
                    val entities = items.mapIndexed { index, item ->
                        var entity = jellyfinMapper.mapDtoToEntity(
                            item = item,
                            serverId = prefixedId,
                            librarySectionId = viewId,
                        )

                        // CRITICAL: Set unique pageOffset to avoid unique index collision.
                        // The (serverId, librarySectionId, filter, sortOrder, pageOffset) index
                        // is UNIQUE — without this, all items default to pageOffset=0 and
                        // INSERT OR REPLACE silently deletes every previous row.
                        // Also pre-resolve image URLs so unified view has full URLs for display.
                        entity = entity.copy(
                            pageOffset = startIndex + index,
                            resolvedThumbUrl = resolveJellyfinUrl(entity.thumbUrl, baseUrl),
                            resolvedArtUrl = resolveJellyfinUrl(entity.artUrl, baseUrl),
                            resolvedBaseUrl = baseUrl,
                        )

                        // Preserve hidden state
                        if (entity.ratingKey in hiddenKeys) {
                            entity = entity.copy(
                                isHidden = true,
                                hiddenAt = System.currentTimeMillis(),
                            )
                        }

                        // Preserve scraped rating (highest priority for displayRating)
                        scrapedRatings[entity.ratingKey]?.let { scraped ->
                            entity = entity.copy(
                                scrapedRating = scraped,
                                displayRating = scraped,
                            )
                        }

                        // Preserve TMDB overrides
                        overriddenMap[entity.ratingKey]?.let { ov ->
                            entity = entity.copy(
                                overriddenSummary = ov.overriddenSummary,
                                overriddenThumbUrl = ov.overriddenThumbUrl,
                            )
                        }

                        entity
                    }

                    // TRACE: Log first entity details
                    if (startIndex == 0 && entities.isNotEmpty()) {
                        val first = entities.first()
                        Timber.w("JELLYFIN_TRACE [syncLibrary] first entity: ratingKey=${first.ratingKey}, serverId=${first.serverId}, type=${first.type}, title=${first.title}, groupKey=${first.groupKey}, filter=${first.filter}, sortOrder=${first.sortOrder}, thumbUrl=${first.thumbUrl?.take(80)}, isHidden=${first.isHidden}")
                    }

                    allFetchedKeys.addAll(ratingKeys)
                    mediaDao.upsertMedia(entities)
                    Timber.w("JELLYFIN_TRACE [syncLibrary] upserted ${entities.size} entities")

                    // Populate id_bridge for cross-server unification (Plex ↔ Jellyfin merge)
                    val bridgeEntries = entities.mapNotNull { entity ->
                        val imdb = entity.imdbId?.takeIf { it.isNotBlank() }
                        val tmdb = entity.tmdbId?.takeIf { it.isNotBlank() }
                        if (imdb != null && tmdb != null) IdBridgeEntity(imdb, tmdb) else null
                    }
                    if (bridgeEntries.isNotEmpty()) idBridgeDao.upsertAll(bridgeEntries)

                    totalSynced += entities.size
                    startIndex += items.size
                }
                Timber.w("JELLYFIN_TRACE [syncLibrary] view $viewName complete: synced so far = $totalSynced")
            }

            // 3. Clean up stale items (in DB but no longer on server)
            val existingMovieKeys = mediaDao.getRatingKeysByServerAndType(prefixedId, "movie")
            val existingShowKeys = mediaDao.getRatingKeysByServerAndType(prefixedId, "show")
            val allExistingKeys = existingMovieKeys + existingShowKeys
            val fetchedSet = allFetchedKeys.toSet()
            val staleKeys = allExistingKeys.filter { it !in fetchedSet }
            Timber.w("JELLYFIN_TRACE [syncLibrary] stale cleanup: existing=${allExistingKeys.size}, fetched=${fetchedSet.size}, stale=${staleKeys.size}")
            if (staleKeys.isNotEmpty()) {
                staleKeys.chunked(500).forEach { chunk ->
                    mediaDao.deleteMediaByKeys(prefixedId, chunk)
                }
            }

            // 4. Update group keys for cross-server aggregation
            if (allFetchedKeys.isNotEmpty()) {
                Timber.w("JELLYFIN_TRACE [syncLibrary] updating groupKeys for ${allFetchedKeys.size} items")
                allFetchedKeys.chunked(500).forEach { chunk ->
                    mediaDao.updateGroupKeys(prefixedId, chunk)
                }
            }

            // 5. Update last synced timestamp
            dao.updateLastSyncedAt(serverId, System.currentTimeMillis())

            // TRACE: Verify items in DB after sync
            val dbMovieCount = mediaDao.getRawCountByServerAndType(prefixedId, "movie")
            val dbShowCount = mediaDao.getRawCountByServerAndType(prefixedId, "show")
            Timber.w("JELLYFIN_TRACE [syncLibrary] DONE for $serverId: synced=$totalSynced, DB movie count=$dbMovieCount, DB show count=$dbShowCount")

            totalSynced
        }.also { result ->
            if (result.isFailure) {
                Timber.e("JELLYFIN_TRACE [syncLibrary] FAILED: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
            }
        }
    }

    // --- Mapping ---

    private fun JellyfinServerEntity.toDomain() = JellyfinServer(
        id = id,
        name = name,
        baseUrl = baseUrl,
        userId = userId,
        userName = userName,
        version = version,
        isActive = isActive,
        lastSyncedAt = lastSyncedAt,
    )

    /**
     * Resolves a relative Jellyfin image path to a full URL with baseUrl.
     * Auth is NOT embedded in the URL — it's added at request time by
     * [JellyfinImageInterceptor] via `Authorization: MediaBrowser Token="..."` header.
     * This follows the Wholphin pattern and prevents stale tokens in cached URLs.
     */
    private fun resolveJellyfinUrl(url: String?, baseUrl: String): String? {
        if (url.isNullOrBlank()) return null
        if (url.startsWith("http")) return url
        return "$baseUrl$url"
    }

    companion object {
        /** Page size for Jellyfin sync. 100 to keep per-request latency under timeout
         *  on large libraries (10K+ items) while avoiding OOM on Mi Box S (2GB RAM). */
        private const val PAGE_SIZE = 100
    }
}
