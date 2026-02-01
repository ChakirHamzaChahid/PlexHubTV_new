package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Recherche centralisée multi-serveurs.
 * Délègue au repository mais gère le cas des requêtes vides.
 */
class SearchAcrossServersUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {
    operator fun invoke(query: String): Flow<Result<List<MediaItem>>> = flow {
        if (query.isBlank()) {
            emit(Result.success(emptyList()))
        } else {
            emit(searchRepository.searchAllServers(query))
        }
    }
}
