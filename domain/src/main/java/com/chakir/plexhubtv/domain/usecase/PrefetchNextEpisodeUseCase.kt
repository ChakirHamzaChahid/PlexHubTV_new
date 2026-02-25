package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case pour précharger le prochain épisode pendant la lecture de l'épisode actuel.
 *
 * Cette optimisation améliore l'UX en:
 * - Préchargeant les métadonnées du prochain épisode (titre, vignette, résumé)
 * - Préparant les données pour un démarrage instantané
 * - Permettant l'affichage anticipé des informations "À suivre"
 *
 * Le préchargement est déclenché à 80% de la lecture pour éviter un impact
 * sur les performances de lecture en cours.
 */
class PrefetchNextEpisodeUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val episodeNavigationUseCase: EpisodeNavigationUseCase,
) {

    private var lastPrefetchedEpisodeId: String? = null

    /**
     * Précharge le prochain épisode si disponible.
     *
     * @param currentEpisode L'épisode actuellement en lecture
     * @return Le prochain épisode préchargé, ou null si aucun épisode suivant
     */
    suspend operator fun invoke(currentEpisode: MediaItem): Result<MediaItem?> =
        withContext(Dispatchers.IO) {
            try {
                // Skip if not an episode
                if (currentEpisode.type != MediaType.Episode) {
                    return@withContext Result.success(null)
                }

                // Skip if already prefetched
                if (lastPrefetchedEpisodeId == currentEpisode.id) {
                    Timber.d("Next episode already prefetched for ${currentEpisode.title}")
                    return@withContext Result.success(null)
                }

                // Get next episode
                val adjacentEpisodes = episodeNavigationUseCase.loadAdjacentEpisodes(currentEpisode)
                    .getOrNull() ?: return@withContext Result.success(null)

                val nextEpisode = adjacentEpisodes.next ?: run {
                    Timber.d("No next episode found for ${currentEpisode.title}")
                    return@withContext Result.success(null)
                }

                Timber.d("Prefetching next episode: ${nextEpisode.title} (S${nextEpisode.parentIndex}E${nextEpisode.episodeIndex})")

                // Prefetch full metadata (this will cache it in the repository/database)
                val prefetchedEpisode = mediaRepository.getMediaDetail(
                    nextEpisode.ratingKey,
                    nextEpisode.serverId
                ).getOrNull()

                if (prefetchedEpisode != null) {
                    lastPrefetchedEpisodeId = currentEpisode.id
                    Timber.i("Successfully prefetched next episode: ${prefetchedEpisode.title}")
                    Result.success(prefetchedEpisode)
                } else {
                    Timber.w("Failed to prefetch next episode metadata")
                    Result.success(null)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error prefetching next episode")
                Result.failure(e)
            }
        }

    /**
     * Réinitialise l'état du prefetch (appelé lors du changement d'épisode).
     */
    fun reset() {
        lastPrefetchedEpisodeId = null
    }
}
