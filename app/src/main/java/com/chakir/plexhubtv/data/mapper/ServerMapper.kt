package com.chakir.plexhubtv.data.mapper

import com.chakir.plexhubtv.core.database.ServerEntity
import com.chakir.plexhubtv.core.model.Server
import com.chakir.plexhubtv.core.network.model.PlexResource
import javax.inject.Inject

/**
 * Mapper pour les objets Serveur.
 * Convertit les ressources Plex.tv (xml/json) en modèle de domaine et entités persistantes.
 */
class ServerMapper
    @Inject
    constructor() {
        fun mapEntityToDomain(entity: ServerEntity): Server {
            val uri = entity.protocol + "://" + entity.address + ":" + entity.port
            return Server(
                clientIdentifier = entity.clientIdentifier,
                name = entity.name,
                address = entity.address,
                port = entity.port,
                connectionUri = uri,
                connectionCandidates =
                    listOf(
                        com.chakir.plexhubtv.core.model.ConnectionCandidate(
                            protocol = entity.protocol,
                            address = entity.address,
                            port = entity.port,
                            uri = uri,
                            local = true,
                            relay = false,
                        ),
                    ),
                accessToken = entity.accessToken,
                isOwned = entity.isOwned,
            )
        }

        fun mapDtoToDomain(resource: PlexResource): Server? {
            val provides = resource.provides.split(",").map { it.trim() }
            if (!provides.contains("server")) return null

            val connections = resource.connections ?: emptyList()
            val bestConnection = connections.find { it.local } ?: connections.firstOrNull()

            return Server(
                clientIdentifier = resource.clientIdentifier,
                name = resource.name,
                address = bestConnection?.address ?: "",
                port = bestConnection?.port ?: 32400,
                connectionUri = bestConnection?.uri ?: "",
                connectionCandidates =
                    connections.map {
                        com.chakir.plexhubtv.core.model.ConnectionCandidate(
                            protocol = it.protocol,
                            address = it.address,
                            port = it.port,
                            uri = it.uri,
                            local = it.local,
                            relay = it.relay ?: false,
                        )
                    },
                accessToken = resource.accessToken,
                isOwned = resource.owned,
                publicAddress = resource.publicAddress,
                httpsRequired = resource.httpsRequired == "1",
                relay = resource.relay,
            )
        }

        fun mapDomainToEntity(server: Server): ServerEntity {
            val uri =
                try {
                    java.net.URI(server.connectionUri)
                } catch (e: Exception) {
                    null
                }
            return ServerEntity(
                clientIdentifier = server.clientIdentifier,
                name = server.name,
                address = server.address,
                port = server.port,
                protocol = uri?.scheme ?: "http",
                accessToken = server.accessToken,
                isOwned = server.isOwned,
            )
        }
    }
