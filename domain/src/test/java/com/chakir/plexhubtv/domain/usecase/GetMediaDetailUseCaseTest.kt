package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetMediaDetailUseCaseTest {
    private val mediaDetailRepository: MediaDetailRepository = mockk()
    private val useCase = GetMediaDetailUseCase(mediaDetailRepository)

    @Test
    fun `invoke returns failure when getMediaDetail fails`() =
        runTest {
            // Given
            val ratingKey = "123"
            val serverId = "server1"
            val exception = Exception("Network error")
            coEvery { mediaDetailRepository.getMediaDetail(ratingKey, serverId) } returns Result.failure(exception)

            // When
            val result = useCase(ratingKey, serverId).first()

            // Then
            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isEqualTo(exception)
            coVerify(exactly = 0) { mediaDetailRepository.getShowSeasons(any(), any()) }
            coVerify(exactly = 0) { mediaDetailRepository.getSeasonEpisodes(any(), any()) }
        }

    @Test
    fun `invoke returns success with no children for Movie`() =
        runTest {
            // Given
            val ratingKey = "123"
            val serverId = "server1"
            val mediaItem =
                mockk<MediaItem>(relaxed = true) {
                    coEvery { type } returns MediaType.Movie
                }
            coEvery { mediaDetailRepository.getMediaDetail(ratingKey, serverId) } returns Result.success(mediaItem)

            // When
            val result = useCase(ratingKey, serverId).first()

            // Then
            assertThat(result.isSuccess).isTrue()
            val detail = result.getOrThrow()
            assertThat(detail.item).isEqualTo(mediaItem)
            assertThat(detail.children).isEmpty()
        }

    @Test
    fun `invoke fetches seasons for Show type`() =
        runTest {
            // Given
            val ratingKey = "show1"
            val serverId = "server1"
            val showItem =
                mockk<MediaItem>(relaxed = true) {
                    coEvery { type } returns MediaType.Show
                }
            val seasons = listOf(mockk<MediaItem>(), mockk<MediaItem>())

            coEvery { mediaDetailRepository.getMediaDetail(ratingKey, serverId) } returns Result.success(showItem)
            coEvery { mediaDetailRepository.getShowSeasons(ratingKey, serverId) } returns Result.success(seasons)

            // When
            val result = useCase(ratingKey, serverId).first()

            // Then
            assertThat(result.isSuccess).isTrue()
            val detail = result.getOrThrow()
            assertThat(detail.item).isEqualTo(showItem)
            assertThat(detail.children).isEqualTo(seasons)
        }

    @Test
    fun `invoke fetches episodes for Season type`() =
        runTest {
            // Given
            val ratingKey = "season1"
            val serverId = "server1"
            val seasonItem =
                mockk<MediaItem>(relaxed = true) {
                    coEvery { type } returns MediaType.Season
                }
            val episodes = listOf(mockk<MediaItem>())

            coEvery { mediaDetailRepository.getMediaDetail(ratingKey, serverId) } returns Result.success(seasonItem)
            coEvery { mediaDetailRepository.getSeasonEpisodes(ratingKey, serverId) } returns Result.success(episodes)

            // When
            val result = useCase(ratingKey, serverId).first()

            // Then
            assertThat(result.isSuccess).isTrue()
            val detail = result.getOrThrow()
            assertThat(detail.item).isEqualTo(seasonItem)
            assertThat(detail.children).isEqualTo(episodes)
        }
}
