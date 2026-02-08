package com.chakir.plexhubtv.core.common.exception

sealed class PlexException(message: String, cause: Throwable? = null) : Exception(message, cause)

class NetworkException(message: String, cause: Throwable? = null) : PlexException(message, cause)

class AuthException(message: String, cause: Throwable? = null) : PlexException(message, cause)

class MediaNotFoundException(message: String) : PlexException(message)

class ServerUnavailableException(serverId: String) : PlexException("Server $serverId unavailable")
