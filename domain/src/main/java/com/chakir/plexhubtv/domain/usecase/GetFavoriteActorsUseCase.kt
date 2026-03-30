package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.FavoriteActor
import com.chakir.plexhubtv.domain.repository.PersonFavoriteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFavoriteActorsUseCase
    @Inject
    constructor(
        private val personFavoriteRepository: PersonFavoriteRepository,
    ) {
        operator fun invoke(): Flow<List<FavoriteActor>> {
            return personFavoriteRepository.getAllFavorites()
        }
    }
