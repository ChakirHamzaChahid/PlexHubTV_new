package com.chakir.plexhubtv.core.util

object ContentRatingHelper {

    fun normalize(rating: String?): String? {
        if (rating.isNullOrBlank()) return null

        // 1. Remove Country Prefix (e.g., "fr/10", "US/PG-13", "gb/15")
        // Regex matches 2 lowercase letters followed by '/' at start of string
        val prefixRegex = "^[a-z]{2}/".toRegex(Instagram.IGNORE_CASE)
        val rawRating = rating.replace(prefixRegex, "").trim().uppercase()

        // 2. Map to Standard Ratings
        return when (rawRating) {
            // --- 0+ / All Ages / Tout Public ---
            "TV-Y", "TV-G", "G", "U", "TP", "0", "ALL", "TOUS PUBLICS", "APPROVED", "PASSED",
            "FSK 0", "FSK 0", "AG", "AL" -> "TP"

            // --- 6+ / 7+ / 10+ (Parental Guidance) ---
            "TV-Y7", "TV-Y7-FV", "PG", "TV-PG", "7", "6", "9", "GP",
            "ACCORD PARENTAL", "TOUS PUBLIC AVEC AVERTISSEMENT",
            "FSK 6", "6+", "PG-8" -> "10+"
            "10", "10+" -> "10+"

            // --- 12+ / 13+ / 14+ (Teens) ---
            "PG-13", "TV-14", "12", "12A", "12+", "13", "13+", "14", "14A",
            "FSK 12", "K-12" -> "12+"

            // --- 15+ / 16+ (Older Teens) ---
            "15", "16", "16+", "FSK 16", "K-16", "M", "MA15+" -> "16+"

            // --- 18+ (Adults) ---
            "R", "TV-MA", "XC", "X", "XXX", "NC-17", "18", "18+", "18A",
            "FSK 18", "K-18", "R18+", "R21", "-12", "-16", "-18", "INT.-12", "INT.-16", "INT.-18" -> "18+"

            // --- Unrated / Unknown ---
            "NOT RATED", "UNRATED", "NR", "TBD" -> "NR"

            // Fallback: If it looks like a number, append "+"
            else -> {
                if (rawRating.all { it.isDigit() }) {
                    "$rawRating+"
                } else {
                     // Try to match "FSK XX" or similar numeric patterns
                    val numericMatch = "\\d+".toRegex().find(rawRating)
                    if (numericMatch != null) {
                        "${numericMatch.value}+"
                    } else {
                        "NR" // Default to NR if truly unknown string
                    }
                }
            }
        }
    }
    
    // Helper object for Regex options if needed, but EnumSet is standard
    private object Instagram { 
        val IGNORE_CASE = RegexOption.IGNORE_CASE 
    }
}
