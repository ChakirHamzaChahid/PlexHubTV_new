package com.chakir.plexhubtv.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException

/**
 * Exécute un appel réseau avec un timeout et une logique de retry avec backoff exponentiel.
 * 
 * @param timeoutMs Le timeout en millisecondes pour chaque tentative (défaut: 30s)
 * @param maxRetries Le nombre maximum de tentatives
 * @param initialDelayMs Le délai initial avant le premier retry
 * @param maxDelayMs Le délai maximum entre deux retries
 * @param factor Le facteur multiplicatif pour le backoff exponentiel
 * @param block Le bloc de code suspensif exécutant l'appel réseau
 * @return Le résultat de type [T]
 */
suspend fun <T> safeApiCall(
    timeoutMs: Long = 30_000L,
    maxRetries: Int = 3,
    initialDelayMs: Long = 1000L,
    maxDelayMs: Long = 5000L,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelayMs
    
    repeat(maxRetries - 1) { attempt ->
        try {
            return withTimeout(timeoutMs) {
                block()
            }
        } catch (e: Exception) {
            if (!shouldRetry(e)) {
                throw e
            }
            Timber.w(e, "Network call failed (attempt ${attempt + 1}/$maxRetries). Retrying in ${currentDelay}ms...")
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
        }
    }
    
    // Dernière tentative
    return withTimeout(timeoutMs) {
        block()
    }
}

private fun shouldRetry(exception: Exception): Boolean {
    return when (exception) {
        is IOException -> true // Erreurs réseau (timeout, DNS, connection reset, etc.)
        is kotlinx.coroutines.TimeoutCancellationException -> true // Retry on timeout!
        is HttpException -> {
            val code = exception.code()
            // Retry on Server Errors (5xx) or Rate Limits (429)
            code in 500..599 || code == 429
        }
        else -> false
    }
}
