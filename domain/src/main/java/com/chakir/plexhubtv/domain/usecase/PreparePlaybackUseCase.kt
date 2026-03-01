package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.MediaItem
import timber.log.Timber
import javax.inject.Inject

/**
 * Prepares a media item for playback by running enrichment and determining
 * whether source selection is needed.
 *
 * Encapsulates the shared logic between MediaDetailViewModel.PlayClicked
 * and SeasonDetailViewModel.PlayEpisode:
 * - Xtream items: skip enrichment (no multi-server concept)
 * - Already enriched items: reuse existing remote sources
 * - Otherwise: run enrichment via EnrichMediaItemUseCase
 *
 * Does NOT handle: queue building, PlaybackManager, navigation, or UI state.
 */
class PreparePlaybackUseCase @Inject constructor(
    private val enrichMediaItemUseCase: EnrichMediaItemUseCase,
) {
    sealed class Result {
        data class ReadyToPlay(val item: MediaItem) : Result()
        data class NeedsSourceSelection(val item: MediaItem) : Result()
    }

    suspend operator fun invoke(item: MediaItem): Result {
        // Xtream: skip enrichment entirely (direct-play, no multi-server)
        if (item.serverId.startsWith("xtream_")) {
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
