package com.chakir.plexhubtv.core.common.handler

import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineExceptionHandler
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class IsDebugBuild

/**
 * Global coroutine exception handler for the entire application.
 *
 * This handler captures all uncaught exceptions in coroutines and:
 * - Logs them via Timber (in DEBUG and RELEASE)
 * - Reports them to Firebase Crashlytics (in RELEASE only)
 * - Re-throws exceptions in DEBUG to make crashes visible during development
 *
 * The handler is injected into [ApplicationScope] and [PlayerController] to ensure
 * comprehensive exception coverage across the app.
 */
@Singleton
class GlobalCoroutineExceptionHandler @Inject constructor(
    private val crashlytics: FirebaseCrashlytics,
    @IsDebugBuild private val isDebug: Boolean
) : CoroutineExceptionHandler {

    override val key: CoroutineContext.Key<*>
        get() = CoroutineExceptionHandler

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        // Log all uncaught exceptions with a distinctive prefix for easy filtering
        Timber.e(exception, "[COROUTINE-CRASH] Uncaught exception in context: $context")

        if (isDebug) {
            // In DEBUG: Log + re-throw to make crashes visible during development
            // This ensures developers see the crash immediately instead of silently continuing
            Timber.wtf(exception, "[COROUTINE-CRASH] RE-THROWING IN DEBUG MODE")
            throw exception
        } else {
            // In RELEASE: Log + record to Crashlytics, but don't crash the app
            // Collection is controlled via FirebaseCrashlytics.setCrashlyticsCollectionEnabled()
            crashlytics.recordException(exception)
        }
    }
}
