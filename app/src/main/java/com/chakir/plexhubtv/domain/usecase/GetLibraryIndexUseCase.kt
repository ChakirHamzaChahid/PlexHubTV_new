package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.repository.LibraryRepository
import javax.inject.Inject

/**
 * Cas d'utilisation pour l'index alphabétique (Fast Scroller).
 * Permet de sauter directement à une lettre (A, B, C...) dans une grande bibliothèque.
 */
class GetLibraryIndexUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    suspend operator fun invoke(
        type: com.chakir.plexhubtv.domain.model.MediaType,
        letter: String,
        filter: String?,
        sort: String?,
        genre: List<String>?,
        serverId: String?,
        selectedServerId: String?,
        libraryKey: String?,
        query: String?
    ): Int {
        // If sorting is NOT alphabetical, this index lookup makes no sense (or behaves differently).
        // For now, assume UI only calls this when sort is "Title".
        return libraryRepository.getIndexOfFirstItem(type, letter, filter, sort, genre, serverId, selectedServerId, libraryKey, query)
    }
}
