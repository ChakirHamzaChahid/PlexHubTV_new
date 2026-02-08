package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.repository.MediaRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SyncWatchlistUseCaseTest {
    private val mediaRepository: MediaRepository = mockk()
    private val useCase = SyncWatchlistUseCase(mediaRepository)

    @Test
    fun `invoke calls syncWatchlist on repository and returns result`() =
        runTest {
            // Given
            val expectedResult = Result.success(Unit)
            coEvery { mediaRepository.syncWatchlist() } returns expectedResult

            // When
            val result = useCase()

            // Then
            assertThat(result.isSuccess).isTrue()
            coVerify(exactly = 1) { mediaRepository.syncWatchlist() }
        }

    @Test
    fun `invoke propagates failure from repository`() =
        runTest {
            // Given
            val exception = Exception("Sync error")
            coEvery { mediaRepository.syncWatchlist() } returns Result.failure(exception)

            // When
            val result = useCase()

            // Then
            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isEqualTo(exception)
        }
}
