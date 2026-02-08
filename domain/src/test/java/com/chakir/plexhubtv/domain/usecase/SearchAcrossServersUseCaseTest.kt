package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.repository.SearchRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SearchAcrossServersUseCaseTest {
    private val searchRepository: SearchRepository = mockk()
    private val useCase = SearchAcrossServersUseCase(searchRepository)

    @Test
    fun `invoke returns empty list when query is blank`() =
        runTest {
            // Given
            val query = "   "

            // When
            val result = useCase(query).first()

            // Then
            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrThrow()).isEmpty()
            coVerify(exactly = 0) { searchRepository.searchAllServers(any()) }
        }

    @Test
    fun `invoke returns results when query is valid`() =
        runTest {
            // Given
            val query = "Matrix"
            val items = listOf(mockk<MediaItem>())
            coEvery { searchRepository.searchAllServers(query) } returns Result.success(items)

            // When
            val result = useCase(query).first()

            // Then
            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrThrow()).isEqualTo(items)
            coVerify(exactly = 1) { searchRepository.searchAllServers(query) }
        }

    @Test
    fun `invoke returns failure when repository fails`() =
        runTest {
            // Given
            val query = "Matrix"
            val exception = Exception("Network error")
            coEvery { searchRepository.searchAllServers(query) } returns Result.failure(exception)

            // When
            val result = useCase(query).first()

            // Then
            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isEqualTo(exception)
        }
}
