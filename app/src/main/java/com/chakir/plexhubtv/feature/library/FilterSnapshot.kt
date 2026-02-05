package com.chakir.plexhubtv.feature.library

// Data class to track filter state changes
data class FilterSnapshot(
    val genre: String?,
    val server: String?,
    val sort: String?,
    val isDescending: Boolean,
    val query: String
)
