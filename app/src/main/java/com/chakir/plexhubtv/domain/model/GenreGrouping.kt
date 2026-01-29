package com.chakir.plexhubtv.domain.model

/**
 * Utility object defining genre grouping for UI.
 * Ported from AggregatorHubPlex.
 */
object GenreGrouping {
    // UI Label -> List of Keywords
    val GROUPS = mapOf(
        "All" to emptyList(), // "Tout"
        "Animation" to listOf("Animation", "Anime", "Japanimé", "Kaï", "Short"),
        "Action & Adventure" to listOf(
            "Action", "Adventure", "Aventure", "Martial Arts", "Action/Aventure"
        ), // "Action & Aventure"
        "Sci-Fi & Fantasy" to listOf(
             "Sci-Fi", "Science Fiction", "Fantasy", "Sci-Fi & Fantasy"
        ), // "Sci-Fi & Fantastique"
        "Family & Kids" to listOf("Familial", "Family", "Children", "Enfants"), // "Famille & Animaux"
        "Comedy" to listOf("Comedy"), // "Comédie"
        "Drama & Romance" to listOf("Drama", "Romance", "Soap", "Adult"), // "Drame & Romance"
        "Thriller & Horror" to listOf(
             "Thriller", "Horror", "Suspense", "Crime", "Mystery", "Film-Noir"
        ), // "Thriller & Horreur"
        "War & History" to listOf("War", "History", "Western", "Biography"), // "Guerre & Histoire"
        "Documentary & TV" to listOf(
             "Documentary", "Reality", "Game Show", "Talk", "News", 
             "Home And Garden", "Food", "Travel", "Sport", 
             "Music", "Musical", "Indie"
        ) // "Documentaire & TV"
    )

    val UI_LABELS = GROUPS.keys.toList()
}
