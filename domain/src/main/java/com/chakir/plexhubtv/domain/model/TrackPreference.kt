package com.chakir.plexhubtv.domain.model

data class TrackPreference(
    val ratingKey: String,
    val serverId: String,
    val audioStreamId: String?,
    val subtitleStreamId: String?,
)
