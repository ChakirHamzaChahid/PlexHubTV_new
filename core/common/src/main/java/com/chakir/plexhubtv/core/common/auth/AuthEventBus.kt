package com.chakir.plexhubtv.core.common.auth

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global event bus for authentication-related events.
 *
 * Provides a reactive channel for auth state changes (e.g., token expiration).
 * Thread-safe for emission from OkHttp interceptors.
 */
@Singleton
class AuthEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AuthEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    /**
     * Emits a TokenInvalid event when 401 response detected.
     * Thread-safe, non-blocking.
     */
    fun emitTokenInvalid() {
        _events.tryEmit(AuthEvent.TokenInvalid)
    }
}

/**
 * Authentication events propagated through the app.
 */
sealed interface AuthEvent {
    /**
     * Token is invalid (expired or revoked).
     * Triggered by 401 responses from Plex API.
     */
    data object TokenInvalid : AuthEvent
}
