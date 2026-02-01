package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.model.MediaType
import com.chakir.plexhubtv.domain.repository.MediaRepository
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.SearchRepository
import com.chakir.plexhubtv.domain.model.MediaSource
import com.chakir.plexhubtv.domain.model.VideoStream
import com.chakir.plexhubtv.domain.model.AudioStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

data class MediaDetail(
    val item: MediaItem,
    val children: List<MediaItem> = emptyList() // Seasons or Episodes
)

/**
 * Cas d'utilisation complexe pour récupérer TOUS les détails d'un média.
 * 
 * Orchestration Parallèle :
 * 1. Récupère les métadonnées principales depuis le serveur source.
 * 2. En parallèle, récupère les enfants (Saisons/Épisodes si applicable).
 * 3. En parallèle, lance une recherche sur TOUS les autres serveurs connectés
 *    pour trouver des doublons (Sources alternatives).
 * 4. Enrichit les sources trouvées avec leurs détails techniques (Résolution, Codecs).
 * 5. Fusionne le tout dans un [MediaDetail] prêt pour l'affichage.
 */
class GetMediaDetailUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val authRepository: AuthRepository,
    private val searchRepository: SearchRepository
) {
    /**
     * @param ratingKey ID de l'élément sur le serveur principal.
     * @param serverId ID du serveur principal.
     */
    operator fun invoke(ratingKey: String, serverId: String): Flow<Result<MediaDetail>> = flow {
        coroutineScope {
            // 1. Start fetching servers immediately (independent of media detail)
            val serversDeferred = async { authRepository.getServers() }
            
            // 2. Start fetching primary detail
            val itemDeferred = async { mediaRepository.getMediaDetail(ratingKey, serverId) }
            
            val itemResult = itemDeferred.await()
            
            if (itemResult.isSuccess) {
                val item = itemResult.getOrThrow()
                
                // 3. Parallelize Children Fetch & Aggregation (Remote Search)
                // These rely on 'item' but are independent of each other
                
                val childrenDeferred = async {
                    when (item.type) {
                        MediaType.Show -> mediaRepository.getShowSeasons(ratingKey, serverId)
                        MediaType.Season -> mediaRepository.getSeasonEpisodes(ratingKey, serverId)
                        else -> Result.success(emptyList())
                    }
                }
                
                // Aggregation needs servers list
                val allServers = serversDeferred.await().getOrNull() ?: emptyList()
                val serverMap = allServers.associate { it.clientIdentifier to it.name }

                val matchesDeferred = async {
                    if (allServers.size > 1) {
                         allServers
                            .filter { it.clientIdentifier != serverId } // Skip current
                            .map { server ->
                                async {
                                    try {
                                        // Search by Title
                                        if (com.chakir.plexhubtv.BuildConfig.DEBUG) {
                                            android.util.Log.d("Aggregation", "Searching for '${item.title}' on ${server.name}")
                                        }
                                        val searchRes = searchRepository.searchOnServer(server, item.title)
                                        val results = searchRes.getOrNull() ?: emptyList()
                                        
                                        results.find { candidate ->
                                            // 1. GUID Match (Best)
                                            val idMatch = (item.imdbId != null && item.imdbId == candidate.imdbId) ||
                                                          (item.tmdbId != null && item.tmdbId == candidate.tmdbId)
                                                          
                                            // 2. Title + Year Match (Fallback)
                                            val titleMatch = candidate.title.equals(item.title, ignoreCase = true)
                                            val yearMatch = (item.year == null || candidate.year == null || item.year == candidate.year)
                                            
                                            // 3. Hierarchy Match (For Episodes: Show Title must match if possible)
                                            val parentMatch = if (item.type == MediaType.Episode && candidate.type == MediaType.Episode) {
                                                 titleMatch
                                            } else {
                                                true
                                            }
    
                                            idMatch || (titleMatch && yearMatch && parentMatch)
                                        }?.let { match ->
                                            if (com.chakir.plexhubtv.BuildConfig.DEBUG) {
                                                android.util.Log.d("Aggregation", "Found match on ${server.name}: ${match.title}")
                                            }
                                            val bestMedia = match.mediaParts.firstOrNull()
                                            val videoStream = bestMedia?.streams?.filterIsInstance<VideoStream>()?.firstOrNull()
                                            val audioStream = bestMedia?.streams?.filterIsInstance<AudioStream>()?.firstOrNull()
                                            
                                            MediaSource(
                                                serverId = server.clientIdentifier,
                                                ratingKey = match.ratingKey,
                                                serverName = server.name,
                                                resolution = videoStream?.let { "${it.height}p" },
                                                container = bestMedia?.container,
                                                videoCodec = videoStream?.codec,
                                                audioCodec = audioStream?.codec,
                                                audioChannels = audioStream?.channels,
                                                fileSize = bestMedia?.size,
                                                bitrate = videoStream?.bitrate,
                                                hasHDR = videoStream?.hasHDR ?: false,
                                                languages = bestMedia?.streams?.filterIsInstance<AudioStream>()
                                                    ?.mapNotNull { it.language }?.distinct() ?: emptyList(),
                                                thumbUrl = match.thumbUrl,
                                                artUrl = match.artUrl
                                            )
                                        }
                                    } catch (e: Exception) {
                                        if (com.chakir.plexhubtv.BuildConfig.DEBUG) {
                                            android.util.Log.e("Aggregation", "Error searching on ${server.name}", e)
                                        }
                                        null
                                    }
                                }
                            }.awaitAll().filterNotNull()
                    } else {
                        emptyList()
                    }
                }

                val childrenResult = childrenDeferred.await()
                val searchMatches = matchesDeferred.await()
                
                // Combine DB sources and Search matches, deduplicate by serverId
                val combinedRemoteSources = (item.remoteSources + searchMatches)
                    .filter { it.serverId != serverId } // Exclude self
                    .distinctBy { it.serverId }

                // Enrich all secondary sources that are missing metadata
                val finalRemoteSources = if (combinedRemoteSources.isNotEmpty()) {
                    coroutineScope {
                        combinedRemoteSources.map { source ->
                            async {
                                // Check if source needs enrichment (missing technical metadata)
                                if (source.videoCodec == null || source.audioCodec == null || source.resolution == null) {
                                    try {
                                        // Fetch full metadata for this source
                                        val sourceDetailResult = mediaRepository.getMediaDetail(source.ratingKey, source.serverId)
                                        
                                        if (sourceDetailResult.isSuccess) {
                                            val sourceItem = sourceDetailResult.getOrThrow()
                                            val bestMedia = sourceItem.mediaParts.firstOrNull()
                                            val videoStream = bestMedia?.streams?.filterIsInstance<VideoStream>()?.firstOrNull()
                                            val audioStream = bestMedia?.streams?.filterIsInstance<AudioStream>()?.firstOrNull()
                                            
                                            // Return enriched source
                                            source.copy(
                                                resolution = videoStream?.let { "${it.height}p" },
                                                container = bestMedia?.container,
                                                videoCodec = videoStream?.codec,
                                                audioCodec = audioStream?.codec,
                                                audioChannels = audioStream?.channels,
                                                fileSize = bestMedia?.size,
                                                bitrate = videoStream?.bitrate,
                                                hasHDR = videoStream?.hasHDR ?: false,
                                                languages = bestMedia?.streams?.filterIsInstance<AudioStream>()
                                                    ?.mapNotNull { it.language }?.distinct() ?: emptyList(),
                                                thumbUrl = sourceItem.thumbUrl,
                                                artUrl = sourceItem.artUrl
                                            )
                                        } else source
                                    } catch (e: Exception) {
                                        if (com.chakir.plexhubtv.BuildConfig.DEBUG) {
                                            android.util.Log.e("GetMediaDetail", "Error enriching source ${source.serverName}", e)
                                        }
                                        source
                                    }
                                } else {
                                    source // Already complete
                                }
                            }
                        }.awaitAll()
                    }
                } else {
                    emptyList()
                }
                
                // Add self to sources
                val bestMedia = item.mediaParts.firstOrNull()
                val videoStream = bestMedia?.streams?.filterIsInstance<VideoStream>()?.firstOrNull()
                val audioStream = bestMedia?.streams?.filterIsInstance<AudioStream>()?.firstOrNull()

                val currentSource = MediaSource(
                    serverId = serverId,
                    ratingKey = ratingKey,
                    serverName = serverMap[serverId] ?: "Unknown",
                    resolution = videoStream?.let { "${it.height}p" },
                    container = bestMedia?.container,
                    videoCodec = videoStream?.codec,
                    audioCodec = audioStream?.codec,
                    audioChannels = audioStream?.channels,
                    fileSize = bestMedia?.size,
                    bitrate = videoStream?.bitrate,
                    hasHDR = videoStream?.hasHDR ?: false,
                    languages = bestMedia?.streams?.filterIsInstance<AudioStream>()
                        ?.mapNotNull { it.language }?.distinct() ?: emptyList(),
                    thumbUrl = item.thumbUrl,
                    artUrl = item.artUrl
                )
                
                // Combine: current source + enriched remote sources
                val finalSources = listOf(currentSource) + finalRemoteSources
    
                emit(Result.success(MediaDetail(
                    item = item.copy(remoteSources = finalSources),
                    children = childrenResult.getOrDefault(emptyList())
                )))
            } else {
                emit(Result.failure(itemResult.exceptionOrNull() ?: Exception("Failed to load details")))
            }
        }
    }
}
