package com.chakir.plexhubtv.feature.loading

import kotlinx.serialization.Serializable

enum class SyncPhase { Discovering, LibrarySync, Extras, Finalizing, Complete }

@Serializable
enum class LibraryStatus { Pending, Running, Success, Error }

@Serializable
data class SyncLibraryState(
    val key: String,
    val name: String,
    val status: LibraryStatus = LibraryStatus.Pending,
    val itemsSynced: Int = 0,
    val itemsTotal: Int = 0,
    val errorMessage: String? = null,
) {
    /** Percentage 0-100 for display, safe against division by zero. */
    val progressPercent: Int
        get() = when {
            status == LibraryStatus.Success -> 100
            itemsTotal > 0 -> (itemsSynced * 100 / itemsTotal).coerceIn(0, 100)
            else -> 0
        }
}

@Serializable
enum class ServerStatus { Pending, Running, Success, PartialSuccess, Error }

@Serializable
data class SyncServerState(
    val serverId: String,
    val serverName: String,
    val status: ServerStatus = ServerStatus.Pending,
    val libraries: List<SyncLibraryState> = emptyList(),
    val errorMessage: String? = null,
) {
    val completedLibraryCount: Int
        get() = libraries.count { it.status == LibraryStatus.Success }

    val progress: Float
        get() {
            if (libraries.isEmpty()) return 0f
            val done = libraries.count {
                it.status == LibraryStatus.Success || it.status == LibraryStatus.Error
            }.toFloat()
            val running = libraries.filter { it.status == LibraryStatus.Running }
                .sumOf {
                    if (it.itemsTotal > 0) it.itemsSynced.toDouble() / it.itemsTotal
                    else 0.0
                }.toFloat()
            return (done + running) / libraries.size
        }
}

/** Global sync state observed by the UI. Not serialized — built in the ViewModel. */
data class SyncGlobalState(
    val phase: SyncPhase,
    val servers: List<SyncServerState>,
    val currentServerIndex: Int,
    val globalProgress: Float,
) {
    val currentServer: SyncServerState?
        get() = servers.getOrNull(currentServerIndex)

    val currentLibrary: SyncLibraryState?
        get() = currentServer?.libraries?.firstOrNull { it.status == LibraryStatus.Running }

    /** Count of fully processed servers (not Pending, not Running). */
    val completedServerCount: Int
        get() = servers.count {
            it.status != ServerStatus.Pending && it.status != ServerStatus.Running
        }
}
