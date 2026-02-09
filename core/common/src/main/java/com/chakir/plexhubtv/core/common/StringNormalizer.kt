package com.chakir.plexhubtv.core.common

import java.text.Normalizer

/**
 * Utility for normalizing strings, particularly for alphabetical sorting.
 */
object StringNormalizer {

    // Common articles to remove when sorting (French and English)
    private val ARTICLES = listOf(
        "the", "a", "an",        // English
        "le", "la", "les",       // French
        "l'", "un", "une", "des" // French
    )

    /**
     * Normalizes a title for alphabetical sorting.
     *
     * Normalization steps:
     * 1. Remove leading articles (The, Le, La, etc.)
     * 2. Strip accents (é→e, ç→c, à→a, ñ→n, etc.)
     * 3. Remove special characters ([, ], !, ?, :, etc.)
     * 4. Convert to uppercase for case-insensitive sorting
     * 5. Trim whitespace
     *
     * Examples:
     * - "Les Évadés" → "EVADES"
     * - "[REC]" → "REC"
     * - "Cut!" → "CUT"
     * - "The Matrix" → "MATRIX"
     * - "Été indien" → "ETE INDIEN"
     *
     * @param title The original title
     * @return Normalized title suitable for sorting
     */
    fun normalizeForSorting(title: String): String {
        if (title.isBlank()) return ""

        var normalized = title.trim()

        // 1. Remove leading articles
        val lowerTitle = normalized.lowercase()
        for (article in ARTICLES) {
            if (lowerTitle.startsWith("$article ")) {
                normalized = normalized.substring(article.length + 1).trim()
                break
            }
        }

        // 2. Strip accents using Java's Normalizer
        // NFD = Canonical Decomposition (é → e + combining accent mark)
        // Then remove all combining marks (\p{M})
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
            .replace("\\p{M}".toRegex(), "")

        // 3. Remove special characters (keep only letters, digits, and spaces)
        // \p{L} = any Unicode letter
        // \p{N} = any Unicode digit
        normalized = normalized.replace("[^\\p{L}\\p{N}\\s]".toRegex(), "")

        // 4. Convert to uppercase and trim
        return normalized.uppercase().trim()
    }
}
