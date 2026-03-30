package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.PersonFavoriteDao
import com.chakir.plexhubtv.core.database.PersonFavoriteEntity
import com.chakir.plexhubtv.core.model.FavoriteActor
import com.chakir.plexhubtv.core.model.Person
import com.chakir.plexhubtv.domain.repository.PersonFavoriteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonFavoriteRepositoryImpl @Inject constructor(
    private val personFavoriteDao: PersonFavoriteDao,
) : PersonFavoriteRepository {

    override fun isFavorite(tmdbId: Int): Flow<Boolean> =
        personFavoriteDao.isFavorite(tmdbId)

    override fun getAllFavorites(): Flow<List<FavoriteActor>> =
        personFavoriteDao.getAllFavorites().map { entities ->
            entities.map { entity ->
                FavoriteActor(
                    tmdbId = entity.tmdbId,
                    name = entity.name,
                    photoUrl = entity.profilePath,
                    knownFor = entity.knownFor,
                    addedAt = entity.addedAt,
                )
            }
        }

    override suspend fun toggleFavorite(person: Person) {
        val isFav = personFavoriteDao.isFavorite(person.id).first()
        if (isFav) {
            personFavoriteDao.delete(person.id)
        } else {
            personFavoriteDao.insert(
                PersonFavoriteEntity(
                    tmdbId = person.id,
                    name = person.name,
                    profilePath = person.photoUrl,
                    knownFor = person.knownFor,
                )
            )
        }
    }
}
