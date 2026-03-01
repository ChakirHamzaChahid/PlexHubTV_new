package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.XtreamAccountStatus
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.BackendRepository
import com.chakir.plexhubtv.domain.repository.XtreamAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class ContentSource(
    val id: String,
    val name: String,
    val type: SourceType,
    val isActive: Boolean,
)

enum class SourceType { Plex, Xtream, Backend }

class GetAllSourcesUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val xtreamAccountRepository: XtreamAccountRepository,
    private val backendRepository: BackendRepository,
) {
    /**
     * Returns a Flow of all content sources (Plex servers + Xtream accounts + Backend servers).
     * Xtream and Backend are reactive; Plex servers are fetched as a snapshot per emission.
     */
    fun observe(): Flow<List<ContentSource>> =
        combine(
            xtreamAccountRepository.observeAccounts(),
            backendRepository.observeServers(),
        ) { xtreamAccounts, backendServers ->
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
            val backendSources = backendServers.map { backend ->
                ContentSource(
                    id = "backend_${backend.id}",
                    name = backend.label,
                    type = SourceType.Backend,
                    isActive = backend.isActive,
                )
            }
            plexSources + xtreamSources + backendSources
        }
}
