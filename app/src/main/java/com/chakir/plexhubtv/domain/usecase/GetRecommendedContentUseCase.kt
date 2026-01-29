package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.util.Resource
import com.chakir.plexhubtv.domain.model.Hub
import com.chakir.plexhubtv.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class GetRecommendedContentUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(): Flow<Resource<List<Hub>>> = 
        mediaRepository.getUnifiedHubs()
            .map { hubs -> Resource.Success(hubs) as Resource<List<Hub>> }
            .catch { e -> emit(Resource.Error(e.message ?: "Unknown Error")) }
            .onStart { emit(Resource.Loading()) }
}
