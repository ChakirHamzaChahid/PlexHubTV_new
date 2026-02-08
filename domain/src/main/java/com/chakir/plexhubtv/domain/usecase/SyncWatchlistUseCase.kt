package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.repository.MediaRepository
import javax.inject.Inject

/**
 * Synchronise la Watchlist globale Plex (Cloud) vers la base de données locale (Room).
 *
 * Processus :
 * 1. Récupère la liste depuis Plex.tv.
 * 2. Pour chaque élément, cherche une correspondance locale (sur les serveurs connectés).
 * 3. Si trouvé, l'ajoute aux Favoris locaux pour un accès rapide.
 */
class SyncWatchlistUseCase
    @Inject
    constructor(
        private val mediaRepository: MediaRepository,
    ) {
        suspend operator fun invoke(): Result<Unit> {
            return mediaRepository.syncWatchlist()
        }
    }
