package com.chakir.plexhubtv.core.model

/**
 * Représente un serveur Plex (Media Server).
 *
 * @property clientIdentifier ID unique de la machine (Machine Identifier).
 * @property name Nom du serveur (ex: "My NAS").
 * @property address Adresse IP locale principale.
 * @property port Port standard (32400 par défaut).
 * @property connectionUri URI complet de connexion par défaut.
 * @property connectionCandidates Liste de toutes les adresses possibles (IPs Locales, IPs Publiques, Relay).
 * @property accessToken Token d'accès (X-Plex-Token) pour ce serveur.
 * @property isOwned Indique si l'utilisateur connecté est le propriétaire de ce serveur.
 * @property publicAddress Adresse publique (WAN) si disponible.
 * @property httpsRequired Si vrai, les connexions doivent être sécurisées (SSL).
 * @property relay Si vrai, la connexion passe par un relais Plex (plus lent, limité à 1Mbps/2Mbps sans Plex Pass).
 * @property connectionState État actuel de la connexion (testé par [ServerConnectionTester]).
 */
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
    val connectionState: ConnectionState = ConnectionState.Unknown,
)

/**
 * Une méthode de connexion possible vers un serveur.
 *
 * @property protocol Protocole (http/https).
 * @property address IP ou nom d'hôte.
 * @property port Port.
 * @property uri URL complète.
 * @property local Si vrai, c'est une adresse RFC1918 (LAN).
 * @property relay Si vrai, c'est une connexion relayée par plex.tv.
 */
data class ConnectionCandidate(
    val protocol: String,
    val address: String,
    val port: Int,
    val uri: String,
    val local: Boolean,
    val relay: Boolean,
)

enum class ConnectionState {
    Unknown,
    Connecting,
    Connected,
    Offline,
    Unauthorized,
}
