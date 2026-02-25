package com.chakir.plexhubtv.core.common.auth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthEventBusTest {

    @Test
    fun `emitTokenInvalid emits event to collectors`() = runTest {
        val eventBus = AuthEventBus()
        val events = mutableListOf<AuthEvent>()

        // Use UnconfinedTestDispatcher so the collector is immediately active
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            eventBus.events.collect { events.add(it) }
        }

        eventBus.emitTokenInvalid()
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertTrue(events[0] is AuthEvent.TokenInvalid)
        job.cancel()
    }

    @Test
    fun `multiple emits are all delivered to active collector`() = runTest {
        val eventBus = AuthEventBus()
        val events = mutableListOf<AuthEvent>()

        // Start collector eagerly
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            eventBus.events.collect { events.add(it) }
        }

        eventBus.emitTokenInvalid()
        eventBus.emitTokenInvalid()
        advanceUntilIdle()

        assertEquals(2, events.size)
        assertTrue(events.all { it is AuthEvent.TokenInvalid })
        job.cancel()
    }

    @Test
    fun `events emitted before collector starts are not replayed`() = runTest {
        val eventBus = AuthEventBus()

        // Emit before any collector subscribes
        eventBus.emitTokenInvalid()

        val events = mutableListOf<AuthEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            eventBus.events.collect { events.add(it) }
        }

        advanceUntilIdle()

        // SharedFlow with replay=0 does not replay past events
        assertEquals(0, events.size)
        job.cancel()
    }
}
