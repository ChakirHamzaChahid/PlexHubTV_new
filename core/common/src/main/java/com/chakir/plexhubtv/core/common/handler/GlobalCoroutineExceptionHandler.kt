package com.chakir.plexhubtv.core.common.handler

import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineExceptionHandler
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/**
 * Global coroutine exception handler for the entire application.
 *
 * This handler captures all uncaught exceptions in coroutines and:
 * - Logs them via Timber (in DEBUG and RELEASE)
 * - Reports them to Firebase Crashlytics (in RELEASE only, via Crashlytics config)
 *
 * The handler is injected into [ApplicationScope] and [PlayerController] to ensure
 * comprehensive exception coverage across the app.
 */
@Singleton
class GlobalCoroutineExceptionHandler @Inject constructor(
    private val crashlytics: FirebaseCrashlytics
) : CoroutineExceptionHandler {

    override val key: CoroutineContext.Key<*>
        get() = CoroutineExceptionHandler

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        // Log all uncaught exceptions with a distinctive prefix for easy filtering
        Timber.e(exception, "[COROUTINE-CRASH] Uncaught exception in context: $context")

        // Record to Crashlytics (collection is already disabled in DEBUG mode via
        // FirebaseCrashlytics.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG))
        crashlytics.recordException(exception)
    }
}
