package com.chakir.plexhubtv.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.map
import androidx.sqlite.db.SimpleSQLiteQuery
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.database.MediaUnifiedDao
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.LibrarySection
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.SourcePrefix
import com.chakir.plexhubtv.core.model.toAppError
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.core.network.PlexClient
import com.chakir.plexhubtv.core.util.MediaUrlResolver
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LibraryRepositoryImpl
    @Inject
    constructor(
        private val serverClientResolver: ServerClientResolver,
        private val authRepository: AuthRepository,
        private val connectionManager: ConnectionManager,
        private val api: PlexApiService,
        private val mapper: MediaMapper,
        private val mediaDao: MediaDao,
        private val mediaUnifiedDao: MediaUnifiedDao,
        private val database: com.chakir.plexhubtv.core.database.PlexDatabase,
        private val settingsRepository: com.chakir.plexhubtv.domain.repository.SettingsRepository,
        private val serverNameResolver: ServerNameResolver,
        private val mediaUrlResolver: MediaUrlResolver,
        private val jellyfinClientResolver: JellyfinClientResolver,
    ) : LibraryRepository {
        override suspend fun getLibraries(serverId: String): Result<List<LibrarySection>> {
            try {
                val client =
                    serverClientResolver.getClient(serverId) ?: run {
                        // Offline: Try DB
                        val cached = database.librarySectionDao().getLibrarySections(serverId).first()
                        if (cached.isNotEmpty()) {
                            return Result.success(
                                cached.map {
                                    LibrarySection(key = it.libraryKey, title = it.title, type = it.type)
                                },
                            )
                        }
                        return Result.failure(AppError.Network.NoConnection("Server offline and no cache"))
                    }

                val response = client.getSections()
                if (response.isSuccessful) {
                    val sections = response.body()?.mediaContainer?.directory ?: emptyList()
                    val domainSections =
                        sections.map {
                            LibrarySection(
                                key = it.key ?: "",
                                title = it.title ?: "Unknown",
                                type = it.type,
                            )
                        }

                    // Cache in DB
                    database.librarySectionDao().insertLibrarySections(
                        sections.map {
                            com.chakir.plexhubtv.core.database.LibrarySectionEntity(
                                id = "$serverId:${it.key}",
                                serverId = serverId,
                                libraryKey = it.key ?: "",
                                title = it.title ?: "Unknown",
                                type = it.type ?: "movie",
                            )
                        },
                    )

                    return Result.success(domainSections)
                }
                return Result.failure(AppError.Network.ServerError("Failed to fetch sections"))
            } catch (e: Exception) {
                return Result.failure(e.toAppError())
            }
        }

        override suspend fun getDistinctServerIds(): List<String> =
            mediaDao.getDistinctServerIds()

        @OptIn(ExperimentalPagingApi::class)
        override fun getLibraryContent(
            serverId: String,
            libraryKey: String,
            mediaType: com.chakir.plexhubtv.core.model.MediaType,
            filter: String?,
            sort: String?,
            isDescending: Boolean,
            genre: List<String>?,
            selectedServerId: String?,
            excludedServerIds: List<String>,
            initialKey: Int?,
            query: String?,
            maxAgeRating: Int?,
        ): Flow<androidx.paging.PagingData<MediaItem>> {
            return flow {
                var resolvedLibraryKey = libraryKey
                var resolvedServerId = serverId

                // Resolve "all" server
                if (resolvedServerId == "all") {
                    // Use a non-blocking/cached way if possible. getServers() hits DB.
                    val servers = authRepository.getServers(forceRefresh = false).getOrNull()
                    resolvedServerId = servers?.firstOrNull()?.clientIdentifier ?: "default"
                }

                // Resolve "default" library key by type
                if (resolvedLibraryKey == "default") {
                    val plexType =
                        when (mediaType) {
                            com.chakir.plexhubtv.core.model.MediaType.Movie -> "movie"
                            com.chakir.plexhubtv.core.model.MediaType.Show -> "show"
                            else -> "movie"
                        }

                    val cachedSection = database.librarySectionDao().getLibrarySectionByType(resolvedServerId, plexType)
                    if (cachedSection != null) {
                        resolvedLibraryKey = cachedSection.libraryKey
                    } else {
                        val result = getLibraries(resolvedServerId)
                        if (result.isSuccess) {
                            val section = result.getOrNull()?.find { it.type == plexType }
                            if (section != null) resolvedLibraryKey = section.key
                        }
                    }
                }

                val client = serverClientResolver.getClient(resolvedServerId)
                val normalizedFilter = filter?.lowercase() ?: "all"
                val baseSort =
                    when (sort) {
                        "Date Added" -> "addedAt"
                        "Title" -> "title"
                        "Year" -> "year"
                        "Rating" -> "rating"
                        else -> sort?.lowercase() ?: "default"
                    }

                val directionSuffix = if (isDescending) "desc" else "asc"
                val normalizedSort = if (baseSort == "default") "default" else "$baseSort:$directionSuffix"

                val dbServerId = if (selectedServerId == null || selectedServerId == "All") null else selectedServerId
                val dbQuery = if (query.isNullOrBlank()) null else query

                // Build Dynamic SQL via QueryBuilder
                val isUnified = serverId == "all"
                val plexTypeStr = if (mediaType == com.chakir.plexhubtv.core.model.MediaType.Movie) "movie" else "show"
                timber.log.Timber.w("JELLYFIN_TRACE [getLibraryContent] serverId=$serverId, resolvedServerId=$resolvedServerId, isUnified=$isUnified, type=$plexTypeStr, filter=$normalizedFilter, sort=$normalizedSort")

                val queryConfig = MediaLibraryQueryBuilder.QueryConfig(
                    isUnified = isUnified,
                    mediaTypeStr = plexTypeStr,
                    libraryKey = resolvedLibraryKey,
                    filter = normalizedFilter,
                    sortOrder = normalizedSort,
                    genre = genre,
                    selectedServerId = dbServerId,
                    excludedServerIds = excludedServerIds,
                    query = dbQuery,
                    baseSort = baseSort,
                    isDescending = isDescending,
                    maxAgeRating = maxAgeRating,
                )
                val allServers = authRepository.getServers(forceRefresh = false).getOrNull() ?: emptyList()
                // Use cached URLs (fast). When cache is empty (e.g. after network change),
                // prefer public IPs over local IPs to avoid stale local addresses.
                val clientMap =
                    allServers.associate { server ->
                        server.clientIdentifier to (
                            connectionManager.getCachedUrl(server.clientIdentifier)
                                ?: server.connectionCandidates
                                    .filter { !it.relay }
                                    .firstOrNull { !isPrivateIp(it.uri) }?.uri
                                ?: server.connectionCandidates.firstOrNull()?.uri
                        )
                    }
                val tokenMap = allServers.associate { it.clientIdentifier to it.accessToken }

                val serverNameMap = serverNameResolver.getServerNameMap()

                // Get preferred server for prioritization in multi-server results
                val defaultServerName = settingsRepository.defaultServer.first()
                val preferredServerIdForMapping = if (defaultServerName != "all") {
                    allServers.find { it.name == defaultServerName }?.clientIdentifier
                } else {
                    null
                }

                if (isUnified) {
                    // ═══ Solution C: Query pre-aggregated media_unified table ═══
                    val builtQuery = MediaLibraryQueryBuilder.buildMaterializedPagedQuery(queryConfig)
                    val rawQuery = builtQuery.toSimpleSQLiteQuery()

                    timber.log.Timber.w("JELLYFIN_TRACE [getLibraryContent] UNIFIED PATH SQL: ${builtQuery.sql.take(800)}")
                    timber.log.Timber.w("JELLYFIN_TRACE [getLibraryContent] UNIFIED PATH args: ${builtQuery.args.joinToString()}")

                    val pager = androidx.paging.Pager(
                        config = androidx.paging.PagingConfig(
                            pageSize = 50,
                            prefetchDistance = 15,
                            initialLoadSize = 100,
                            enablePlaceholders = true,
                            maxSize = 500,
                        ),
                        initialKey = initialKey,
                        pagingSourceFactory = { mediaUnifiedDao.getPagedUnified(rawQuery) },
                    )

                    var loggedFirstPage = false
                    emitAll(
                        pager.flow.map { pagingData ->
                            pagingData.map { entity ->
                                // TRACE: Log first few unified entities to verify data
                                if (!loggedFirstPage) {
                                    loggedFirstPage = true
                                    timber.log.Timber.w("JELLYFIN_TRACE [unified paging] first entity: groupKey=${entity.groupKey}, bestServerId=${entity.bestServerId}, bestRatingKey=${entity.bestRatingKey}, title=${entity.title}, type=${entity.type}, thumbUrl=${entity.thumbUrl?.take(60)}, resolvedThumbUrl=${entity.resolvedThumbUrl?.take(60)}")
                                }

                                val domain = mapper.mapUnifiedEntityToDomain(entity)
                                val baseUrl = clientMap[entity.bestServerId]
                                val token = tokenMap[entity.bestServerId]

                                val finalDomain = resolveRemoteSources(
                                    domain, entity.serverIds, serverNameMap, preferredServerIdForMapping
                                )

                                if (baseUrl != null) {
                                    mediaUrlResolver.resolveUrls(finalDomain, baseUrl, token).copy(
                                        baseUrl = baseUrl,
                                        accessToken = token,
                                    )
                                } else if (entity.bestServerId.startsWith(SourcePrefix.JELLYFIN)) {
                                    // Jellyfin: resolve relative URLs with api_key (not X-Plex-Token)
                                    val jfClient = jellyfinClientResolver.getClient(entity.bestServerId)
                                    if (jfClient != null) {
                                        timber.log.Timber.d("JELLYFIN_TRACE [unified paging] Jellyfin URL resolved for ${entity.title}: baseUrl=${jfClient.baseUrl}")
                                        resolveJellyfinUrls(finalDomain, jfClient.baseUrl, jfClient.accessToken)
                                    } else {
                                        timber.log.Timber.e("JELLYFIN_TRACE [unified paging] Jellyfin client NULL for bestServerId=${entity.bestServerId}")
                                        finalDomain
                                    }
                                } else {
                                    finalDomain
                                }
                            }
                        },
                    )
                } else {
                    // ═══ Existing: Query media table with GROUP BY ═══
                    val builtQuery = MediaLibraryQueryBuilder.buildPagedQuery(queryConfig)
                    val rawQuery = builtQuery.toSimpleSQLiteQuery()

                    timber.log.Timber.d("LIBRARY_SORT [baseSort=$baseSort, isDesc=$isDescending] SQL Query: ${builtQuery.sql.take(500)}")

                    val factory = { mediaDao.getMediaPagedRaw(rawQuery) }

                    val remoteMediator =
                        if (client != null) {
                            com.chakir.plexhubtv.data.paging.MediaRemoteMediator(
                                libraryKey = resolvedLibraryKey,
                                filter = normalizedFilter,
                                sortOrder = normalizedSort,
                                api = api,
                                database = database,
                                serverId = resolvedServerId,
                                serverUrl = client.baseUrl,
                                token = client.server.accessToken ?: "",
                                mapper = mapper,
                                isOwned = client.server.isOwned,
                            )
                        } else {
                            null
                        }

                    val pager =
                        androidx.paging.Pager(
                            config =
                                androidx.paging.PagingConfig(
                                    pageSize = 50,
                                    prefetchDistance = 15,
                                    initialLoadSize = 100,
                                    enablePlaceholders = true,
                                    maxSize = 500,
                                ),
                            initialKey = initialKey,
                            remoteMediator = remoteMediator,
                            pagingSourceFactory = factory,
                        )

                    emitAll(
                        pager.flow.map { pagingData ->
                            pagingData.map { entity ->
                                val domain = mapper.mapEntityToDomain(entity)
                                val baseUrl = clientMap[entity.serverId]
                                val token = tokenMap[entity.serverId]

                                val finalDomain = resolveRemoteSources(
                                    domain, entity.serverIds, serverNameMap, preferredServerIdForMapping
                                )

                                if (baseUrl != null) {
                                    mediaUrlResolver.resolveUrls(finalDomain, baseUrl, token).copy(
                                        baseUrl = baseUrl,
                                        accessToken = token,
                                    )
                                } else if (entity.serverId.startsWith(SourcePrefix.JELLYFIN)) {
                                    val jfClient = jellyfinClientResolver.getClient(entity.serverId)
                                    if (jfClient != null) {
                                        resolveJellyfinUrls(finalDomain, jfClient.baseUrl, jfClient.accessToken)
                                    } else finalDomain
                                } else {
                                    finalDomain
                                }
                            }
                        },
                    )
                }
            }
        }

        /**
         * Parses serverIds "s1=rk1,s2=rk2" → MediaSource list and attaches to domain item.
         * Shared by both unified and non-unified paging paths.
         */
        private fun resolveRemoteSources(
            domain: MediaItem,
            serverIdsStr: String?,
            serverNameMap: Map<String, String>,
            preferredServerIdForMapping: String?,
        ): MediaItem {
            if (serverIdsStr == null || !serverIdsStr.contains("=")) return domain

            val pairs = serverIdsStr.split(",").map { it.split("=") }.filter { it.size == 2 }
            var sIds = pairs.map { it[0] }
            var rKeys = pairs.map { it[1] }

            // Prioritize default server
            if (sIds.size == rKeys.size && sIds.size > 1 && preferredServerIdForMapping != null) {
                val preferredIndex = sIds.indexOf(preferredServerIdForMapping)
                if (preferredIndex > 0) {
                    sIds = listOf(sIds[preferredIndex]) + sIds.filterIndexed { idx, _ -> idx != preferredIndex }
                    rKeys = listOf(rKeys[preferredIndex]) + rKeys.filterIndexed { idx, _ -> idx != preferredIndex }
                }
            }

            if (sIds.size != rKeys.size) return domain

            val sources = sIds.zip(rKeys)
                .distinctBy { it.first }
                .map { (sId, rKey) ->
                    com.chakir.plexhubtv.core.model.MediaSource(
                        serverId = sId,
                        ratingKey = rKey,
                        serverName = serverNameMap[sId] ?: sId.take(12),
                        resolution = null,
                        thumbUrl = null,
                        artUrl = null,
                    )
                }
            return domain.copy(remoteSources = sources)
        }

        /** Resolves relative Jellyfin image URLs with baseUrl + api_key query param. */
        private fun resolveJellyfinUrls(item: MediaItem, baseUrl: String, token: String): MediaItem =
            item.copy(
                thumbUrl = resolveJellyfinUrl(item.thumbUrl, baseUrl, token),
                artUrl = resolveJellyfinUrl(item.artUrl, baseUrl, token),
                parentThumb = resolveJellyfinUrl(item.parentThumb, baseUrl, token),
                grandparentThumb = resolveJellyfinUrl(item.grandparentThumb, baseUrl, token),
                baseUrl = baseUrl,
                accessToken = token,
            )

        private fun resolveJellyfinUrl(url: String?, baseUrl: String, token: String): String? {
            if (url.isNullOrBlank()) return null
            if (url.startsWith("http")) return url
            val separator = if (url.contains("?")) "&" else "?"
            return "$baseUrl$url${separator}api_key=$token"
        }

        override suspend fun getFilteredCount(
            type: com.chakir.plexhubtv.core.model.MediaType,
            filter: String?,
            sort: String?,
            isDescending: Boolean,
            genre: List<String>?,
            serverId: String?,
            selectedServerId: String?,
            excludedServerIds: List<String>,
            libraryKey: String?,
            query: String?,
        ): Int {
            var resolvedLibraryKey = libraryKey ?: "default"
            var resolvedServerId = serverId ?: "all"

            if (resolvedServerId == "all") {
                val servers = authRepository.getServers(forceRefresh = false).getOrNull()
                resolvedServerId = servers?.firstOrNull()?.clientIdentifier ?: "default"
            }

            val baseSort = when (sort) {
                "Date Added" -> "addedAt"
                "Title" -> "title"
                "Year" -> "year"
                "Rating" -> "rating"
                else -> sort?.lowercase() ?: "default"
            }
            val directionSuffix = if (isDescending) "desc" else "asc"
            val normalizedSort = if (baseSort == "default") "default" else "$baseSort:$directionSuffix"

            val normalizedFilter = filter?.lowercase() ?: "all"
            val dbServerId = if (selectedServerId == null || selectedServerId.equals("all", ignoreCase = true)) null else selectedServerId
            val dbQuery = if (query.isNullOrBlank()) null else query

            val isUnified = serverId == "all" || serverId == null
            val typeStr = if (type == com.chakir.plexhubtv.core.model.MediaType.Movie) "movie" else "show"

            if (!isUnified && resolvedLibraryKey == "default") {
                val cachedSection = database.librarySectionDao().getLibrarySectionByType(resolvedServerId, typeStr)
                if (cachedSection != null) {
                    resolvedLibraryKey = cachedSection.libraryKey
                } else {
                    val result = getLibraries(resolvedServerId)
                    val section = result.getOrNull()?.find { it.type == typeStr }
                    if (section != null) resolvedLibraryKey = section.key
                }
            }

            val queryConfig = MediaLibraryQueryBuilder.QueryConfig(
                isUnified = isUnified,
                mediaTypeStr = typeStr,
                libraryKey = resolvedLibraryKey,
                filter = normalizedFilter,
                sortOrder = normalizedSort,
                genre = genre,
                selectedServerId = dbServerId,
                excludedServerIds = excludedServerIds,
                query = dbQuery,
            )

            return if (isUnified) {
                val rawQuery = MediaLibraryQueryBuilder.buildMaterializedCountQuery(queryConfig).toSimpleSQLiteQuery()
                mediaUnifiedDao.getCountUnified(rawQuery)
            } else {
                val rawQuery = MediaLibraryQueryBuilder.buildCountQuery(queryConfig).toSimpleSQLiteQuery()
                mediaDao.getMediaCountRaw(rawQuery)
            }
        }

        override suspend fun getIndexOfFirstItem(
            type: com.chakir.plexhubtv.core.model.MediaType,
            letter: String,
            filter: String?,
            sort: String?,
            genre: List<String>?,
            serverId: String?,
            selectedServerId: String?,
            excludedServerIds: List<String>,
            libraryKey: String?,
            query: String?,
        ): Int {
            var resolvedLibraryKey = libraryKey ?: "default"
            var resolvedServerId = serverId ?: "all"

            if (resolvedServerId == "all") {
                val servers = authRepository.getServers(forceRefresh = false).getOrNull()
                resolvedServerId = servers?.firstOrNull()?.clientIdentifier ?: "default"
            }

            // Normalize sort (MUST match getLibraryContent logic)
            val normalizedSort =
                when (sort) {
                    "Date Added" -> "addedAt:desc"
                    "Title" -> "title:asc"
                    "Year" -> "year:desc"
                    "Rating" -> "rating:desc"
                    else -> sort?.lowercase() ?: "default"
                }

            val normalizedFilter = filter?.lowercase() ?: "all"
            val dbServerId = if (selectedServerId == null || selectedServerId.equals("all", ignoreCase = true)) null else selectedServerId
            val dbQuery = if (query.isNullOrBlank()) null else query

            val isUnified = serverId == "all" || serverId == null
            val typeStr = if (type == com.chakir.plexhubtv.core.model.MediaType.Movie) "movie" else "show"

            if (!isUnified && resolvedLibraryKey == "default") {
                val cachedSection = database.librarySectionDao().getLibrarySectionByType(resolvedServerId, typeStr)
                if (cachedSection != null) {
                    resolvedLibraryKey = cachedSection.libraryKey
                } else {
                    val result = getLibraries(resolvedServerId)
                    val section = result.getOrNull()?.find { it.type == typeStr }
                    if (section != null) resolvedLibraryKey = section.key
                }
            }

            val queryConfig = MediaLibraryQueryBuilder.QueryConfig(
                isUnified = isUnified,
                mediaTypeStr = typeStr,
                libraryKey = resolvedLibraryKey,
                filter = normalizedFilter,
                sortOrder = normalizedSort,
                genre = genre,
                selectedServerId = dbServerId,
                excludedServerIds = excludedServerIds,
                query = dbQuery,
            )

            return if (isUnified) {
                val rawQuery = MediaLibraryQueryBuilder.buildMaterializedIndexQuery(queryConfig, letter).toSimpleSQLiteQuery()
                mediaUnifiedDao.getCountUnified(rawQuery)
            } else {
                val rawQuery = MediaLibraryQueryBuilder.buildIndexQuery(queryConfig, letter).toSimpleSQLiteQuery()
                mediaDao.getMediaCountRaw(rawQuery)
            }
        }

    }

/**
 * Detects RFC1918 private IP addresses in a URL's hostname.
 * Used to deprioritize local IPs when the cache is empty (e.g. after network change),
 * since local addresses are unlikely to work from a different network.
 */
private fun isPrivateIp(url: String): Boolean {
    val host = try {
        java.net.URI(url).host ?: return false
    } catch (_: Exception) {
        return false
    }
    // Plex uses dashed-IP format: 192-168-1-2.xxx.plex.direct
    val normalizedHost = host.replace("-", ".")
    return normalizedHost.startsWith("10.") ||
        normalizedHost.startsWith("192.168.") ||
        normalizedHost.startsWith("172.16.") ||
        normalizedHost.startsWith("172.17.") ||
        normalizedHost.startsWith("172.18.") ||
        normalizedHost.startsWith("172.19.") ||
        normalizedHost.startsWith("172.20.") ||
        normalizedHost.startsWith("172.21.") ||
        normalizedHost.startsWith("172.22.") ||
        normalizedHost.startsWith("172.23.") ||
        normalizedHost.startsWith("172.24.") ||
        normalizedHost.startsWith("172.25.") ||
        normalizedHost.startsWith("172.26.") ||
        normalizedHost.startsWith("172.27.") ||
        normalizedHost.startsWith("172.28.") ||
        normalizedHost.startsWith("172.29.") ||
        normalizedHost.startsWith("172.30.") ||
        normalizedHost.startsWith("172.31.") ||
        normalizedHost.startsWith("127.") ||
        normalizedHost == "localhost"
}
