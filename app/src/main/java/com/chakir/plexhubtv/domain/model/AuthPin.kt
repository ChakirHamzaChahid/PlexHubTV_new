package com.chakir.plexhubtv.domain.model

data class AuthPin(
    val id: String,
    val code: String,
    val url: String = "https://plex.tv/link"
)
