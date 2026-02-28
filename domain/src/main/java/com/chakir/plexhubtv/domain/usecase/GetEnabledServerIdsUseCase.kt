package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.repository.LibraryRepository
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetEnabledServerIdsUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(): List<String> {
        val syncedIds = libraryRepository.getDistinctServerIds().toSet()
        val excludedIds = settingsRepository.excludedServerIds.first()
        return syncedIds.filter { it !in excludedIds }
    }
}
