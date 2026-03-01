package com.chakir.plexhubtv.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UnificationIdTest {

    // --- calculate() tests (Plex / metadata-rich sources) ---

    @Test
    fun `calculate prefers IMDb over TMDB`() {
        val result = UnificationId.calculate(
            imdbId = "tt1111111",
            tmdbId = "22222",
            title = "Test Movie",
            year = 2020,
        )
        assertThat(result).isEqualTo("imdb://tt1111111")
    }

    @Test
    fun `calculate uses TMDB when IMDb absent`() {
        val result = UnificationId.calculate(
            imdbId = null,
            tmdbId = "33333",
            title = "Test Movie",
            year = 2020,
        )
        assertThat(result).isEqualTo("tmdb://33333")
    }

    @Test
    fun `calculate uses TMDB when IMDb blank`() {
        val result = UnificationId.calculate(
            imdbId = "",
            tmdbId = "44444",
            title = "Test Movie",
            year = 2020,
        )
        assertThat(result).isEqualTo("tmdb://44444")
    }

    @Test
    fun `calculate falls back to title and year`() {
        val result = UnificationId.calculate(
            imdbId = null,
            tmdbId = null,
            title = "Test Movie!",
            year = 2020,
        )
        assertThat(result).isEqualTo("test movie_2020")
    }

    @Test
    fun `calculate fallback strips special characters`() {
        val result = UnificationId.calculate(
            imdbId = null,
            tmdbId = null,
            title = "100 Girls (2000)",
            year = 2000,
        )
        assertThat(result).isEqualTo("100 girls 2000_2000")
    }

    @Test
    fun `calculate fallback uses 0 for null year`() {
        val result = UnificationId.calculate(
            imdbId = null,
            tmdbId = null,
            title = "Unknown Film",
            year = null,
        )
        assertThat(result).isEqualTo("unknown film_0")
    }

    @Test
    fun `calculate fallback uses unknown for null title`() {
        val result = UnificationId.calculate(
            imdbId = null,
            tmdbId = null,
            title = null,
            year = 2020,
        )
        assertThat(result).isEqualTo("unknown_2020")
    }

    // --- calculateFromTitle() tests (Xtream / title-only sources) ---

    @Test
    fun `calculateFromTitle with year`() {
        val result = UnificationId.calculateFromTitle("The Matrix", 1999)
        assertThat(result).isEqualTo("title_matrix_1999")
    }

    @Test
    fun `calculateFromTitle without year`() {
        val result = UnificationId.calculateFromTitle("The Matrix", null)
        assertThat(result).isEqualTo("title_matrix")
    }

    @Test
    fun `calculateFromTitle strips accents`() {
        val result = UnificationId.calculateFromTitle("Les Evades", 1994)
        assertThat(result).isEqualTo("title_evades_1994")
    }

    @Test
    fun `calculateFromTitle strips special characters`() {
        val result = UnificationId.calculateFromTitle("[REC]", 2007)
        assertThat(result).isEqualTo("title_rec_2007")
    }

    @Test
    fun `calculateFromTitle removes French articles`() {
        val result = UnificationId.calculateFromTitle("Le Parrain", 1972)
        assertThat(result).isEqualTo("title_parrain_1972")
    }

    @Test
    fun `calculateFromTitle returns empty for Unknown`() {
        val result = UnificationId.calculateFromTitle("Unknown", 2020)
        assertThat(result).isEmpty()
    }

    @Test
    fun `calculateFromTitle normalizes whitespace`() {
        val result = UnificationId.calculateFromTitle("Star  Wars", 1977)
        assertThat(result).isEqualTo("title_star_wars_1977")
    }
}
