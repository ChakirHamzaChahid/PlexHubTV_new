package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.repository.HubsRepository
import com.chakir.plexhubtv.domain.repository.OnDeckRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GetUnifiedHomeContentUseCaseTest {
    private val onDeckRepository: OnDeckRepository = mockk()
    private val hubsRepository: HubsRepository = mockk()
    private val sortOnDeckUseCase: SortOnDeckUseCase = mockk(relaxed = true)

    @Test
    fun `sharedContent returns success when both sources emit`() =
        runTest {
            // Given
            val onDeck = listOf(mockk<MediaItem>())
            val hubs = listOf(mockk<Hub>())
            every { onDeckRepository.getUnifiedOnDeck() } returns flowOf(onDeck)
            every { hubsRepository.getUnifiedHubs() } returns flowOf(hubs)
            every { sortOnDeckUseCase.invoke(any()) } answers { firstArg() }

            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val testScope = CoroutineScope(testDispatcher + SupervisorJob())
            val useCase = GetUnifiedHomeContentUseCase(
                onDeckRepository,
                hubsRepository,
                sortOnDeckUseCase,
                testDispatcher,
                testScope,
            )

            // Collect to trigger WhileSubscribed start
            val values = mutableListOf<Result<HomeContent>?>()
            val job = testScope.launch {
                useCase.sharedContent.collect { values.add(it) }
            }
            advanceUntilIdle()

            // Then
            val result = values.filterNotNull().lastOrNull()
            assertThat(result).isNotNull()
            assertThat(result!!.isSuccess).isTrue()
            val content = result.getOrThrow()
            assertThat(content.onDeck).isEqualTo(onDeck)
            assertThat(content.hubs).isEqualTo(hubs)

            job.cancel()
            testScope.cancel()
        }

    @Test
    fun `sharedContent returns partial success when OnDeck fails`() =
        runTest {
            // Given
            val exception = RuntimeException("Network error")
            val hubs = listOf(mockk<Hub>())

            every { onDeckRepository.getUnifiedOnDeck() } returns flow { throw exception }
            every { hubsRepository.getUnifiedHubs() } returns flowOf(hubs)
            every { sortOnDeckUseCase.invoke(any()) } answers { firstArg() }

            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val testScope = CoroutineScope(testDispatcher + SupervisorJob())
            val useCase = GetUnifiedHomeContentUseCase(
                onDeckRepository,
                hubsRepository,
                sortOnDeckUseCase,
                testDispatcher,
                testScope,
            )

            // Collect to trigger WhileSubscribed start
            val values = mutableListOf<Result<HomeContent>?>()
            val job = testScope.launch {
                useCase.sharedContent.collect { values.add(it) }
            }
            advanceUntilIdle()

            // Then
            val result = values.filterNotNull().lastOrNull()
            assertThat(result).isNotNull()
            assertThat(result!!.isSuccess).isTrue()
            val content = result.getOrThrow()
            assertThat(content.onDeck).isEmpty()
            assertThat(content.hubs).isEqualTo(hubs)

            job.cancel()
            testScope.cancel()
        }
}
