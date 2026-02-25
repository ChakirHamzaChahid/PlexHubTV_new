package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chakir.plexhubtv.core.model.AgeRating
import com.chakir.plexhubtv.core.model.Profile
import com.chakir.plexhubtv.core.model.VideoQuality

/**
 * Entity Room pour les profils utilisateurs.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val avatarEmoji: String?,
    val isKidsProfile: Boolean,
    val ageRating: String,
    val autoPlayNext: Boolean,
    val preferredAudioLanguage: String?,
    val preferredSubtitleLanguage: String?,
    val preferredQuality: String,
    val createdAt: Long,
    val lastUsed: Long,
    val isActive: Boolean,
)

/**
 * Convertit une entité Room en modèle domain.
 */
fun ProfileEntity.toProfile(): Profile {
    return Profile(
        id = id,
        name = name,
        avatarUrl = avatarUrl,
        avatarEmoji = avatarEmoji,
        isKidsProfile = isKidsProfile,
        ageRating = AgeRating.valueOf(ageRating),
        autoPlayNext = autoPlayNext,
        preferredAudioLanguage = preferredAudioLanguage,
        preferredSubtitleLanguage = preferredSubtitleLanguage,
        preferredQuality = VideoQuality.valueOf(preferredQuality),
        createdAt = createdAt,
        lastUsed = lastUsed,
        isActive = isActive
    )
}

/**
 * Convertit un modèle domain en entité Room.
 */
fun Profile.toEntity(): ProfileEntity {
    return ProfileEntity(
        id = id,
        name = name,
        avatarUrl = avatarUrl,
        avatarEmoji = avatarEmoji,
        isKidsProfile = isKidsProfile,
        ageRating = ageRating.name,
        autoPlayNext = autoPlayNext,
        preferredAudioLanguage = preferredAudioLanguage,
        preferredSubtitleLanguage = preferredSubtitleLanguage,
        preferredQuality = preferredQuality.name,
        createdAt = createdAt,
        lastUsed = lastUsed,
        isActive = isActive
    )
}
