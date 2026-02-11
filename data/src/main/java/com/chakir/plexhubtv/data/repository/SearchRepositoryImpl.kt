package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.SearchCacheDao
import com.chakir.plexhubtv.core.database.SearchCacheEntity
import com.chakir.plexhubtv.core.database.ServerDao
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.core.network.PlexClient
import com.chakir.plexhubtv.core.network.model.MetadataDTO
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.data.mapper.ServerMapper
import com.chakir.plexhubtv.domain.repository.SearchRepository
import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * Implémentation de la recherche globale.
 *
 * Fonctionnalité majeure :
 * - **Recherche Fédérée** : Lance des requêtes de recherche en parallèle (Async/Await) sur TOUS les serveurs enregistrés.
 * - Agrège et trie les résultats avant de les retourner à l'UI.
 */
class SearchRepositoryImpl
    @Inject
    constructor(
        private val api: PlexApiService,
        private val connectionManager: ConnectionManager,
        private val serverDao: ServerDao,
        private val serverMapper: ServerMapper,
        private val mapper: MediaMapper,
        private val searchCacheDao: SearchCacheDao,
        private val gson: Gson,
    ) : SearchRepository {
        override suspend fun searchAllServers(
            query: String,
            year: Int?,
            type: String?,
            unwatched: Boolean?,
        ): Result<List<MediaItem>> =
            kotlinx.coroutines.coroutineScope {
                try {
                    // Get all servers as a one-time list (first emission)
                    val serversEntities = serverDao.getAllServers().firstOrNull() ?: emptyList()
                    val servers = serversEntities.map { entity -> serverMapper.mapEntityToDomain(entity) }

                    if (servers.isEmpty()) {
                        return@coroutineScope Result.success(emptyList())
                    }

                    Timber.d("Searching across ${servers.size} servers for: $query")

                    val results: List<MediaItem> =
                        servers.map { server ->
                            async {
                                // ✅ Timeout per server (5 seconds) to prevent slow servers blocking entire search
                                val searchResult = withTimeoutOrNull(5000L) {
                                    searchOnServer(server, query, year, type, unwatched)
                                }

                                searchResult?.fold(
                                    onSuccess = { it },
                                    onFailure = { e ->
                                        Timber.e("Search failed on server ${server.name}: ${e.message}")
                                        emptyList()
                                    },
                                ) ?: run {
                                    Timber.w("Search timeout on server ${server.name} (>5s)")
                                    emptyList()
                                }
                            }
                        }.awaitAll().flatten()

                    // Deduplicate by ratingKey + serverId or aggregation logic if needed
                    // For now, simple flatten and sort
                    val sortedResults = results.sortedByDescending { item -> item.year ?: 0 }

                    Result.success(sortedResults)
                } catch (e: Exception) {
                    Timber.e(e, "Global search failed")
                    Result.failure(e)
                }
            }

        override suspend fun searchOnServer(
            server: com.chakir.plexhubtv.core.model.Server,
            query: String,
            year: Int?,
            type: String?,
            unwatched: Boolean?,
        ): Result<List<MediaItem>> {
            // 1. Check cache first
            val normalizedQuery = query.lowercase().trim()
            val cached = searchCacheDao.get(normalizedQuery, server.clientIdentifier)
            if (cached != null && !cached.isExpired()) {
                try {
                    val cachedItems = gson.fromJson(cached.resultsJson, Array<MediaItem>::class.java).toList()
                    Timber.d("Search cache hit for '$query' on ${server.name}")
                    return Result.success(cachedItems)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse cached search results")
                }
            }

            // 2. Attempt network fetch
            return try {
                val baseUrl = connectionManager.findBestConnection(server)
                    ?: return if (cached != null) {
                        // Offline but cache available (even if expired) → serve cache
                        Timber.d("No connection, serving stale cache for '$query' on ${server.name}")
                        val fallback = gson.fromJson(cached.resultsJson, Array<MediaItem>::class.java).toList()
                        Result.success(fallback)
                    } else {
                        Result.failure(Exception("No connection and no cache"))
                    }

                val client = PlexClient(server, api, baseUrl)
                val response = client.search(query, year, type, unwatched)

                if (response.isSuccessful) {
                    val mediaContainer = response.body()?.mediaContainer
                    val metadata = mutableListOf<MetadataDTO>()

                    mediaContainer?.metadata?.let { metadata.addAll(it) }
                    mediaContainer?.hubs?.forEach { hub ->
                        hub.metadata?.let { metadata.addAll(it) }
                    }

                    val items =
                        metadata
                            .filter { mapper.isQualityMetadata(it) }
                            .map { dto ->
                                // Pass "0" as library key for search results as context is unknown or mixed
                                mapper.mapDtoToDomain(dto, server.clientIdentifier, baseUrl, server.accessToken)
                            }

                    // 3. Update cache
                    searchCacheDao.upsert(
                        SearchCacheEntity(
                            query = normalizedQuery,
                            serverId = server.clientIdentifier,
                            resultsJson = gson.toJson(items),
                            resultCount = items.size
                        )
                    )

                    Result.success(items)
                } else {
                    Result.failure(Exception("Search failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Network error during search on ${server.name}")
                // 4. Network error → fallback to cache
                if (cached != null) {
                    try {
                        val fallback = gson.fromJson(cached.resultsJson, Array<MediaItem>::class.java).toList()
                        Timber.d("Network error, serving cached results for '$query'")
                        Result.success(fallback)
                    } catch (parseError: Exception) {
                        Result.failure(e)
                    }
                } else {
                    Result.failure(e)
                }
            }
        }
    }
