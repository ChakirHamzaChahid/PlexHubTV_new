package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.model.MediaSource
import com.chakir.plexhubtv.domain.model.MediaType
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.SearchRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class EnrichMediaItemUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(item: MediaItem): MediaItem = coroutineScope {
        val serversResult = authRepository.getServers()
        val allServers = serversResult.getOrNull() ?: return@coroutineScope item
        val serverMap = allServers.associate { it.clientIdentifier to it.name }

        // Start with current source
        val currentSource = MediaSource(
            serverId = item.serverId,
            ratingKey = item.ratingKey,
            serverName = serverMap[item.serverId] ?: "Unknown",
            resolution = null
        )
        
        // If only one server, return immediately
        if (allServers.size <= 1) {
            return@coroutineScope item.copy(remoteSources = listOf(currentSource))
        }

        val matches = allServers
            .filter { it.clientIdentifier != item.serverId } // Skip current
            .map { server ->
                async {
                    try {
                        val searchRes = searchRepository.searchOnServer(server, item.title)
                        val results = searchRes.getOrNull() ?: emptyList()
                        
                        results.find { candidate ->
                            // 1. GUID Match (Best)
                            // Note: candidate might generic, verify IDs
                            val idMatch = (item.imdbId != null && item.imdbId == candidate.imdbId) ||
                                          (item.tmdbId != null && item.tmdbId == candidate.tmdbId)
                                          
                            // 2. Title + Year Match (Fallback)
                            val titleMatch = candidate.title.equals(item.title, ignoreCase = true)
                            val yearMatch = (item.year == null || candidate.year == null || item.year == candidate.year)
                            
                            // 3. Hierarchy Match for Episodes
                            // Ideally check Parent/Show title but simplistic search result might lack it.
                            // We rely on Title + Year + Type match.
                            val typeMatch = item.type == candidate.type
                            
                            // Strictness: If Episode, ensure Parent Index (Season) and Episode Index match if available
                            val episodeMatch = if (item.type == MediaType.Episode && candidate.type == MediaType.Episode) {
                                (item.parentIndex == candidate.parentIndex) && (item.episodeIndex == candidate.episodeIndex)
                            } else true

                            val isMatch = (idMatch) || (titleMatch && yearMatch && typeMatch && episodeMatch)
                            isMatch
                        }?.let { match ->
                            MediaSource(
                                serverId = server.clientIdentifier,
                                ratingKey = match.ratingKey,
                                serverName = server.name,
                                resolution = null // Populate if available from match details
                            )
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()

        val finalSources = listOf(currentSource) + matches
        item.copy(remoteSources = finalSources)
    }
}
