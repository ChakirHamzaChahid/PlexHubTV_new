package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.util.Resource
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.model.LibrarySection
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    // We might need a "Library" model, using generic string for now
    suspend fun getLibraries(serverId: String): Result<List<LibrarySection>> 
    
    fun getLibraryContent(
        serverId: String, 
        libraryKey: String, 
        mediaType: com.chakir.plexhubtv.domain.model.MediaType,
        filter: String? = null, 
        sort: String? = null,
        genre: String? = null,
        selectedServerId: String? = null,
        initialKey: Int? = null
    ): Flow<androidx.paging.PagingData<MediaItem>>
    
    suspend fun getIndexOfFirstItem(
        type: com.chakir.plexhubtv.domain.model.MediaType,
        letter: String,
        filter: String?,
        sort: String?,
        genre: String?,
        serverId: String?,
        libraryKey: String? // Optional, for context
    ): Int
}
