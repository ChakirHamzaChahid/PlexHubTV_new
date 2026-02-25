package com.chakir.plexhubtv.data.mapper

import com.chakir.plexhubtv.core.database.ConnectionCandidateEntity
import com.chakir.plexhubtv.core.database.ServerEntity
import com.chakir.plexhubtv.core.model.ConnectionCandidate
import com.chakir.plexhubtv.core.model.Server
import com.chakir.plexhubtv.core.network.model.PlexResource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject

/**
 * Mapper pour les objets Serveur.
 * Convertit les ressources Plex.tv (xml/json) en modèle de domaine et entités persistantes.
 */
class ServerMapper
    @Inject
    constructor() {
        private val gson = Gson()
        private val candidateListType = object : TypeToken<List<ConnectionCandidateEntity>>() {}.type

        fun mapEntityToDomain(entity: ServerEntity): Server {
            val uri = entity.protocol + "://" + entity.address + ":" + entity.port

            // Parse stored connection candidates from JSON
            val candidates = try {
                val parsed: List<ConnectionCandidateEntity> = gson.fromJson(entity.connectionCandidatesJson, candidateListType)
                parsed.map {
                    ConnectionCandidate(
                        protocol = it.protocol,
                        address = it.address,
                        port = it.port,
                        uri = it.uri,
                        local = it.local,
                        relay = it.relay,
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }

            // Fallback: if no candidates in JSON, create one from the primary address
            val connectionCandidates = candidates.ifEmpty {
                listOf(
                    ConnectionCandidate(
                        protocol = entity.protocol,
                        address = entity.address,
                        port = entity.port,
                        uri = uri,
                        local = true,
                        relay = false,
                    ),
                )
            }

            return Server(
                clientIdentifier = entity.clientIdentifier,
                name = entity.name,
                address = entity.address,
                port = entity.port,
                connectionUri = uri,
                connectionCandidates = connectionCandidates,
                accessToken = entity.accessToken,
                isOwned = entity.isOwned,
                relay = entity.relay,
                publicAddress = entity.publicAddress,
                httpsRequired = entity.httpsRequired,
            )
        }

        fun mapDtoToDomain(resource: PlexResource): Server? {
            val provides = resource.provides.split(",").map { it.trim() }
            if (!provides.contains("server")) return null

            val connections = resource.connections ?: emptyList()
            val bestConnection = connections.find { it.local } ?: connections.firstOrNull()

            val candidates = connections.map {
                ConnectionCandidate(
                    protocol = it.protocol,
                    address = it.address,
                    port = it.port,
                    uri = it.uri,
                    local = it.local,
                    relay = it.relay ?: false,
                )
            }.toMutableList()

            // Add publicAddress as a candidate if not already present in connections
            val publicAddr = resource.publicAddress
            if (!publicAddr.isNullOrBlank()) {
                val alreadyPresent = candidates.any { it.address == publicAddr }
                if (!alreadyPresent) {
                    val protocol = if (resource.httpsRequired == "1") "https" else "http"
                    val port = bestConnection?.port ?: 32400
                    candidates.add(
                        ConnectionCandidate(
                            protocol = protocol,
                            address = publicAddr,
                            port = port,
                            uri = "$protocol://$publicAddr:$port",
                            local = false,
                            relay = false,
                        ),
                    )
                }
            }

            return Server(
                clientIdentifier = resource.clientIdentifier,
                name = resource.name,
                address = bestConnection?.address ?: "",
                port = bestConnection?.port ?: 32400,
                connectionUri = bestConnection?.uri ?: "",
                connectionCandidates = candidates,
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

            // Serialize all connection candidates to JSON
            val candidateEntities = server.connectionCandidates.map {
                ConnectionCandidateEntity(
                    protocol = it.protocol,
                    address = it.address,
                    port = it.port,
                    uri = it.uri,
                    local = it.local,
                    relay = it.relay,
                )
            }

            return ServerEntity(
                clientIdentifier = server.clientIdentifier,
                name = server.name,
                address = server.address,
                port = server.port,
                protocol = uri?.scheme ?: "http",
                accessToken = server.accessToken,
                isOwned = server.isOwned,
                relay = server.relay,
                publicAddress = server.publicAddress,
                httpsRequired = server.httpsRequired,
                connectionCandidatesJson = gson.toJson(candidateEntities),
            )
        }
    }
