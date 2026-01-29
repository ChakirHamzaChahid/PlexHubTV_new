package com.chakir.plexhubtv.data.model

import com.google.gson.annotations.SerializedName

data class GenericPlexResponse(
    @SerializedName("MediaContainer") val mediaContainer: MediaContainer?
)
