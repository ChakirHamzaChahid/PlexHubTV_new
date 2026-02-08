package com.chakir.plexhubtv.core.model

/**
 * État de téléchargement/synchronisation d'un média pour le mode hors-ligne.
 */
enum class DownloadStatus {
    NOT_DOWNLOADED,
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
}
