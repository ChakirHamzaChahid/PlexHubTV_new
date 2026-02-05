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

/**
 * Cas d'utilisation pour enrichir un MediaItem avec des sources externes (autres serveurs).
 *
 * Scénario :
 * L'utilisateur a un film sur le serveur A. Ce UseCase va chercher si ce même film
 * existe sur le serveur B ou C pour offrir plus d'options de lecture (fallback).
 *
 * Algorithme de Matching :
 * 1. Recherche via GUID (IMDB/TMDB) -> Match parfait.
 * 2. Fallback sur "Titre + Année" si les GUIDs manquent.
 * 3. Vérification de la hiérarchie pour les épisodes (Saison + Index).
 */
class EnrichMediaItemUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val searchRepository: SearchRepository,
    private val mediaRepository: com.chakir.plexhubtv.domain.repository.MediaRepository
) {
    /**
     * Exécute l'enrichissement en parallèle sur tous les serveurs connectés.
     * @param item L'élément de référence à enrichir.
     * @return Une copie de [item] avec la liste [MediaItem.remoteSources] remplie.
     */
    suspend operator fun invoke(item: MediaItem): MediaItem = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        kotlinx.coroutines.coroutineScope {
            val serversResult = authRepository.getServers()
            val allServers = serversResult.getOrNull() ?: return@coroutineScope item
            val serverMap = allServers.associate { it.clientIdentifier to it.name }

            // Start with current source
            val bestMedia = item.mediaParts.firstOrNull()
            val videoStream = bestMedia?.streams?.filterIsInstance<com.chakir.plexhubtv.domain.model.VideoStream>()?.firstOrNull()
            val audioStream = bestMedia?.streams?.filterIsInstance<com.chakir.plexhubtv.domain.model.AudioStream>()?.firstOrNull()

            val currentSource = MediaSource(
                serverId = item.serverId,
                ratingKey = item.ratingKey,
                serverName = serverMap[item.serverId] ?: "Unknown",
                resolution = videoStream?.let { "${it.height}p" },
                container = bestMedia?.container,
                videoCodec = videoStream?.codec,
                audioCodec = audioStream?.codec,
                audioChannels = audioStream?.channels,
                fileSize = bestMedia?.size,
                bitrate = videoStream?.bitrate,
                hasHDR = videoStream?.hasHDR ?: false,
                languages = bestMedia?.streams?.filterIsInstance<com.chakir.plexhubtv.domain.model.AudioStream>()
                     ?.mapNotNull { it.language }?.distinct() ?: emptyList(),
                thumbUrl = item.thumbUrl,
                artUrl = item.artUrl
            )
            
            // If only one server, return immediately
            if (allServers.size <= 1) {
                return@coroutineScope item.copy(remoteSources = listOf(currentSource))
            }

            val matchesDeferred = allServers
                .filter { it.clientIdentifier != item.serverId } // Skip current
                .map { server ->
                    async {
                        try {
                            val searchRes = searchRepository.searchOnServer(server, item.title)
                            val results = searchRes.getOrNull() ?: emptyList()
                            
                            results.find { candidate ->
                                // 1. GUID Match (Best)
                                val idMatch = (item.imdbId != null && item.imdbId == candidate.imdbId) ||
                                              (item.tmdbId != null && item.tmdbId == candidate.tmdbId)
                                              
                                // 2. Title + Year Match (Fallback)
                                val titleMatch = candidate.title.equals(item.title, ignoreCase = true)
                                val yearMatch = (item.year == null || candidate.year == null || item.year == candidate.year)
                                
                                // 3. Hierarchy Match
                                val typeMatch = item.type == candidate.type
                                val episodeMatch = if (item.type == MediaType.Episode && candidate.type == MediaType.Episode) {
                                    (item.parentIndex == candidate.parentIndex) && (item.episodeIndex == candidate.episodeIndex)
                                } else true

                                (idMatch) || (titleMatch && yearMatch && typeMatch && episodeMatch)
                            }?.let { match ->
                                // FOUND A MATCH! Now fetch its details to get full metadata.
                                try {
                                    val detailRes = mediaRepository.getMediaDetail(match.ratingKey, server.clientIdentifier)
                                    val fullDetail = detailRes.getOrNull()
                                    
                                    // Use full details if available, otherwise fallback to search match
                                    val safeMatch = fullDetail ?: match
                                    
                                    val matchMedia = safeMatch.mediaParts.firstOrNull()
                                    val matchVideo = matchMedia?.streams?.filterIsInstance<com.chakir.plexhubtv.domain.model.VideoStream>()?.firstOrNull()
                                    val matchAudio = matchMedia?.streams?.filterIsInstance<com.chakir.plexhubtv.domain.model.AudioStream>()?.firstOrNull()

                                    MediaSource(
                                        serverId = server.clientIdentifier,
                                        ratingKey = safeMatch.ratingKey,
                                        serverName = server.name,
                                        resolution = matchVideo?.let { "${it.height}p" },
                                        container = matchMedia?.container,
                                        videoCodec = matchVideo?.codec,
                                        audioCodec = matchAudio?.codec,
                                        audioChannels = matchAudio?.channels,
                                        fileSize = matchMedia?.size,
                                        bitrate = matchVideo?.bitrate,
                                        hasHDR = matchVideo?.hasHDR ?: false,
                                        languages = matchMedia?.streams?.filterIsInstance<com.chakir.plexhubtv.domain.model.AudioStream>()
                                             ?.mapNotNull { it.language }?.distinct() ?: emptyList(),
                                        thumbUrl = safeMatch.thumbUrl,
                                        artUrl = safeMatch.artUrl
                                    )
                                } catch (e: Exception) {
                                    // Fallback if detailed fetch fails
                                     MediaSource(
                                        serverId = server.clientIdentifier,
                                        ratingKey = match.ratingKey,
                                        serverName = server.name,
                                        resolution = null
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                
            val matches = matchesDeferred.awaitAll().filterNotNull()

            val finalSources = listOf(currentSource) + matches
            item.copy(remoteSources = finalSources)
        }
    }
}
