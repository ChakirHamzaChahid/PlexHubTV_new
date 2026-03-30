package com.chakir.plexhubtv.core.ui

import android.content.Context
import com.chakir.plexhubtv.core.model.AppError

/**
 * Resolves an [AppError] to a localized user-facing message using Android string resources.
 * Falls back to the error's own message or a generic string if no specific resource matches.
 */
fun AppError.resolveMessage(context: Context): String {
    val resId = when (this) {
        // Network errors
        is AppError.Network.NoConnection -> R.string.error_network_no_connection
        is AppError.Network.Timeout -> R.string.error_network_timeout
        is AppError.Network.ServerError -> if (message != null) null else R.string.error_network_server
        is AppError.Network.NotFound -> R.string.error_network_not_found
        is AppError.Network.Unauthorized -> R.string.error_network_unauthorized

        // Auth errors
        is AppError.Auth.InvalidToken -> R.string.error_auth_invalid_token
        is AppError.Auth.SessionExpired -> R.string.error_auth_session_expired
        is AppError.Auth.NoServersFound -> R.string.error_auth_no_servers
        is AppError.Auth.PinGenerationFailed -> R.string.error_auth_pin_failed
        is AppError.Auth.InvalidCredentials -> if (message != null) null else R.string.error_auth_invalid_credentials

        // Media errors
        is AppError.Media.NotFound -> R.string.error_media_not_found
        is AppError.Media.LoadFailed -> if (message != null) null else R.string.error_media_load_failed
        is AppError.Media.NoPlayableContent -> R.string.error_media_no_playable
        is AppError.Media.UnsupportedFormat -> R.string.error_media_unsupported

        // Playback errors
        is AppError.Playback.InitializationFailed -> R.string.error_playback_init
        is AppError.Playback.StreamingError -> if (message != null) null else R.string.error_playback_streaming
        is AppError.Playback.CodecNotSupported -> R.string.error_playback_codec
        is AppError.Playback.DrmError -> R.string.error_playback_drm

        // Search errors
        is AppError.Search.QueryTooShort -> R.string.error_search_too_short
        is AppError.Search.NoResults -> R.string.error_search_no_results
        is AppError.Search.SearchFailed -> R.string.error_search_failed

        // Storage errors
        is AppError.Storage.DiskFull -> R.string.error_storage_disk_full
        is AppError.Storage.ReadError -> R.string.error_storage_read
        is AppError.Storage.WriteError -> R.string.error_storage_write

        // Generic errors
        is AppError.Validation -> if (message != null) null else R.string.error_validation
        is AppError.Unknown -> if (message != null) null else R.string.error_unknown
    }

    return if (resId != null) {
        context.getString(resId)
    } else {
        message ?: context.getString(R.string.error_unknown)
    }
}
