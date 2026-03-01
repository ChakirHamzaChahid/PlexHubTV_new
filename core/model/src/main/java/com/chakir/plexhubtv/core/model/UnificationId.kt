package com.chakir.plexhubtv.core.model

import java.text.Normalizer

/**
 * Single source of truth for unificationId calculation across all media sources.
 *
 * Two strategies:
 * - [calculateUnificationId]: For metadata-rich sources (Plex) — IMDb > TMDB > title fallback
 * - [calculateTitleUnificationId]: For title-only sources (Xtream) — normalized title + year
 *
 * These produce INTENTIONALLY different formats because they serve different matching contexts.
 * Plex items with IMDb/TMDB IDs will match via those IDs; Xtream items match via title normalization.
 */
object UnificationId {

    /**
     * Calculates a unificationId for metadata-rich sources (Plex, or any source with IMDb/TMDB).
     *
     * Priority: IMDb > TMDB > title+year fallback.
     * The title fallback uses simple normalization (lowercase, strip non-alnum).
     */
    fun calculate(imdbId: String?, tmdbId: String?, title: String?, year: Int?): String {
        return when {
            !imdbId.isNullOrBlank() -> "imdb://$imdbId"
            !tmdbId.isNullOrBlank() -> "tmdb://$tmdbId"
            else -> {
                val safeTitle = title?.lowercase()?.trim()?.replace(Regex("[^a-z0-9 ]"), "") ?: "unknown"
                "${safeTitle}_${year ?: 0}"
            }
        }
    }

    /**
     * Calculates a unificationId for title-only sources (Xtream).
     *
     * Uses thorough normalization: strip articles, accents, special chars.
     * Produces format: `title_normalized_year` or `title_normalized`.
     */
    fun calculateFromTitle(title: String, year: Int?): String {
        if (title == "Unknown") return ""
        val normalized = normalizeTitle(title)
            .lowercase()
            .replace(Regex("\\s+"), "_")
        return if (year != null) "title_${normalized}_$year" else "title_$normalized"
    }

    // --- Internal normalization (mirrors StringNormalizer.normalizeForSorting) ---

    private val ARTICLES = listOf(
        "the", "a", "an",
        "le", "la", "les",
        "l'", "un", "une", "des",
    )

    private fun normalizeTitle(title: String): String {
        if (title.isBlank()) return ""

        var normalized = title.trim()

        // Remove leading articles
        val lowerTitle = normalized.lowercase()
        for (article in ARTICLES) {
            if (lowerTitle.startsWith("$article ")) {
                normalized = normalized.substring(article.length + 1).trim()
                break
            }
        }

        // Strip accents (NFD decomposition + remove combining marks)
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
            .replace("\\p{M}".toRegex(), "")

        // Remove special characters (keep letters, digits, spaces)
        normalized = normalized.replace("[^\\p{L}\\p{N}\\s]".toRegex(), "")

        return normalized.uppercase().trim()
    }
}
