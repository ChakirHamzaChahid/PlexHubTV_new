package com.chakir.plexhubtv.core.common

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
    crossinline block: suspend () -> T,
): Result<T> {
    return try {
        Result.success(block())
    } catch (e: AppError) {
        Result.failure(e)
    } catch (e: UnknownHostException) {
        Timber.e(e, "%s: No connection", tag)
        Result.failure(AppError.Network.NoConnection(e.message))
    } catch (e: SocketTimeoutException) {
        Timber.e(e, "%s: Timeout", tag)
        Result.failure(AppError.Network.Timeout(e.message))
    } catch (e: IOException) {
        Timber.e(e, "%s: Network error", tag)
        Result.failure(AppError.Network.ServerError(e.message, e))
    } catch (e: HttpException) {
        Timber.e(e, "%s: HTTP %d", tag, e.code())
        Result.failure(e.toAppError())
    } catch (e: Exception) {
        Timber.e(e, "%s: Unknown error", tag)
        Result.failure(AppError.Unknown(e.message, e))
    }
}

fun HttpException.toAppError(): AppError = when (code()) {
    401, 403 -> AppError.Network.Unauthorized("HTTP ${code()}", this)
    404 -> AppError.Network.NotFound("HTTP ${code()}", this)
    in 500..599 -> AppError.Network.ServerError("HTTP ${code()}", this)
    else -> AppError.Unknown("HTTP ${code()}: ${message()}", this)
}
