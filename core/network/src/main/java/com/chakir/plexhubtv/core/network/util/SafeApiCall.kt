package com.chakir.plexhubtv.core.network.util

import com.chakir.plexhubtv.core.model.AppError
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Wraps a suspend block with standardized error handling.
 * Catches common exceptions and maps them to [AppError].
 *
 * For business-specific early returns (validation, auth checks),
 * use `Result.failure(AppError.Xxx(...))` BEFORE calling safeApiCall,
 * or `throw AppError.Xxx(...)` inside the block.
 */
suspend inline fun <T> safeApiCall(
    tag: String = "",
    timeoutMs: Long = 30_000L,
    maxRetries: Int = 3,
    initialDelayMs: Long = 1000L,
    maxDelayMs: Long = 5000L,
    factor: Double = 2.0,
    crossinline block: suspend () -> T,
): Result<T> {
    var currentDelay = initialDelayMs
    var lastException: Exception? = null

    repeat(maxRetries) { attempt ->
        try {
            return Result.success(kotlinx.coroutines.withTimeout(timeoutMs) { block() })
        } catch (e: AppError) {
            return Result.failure(e)
        } catch (e: UnknownHostException) {
            Timber.e(e, "%s: No connection (attempt %d/%d)", tag, attempt + 1, maxRetries)
            lastException = e
        } catch (e: SocketTimeoutException) {
            Timber.e(e, "%s: Timeout (attempt %d/%d)", tag, attempt + 1, maxRetries)
            lastException = e
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Timber.e(e, "%s: Coroutine Timeout (attempt %d/%d)", tag, attempt + 1, maxRetries)
            lastException = e
        } catch (e: IOException) {
            Timber.e(e, "%s: Network error (attempt %d/%d)", tag, attempt + 1, maxRetries)
            lastException = e
        } catch (e: HttpException) {
            val code = e.code()
            // Retry on Server Errors (5xx) or Rate Limits (429)
            if (code in 500..599 || code == 429) {
                Timber.e(e, "%s: HTTP %d (attempt %d/%d)", tag, code, attempt + 1, maxRetries)
                lastException = e
            } else {
                Timber.e(e, "%s: HTTP %d", tag, code)
                return Result.failure(e.toAppError())
            }
        } catch (e: Exception) {
            Timber.e(e, "%s: Unknown error", tag)
            return Result.failure(AppError.Unknown(e.message, e))
        }

        if (attempt < maxRetries - 1) {
            kotlinx.coroutines.delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
        }
    }

    return when (val e = lastException) {
        is UnknownHostException -> Result.failure(AppError.Network.NoConnection(e?.message))
        is SocketTimeoutException, is kotlinx.coroutines.TimeoutCancellationException -> Result.failure(AppError.Network.Timeout(e?.message))
        is IOException -> Result.failure(AppError.Network.ServerError(e?.message, e))
        is HttpException -> Result.failure(e.toAppError())
        else -> Result.failure(AppError.Unknown(e?.message, e))
    }
}

fun HttpException.toAppError(): AppError = when (code()) {
    401, 403 -> AppError.Network.Unauthorized("HTTP ${code()}", this)
    404 -> AppError.Network.NotFound("HTTP ${code()}", this)
    in 500..599 -> AppError.Network.ServerError("HTTP ${code()}", this)
    else -> AppError.Unknown("HTTP ${code()}: ${message()}", this)
}
