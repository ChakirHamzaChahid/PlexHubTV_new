package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface OnDeckRepository {
    fun getUnifiedOnDeck(): Flow<List<MediaItem>>
}
