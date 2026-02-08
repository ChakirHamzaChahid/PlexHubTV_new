package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.repository.MediaRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetUnifiedHomeContentUseCaseTest {
    private val mediaRepository: MediaRepository = mockk()
    private val testDispatcher = StandardTestDispatcher()
    private val useCase = GetUnifiedHomeContentUseCase(mediaRepository, testDispatcher)

    @Test
    fun `invoke returns success when both sources emit`() =
        runTest {
            // Given
            val onDeck = listOf(mockk<MediaItem>())
            val hubs = listOf(mockk<Hub>())
            every { mediaRepository.getUnifiedOnDeck() } returns flowOf(onDeck)
            every { mediaRepository.getUnifiedHubs() } returns flowOf(hubs)

            // When
            val result = useCase().first()

            // Then
            assertThat(result.isSuccess).isTrue()
            val content = result.getOrThrow()
            assertThat(content.onDeck).isEqualTo(onDeck)
            assertThat(content.hubs).isEqualTo(hubs)
        }

    @Test
    fun `invoke returns partial success when OnDeck fails`() =
        runTest {
            // Given
            val exception = RuntimeException("Network error")
            val hubs = listOf(mockk<Hub>())

            every { mediaRepository.getUnifiedOnDeck() } returns flow { throw exception }
            every { mediaRepository.getUnifiedHubs() } returns flowOf(hubs)

            // When
            val result = useCase().first()

            // Then
            assertThat(result.isSuccess).isTrue()
            val content = result.getOrThrow()
            assertThat(content.onDeck).isEmpty()
            assertThat(content.hubs).isEqualTo(hubs)
        }
}
