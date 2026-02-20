package com.chakir.plexhubtv.core.network

import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.model.ConnectionCandidate
import com.chakir.plexhubtv.core.model.ConnectionState
import com.chakir.plexhubtv.core.model.Server
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ConnectionManagerTest {

    private lateinit var connectionTester: ServerConnectionTester
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var connectionManager: ConnectionManager
    private val testScope = CoroutineScope(SupervisorJob())

    @Before
    fun setUp() {
        connectionTester = mockk(relaxed = true)
        settingsDataStore = mockk(relaxed = true)

        // Mock DataStore flow
        every { settingsDataStore.cachedConnections } returns MutableStateFlow(emptyMap())

        connectionManager = ConnectionManager(
            connectionTester = connectionTester,
            settingsDataStore = settingsDataStore,
            scope = testScope
        )
    }

    /**
     * Test RC1: Verify thread-safe race completion when multiple URLs fail concurrently
     */
    @Test
    fun `raceUrls completes with null when all URLs fail concurrently`() = runTest {
        // Setup: 4 URLs that all fail with different delays (simulates concurrent failures)
        val candidates = listOf(
            ConnectionCandidate("http", "server1.local", 32400, "http://server1.local:32400", true, false),
            ConnectionCandidate("http", "server2.local", 32400, "http://server2.local:32400", true, false),
            ConnectionCandidate("http", "192.168.1.100", 32400, "http://192.168.1.100:32400", true, false),
            ConnectionCandidate("https", "public.plex.tv", 443, "https://public.plex.tv/relay", false, true)
        )

        coEvery {
            connectionTester.testConnection(any(), any(), any())
        } coAnswers {
            // Simulate varying network delays (10-50ms)
            delay((10L..50L).random())
            ConnectionResult("Failed", false, 0, 503)
        }

        val server = createTestServer(candidates)

        // Act: All URLs fail → should return null without hanging
        val result = connectionManager.findBestConnection(server)

        // Assert: Should complete with null (not hang indefinitely)
        assertNull("Should return null when all connections fail", result)

        // Verify all candidates were tested
        coVerify(exactly = 4) { connectionTester.testConnection(any(), any(), any()) }
    }

    /**
     * Test RC2: Verify thread-safe race completion when one URL wins amid concurrent tests
     */
    @Test
    fun `raceUrls returns winner when one URL succeeds amid concurrent failures`() = runTest {
        val winningUrl = "http://192.168.1.100:32400"
        val candidates = listOf(
            ConnectionCandidate("http", "server1.local", 32400, "http://server1.local:32400", true, false),
            ConnectionCandidate("http", "192.168.1.100", 32400, winningUrl, true, false),
            ConnectionCandidate("http", "server3.local", 32400, "http://server3.local:32400", true, false)
        )

        coEvery { connectionTester.testConnection(winningUrl, any(), any()) } coAnswers {
            delay(20) // Wins after 20ms
            ConnectionResult("Success", true, 20, 200)
        }

        coEvery {
            connectionTester.testConnection(not(winningUrl), any(), any())
        } coAnswers {
            delay(100) // Others fail slowly
            ConnectionResult("Timeout", false, 100, 408)
        }

        val server = createTestServer(candidates)

        // Act: One URL wins
        val result = connectionManager.findBestConnection(server)

        // Assert: Should return the winner
        assertEquals("Should return the winning URL", winningUrl, result)
        assertNotNull("Result should not be null", result)
    }

    /**
     * Test RC1+RC2: Stress test with many concurrent URLs to expose race conditions
     */
    @Test
    fun `raceUrls handles high concurrency without race conditions`() = runTest {
        // Setup: 10 URLs with random success/failure
        val candidates = (1..10).map { i ->
            ConnectionCandidate("http", "server$i.local", 32400, "http://server$i.local:32400", true, false)
        }

        val winningUrl = "http://server5.local:32400"

        coEvery { connectionTester.testConnection(winningUrl, any(), any()) } coAnswers {
            delay(50) // Winner takes 50ms
            ConnectionResult("Success", true, 50, 200)
        }

        coEvery {
            connectionTester.testConnection(not(winningUrl), any(), any())
        } coAnswers {
            delay((10L..200L).random()) // Others fail with random delays
            ConnectionResult("Failed", false, 0, 503)
        }

        val server = createTestServer(candidates)

        // Act: Run multiple times to stress-test atomicity
        repeat(5) { iteration ->
            connectionManager.clearFailedServers() // Reset for each iteration
            val result = connectionManager.findBestConnection(server)

            // Assert: Should consistently return winner (or null if it hasn't completed yet)
            // But must NEVER hang
            assertNotNull("Iteration $iteration: Should not hang", result)
            assertEquals("Iteration $iteration: Should return winner", winningUrl, result)
        }
    }

    /**
     * Test failedServers ConcurrentHashMap thread-safety
     */
    @Test
    fun `failedServers map handles concurrent access without corruption`() = runTest {
        val candidates = listOf(
            ConnectionCandidate("http", "server1.local", 32400, "http://server1.local:32400", true, false)
        )

        // All connections fail
        coEvery { connectionTester.testConnection(any(), any(), any()) } returns
            ConnectionResult("Failed", false, 0, 503)

        val server = createTestServer(candidates)

        // Act: Trigger concurrent failures from multiple coroutines
        val jobs = (1..5).map { i ->
            launch {
                connectionManager.findBestConnection(server)
            }
        }

        jobs.forEach { it.join() }

        // Assert: Should not throw ConcurrentModificationException
        // and clearFailedServers should work safely
        connectionManager.clearFailedServers()

        // Verify we can retry after clear
        val result = connectionManager.findBestConnection(server)
        assertNull("Should still fail but not hang", result)
    }

    private fun createTestServer(candidates: List<ConnectionCandidate>): Server {
        return Server(
            clientIdentifier = "test-server-id",
            name = "Test Server",
            address = "test.local",
            port = 32400,
            connectionUri = "http://test.local:32400",
            connectionCandidates = candidates,
            accessToken = "test-token",
            isOwned = true,
            publicAddress = null,
            httpsRequired = false,
            relay = true,
            connectionState = ConnectionState.Unknown
        )
    }
}
