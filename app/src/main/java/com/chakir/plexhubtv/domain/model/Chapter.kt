package com.chakir.plexhubtv.domain.model

data class Chapter(
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val thumbUrl: String? = null
)
