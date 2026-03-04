package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.repository.FavoritesRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ToggleFavoriteUseCaseTest {
    private val favoritesRepository: FavoritesRepository = mockk()
    private val useCase = ToggleFavoriteUseCase(favoritesRepository)

    @Test
    fun `invoke calls toggleFavorite on repository and returns result`() =
        runTest {
            // Given
            val mediaItem = mockk<MediaItem>()
            val expectedResult = true
            coEvery { favoritesRepository.toggleFavorite(mediaItem) } returns Result.success(expectedResult)

            // When
            val result = useCase(mediaItem)

            // Then
            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrThrow()).isEqualTo(expectedResult)
            coVerify(exactly = 1) { favoritesRepository.toggleFavorite(mediaItem) }
        }

    @Test
    fun `invoke propagates failure from repository`() =
        runTest {
            // Given
            val mediaItem = mockk<MediaItem>()
            val exception = Exception("Database error")
            coEvery { favoritesRepository.toggleFavorite(mediaItem) } returns Result.failure(exception)

            // When
            val result = useCase(mediaItem)

            // Then
            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isEqualTo(exception)
        }
}
