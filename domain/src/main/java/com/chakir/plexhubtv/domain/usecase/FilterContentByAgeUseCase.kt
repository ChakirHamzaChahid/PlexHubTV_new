package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.AgeRating
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.Profile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Filters media items based on the active profile's age rating.
 *
 * Uses the normalized [MediaItem.contentRating] field (set by MediaMapper via ContentRatingHelper)
 * to determine whether each item is appropriate for the profile's age restriction.
 */
@Singleton
class FilterContentByAgeUseCase @Inject constructor() {

    operator fun invoke(items: List<MediaItem>, profile: Profile): List<MediaItem> {
        val maxAge = getMaxAge(profile)
        if (maxAge >= 99) return items
        return items.filter { isAllowed(it.contentRating, maxAge) }
    }

    fun isItemAllowed(item: MediaItem, profile: Profile): Boolean {
        val maxAge = getMaxAge(profile)
        if (maxAge >= 99) return true
        return isAllowed(item.contentRating, maxAge)
    }

    private fun getMaxAge(profile: Profile): Int {
        val profileMax = when (profile.ageRating) {
            AgeRating.GENERAL -> 0
            AgeRating.PARENTAL_7 -> 7
            AgeRating.PARENTAL_13 -> 13
            AgeRating.PARENTAL_16 -> 16
            AgeRating.ADULT -> 99
        }
        return if (profile.isKidsProfile) minOf(profileMax, 7) else profileMax
    }

    private fun isAllowed(contentRating: String?, maxAge: Int): Boolean {
        if (contentRating.isNullOrBlank()) return true
        return when (contentRating) {
            "TP" -> true
            "NR" -> true
            "XXX" -> maxAge >= 99
            else -> {
                val age = contentRating.replace("+", "").toIntOrNull()
                age == null || age <= maxAge
            }
        }
    }
}
