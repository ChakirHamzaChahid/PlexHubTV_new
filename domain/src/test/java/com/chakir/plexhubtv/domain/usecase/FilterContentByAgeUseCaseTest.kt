package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.AgeRating
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.Profile
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class FilterContentByAgeUseCaseTest {

    private lateinit var useCase: FilterContentByAgeUseCase

    private fun mediaItem(rating: String?) = MediaItem(
        id = "s1_${rating ?: "null"}",
        ratingKey = rating ?: "null",
        serverId = "server1",
        title = "Item rated $rating",
        type = MediaType.Movie,
        contentRating = rating,
    )

    private fun profile(
        ageRating: AgeRating = AgeRating.ADULT,
        isKids: Boolean = false,
    ) = Profile(
        id = "profile1",
        name = "Test",
        ageRating = ageRating,
        isKidsProfile = isKids,
    )

    private val mixedItems = listOf(
        mediaItem("TP"),
        mediaItem("6+"),
        mediaItem("10+"),
        mediaItem("12+"),
        mediaItem("16+"),
        mediaItem("18+"),
        mediaItem("XXX"),
        mediaItem("NR"),
        mediaItem(null),
    )

    @Before
    fun setup() {
        useCase = FilterContentByAgeUseCase()
    }

    @Test
    fun `adult profile returns all items unfiltered`() {
        val result = useCase(mixedItems, profile(AgeRating.ADULT))

        assertThat(result).hasSize(mixedItems.size)
    }

    @Test
    fun `general profile blocks 6+ and above`() {
        val result = useCase(mixedItems, profile(AgeRating.GENERAL))

        val ratings = result.map { it.contentRating }
        assertThat(ratings).containsExactly("TP", "NR", null)
    }

    @Test
    fun `parental_7 profile allows up to 6+`() {
        val result = useCase(mixedItems, profile(AgeRating.PARENTAL_7))

        val ratings = result.map { it.contentRating }
        assertThat(ratings).containsExactly("TP", "6+", "NR", null)
    }

    @Test
    fun `parental_13 profile allows up to 12+`() {
        val result = useCase(mixedItems, profile(AgeRating.PARENTAL_13))

        val ratings = result.map { it.contentRating }
        assertThat(ratings).containsExactly("TP", "6+", "10+", "12+", "NR", null)
    }

    @Test
    fun `parental_16 profile allows up to 16+`() {
        val result = useCase(mixedItems, profile(AgeRating.PARENTAL_16))

        val ratings = result.map { it.contentRating }
        assertThat(ratings).containsExactly("TP", "6+", "10+", "12+", "16+", "NR", null)
    }

    @Test
    fun `kids profile caps at 7 even with higher ageRating`() {
        val result = useCase(mixedItems, profile(AgeRating.PARENTAL_16, isKids = true))

        val ratings = result.map { it.contentRating }
        assertThat(ratings).containsExactly("TP", "6+", "NR", null)
    }

    @Test
    fun `null contentRating items are always allowed`() {
        val items = listOf(mediaItem(null))

        val result = useCase(items, profile(AgeRating.GENERAL))

        assertThat(result).hasSize(1)
    }

    @Test
    fun `NR rated items are always allowed`() {
        val items = listOf(mediaItem("NR"))

        val result = useCase(items, profile(AgeRating.GENERAL))

        assertThat(result).hasSize(1)
    }

    @Test
    fun `XXX items blocked unless adult profile`() {
        val items = listOf(mediaItem("XXX"))

        assertThat(useCase(items, profile(AgeRating.PARENTAL_16))).isEmpty()
        assertThat(useCase(items, profile(AgeRating.ADULT))).hasSize(1)
    }

    @Test
    fun `TP rated items always allowed`() {
        val items = listOf(mediaItem("TP"))

        assertThat(useCase(items, profile(AgeRating.GENERAL))).hasSize(1)
    }

    @Test
    fun `empty list returns empty list`() {
        val result = useCase(emptyList(), profile(AgeRating.GENERAL))

        assertThat(result).isEmpty()
    }
}
