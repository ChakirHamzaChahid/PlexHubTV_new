package com.chakir.plexhubtv.domain.model

data class Marker(
    val title: String = "Marker",
    val type: String, // 'intro' or 'credits'
    val startTime: Long,
    val endTime: Long
)
