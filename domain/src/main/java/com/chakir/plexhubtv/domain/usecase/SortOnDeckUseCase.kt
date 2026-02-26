package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tri intelligent des items On Deck / Continue Watching.
 *
 * Clé 1 : `lastViewedAt` décroissant (les plus récemment regardés en premier).
 * Clé 2 : priorité aux épisodes/films en cours de visionnage (< 90% de progression)
 *          par rapport aux items terminés mais encore présents dans On Deck.
 */
@Singleton
class SortOnDeckUseCase
    @Inject
    constructor() {
        operator fun invoke(items: List<MediaItem>): List<MediaItem> =
            items.sortedWith(
                compareByDescending<MediaItem> { it.lastViewedAt ?: 0L }
                    .thenByDescending { priorityScore(it) },
            )

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
