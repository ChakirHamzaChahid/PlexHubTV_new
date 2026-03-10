package com.chakir.plexhubtv.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.map
import androidx.sqlite.db.SimpleSQLiteQuery
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.LibrarySection
import com.chakir.plexhubtv.core.model.MediaItem
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
        private val database: com.chakir.plexhubtv.core.database.PlexDatabase,
        private val settingsRepository: com.chakir.plexhubtv.domain.repository.SettingsRepository,
        private val serverNameResolver: ServerNameResolver,
        private val mediaUrlResolver: MediaUrlResolver,
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
                val builtQuery = MediaLibraryQueryBuilder.buildPagedQuery(queryConfig)
                val rawQuery = builtQuery.toSimpleSQLiteQuery()

                // DEBUG: Log SQL query and sort parameters
                timber.log.Timber.d("LIBRARY_SORT [baseSort=$baseSort, isDesc=$isDescending] SQL Query: ${builtQuery.sql.take(500)}")
                timber.log.Timber.d("LIBRARY_SORT Bind Args: ${builtQuery.args.take(5)}")

                val factory = { mediaDao.getMediaPagedRaw(rawQuery) }

                val remoteMediator =
                    if (serverId != "all" && client != null) {
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
                                prefetchDistance = 15, // TV viewport shows ~15-20 items; 50 caused excessive prefetch
                                initialLoadSize = 100,
                                enablePlaceholders = true,
                                maxSize = 800, // Reduced from 2000: saves ~50% paging RAM on 2GB TV devices
                            ),
                        initialKey = initialKey,
                        remoteMediator = remoteMediator,
                        pagingSourceFactory = factory,
                    )

                val allServers = authRepository.getServers(forceRefresh = false).getOrNull() ?: emptyList()
                // FIX: Use cached URLs instead of testing connections (was causing 12s delay)
                val clientMap =
                    allServers.associate { server ->
                        server.clientIdentifier to (
                            connectionManager.getCachedUrl(server.clientIdentifier)
                                ?: server.connectionCandidates.firstOrNull()?.uri
                        ) // Fallback to first URL if no cache
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

                emitAll(
                    pager.flow.map { pagingData ->
                        pagingData.map { entity ->
                            val domain = mapper.mapEntityToDomain(entity)
                            val baseUrl = clientMap[entity.serverId]
                            val token = tokenMap[entity.serverId]

                            val serverIdsStr = entity.serverIds
                            val finalDomain =
                                if (serverIdsStr != null && serverIdsStr.contains("=")) {
                                    val pairs = serverIdsStr.split(",").map { it.split("=") }.filter { it.size == 2 }
                                    var sIds = pairs.map { it[0] }
                                    var rKeys = pairs.map { it[1] }

                                    // Prioritize default server in multi-server results (Kotlin-side, SQLite version independent)
                                    if (sIds.size == rKeys.size && sIds.size > 1 && preferredServerIdForMapping != null) {
                                        val preferredIndex = sIds.indexOf(preferredServerIdForMapping)
                                        if (preferredIndex > 0) { // Only reorder if preferred server is not already first
                                            // Move preferred server to first position
                                            sIds = listOf(sIds[preferredIndex]) + sIds.filterIndexed { idx, _ -> idx != preferredIndex }
                                            rKeys = listOf(rKeys[preferredIndex]) + rKeys.filterIndexed { idx, _ -> idx != preferredIndex }
                                        }
                                    }

                                    if (sIds.size == rKeys.size) {
                                        val sources =
                                            sIds.zip(rKeys)
                                                .distinctBy { it.first } // Deduplicate by serverId
                                                .mapNotNull { (sId, rKey) ->
                                                    val serverName = serverNameMap[sId] ?: sId.take(12)

                                                    com.chakir.plexhubtv.core.model.MediaSource(
                                                        serverId = sId,
                                                        ratingKey = rKey,
                                                        serverName = serverName,
                                                        resolution = null,
                                                        thumbUrl = null,
                                                        artUrl = null,
                                                    )
                                                }
                                        domain.copy(remoteSources = sources)
                                    } else {
                                        domain
                                    }
                                } else {
                                    domain
                                }

                            // Always resolve URLs against CURRENT baseUrl (resolvedThumbUrl may
                            // contain a stale server address if the connection changed since sync)
                            if (baseUrl != null) {
                                mediaUrlResolver.resolveUrls(finalDomain, baseUrl, token).copy(
                                    baseUrl = baseUrl,
                                    accessToken = token,
                                )
                            } else {
                                finalDomain
                            }
                        }
                    },
                )
            }
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
            val rawQuery = MediaLibraryQueryBuilder.buildCountQuery(queryConfig).toSimpleSQLiteQuery()
            return mediaDao.getMediaCountRaw(rawQuery)
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
            val rawQuery = MediaLibraryQueryBuilder.buildIndexQuery(queryConfig, letter).toSimpleSQLiteQuery()
            return mediaDao.getMediaCountRaw(rawQuery)
        }

    }
