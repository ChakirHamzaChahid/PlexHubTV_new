package com.chakir.plexhubtv.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository pour gérer la synchronisation du statut de lecture en mode hors-ligne.
 * 
 * Sa responsabilité est de stocker les actions (Marquer comme vu, progression)
 * lorsque l'appareil n'a pas internet, et de les synchroniser avec le serveur
 * dès que la connexion est rétablie.
 */
interface OfflineWatchSyncRepository {
    
    /** Seuil (pourcentage) au-delà duquel un élément est considéré comme "Vu" localement. */
    val WATCHED_THRESHOLD: Double
        get() = 0.90
    
    /** Nombre maximum de tentatives de synchronisation avant abandon. */
    val MAX_SYNC_ATTEMPTS: Int
        get() = 5
    
    /**
     * Met en file d'attente une mise à jour de progression (Resume Point).
     * @param serverId ID du serveur.
     * @param ratingKey ID du média.
     * @param viewOffset Position actuelle en ms.
     * @param duration Durée totale en ms.
     */
    suspend fun queueProgressUpdate(
        serverId: String,
        ratingKey: String,
        viewOffset: Long,
        duration: Long
    )
    
    /** Met en file d'attente une action manuelle "Marquer comme vu". */
    suspend fun queueMarkWatched(serverId: String, ratingKey: String)
    
    /** Met en file d'attente une action manuelle "Marquer comme non vu". */
    suspend fun queueMarkUnwatched(serverId: String, ratingKey: String)
    
    /**
     * Récupère le statut de lecture local (optimiste).
     * @return true (Vu), false (Non vu), ou null (Pas de modif locale).
     */
    suspend fun getLocalWatchStatus(globalKey: String): Boolean?
    
    /** Batch : Récupère les statuts locaux pour plusieurs éléments. */
    suspend fun getLocalWatchStatusesBatched(globalKeys: Set<String>): Map<String, Boolean?>
    
    /** Récupère la position de lecture locale (Resume Point). */
    suspend fun getLocalViewOffset(globalKey: String): Long?
    
    /** Nombre d'éléments en attente de synchro. */
    suspend fun getPendingSyncCount(): Int
    
    /** Observe le nombre d'éléments en attente. */
    fun observePendingSyncCount(): Flow<Int>
    
    /** Pousse (Push) toutes les actions en attente vers les serveurs respectifs. */
    suspend fun syncPendingItems(): Result<Int>
    
    /** Tire (Pull) les derniers états "Vu" depuis les serveurs pour mettre à jour le cache local. */
    suspend fun syncWatchStatesFromServer(): Result<Int>
    
    /** Exécute une synchro complète bidirectionnelle (Push local, puis Pull distant). */
    suspend fun performBidirectionalSync(force: Boolean = false): Result<Unit>
    
    /** Efface toutes les actions en attente (Reset). */
    suspend fun clearAll()
    
    /** Vérifie si la progression justifie le statut "Vu" (> 90%). */
    fun isWatchedByProgress(viewOffset: Long, duration: Long): Boolean =
        if (duration == 0L) false else (viewOffset.toDouble() / duration) >= WATCHED_THRESHOLD
}
