package com.chakir.plexhubtv.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.map
import androidx.sqlite.db.SimpleSQLiteQuery
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.model.LibrarySection
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.core.network.PlexClient
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LibraryRepositoryImpl
    @Inject
    constructor(
        private val api: PlexApiService,
        private val connectionManager: ConnectionManager,
        private val authRepository: AuthRepository,
        private val mapper: MediaMapper,
        private val mediaDao: MediaDao,
        private val database: com.chakir.plexhubtv.core.database.PlexDatabase,
    ) : LibraryRepository {
        override suspend fun getLibraries(serverId: String): Result<List<LibrarySection>> {
            try {
                val client =
                    getClient(serverId) ?: run {
                        // Offline: Try DB
                        val cached = database.librarySectionDao().getLibrarySections(serverId).first()
                        if (cached.isNotEmpty()) {
                            return Result.success(
                                cached.map {
                                    LibrarySection(key = it.libraryKey, title = it.title, type = it.type)
                                },
                            )
                        }
                        return Result.failure(Exception("Server offline and no cache"))
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
                return Result.failure(Exception("Failed to fetch sections"))
            } catch (e: Exception) {
                return Result.failure(e)
            }
        }

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

                val client = getClient(resolvedServerId)
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

                // Build Dynamic SQL for RawQuery with parameterized queries
                val isUnified = serverId == "all"
                val plexTypeStr = if (mediaType == com.chakir.plexhubtv.core.model.MediaType.Movie) "movie" else "show"

                val sqlBuilder = StringBuilder()
                val bindArgs = mutableListOf<Any>()

                if (isUnified) {
                    sqlBuilder.append("SELECT *, MAX(addedAt) as addedAt, ")
                    sqlBuilder.append(
                        "(COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) as rating, ",
                    )
                    sqlBuilder.append(
                        "(COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) as audienceRating, ",
                    )
                    sqlBuilder.append("GROUP_CONCAT(ratingKey) as ratingKeys, GROUP_CONCAT(serverId) as serverIds FROM media ")
                    sqlBuilder.append("WHERE type = ? ")
                    bindArgs.add(plexTypeStr)
                } else {
                    sqlBuilder.append("SELECT * FROM media ")
                    sqlBuilder.append("WHERE librarySectionId = ? AND filter = ? AND sortOrder = ? ")
                    bindArgs.add(resolvedLibraryKey)
                    bindArgs.add(normalizedFilter)
                    bindArgs.add(normalizedSort)
                }

                // Add Genre Filter (Multiple Keywords support)
                if (!genre.isNullOrEmpty()) {
                    sqlBuilder.append("AND (")
                    genre.forEachIndexed { index, keyword ->
                        if (index > 0) sqlBuilder.append(" OR ")
                        sqlBuilder.append("genres LIKE ?")
                        bindArgs.add("%$keyword%")
                    }
                    sqlBuilder.append(") ")
                }

                // Exclude Servers (Unified Only)
                if (isUnified && excludedServerIds.isNotEmpty()) {
                    val placeholders = excludedServerIds.joinToString(",") { "?" }
                    sqlBuilder.append("AND serverId NOT IN ($placeholders) ")
                    bindArgs.addAll(excludedServerIds)
                }

                // Add Server Filter (Unified Only)
                if (isUnified && dbServerId != null) {
                    sqlBuilder.append("AND serverId = ? ")
                    bindArgs.add(dbServerId)
                }

                // Add Search Query
                if (dbQuery != null) {
                    sqlBuilder.append("AND title LIKE ? ")
                    bindArgs.add("%$dbQuery%")
                }

                // Grouping for Unified
                if (isUnified) {
                    sqlBuilder.append("GROUP BY CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END ")
                }

                // Add Sorting (whitelist-validated, not parameterizable in SQL)
                val safeDirection = if (isDescending) "DESC" else "ASC"
                val orderBy =
                    if (isUnified) {
                        when (baseSort) {
                            "title" -> "title $safeDirection"
                            "year" -> "year $safeDirection, title ASC"
                            "rating" -> "rating $safeDirection, title ASC"
                            "addedAt" -> "addedAt $safeDirection"
                            else -> "addedAt $safeDirection"
                        }
                    } else {
                        "pageOffset ASC"
                    }
                sqlBuilder.append("ORDER BY $orderBy")

                val rawQuery = SimpleSQLiteQuery(sqlBuilder.toString(), bindArgs.toTypedArray())
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
                        )
                    } else {
                        null
                    }

                val pager =
                    androidx.paging.Pager(
                        config =
                            androidx.paging.PagingConfig(
                                pageSize = 50,
                                prefetchDistance = 50,
                                initialLoadSize = 100,
                                enablePlaceholders = true,
                                maxSize = 2000,
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

                emitAll(
                    pager.flow.map { pagingData ->
                        pagingData.map { entity ->
                            val domain = mapper.mapEntityToDomain(entity)
                            val baseUrl = clientMap[entity.serverId]
                            val token = tokenMap[entity.serverId]

                            val finalDomain =
                                if (entity.serverIds != null && entity.ratingKeys != null) {
                                    val sIds = entity.serverIds!!.split(",")
                                    val rKeys = entity.ratingKeys!!.split(",")

                                    if (sIds.size == rKeys.size && sIds.size > 1) {
                                        val sources =
                                            sIds.zip(rKeys).mapNotNull { (sId, rKey) ->
                                                val srv = allServers.find { it.clientIdentifier == sId } ?: return@mapNotNull null
                                                val connection = clientMap[sId] ?: return@mapNotNull null

                                                com.chakir.plexhubtv.core.model.MediaSource(
                                                    serverId = sId,
                                                    ratingKey = rKey,
                                                    serverName = srv.name,
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

                            if (baseUrl != null && finalDomain.thumbUrl?.startsWith("http") == false) {
                                finalDomain.copy(
                                    thumbUrl = "${baseUrl}${finalDomain.thumbUrl}?X-Plex-Token=$token",
                                    artUrl =
                                        finalDomain.artUrl?.let {
                                            if (!it.startsWith(
                                                    "http",
                                                )
                                            ) {
                                                "${baseUrl}$it?X-Plex-Token=$token"
                                            } else {
                                                it
                                            }
                                        },
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

            val sqlBuilder = StringBuilder()
            val bindArgs = mutableListOf<Any>()

            if (isUnified) {
                sqlBuilder.append("SELECT COUNT(DISTINCT CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END) ")
                sqlBuilder.append("FROM media WHERE type = ? ")
                bindArgs.add(typeStr)
            } else {
                // Resolve library key if necessary
                if (resolvedLibraryKey == "default") {
                    val cachedSection = database.librarySectionDao().getLibrarySectionByType(resolvedServerId, typeStr)
                    if (cachedSection != null) {
                        resolvedLibraryKey = cachedSection.libraryKey
                    } else {
                        val result = getLibraries(resolvedServerId)
                        val section = result.getOrNull()?.find { it.type == typeStr }
                        if (section != null) resolvedLibraryKey = section.key
                    }
                }
                sqlBuilder.append("SELECT COUNT(*) FROM media ")
                sqlBuilder.append("WHERE librarySectionId = ? AND filter = ? AND sortOrder = ? ")
                bindArgs.add(resolvedLibraryKey)
                bindArgs.add(normalizedFilter)
                bindArgs.add(normalizedSort)
            }

            // Add Genre Filter
            if (!genre.isNullOrEmpty()) {
                sqlBuilder.append("AND (")
                genre.forEachIndexed { index, keyword ->
                    if (index > 0) sqlBuilder.append(" OR ")
                    sqlBuilder.append("genres LIKE ?")
                    bindArgs.add("%$keyword%")
                }
                sqlBuilder.append(") ")
            }

            // Exclude Servers (Unified Only)
            if (isUnified && excludedServerIds.isNotEmpty()) {
                val placeholders = excludedServerIds.joinToString(",") { "?" }
                sqlBuilder.append("AND serverId NOT IN ($placeholders) ")
                bindArgs.addAll(excludedServerIds)
            }

            // Add Server Filter (Unified Only)
            if (isUnified && dbServerId != null) {
                sqlBuilder.append("AND serverId = ? ")
                bindArgs.add(dbServerId)
            }

            // Add Search Query
            if (dbQuery != null) {
                sqlBuilder.append("AND title LIKE ? ")
                bindArgs.add("%$dbQuery%")
            }

            // Alphabet constraint
            sqlBuilder.append("AND UPPER(title) < UPPER(?)")
            bindArgs.add(letter)

            val rawQuery = SimpleSQLiteQuery(sqlBuilder.toString(), bindArgs.toTypedArray())
            return mediaDao.getMediaCountRaw(rawQuery)
        }

        private suspend fun getClient(serverId: String): PlexClient? {
            val servers = authRepository.getServers(forceRefresh = false).getOrNull() ?: return null

            val targetServer =
                if (serverId == "default_server" || serverId == "all") {
                    servers.firstOrNull()
                } else {
                    servers.find { it.clientIdentifier == serverId }
                } ?: return null

            // Use cached connection directly here too for consistency, or non-blocking logic
            // But getLibraryContent handles connection itself for fetching.
            // If we need a client, we need a URL. For now, use existing flow but prefer cache if exposed
            val baseUrl =
                connectionManager.getCachedUrl(targetServer.clientIdentifier)
                    ?: connectionManager.findBestConnection(targetServer) // This might still block if not cached?
            // Actually findBestConnection inside getClient IS blocking.
            // Ideally we should use getCachedUrl and fallback.

            return if (baseUrl != null) PlexClient(targetServer, api, baseUrl) else null
        }
    }
