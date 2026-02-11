package com.chakir.plexhubtv.di.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContentRatingHelperTest {
    @Test
    fun `normalize - handles regular ratings`() {
        assertThat(ContentRatingHelper.normalize("G")).isEqualTo("TP")
        assertThat(ContentRatingHelper.normalize("PG")).isEqualTo("10+")
        assertThat(ContentRatingHelper.normalize("PG-13")).isEqualTo("12+")
        assertThat(ContentRatingHelper.normalize("R")).isEqualTo("18+")
    }

    @Test
    fun `normalize - removes country prefix`() {
        assertThat(ContentRatingHelper.normalize("fr/10")).isEqualTo("10+")
        assertThat(ContentRatingHelper.normalize("us/PG-13")).isEqualTo("12+")
        assertThat(ContentRatingHelper.normalize("fr/Tous publics")).isEqualTo("TP")
    }

    @Test
    fun `normalize - handles numeric ratings`() {
        assertThat(ContentRatingHelper.normalize("12")).isEqualTo("12+")
        assertThat(ContentRatingHelper.normalize("16")).isEqualTo("16+")
        assertThat(ContentRatingHelper.normalize("18")).isEqualTo("18+")
    }

    @Test
    fun `normalize - handles FSK and other prefixes`() {
        assertThat(ContentRatingHelper.normalize("FSK 16")).isEqualTo("16+")
        assertThat(ContentRatingHelper.normalize("K-12")).isEqualTo("12+")
        assertThat(ContentRatingHelper.normalize("INT.-18")).isEqualTo("18+")
        assertThat(ContentRatingHelper.normalize("-12")).isEqualTo("12+")
    }

    @Test
    fun `normalize - returns null for empty input`() {
        assertThat(ContentRatingHelper.normalize(null)).isNull()
        assertThat(ContentRatingHelper.normalize("")).isNull()
    }

    @Test
    fun `normalize - returns NR for unknown strings`() {
        assertThat(ContentRatingHelper.normalize("Unknown")).isEqualTo("NR")
        assertThat(ContentRatingHelper.normalize("NOT RATED")).isEqualTo("NR")
    }
}
