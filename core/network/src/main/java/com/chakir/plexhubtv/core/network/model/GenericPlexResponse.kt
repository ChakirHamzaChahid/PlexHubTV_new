package com.chakir.plexhubtv.core.network.model

import com.google.gson.annotations.SerializedName

/**
 * Wrapper générique pour la réponse JSON standard de Plex.
 */
data class GenericPlexResponse(
    @SerializedName("MediaContainer") val mediaContainer: MediaContainer?,
)
