package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.XtreamAccountStatus
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.XtreamAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class ContentSource(
    val id: String,
    val name: String,
    val type: SourceType,
    val isActive: Boolean,
)

enum class SourceType { Plex, Xtream }

class GetAllSourcesUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val xtreamAccountRepository: XtreamAccountRepository,
) {
    /**
     * Returns a Flow of all content sources (Plex servers + Xtream accounts).
     * Xtream accounts are reactive; Plex servers are fetched as a snapshot per emission.
     */
    fun observe(): Flow<List<ContentSource>> =
        xtreamAccountRepository.observeAccounts().map { xtreamAccounts ->
            val plexSources = authRepository.getServers()
                .getOrDefault(emptyList())
                .map { server ->
                    ContentSource(
                        id = server.clientIdentifier,
                        name = server.name,
                        type = SourceType.Plex,
                        isActive = true,
                    )
                }
            val xtreamSources = xtreamAccounts.map { account ->
                ContentSource(
                    id = "xtream_${account.id}",
                    name = account.label,
                    type = SourceType.Xtream,
                    isActive = account.status == XtreamAccountStatus.Active,
                )
            }
            plexSources + xtreamSources
        }
}
