package com.chakir.plexhubtv.core.database

import androidx.room.Entity

@Entity(
    tableName = "remote_keys",
    primaryKeys = ["libraryKey", "filter", "sortOrder", "offset"]
)
data class RemoteKey(
    val libraryKey: String,
    val filter: String,
    val sortOrder: String,
    val offset: Int,
    val prevKey: Int?,
    val nextKey: Int?
)
