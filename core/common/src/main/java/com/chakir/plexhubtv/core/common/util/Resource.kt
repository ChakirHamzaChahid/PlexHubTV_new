package com.chakir.plexhubtv.core.common.util

/**
 * Classe scellée (Sealed Class) générique pour gérer les états de chargement des données (State Management).
 * Utilisée couramment avec les Flow pour remonter l'état de l'UI (Loading -> Success/Error).
 */
sealed class Resource<T>(val data: T? = null, val message: String? = null) {
    class Success<T>(data: T) : Resource<T>(data)

    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)

    class Loading<T>(data: T? = null) : Resource<T>(data)
}
