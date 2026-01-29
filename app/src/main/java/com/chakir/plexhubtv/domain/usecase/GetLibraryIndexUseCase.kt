package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.repository.LibraryRepository
import javax.inject.Inject

class GetLibraryIndexUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    suspend operator fun invoke(
        type: com.chakir.plexhubtv.domain.model.MediaType,
        letter: String,
        filter: String?,
        sort: String?,
        genre: String?,
        serverId: String?,
        libraryKey: String?
    ): Int {
        // If sorting is NOT alphabetical, this index lookup makes no sense (or behaves differently).
        // For now, assume UI only calls this when sort is "Title".
        return libraryRepository.getIndexOfFirstItem(type, letter, filter, sort, genre, serverId, libraryKey)
    }
}
