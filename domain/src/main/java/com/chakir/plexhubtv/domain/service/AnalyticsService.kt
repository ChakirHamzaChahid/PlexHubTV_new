package com.chakir.plexhubtv.domain.service

/**
 * Abstraction over analytics providers (Firebase, etc.).
 * ViewModels inject this instead of using Firebase directly,
 * enabling testability and decoupling from the analytics SDK.
 */
interface AnalyticsService {
    fun logEvent(name: String, params: Map<String, Any> = emptyMap())
}
