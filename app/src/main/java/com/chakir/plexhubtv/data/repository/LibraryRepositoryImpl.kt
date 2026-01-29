package com.chakir.plexhubtv.data.repository

import androidx.paging.ExperimentalPagingApi
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.repository.LibraryRepository
import javax.inject.Inject

import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.core.network.PlexClient
import com.chakir.plexhubtv.domain.model.LibrarySection

import com.chakir.plexhubtv.core.util.Resource
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.database.MediaEntity
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import androidx.paging.map

class LibraryRepositoryImpl @Inject constructor(
    private val api: PlexApiService,
    private val connectionManager: ConnectionManager,
    private val authRepository: AuthRepository,
    private val mapper: MediaMapper,
    private val mediaDao: MediaDao,
    private val database: com.chakir.plexhubtv.core.database.PlexDatabase
) : LibraryRepository {

    override suspend fun getLibraries(serverId: String): Result<List<LibrarySection>> {
        try {
            val client = getClient(serverId) ?: run {
                // Offline: Try DB
                val cached = database.librarySectionDao().getLibrarySections(serverId).first()
                if (cached.isNotEmpty()) {
                    return Result.success(cached.map { 
                        LibrarySection(key = it.libraryKey, title = it.title, type = it.type)
                    })
                }
                return Result.failure(Exception("Server offline and no cache"))
            }

            val response = client.getSections()
            if (response.isSuccessful) {
                val sections = response.body()?.mediaContainer?.directory ?: emptyList()
                val domainSections = sections.map { 
                    LibrarySection(
                        key = it.key ?: "",
                        title = it.title ?: "Unknown",
                        type = it.type
                    )
                }

                // Cache in DB
                database.librarySectionDao().insertLibrarySections(sections.map { 
                    com.chakir.plexhubtv.core.database.LibrarySectionEntity(
                        id = "$serverId:${it.key}",
                        serverId = serverId,
                        libraryKey = it.key ?: "",
                        title = it.title ?: "Unknown",
                        type = it.type ?: "movie"
                    )
                })

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
        mediaType: com.chakir.plexhubtv.domain.model.MediaType,
        filter: String?,
        sort: String?,
        genre: String?,
        selectedServerId: String?,
        initialKey: Int?
    ): Flow<androidx.paging.PagingData<MediaItem>> {
        return flow {
            var resolvedLibraryKey = libraryKey
            var resolvedServerId = serverId

            // Resolve "all" server
            if (resolvedServerId == "all") {
                val servers = authRepository.getServers().getOrNull()
                resolvedServerId = servers?.firstOrNull()?.clientIdentifier ?: "default"
            }

            // Resolve "default" library key by type
            if (resolvedLibraryKey == "default") {
                val plexType = when (mediaType) {
                    com.chakir.plexhubtv.domain.model.MediaType.Movie -> "movie"
                    com.chakir.plexhubtv.domain.model.MediaType.Show -> "show"
                    else -> "movie"
                }
                
                // Try cache first
                val cachedSection = database.librarySectionDao().getLibrarySectionByType(resolvedServerId, plexType)
                if (cachedSection != null) {
                    resolvedLibraryKey = cachedSection.libraryKey
                } else {
                    // Try to fetch and resolve
                    val result = getLibraries(resolvedServerId)
                    if (result.isSuccess) {
                        val section = result.getOrNull()?.find { it.type == plexType }
                        if (section != null) {
                            resolvedLibraryKey = section.key
                        }
                    }
                }
            }

            // Resolve Client (Server URL & Token)
            val client = getClient(resolvedServerId)
            
            // Normalize filter and sort for consistency between insert and query
            val normalizedFilter = filter?.lowercase() ?: "all"
            val normalizedSort = when (sort) {
                "Date Added" -> "addedAt:desc"
                "Title" -> "titleSort:asc"
                "Year" -> "year:desc"
                "Rating" -> "rating:desc"
                else -> sort?.lowercase() ?: "default"
            }

            // Normalize Genre & Server Filter (Treat "All" or null as null for DAO)
            val dbGenre = if (genre == null || genre == "All") null else genre
            val dbServerId = if (selectedServerId == null || selectedServerId == "All") null else selectedServerId
            
            // Factory for PagingSource
             val factory = if (serverId == "all") {
                  // Unified Aggregated View (Deduplicated across servers)
                  val plexTypeStr = when (mediaType) {
                      com.chakir.plexhubtv.domain.model.MediaType.Movie -> "movie"
                      com.chakir.plexhubtv.domain.model.MediaType.Show -> "show"
                      else -> "movie"
                  }
                  
                  when {
                      normalizedSort.startsWith("title") -> { { mediaDao.aggregatedPagingSourceByTitle(plexTypeStr, dbGenre, dbServerId) } }
                      normalizedSort.startsWith("year") -> { { mediaDao.aggregatedPagingSourceByYear(plexTypeStr, dbGenre, dbServerId) } }
                      normalizedSort.startsWith("rating") -> { { mediaDao.aggregatedPagingSourceByRating(plexTypeStr, dbGenre, dbServerId) } }
                      else -> { { mediaDao.aggregatedPagingSourceByDate(plexTypeStr, dbGenre, dbServerId) } }
                  }
             } else {
                  // Single Library View
                  { mediaDao.pagingSource(resolvedLibraryKey, normalizedFilter, normalizedSort) }
             }

            val remoteMediator = if (serverId != "all" && client != null) {
                com.chakir.plexhubtv.data.paging.MediaRemoteMediator(
                    libraryKey = resolvedLibraryKey,
                    filter = normalizedFilter,
                    sortOrder = normalizedSort,
                    api = api,
                    database = database,
                    serverId = resolvedServerId,
                    serverUrl = client.baseUrl,
                    token = client.server.accessToken ?: "",
                    mapper = mapper
                )
            } else {
                null
            }
            
            val pager = androidx.paging.Pager(
                config = androidx.paging.PagingConfig(
                    pageSize = 100,
                    prefetchDistance = 200, 
                    initialLoadSize = 1000,
                    enablePlaceholders = true
                ),
                initialKey = initialKey,
                remoteMediator = remoteMediator,
                pagingSourceFactory = factory
            )
            
            val allServers = authRepository.getServers().getOrNull() ?: emptyList()
            val clientMap = allServers.associate { server ->
                server.clientIdentifier to connectionManager.findBestConnection(server)
            }
            val tokenMap = allServers.associate { it.clientIdentifier to it.accessToken }

            emitAll(pager.flow.map { pagingData ->
                pagingData.map { entity -> 
                    val domain = mapper.mapEntityToDomain(entity)
                    val baseUrl = clientMap[entity.serverId]
                    val token = tokenMap[entity.serverId]
                    
                    val finalDomain = if (entity.serverIds != null && entity.ratingKeys != null) {
                        val sIds = entity.serverIds.split(",")
                        val rKeys = entity.ratingKeys.split(",")
                        
                        if (sIds.size == rKeys.size && sIds.size > 1) {
                            val sources = sIds.zip(rKeys).mapNotNull { (sId, rKey) ->
                                val srv = allServers.find { it.clientIdentifier == sId } ?: return@mapNotNull null
                                val connection = clientMap[sId] ?: return@mapNotNull null
                                
                                val name = srv.name
                                com.chakir.plexhubtv.domain.model.MediaSource(
                                    serverId = sId,
                                    ratingKey = rKey,
                                    serverName = name,
                                    resolution = null,
                                    thumbUrl = null,
                                    artUrl = null
                                )
                            }
                            domain.copy(remoteSources = sources)
                        } else domain
                    } else domain
                    
                    if (baseUrl != null && finalDomain.thumbUrl?.startsWith("http") == false) {
                        finalDomain.copy(
                            thumbUrl = "${baseUrl}${finalDomain.thumbUrl}?X-Plex-Token=$token",
                            artUrl = finalDomain.artUrl?.let { if (!it.startsWith("http")) "${baseUrl}${it}?X-Plex-Token=$token" else it },
                            baseUrl = baseUrl,
                            accessToken = token
                        )
                    } else {
                        finalDomain
                    }
                }
            })
        }
    }

    override suspend fun getIndexOfFirstItem(
        type: com.chakir.plexhubtv.domain.model.MediaType,
        letter: String,
        filter: String?,
        sort: String?,
        genre: String?,
        serverId: String?,
        libraryKey: String?
    ): Int {
        var resolvedLibraryKey = libraryKey ?: "default"
        var resolvedServerId = serverId ?: "all"

        if (resolvedServerId == "all") {
             val servers = authRepository.getServers().getOrNull()
             resolvedServerId = servers?.firstOrNull()?.clientIdentifier ?: "default"
             // But for 'all' queries, we keep 'all' as logic flag unless strict matching needed
             // Actually getLibraryContent checking: if (resolvedServerId == "all")...
             // If serverId was "all", we want UNIFIED query.
        }
        
        // Normalize sort (MUST match getLibraryContent logic)
        val normalizedSort = when (sort) {
            "Date Added" -> "addedAt:desc"
            "Title" -> "titleSort:asc"
            "Year" -> "year:desc"
            "Rating" -> "rating:desc"
            else -> sort?.lowercase() ?: "default"
        }
        
        val normalizedFilter = filter?.lowercase() ?: "all"
        val dbGenre = if (genre == null || genre == "All") null else genre
        val dbServerId = if (serverId == null || serverId == "All") null else serverId

        if (serverId == "all") {
            // Unified View
            val typeStr = if (type == com.chakir.plexhubtv.domain.model.MediaType.Movie) "movie" else "show"
            return mediaDao.getUnifiedCountBeforeTitle(typeStr, letter, dbGenre, dbServerId)
        } else {
            // Single Library
            if (resolvedLibraryKey == "default") {
                val plexType = if (type == com.chakir.plexhubtv.domain.model.MediaType.Movie) "movie" else "show"
                val cachedSection = database.librarySectionDao().getLibrarySectionByType(resolvedServerId, plexType)
                if (cachedSection != null) {
                    resolvedLibraryKey = cachedSection.libraryKey
                } else {
                     // Try fetch (simplified, usually cached by now)
                     val result = getLibraries(resolvedServerId)
                     val section = result.getOrNull()?.find { it.type == plexType }
                     if (section != null) resolvedLibraryKey = section.key
                }
            }
            
            return mediaDao.getLibraryCountBeforeTitle(resolvedLibraryKey, normalizedFilter, normalizedSort, letter)
        }
    }

    private suspend fun getClient(serverId: String): PlexClient? {
        // If serverId is "default_server" or similar, pick the first available one
        val servers = authRepository.getServers().getOrNull() ?: return null
        
        val targetServer = if (serverId == "default_server" || serverId == "all") {
             servers.firstOrNull()
        } else {
             servers.find { it.clientIdentifier == serverId }
        } ?: return null

        val baseUrl = connectionManager.findBestConnection(targetServer) ?: return null
        return PlexClient(targetServer, api, baseUrl)
    }
}
