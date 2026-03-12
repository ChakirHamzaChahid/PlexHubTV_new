package com.chakir.plexhubtv.core.model

data class Person(
    val id: Int,
    val name: String,
    val biography: String?,
    val birthday: String?,
    val deathday: String?,
    val placeOfBirth: String?,
    val photoUrl: String?,
    val knownFor: String?,
    val castCredits: List<PersonCredit> = emptyList(),
    val crewCredits: List<PersonCredit> = emptyList(),
)

data class PersonCredit(
    val id: Int,
    val title: String,
    val mediaType: String, // "movie" or "tv"
    val character: String?,
    val job: String?,
    val posterUrl: String?,
    val voteAverage: Double?,
    val year: String?,
)
