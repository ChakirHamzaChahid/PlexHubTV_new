package com.chakir.plexhubtv.core.model

data class FavoriteActor(
    val tmdbId: Int,
    val name: String,
    val photoUrl: String?,
    val knownFor: String?,
    val addedAt: Long,
)
