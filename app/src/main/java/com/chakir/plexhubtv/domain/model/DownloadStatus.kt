package com.chakir.plexhubtv.domain.model

/**
 * État de téléchargement/synchronisation d'un média pour le mode hors-ligne.
 */
enum class DownloadStatus {
    /** Non téléchargé, mais disponible sur le serveur. */
    NotDownloaded,
    
    /** En file d'attente de téléchargement (WorkManager). */
    Pending,
    
    /** Téléchargement actif. */
    Downloading,
    
    /** Téléchargement terminé, fichier local disponible. */
    Downloaded,
    
    /** Echec du téléchargement (erreur réseau/disque). */
    Failed
}
