package com.chakir.plexhubtv.core.model

/**
 * Représente un profil utilisateur avec ses préférences et paramètres.
 *
 * Les profils permettent une expérience personnalisée par utilisateur:
 * - Historique de visionnage séparé
 * - Préférences de lecture individuelles
 * - Favoris et watchlist personnels
 * - Contrôle parental optionnel
 */
data class Profile(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val avatarEmoji: String? = null,
    val isKidsProfile: Boolean = false,
    val ageRating: AgeRating = AgeRating.GENERAL,
    val autoPlayNext: Boolean = true,
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val preferredQuality: VideoQuality = VideoQuality.AUTO,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis(),
    val isActive: Boolean = false,
)

/**
 * Classifications d'âge pour le contrôle parental
 */
enum class AgeRating {
    GENERAL,      // Tout public
    PARENTAL_7,   // 7+
    PARENTAL_13,  // 13+
    PARENTAL_16,  // 16+
    ADULT,        // 18+
}

/**
 * Qualités vidéo disponibles
 */
enum class VideoQuality {
    AUTO,         // Adaptation automatique
    SD,           // 480p
    HD,           // 720p
    FULL_HD,      // 1080p
    UHD_4K,       // 2160p
}
