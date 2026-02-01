package com.chakir.plexhubtv.core.util

/**
 * Utilitaire pour normaliser les classifications par âge (Content Ratings).
 *
 * Responsabilité :
 * - Standardiser les ratings hétérogènes venant de divers pays/agents (ex: "fr/10", "US/PG-13", "FSK 16").
 * - Convertir en un ensemble restreint de catégories compréhensibles : TP, 6+, 10+, 12+, 16+, 18+, XXX.
 */
object ContentRatingHelper {

    /**
     * Normalise une chaîne de classification brute.
     *
     * @param rating La chaîne brute venant de l'API Plex (peut être null).
     * @return Une chaîne normalisée (ex: "12+") ou "NR" (Not Rated) en cas d'échec.
     */
    fun normalize(rating: String?): String? {
        if (rating.isNullOrBlank()) return null

        // 1. Suppression du préfixe pays (ex: "fr/10", "use/PG-13")
        // Regex : Détecte deux lettres minuscules suivies d'un slash au début.
        val prefixRegex = "^[a-z]{2}/".toRegex(RegexOption.IGNORE_CASE)
        val rawRating = rating.replace(prefixRegex, "").trim().uppercase()

        // 2. Mapping vers les standards définis
        return when (rawRating) {
        // --- TP (Tout Public / 0+ / All Ages) ---
        "TV-Y", "TV-G", "G", "U", "TP", "0", "ALL", "TOUS PUBLICS", 
        "APPROVED", "PASSED", "FSK 0", "AG", "AL", "LIBRE" 
            -> "TP"

        // --- 6+ (Jeune Public avec Avertissement) ---
        "TV-Y7", "TV-Y7-FV", "6", "7", "6+", "FSK 6", "A" 
            -> "6+"

        // --- 10+ (Parental Guidance) ---
        "PG", "TV-PG", "9", "10", "10+", "GP", "PG-8", "PG-12",
        "ACCORD PARENTAL", "TOUS PUBLIC AVEC AVERTISSEMENT" 
            -> "10+"

        // --- 12+ (Adolescents) ---
        "PG-13", "TV-14", "12", "-12", "12A", "12+", "13", "13+", 
        "INT.-12", "FSK 12", "K-12", "T" 
            -> "12+"

        // --- 14+ (Grands Adolescents) ---
        "14", "14A", "14+" 
            -> "14+"

        // --- 15+ (Audience Mature) ---
        "15", "15+", "R15+", "M", "MA15+" 
            -> "15+"

        // --- 16+ (Ados Restreint) ---
        "16", "16+", "FSK 16", "K-16", "INT.-16", "-16" 
            -> "16+"

        // --- 18+ (Adultes Seulement) ---
        "R", "TV-MA", "NC-17", "18", "18+", "18A",
        "FSK 18", "K-18", "R18+", "R21", "-18", "INT.-18", "AO" 
            -> "18+"

        // --- XXX (Contenu Explicite) ---
        "X", "XXX", "XC" 
            -> "XXX"

        // --- NR (Non Classé / Inconnu) ---
        "NOT RATED", "UNRATED", "NR", "TBD", "N/A", "UNKNOWN", "NONE" 
            -> "NR"

        // --- Logique de repli (Fallback) ---
        else -> {
            // 1. Nombre pur (ex: "8" -> "8+")
            if (rawRating.all { it.isDigit() }) {
                return "${rawRating}+"
            }
            
            // 2. Patterns spécifiques "FSK XX", "K-XX" (extraction du nombre)
            val fskMatch = "FSK\\s*(\\d+)".toRegex().find(rawRating)
            if (fskMatch != null) {
                return "${fskMatch.groupValues[1]}+"
            }
            
            val kMatch = "K-(\\d+)".toRegex().find(rawRating)
            if (kMatch != null) {
                return "${kMatch.groupValues[1]}+"
            }
            
            val intMatch = "INT\\.?-(\\d+)".toRegex().find(rawRating)
            if (intMatch != null) {
                return "${intMatch.groupValues[1]}+"
            }
            
            // 3. Pattern "-XX" (ex: "-12" -> "12+")
            val dashMatch = "-(\\d+)".toRegex().find(rawRating)
            if (dashMatch != null) {
                return "${dashMatch.groupValues[1]}+"
            }
            
            // 4. Dernier recours : tout nombre trouvé
            val numericMatch = "\\d+".toRegex().find(rawRating)
            if (numericMatch != null) {
                return "${numericMatch.value}+"
            }
            
            // 5. Défaut absolu
            "NR"
        }
    }
    }
    
}
