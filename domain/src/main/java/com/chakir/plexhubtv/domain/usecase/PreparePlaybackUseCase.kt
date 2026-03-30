package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.AudioStream
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaSource
import com.chakir.plexhubtv.core.model.VideoStream
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
 * - Plex merged versions: split mediaParts by mediaIndex into separate sources
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

        // Multiple remote sources found (different servers or same-server alternatives)
        if (enriched.remoteSources.size > 1) {
            return Result.NeedsSourceSelection(enriched)
        }

        // Check for Plex merged versions: same ratingKey, multiple Media entries
        val expandedItem = expandPlexMergedVersions(enriched)
        return if (expandedItem.remoteSources.size > 1) {
            Result.NeedsSourceSelection(expandedItem)
        } else {
            Result.ReadyToPlay(enriched)
        }
    }

    /**
     * Detects Plex merged versions (multiple Media entries under one ratingKey)
     * and expands them into separate MediaSource entries for source selection.
     *
     * Each Media entry in Plex represents a different file version (e.g. 720p vs 576i).
     * The mapper preserves the mediaIndex on each MediaPart via flatMapIndexed.
     */
    private fun expandPlexMergedVersions(item: MediaItem): MediaItem {
        if (item.mediaParts.size <= 1) return item

        val mediaGroups = item.mediaParts.groupBy { it.mediaIndex }
        if (mediaGroups.size <= 1) return item

        Timber.d("Plex merged versions detected for '${item.title}': ${mediaGroups.size} media entries")

        val serverName = item.remoteSources.firstOrNull()?.serverName ?: "Local"
        val sources = mediaGroups.map { (mediaIdx, parts) ->
            val videoStream = parts.firstOrNull()?.streams?.filterIsInstance<VideoStream>()?.firstOrNull()
            val audioStream = parts.firstOrNull()?.streams?.filterIsInstance<AudioStream>()?.firstOrNull()
            val audioLanguages = parts.firstOrNull()?.streams
                ?.filterIsInstance<AudioStream>()
                ?.mapNotNull { it.language }
                ?.distinct()
                ?: emptyList()

            MediaSource(
                serverId = item.serverId,
                ratingKey = item.ratingKey,
                serverName = serverName,
                resolution = videoStream?.let { "${it.height}p" },
                container = parts.firstOrNull()?.container,
                videoCodec = videoStream?.codec,
                audioCodec = audioStream?.codec,
                audioChannels = audioStream?.channels,
                fileSize = parts.sumOf { it.size ?: 0L }.takeIf { it > 0 },
                bitrate = videoStream?.bitrate,
                hasHDR = videoStream?.hasHDR == true,
                languages = audioLanguages,
                thumbUrl = item.thumbUrl,
                artUrl = item.artUrl,
                viewOffset = item.viewOffset,
                mediaIndex = mediaIdx,
            )
        }

        return item.copy(remoteSources = sources)
    }
}
