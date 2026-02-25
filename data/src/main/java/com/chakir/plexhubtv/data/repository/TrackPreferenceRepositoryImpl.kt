package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.TrackPreferenceDao
import com.chakir.plexhubtv.core.database.TrackPreferenceEntity
import com.chakir.plexhubtv.domain.model.TrackPreference
import com.chakir.plexhubtv.domain.repository.TrackPreferenceRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackPreferenceRepositoryImpl @Inject constructor(
    private val trackPreferenceDao: TrackPreferenceDao,
) : TrackPreferenceRepository {

    override suspend fun getPreference(ratingKey: String, serverId: String): TrackPreference? {
        return trackPreferenceDao.getPreferenceSync(ratingKey, serverId)?.toDomain()
    }

    override suspend fun savePreference(preference: TrackPreference) {
        trackPreferenceDao.upsertPreference(preference.toEntity())
    }

    private fun TrackPreferenceEntity.toDomain() = TrackPreference(
        ratingKey = ratingKey,
        serverId = serverId,
        audioStreamId = audioStreamId,
        subtitleStreamId = subtitleStreamId,
    )

    private fun TrackPreference.toEntity() = TrackPreferenceEntity(
        ratingKey = ratingKey,
        serverId = serverId,
        audioStreamId = audioStreamId,
        subtitleStreamId = subtitleStreamId,
    )
}
