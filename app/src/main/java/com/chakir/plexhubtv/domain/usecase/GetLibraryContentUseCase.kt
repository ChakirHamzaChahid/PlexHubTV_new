package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.util.Resource
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetLibraryContentUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    operator fun invoke(
        serverId: String, 
        libraryKey: String,
        mediaType: com.chakir.plexhubtv.domain.model.MediaType,
        filter: String? = null,
        sort: String? = null,
        genre: String? = null,
        selectedServerId: String? = null,
        initialKey: Int? = null
    ): Flow<androidx.paging.PagingData<MediaItem>> = libraryRepository.getLibraryContent(
        serverId, libraryKey, mediaType, filter, sort, genre, selectedServerId, initialKey
    )
}
