package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.domain.model.TrackPreference

interface TrackPreferenceRepository {
    suspend fun getPreference(ratingKey: String, serverId: String): TrackPreference?
    suspend fun savePreference(preference: TrackPreference)
}
