package com.chakir.plexhubtv.data.usecase

import com.chakir.plexhubtv.core.model.AudioStream
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaSource
import com.chakir.plexhubtv.core.model.VideoStream
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.core.network.PlexClient
import com.chakir.plexhubtv.core.network.model.MetadataDTO
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.SearchRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import javax.inject.Inject

/**
 * Algorithme sophistiqué pour trouver les sources d'un épisode sur d'autres serveurs.
 *
 * Problème : La recherche Plex standard sur un serveur secondaire retourne souvent
 * des résultats incomplets pour les épisodes (pas de parentIndex/index).
 *
 * Stratégies utilisées :
 * 1. **Recherche Directe** : Tente une correspondance GUID (IMDB/TMDB) immédiate.
 *    C'est rapide mais dépend de la qualité des métadonnées du serveur.
 * 2. **Traversée d'Arbre (Tree Traversal)** : Si la recherche directe échoue ou est ambiguë :
 *    - Recherche la SÉRIE par titre.
 *    - Explore les enfants de la série pour trouver la SAISON correspondante (par index).
 *    - Explore les enfants de la saison pour trouver l'ÉPISODE correspondant (par index).
 *    C'est plus lent (plusieurs appels API) mais extrêmement fiable (garantit le bon épisode).
 */
class ResolveEpisodeSourcesUseCase
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val searchRepository: SearchRepository,
        private val connectionManager: ConnectionManager,
        private val api: PlexApiService,
        private val mapper: MediaMapper,
    ) {
        /**
         * @param episode L'épisode source.
         * @return Liste de [MediaSource] pointant vers les fichiers correspondants sur les autres serveurs.
         */
        suspend operator fun invoke(episode: MediaItem): List<MediaSource> =
            coroutineScope {
                val serversResult = authRepository.getServers()
                val allServers = serversResult.getOrNull() ?: return@coroutineScope emptyList()
                val serverMap = allServers.associate { it.clientIdentifier to it.name }

                // We already have the source from the current item
                val currentSource =
                    MediaSource(
                        serverId = episode.serverId,
                        ratingKey = episode.ratingKey,
                        serverName = serverMap[episode.serverId] ?: "Unknown",
                    )

                // Find matches on other servers
                val otherServers = allServers.filter { it.clientIdentifier != episode.serverId }

                val matches =
                    otherServers.map { server ->
                        async {
                            try {
                                Timber.d("Resolving episode '${episode.title}' on ${server.name}")

                                // Strategy 1: Direct Search (Fast & Accurate if GUIDs present)
                                val episodeSearch = searchRepository.searchOnServer(server, episode.title)
                                val directMatch =
                                    episodeSearch.getOrNull()?.find { candidate ->
                                        // Match by GUID if available
                                        val idMatch =
                                            (episode.imdbId != null && episode.imdbId == candidate.imdbId) ||
                                                (episode.tmdbId != null && episode.tmdbId == candidate.tmdbId)

                                        // Match by strict Title + Index? (Risk of "Pilot" matching another show's Pilot)
                                        // So only accept ID match here, OR very strict hierarchy if available in search result
                                        idMatch
                                    }

                                if (directMatch != null) {
                                    Timber.d("Direct Match found: ${directMatch.title}")
                                    val bestMedia = directMatch.mediaParts.firstOrNull()
                                    val videoStream = bestMedia?.streams?.filterIsInstance<VideoStream>()?.firstOrNull()
                                    val audioStream = bestMedia?.streams?.filterIsInstance<AudioStream>()?.firstOrNull()

                                    return@async MediaSource(
                                        serverId = server.clientIdentifier,
                                        ratingKey = directMatch.ratingKey,
                                        serverName = server.name,
                                        resolution = videoStream?.let { "${it.height}p" },
                                        container = bestMedia?.container,
                                        videoCodec = videoStream?.codec,
                                        audioCodec = audioStream?.codec,
                                        audioChannels = audioStream?.channels,
                                        fileSize = bestMedia?.size,
                                        bitrate = videoStream?.bitrate,
                                        hasHDR = videoStream?.hasHDR ?: false,
                                        languages =
                                            bestMedia?.streams?.filterIsInstance<com.chakir.plexhubtv.core.model.AudioStream>()
                                                ?.mapNotNull { it.language }?.distinct() ?: emptyList(),
                                        thumbUrl = directMatch.thumbUrl,
                                        artUrl = directMatch.artUrl,
                                    )
                                }

                                // Strategy 2: Tree Traversal (Show -> Season -> Episode)
                                // Reliable for index-based matching when GUIDs fail
                                val showTitle = episode.grandparentTitle ?: episode.parentTitle ?: "Unknown Show"

                                // 1. Search for Show
                                val searchResult = searchRepository.searchOnServer(server, showTitle)
                                val showMatch =
                                    searchResult.getOrNull()?.find {
                                        it.title.equals(showTitle, ignoreCase = true) && it.type == com.chakir.plexhubtv.core.model.MediaType.Show
                                    } ?: return@async null

                                val baseUrl = connectionManager.findBestConnection(server) ?: return@async null
                                val client = PlexClient(server, api, baseUrl)

                                // 2. Find Season (by Index)
                                val seasonsResponse = client.getChildren(showMatch.ratingKey)
                                if (!seasonsResponse.isSuccessful) return@async null

                                val seasonMatch =
                                    seasonsResponse.body()?.mediaContainer?.metadata?.find {
                                        (it as? MetadataDTO)?.index == episode.seasonIndex
                                    } as? MetadataDTO ?: return@async null

                                // 3. Find Episode (by Index)
                                val episodesResponse = client.getChildren(seasonMatch.ratingKey)
                                if (!episodesResponse.isSuccessful) return@async null

                                val episodeMatch =
                                    episodesResponse.body()?.mediaContainer?.metadata?.find {
                                        (it as? MetadataDTO)?.index == episode.episodeIndex
                                    } as? MetadataDTO

                                if (episodeMatch != null) {
                                    Timber.d("Tree Match found: ${episodeMatch.title}")

                                    val episodeItem =
                                        mapper.mapDtoToDomain(
                                            episodeMatch,
                                            server.clientIdentifier,
                                            baseUrl,
                                            server.accessToken,
                                        )
                                    val bestMedia = episodeItem.mediaParts.firstOrNull()
                                    val videoStream = bestMedia?.streams?.filterIsInstance<VideoStream>()?.firstOrNull()
                                    val audioStream = bestMedia?.streams?.filterIsInstance<AudioStream>()?.firstOrNull()

                                    MediaSource(
                                        serverId = server.clientIdentifier,
                                        ratingKey = episodeMatch.ratingKey,
                                        serverName = server.name,
                                        resolution = videoStream?.let { "${it.height}p" },
                                        container = bestMedia?.container,
                                        videoCodec = videoStream?.codec,
                                        audioCodec = audioStream?.codec,
                                        audioChannels = audioStream?.channels,
                                        fileSize = bestMedia?.size,
                                        bitrate = videoStream?.bitrate,
                                        hasHDR = videoStream?.hasHDR ?: false,
                                        languages =
                                            bestMedia?.streams?.filterIsInstance<AudioStream>()
                                                ?.mapNotNull { it.language }?.distinct() ?: emptyList(),
                                        thumbUrl = episodeItem.thumbUrl,
                                        artUrl = episodeItem.artUrl,
                                    )
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error resolving on ${server.name}")
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()

                listOf(currentSource) + matches
            }
    }
