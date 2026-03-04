package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tri intelligent des items On Deck / Continue Watching.
 *
 * Clé 1 : timestamp effectif décroissant — `lastViewedAt` si disponible,
 *          sinon `addedAt` comme indicateur de récence pour les épisodes
 *          de continuation (non commencés mais "prochains" dans une série).
 * Clé 2 : priorité aux épisodes/films en cours de visionnage (< 90 %)
 *          par rapport aux items terminés mais encore présents dans On Deck.
 */
@Singleton
class SortOnDeckUseCase
    @Inject
    constructor() {
        operator fun invoke(items: List<MediaItem>): List<MediaItem> =
            items.sortedWith(
                compareByDescending<MediaItem> { effectiveTimestamp(it) }
                    .thenByDescending { priorityScore(it) },
            )

        /**
         * Returns the best available recency timestamp for sorting.
         * - Items with `lastViewedAt > 0`: use it directly (most accurate).
         * - Continuation episodes (`lastViewedAt = 0/null`): fall back to `addedAt`
         *   which approximates when the show became relevant in the library.
         */
        private fun effectiveTimestamp(item: MediaItem): Long {
            val viewed = item.lastViewedAt ?: 0L
            if (viewed > 0L) return viewed
            return item.addedAt ?: 0L
        }

        private fun priorityScore(item: MediaItem): Int {
            val duration = item.durationMs ?: return 0
            if (duration <= 0) return 0
            val progress = item.viewOffset.toFloat() / duration.toFloat()
            return when {
                item.type == MediaType.Episode && progress in 0.01f..0.89f -> 2
                item.type == MediaType.Movie && progress in 0.01f..0.89f -> 1
                else -> 0
            }
        }
    }
