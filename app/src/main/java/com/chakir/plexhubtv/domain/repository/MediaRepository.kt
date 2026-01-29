package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.model.Hub
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    // Multi-server Aggregation
    fun getUnifiedOnDeck(): Flow<List<MediaItem>>
    fun getUnifiedHubs(): Flow<List<Hub>>
    suspend fun getUnifiedLibrary(mediaType: String): Result<List<MediaItem>>

    // Details & Children
    suspend fun getMediaDetail(ratingKey: String, serverId: String): Result<MediaItem>
    suspend fun getSeasonEpisodes(ratingKey: String, serverId: String): Result<List<MediaItem>>
    suspend fun getShowSeasons(ratingKey: String, serverId: String): Result<List<MediaItem>>

    // User Actions
    suspend fun toggleWatchStatus(media: MediaItem, isWatched: Boolean): Result<Unit>
    suspend fun updatePlaybackProgress(media: MediaItem, positionMs: Long): Result<Unit>
    
    // Player Support
    suspend fun getNextMedia(currentItem: MediaItem): MediaItem?
    suspend fun getPreviousMedia(currentItem: MediaItem): MediaItem?

    // Favorites
    fun getFavorites(): Flow<List<MediaItem>>
    fun isFavorite(ratingKey: String, serverId: String): Flow<Boolean>
    suspend fun toggleFavorite(media: MediaItem): Result<Boolean>

    // History
    fun getWatchHistory(limit: Int = 50, offset: Int = 0): Flow<List<MediaItem>>
}
