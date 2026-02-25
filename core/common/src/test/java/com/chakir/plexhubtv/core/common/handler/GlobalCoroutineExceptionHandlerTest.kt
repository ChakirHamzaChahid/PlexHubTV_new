package com.chakir.plexhubtv.core.common.handler

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Tests for [GlobalCoroutineExceptionHandler] behavior in DEBUG vs RELEASE.
 *
 * STAB-2 Verification:
 * - DEBUG: Exceptions should be logged AND re-thrown to crash the app visibly
 * - RELEASE: Exceptions should be logged, sent to Crashlytics, but NOT re-thrown
 */
class GlobalCoroutineExceptionHandlerTest {

    private lateinit var crashlytics: FirebaseCrashlytics

    @Before
    fun setup() {
        crashlytics = mockk(relaxed = true)
    }

    @Test
    fun `DEBUG - exception is re-thrown after logging`() {
        // Given: Handler configured for DEBUG mode
        val handler = GlobalCoroutineExceptionHandler(crashlytics, isDebug = true)
        val testException = RuntimeException("Test exception in DEBUG")

        // When: handleException is called directly
        var exceptionWasThrown = false
        try {
            handler.handleException(EmptyCoroutineContext, testException)
            fail("Expected RuntimeException to be re-thrown in DEBUG mode")
        } catch (e: RuntimeException) {
            // Then: The exception should be re-thrown
            exceptionWasThrown = true
            assert(e.message == "Test exception in DEBUG") {
                "Expected original exception to be re-thrown"
            }
        }

        assert(exceptionWasThrown) { "Exception should have been thrown" }

        // And: Crashlytics should NOT be called in DEBUG (collection disabled at app level)
        verify(exactly = 0) { crashlytics.recordException(any()) }
    }

    @Test
    fun `RELEASE - exception is logged but NOT re-thrown`() {
        // Given: Handler configured for RELEASE mode
        val handler = GlobalCoroutineExceptionHandler(crashlytics, isDebug = false)
        val testException = RuntimeException("Test exception in RELEASE")

        // When: handleException is called directly
        handler.handleException(EmptyCoroutineContext, testException)

        // Then: No exception should be re-thrown (method completes normally)
        // And: Crashlytics should record the exception
        verify(exactly = 1) { crashlytics.recordException(testException) }
    }

    @Test
    fun `handleException logs different exception types in DEBUG`() {
        // Given: Handler configured for DEBUG mode
        val handler = GlobalCoroutineExceptionHandler(crashlytics, isDebug = true)
        val testException = IllegalStateException("Illegal state in DEBUG")

        // When: handleException is called with different exception type
        var correctExceptionThrown = false
        try {
            handler.handleException(EmptyCoroutineContext, testException)
        } catch (e: IllegalStateException) {
            // Then: The same exception type should be re-thrown
            correctExceptionThrown = true
            assert(e.message == "Illegal state in DEBUG")
        }

        assert(correctExceptionThrown) { "IllegalStateException should have been thrown" }
    }

    @Test
    fun `handleException records different exception types to Crashlytics in RELEASE`() {
        // Given: Handler configured for RELEASE mode
        val handler = GlobalCoroutineExceptionHandler(crashlytics, isDebug = false)
        val testException = NullPointerException("NPE in RELEASE")

        // When: handleException is called with different exception type
        handler.handleException(EmptyCoroutineContext, testException)

        // Then: Crashlytics should be called with the exception
        verify(exactly = 1) { crashlytics.recordException(testException) }
    }
}
