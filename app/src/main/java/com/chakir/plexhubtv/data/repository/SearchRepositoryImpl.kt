package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.PlexClient
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.repository.SearchRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * Implémentation de la recherche globale.
 *
 * Fonctionnalité majeure :
 * - **Recherche Fédérée** : Lance des requêtes de recherche en parallèle (Async/Await) sur TOUS les serveurs enregistrés.
 * - Agrège et trie les résultats avant de les retourner à l'UI.
 */
class SearchRepositoryImpl @Inject constructor(
    private val api: PlexApiService,
    private val connectionManager: ConnectionManager,
    private val mapper: MediaMapper,
    private val serverDao: com.chakir.plexhubtv.core.database.ServerDao,
    private val serverMapper: com.chakir.plexhubtv.data.mapper.ServerMapper
) : SearchRepository {

    override suspend fun searchAllServers(query: String): Result<List<MediaItem>> = kotlinx.coroutines.coroutineScope {
        try {
            // Get all servers as a one-time list (first emission)
            val serversEntities = serverDao.getAllServers().firstOrNull() ?: emptyList()
            val servers = serversEntities.map { entity -> serverMapper.mapEntityToDomain(entity) }
            
            if (servers.isEmpty()) {
                return@coroutineScope Result.success(emptyList())
            }

            android.util.Log.d("SearchRepo", "Searching across ${servers.size} servers for: $query")

            val results: List<MediaItem> = servers.map { server ->
                async {
                    searchOnServer(server, query).getOrDefault(emptyList())
                }
            }.awaitAll().flatten()

            // Deduplicate by ratingKey + serverId or aggregation logic if needed
            // For now, simple flatten and sort
            val sortedResults = results.sortedByDescending { item -> item.year ?: 0 }
            
            Result.success(sortedResults)
        } catch (e: Exception) {
            android.util.Log.e("SearchRepo", "Global search failed: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun searchOnServer(server: com.chakir.plexhubtv.domain.model.Server, query: String): Result<List<MediaItem>> {
        return try {
            val baseUrl = connectionManager.findBestConnection(server) ?: return Result.failure(Exception("No connection"))
            val client = PlexClient(server, api, baseUrl)
            
            val response = client.search(query)
            if (response.isSuccessful) {
                val mediaContainer = response.body()?.mediaContainer
                val metadata = mutableListOf<com.chakir.plexhubtv.data.model.MetadataDTO>()
                
                mediaContainer?.metadata?.let { metadata.addAll(it) }
                mediaContainer?.hubs?.forEach { hub ->
                    hub.metadata?.let { metadata.addAll(it) }
                }

                val items = metadata
                    .filter { mapper.isQualityMetadata(it) }
                    .map { dto ->
                     // Pass "0" as library key for search results as context is unknown or mixed
                     mapper.mapDtoToDomain(dto, server.clientIdentifier, baseUrl, server.accessToken)
                }
                Result.success(items)
            } else {
                Result.failure(Exception("Search failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
