package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.Hub
import kotlinx.coroutines.flow.Flow

interface HubsRepository {
    fun getUnifiedHubs(): Flow<List<Hub>>
}
