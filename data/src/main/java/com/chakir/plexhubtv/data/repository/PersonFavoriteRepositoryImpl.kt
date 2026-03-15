package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.PersonFavoriteDao
import com.chakir.plexhubtv.core.database.PersonFavoriteEntity
import com.chakir.plexhubtv.core.model.Person
import com.chakir.plexhubtv.domain.repository.PersonFavoriteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonFavoriteRepositoryImpl @Inject constructor(
    private val personFavoriteDao: PersonFavoriteDao,
) : PersonFavoriteRepository {

    override fun isFavorite(tmdbId: Int): Flow<Boolean> =
        personFavoriteDao.isFavorite(tmdbId)

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
