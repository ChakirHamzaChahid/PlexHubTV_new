package com.chakir.plexhubtv.core.model

/**
 * Sealed class représentant toutes les erreurs possibles de l'application.
 * Permet une gestion centralisée et typée des erreurs.
 */
sealed class AppError(
    open val message: String? = null,
    open val cause: Throwable? = null
) {
    /**
     * Erreurs réseau
     */
    sealed class Network(message: String? = null, cause: Throwable? = null) : AppError(message, cause) {
        data class NoConnection(override val message: String? = null) : Network(message)
        data class Timeout(override val message: String? = null) : Network(message)
        data class ServerError(override val message: String? = null, override val cause: Throwable? = null) : Network(message, cause)
        data class NotFound(override val message: String? = null) : Network(message)
        data class Unauthorized(override val message: String? = null) : Network(message)
    }

    /**
     * Erreurs d'authentification
     */
    sealed class Auth(message: String? = null, cause: Throwable? = null) : AppError(message, cause) {
        data class InvalidToken(override val message: String? = null) : Auth(message)
        data class SessionExpired(override val message: String? = null) : Auth(message)
        data class NoServersFound(override val message: String? = null) : Auth(message)
        data class PinGenerationFailed(override val message: String? = null, override val cause: Throwable? = null) : Auth(message, cause)
    }

    /**
     * Erreurs de médias
     */
    sealed class Media(message: String? = null, cause: Throwable? = null) : AppError(message, cause) {
        data class NotFound(override val message: String? = null) : Media(message)
        data class LoadFailed(override val message: String? = null, override val cause: Throwable? = null) : Media(message, cause)
        data class NoPlayableContent(override val message: String? = null) : Media(message)
        data class UnsupportedFormat(override val message: String? = null) : Media(message)
    }

    /**
     * Erreurs de playback
     */
    sealed class Playback(message: String? = null, cause: Throwable? = null) : AppError(message, cause) {
        data class InitializationFailed(override val message: String? = null, override val cause: Throwable? = null) : Playback(message, cause)
        data class StreamingError(override val message: String? = null, override val cause: Throwable? = null) : Playback(message, cause)
        data class CodecNotSupported(override val message: String? = null) : Playback(message)
        data class DrmError(override val message: String? = null, override val cause: Throwable? = null) : Playback(message, cause)
    }

    /**
     * Erreurs de recherche
     */
    sealed class Search(message: String? = null, cause: Throwable? = null) : AppError(message, cause) {
        data class QueryTooShort(override val message: String? = null) : Search(message)
        data class NoResults(override val message: String? = null) : Search(message)
        data class SearchFailed(override val message: String? = null, override val cause: Throwable? = null) : Search(message, cause)
    }

    /**
     * Erreurs de cache/stockage
     */
    sealed class Storage(message: String? = null, cause: Throwable? = null) : AppError(message, cause) {
        data class DiskFull(override val message: String? = null) : Storage(message)
        data class ReadError(override val message: String? = null, override val cause: Throwable? = null) : Storage(message, cause)
        data class WriteError(override val message: String? = null, override val cause: Throwable? = null) : Storage(message, cause)
    }

    /**
     * Erreurs génériques
     */
    data class Unknown(override val message: String? = null, override val cause: Throwable? = null) : AppError(message, cause)
    data class Validation(override val message: String? = null) : AppError(message)
}

/**
 * Convertit une exception en AppError approprié
 */
fun Throwable.toAppError(): AppError {
    return when (this) {
        is java.net.UnknownHostException -> AppError.Network.NoConnection(this.message)
        is java.net.SocketTimeoutException -> AppError.Network.Timeout(this.message)
        is java.io.IOException -> AppError.Network.ServerError(this.message, this)
        else -> AppError.Unknown(this.message, this)
    }
}
