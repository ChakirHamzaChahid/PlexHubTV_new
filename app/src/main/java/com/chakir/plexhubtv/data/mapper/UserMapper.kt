package com.chakir.plexhubtv.data.mapper

import com.chakir.plexhubtv.core.model.PlexHomeUser
import com.chakir.plexhubtv.core.network.model.PlexHomeUserDto
import javax.inject.Inject

/**
 * Mapper pour les utilisateurs Plex Home.
 */
class UserMapper
    @Inject
    constructor() {
        fun mapDtoToDomain(dto: PlexHomeUserDto): PlexHomeUser {
            return PlexHomeUser(
                id = dto.id,
                uuid = dto.uuid,
                title = dto.title,
                username = dto.username,
                email = dto.email,
                friendlyName = dto.friendlyName,
                thumb = dto.thumb,
                hasPassword = dto.hasPassword,
                restricted = dto.restricted,
                protected = dto.protected,
                admin = dto.admin,
                guest = dto.guest,
            )
        }
    }
