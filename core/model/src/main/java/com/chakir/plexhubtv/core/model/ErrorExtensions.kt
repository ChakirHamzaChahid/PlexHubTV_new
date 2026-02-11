package com.chakir.plexhubtv.core.model

/**
 * Extension pour convertir les erreurs techniques en messages utilisateur compréhensibles.
 */
fun AppError.toUserMessage(): String {
    return when (this) {
        // Erreurs réseau
        is AppError.Network.NoConnection -> "Impossible de se connecter au réseau. Vérifiez votre connexion Internet."
        is AppError.Network.Timeout -> "La requête a pris trop de temps. Veuillez réessayer."
        is AppError.Network.ServerError -> message ?: "Erreur serveur. Veuillez réessayer plus tard."
        is AppError.Network.NotFound -> "Contenu introuvable."
        is AppError.Network.Unauthorized -> "Accès non autorisé. Veuillez vous reconnecter."

        // Erreurs d'authentification
        is AppError.Auth.InvalidToken -> "Token d'authentification invalide. Veuillez vous reconnecter."
        is AppError.Auth.SessionExpired -> "Votre session a expiré. Veuillez vous reconnecter."
        is AppError.Auth.NoServersFound -> "Aucun serveur Plex trouvé. Vérifiez votre compte Plex."
        is AppError.Auth.PinGenerationFailed -> "Impossible de générer le code PIN. Veuillez réessayer."

        // Erreurs de médias
        is AppError.Media.NotFound -> "Média introuvable."
        is AppError.Media.LoadFailed -> message ?: "Impossible de charger le média. Veuillez réessayer."
        is AppError.Media.NoPlayableContent -> "Aucun contenu lisible trouvé pour ce média."
        is AppError.Media.UnsupportedFormat -> "Format de média non supporté."

        // Erreurs de playback
        is AppError.Playback.InitializationFailed -> "Impossible d'initialiser le lecteur. Veuillez réessayer."
        is AppError.Playback.StreamingError -> message ?: "Erreur de lecture. Vérifiez votre connexion."
        is AppError.Playback.CodecNotSupported -> "Codec vidéo non supporté par votre appareil."
        is AppError.Playback.DrmError -> "Erreur de protection DRM. Impossible de lire ce contenu."

        // Erreurs de recherche
        is AppError.Search.QueryTooShort -> "Votre recherche doit contenir au moins 2 caractères."
        is AppError.Search.NoResults -> "Aucun résultat trouvé pour votre recherche."
        is AppError.Search.SearchFailed -> "La recherche a échoué. Veuillez réessayer."

        // Erreurs de stockage
        is AppError.Storage.DiskFull -> "Espace de stockage insuffisant."
        is AppError.Storage.ReadError -> "Impossible de lire les données du cache."
        is AppError.Storage.WriteError -> "Impossible d'écrire dans le cache."

        // Erreurs génériques
        is AppError.Validation -> message ?: "Données invalides."
        is AppError.Unknown -> message ?: "Une erreur inattendue s'est produite. Veuillez réessayer."
    }
}

/**
 * Détermine si l'erreur est critique et nécessite une action utilisateur immédiate
 */
fun AppError.isCritical(): Boolean {
    return when (this) {
        is AppError.Auth.SessionExpired,
        is AppError.Auth.InvalidToken,
        is AppError.Network.Unauthorized,
        is AppError.Storage.DiskFull -> true
        else -> false
    }
}

/**
 * Détermine si l'erreur peut être résolue par un retry
 */
fun AppError.isRetryable(): Boolean {
    return when (this) {
        is AppError.Network.NoConnection,
        is AppError.Network.Timeout,
        is AppError.Network.ServerError,
        is AppError.Media.LoadFailed,
        is AppError.Playback.StreamingError,
        is AppError.Search.SearchFailed -> true
        else -> false
    }
}
