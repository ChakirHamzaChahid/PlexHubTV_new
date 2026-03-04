package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.source.MediaSourceHandler
import timber.log.Timber
import javax.inject.Inject

/**
 * Prepares a media item for playback by running enrichment and determining
 * whether source selection is needed.
 *
 * Encapsulates the shared logic between MediaDetailViewModel.PlayClicked
 * and SeasonDetailViewModel.PlayEpisode:
 * - Direct-stream sources: skip enrichment (no multi-server concept)
 * - Already enriched items: reuse existing remote sources
 * - Otherwise: run enrichment via EnrichMediaItemUseCase
 *
 * Does NOT handle: queue building, PlaybackManager, navigation, or UI state.
 */
class PreparePlaybackUseCase @Inject constructor(
    private val enrichMediaItemUseCase: EnrichMediaItemUseCase,
    private val sourceHandlers: Set<@JvmSuppressWildcards MediaSourceHandler>,
) {
    sealed class Result {
        data class ReadyToPlay(val item: MediaItem) : Result()
        data class NeedsSourceSelection(val item: MediaItem) : Result()
    }

    private fun resolveHandler(serverId: String): MediaSourceHandler? =
        sourceHandlers.find { it.matches(serverId) }

    suspend operator fun invoke(item: MediaItem): Result {
        // Direct-stream sources: skip enrichment (no multi-server concept)
        val handler = resolveHandler(item.serverId)
        if (handler != null && !handler.needsEnrichment()) {
            return Result.ReadyToPlay(item)
        }

        // Already enriched with multiple sources (pre-loaded from unified seasons or background enrichment)
        if (item.remoteSources.size > 1) {
            return Result.NeedsSourceSelection(item)
        }

        // Enrich to discover remote sources
        val enriched = try {
            enrichMediaItemUseCase(item)
        } catch (e: Exception) {
            Timber.w(e, "Enrichment failed for ${item.title}, falling back to direct play")
            item
        }

        return if (enriched.remoteSources.size > 1) {
            Result.NeedsSourceSelection(enriched)
        } else {
            Result.ReadyToPlay(enriched)
        }
    }
}
