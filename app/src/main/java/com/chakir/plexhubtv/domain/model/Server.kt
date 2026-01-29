package com.chakir.plexhubtv.domain.model

data class Server(
    val clientIdentifier: String,
    val name: String,
    val address: String,
    val port: Int,
    val connectionUri: String,
    val connectionCandidates: List<ConnectionCandidate> = emptyList(),
    val accessToken: String?,
    val isOwned: Boolean,
    val publicAddress: String? = null,
    val httpsRequired: Boolean = false,
    val relay: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.Unknown
)

data class ConnectionCandidate(
    val protocol: String,
    val address: String,
    val port: Int,
    val uri: String,
    val local: Boolean,
    val relay: Boolean
)

enum class ConnectionState {
    Unknown, Connecting, Connected, Offline, Unauthorized
}
