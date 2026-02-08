package com.chakir.plexhubtv.core.model

/**
 * Objet utilitaire définissant le regroupement des genres pour l'UI.
 * Porté depuis l'ancien code AggregatorHubPlex.
 *
 * Permet de réduire la liste de 200+ genres Plex en 15 catégories principales
 * claires pour l'utilisateur (ex: "Action & Aventure", "Animation", etc.).
 */
object GenreGrouping {
    // Label UI -> Liste de mots-clés de genres Plex associés
    val GROUPS =
        mapOf(
            "Toutes" to emptyList(),
            "Action & Aventure" to
                listOf(
                    "Action", "Adventure", "Aventure", "Action & Adventure", "Action/Adventure",
                    "Action/Aventure", "Aventure;Action", "Martial Arts",
                ),
            "Animation & Anime" to
                listOf(
                    "Animation", "Anime", "Japanimé", "Kaï", "Short", "Japanime",
                ),
            "Sci-Fi & Fantastique" to
                listOf(
                    "Sci-Fi", "Science Fiction", "Science-Fiction", "Fantasy", "Fantastique",
                    "Sci-Fi & Fantasy", "Science-Fiction & Fantastique",
                ),
            "Comédie" to listOf("Comedy", "Comédie"),
            "Drame & Romance" to
                listOf(
                    "Drama", "Drame", "Romance", "Soap", "Adult", "Mini-Series", "TV Movie", "Téléfilm",
                ),
            "Thriller & Mystère" to
                listOf(
                    "Thriller", "Suspense", "Crime", "Mystery", "Mystère", "Film-Noir",
                ),
            "Horreur" to listOf("Horror", "Horreur"),
            "Famille & Enfants" to
                listOf(
                    "Family", "Familial", "Children", "Enfants", "Enfants et famille",
                    "Enfants;International;Bonne humeur;Amusant", "Animation;Enfants",
                ),
            "Documentaire & Histoire" to
                listOf(
                    "Documentary", "Documentaire", "History", "Histoire", "Biography", "War", "Guerre", "War & Politics",
                ),
            "Réalité & Talk" to
                listOf(
                    "Reality", "Game Show", "Talk", "Talk Show", "Awards Show", "News",
                ),
            "Musique & Spectacles" to listOf("Music", "Musique", "Musical"),
            "Loisirs & Sports" to
                listOf(
                    "Food", "Travel", "Sport", "Home and Garden",
                ),
            "Western & Divers" to
                listOf(
                    "Western", "Indie", "3d", "faith based",
                ),
        )

    val UI_LABELS = GROUPS.keys.toList()
}
