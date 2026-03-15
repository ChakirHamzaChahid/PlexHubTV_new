package com.chakir.plexhubtv.feature.loading

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Test

class SyncStatusModelTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── SyncServerState.progress ──

    @Test
    fun `progress - empty libraries returns 0`() {
        val server = SyncServerState(serverId = "s1", serverName = "NAS", libraries = emptyList())
        assertThat(server.progress).isEqualTo(0f)
    }

    @Test
    fun `progress - all success returns 1`() {
        val server = SyncServerState(
            serverId = "s1",
            serverName = "NAS",
            libraries = listOf(
                SyncLibraryState("1", "Films", LibraryStatus.Success, 100, 100),
                SyncLibraryState("2", "Shows", LibraryStatus.Success, 50, 50),
            ),
        )
        assertThat(server.progress).isEqualTo(1f)
    }

    @Test
    fun `progress - mix running and success computes correctly`() {
        val server = SyncServerState(
            serverId = "s1",
            serverName = "NAS",
            libraries = listOf(
                SyncLibraryState("1", "Films", LibraryStatus.Success, 100, 100),
                SyncLibraryState("2", "Shows", LibraryStatus.Running, 25, 50),
            ),
        )
        // done=1 (Success), running=0.5 (25/50), total=2 => (1 + 0.5) / 2 = 0.75
        assertThat(server.progress).isWithin(0.001f).of(0.75f)
    }

    @Test
    fun `progress - running with 0 total treated as 0 contribution`() {
        val server = SyncServerState(
            serverId = "s1",
            serverName = "NAS",
            libraries = listOf(
                SyncLibraryState("1", "Films", LibraryStatus.Running, 10, 0),
            ),
        )
        // done=0, running=0.0 (itemsTotal=0), total=1 => 0/1 = 0
        assertThat(server.progress).isEqualTo(0f)
    }

    @Test
    fun `progress - error libraries count as done`() {
        val server = SyncServerState(
            serverId = "s1",
            serverName = "NAS",
            libraries = listOf(
                SyncLibraryState("1", "Films", LibraryStatus.Error, 0, 100, "Connection lost"),
                SyncLibraryState("2", "Shows", LibraryStatus.Success, 50, 50),
            ),
        )
        // done=2 (Error+Success), running=0, total=2 => 2/2 = 1.0
        assertThat(server.progress).isEqualTo(1f)
    }

    // ── SyncServerState.completedLibraryCount ──

    @Test
    fun `completedLibraryCount - counts only Success`() {
        val server = SyncServerState(
            serverId = "s1",
            serverName = "NAS",
            libraries = listOf(
                SyncLibraryState("1", "Films", LibraryStatus.Success),
                SyncLibraryState("2", "Shows", LibraryStatus.Error),
                SyncLibraryState("3", "Music", LibraryStatus.Running),
                SyncLibraryState("4", "Anime", LibraryStatus.Pending),
            ),
        )
        assertThat(server.completedLibraryCount).isEqualTo(1)
    }

    // ── SyncGlobalState ──

    @Test
    fun `currentServer - valid index returns server`() {
        val servers = listOf(
            SyncServerState("s1", "NAS 1"),
            SyncServerState("s2", "NAS 2"),
        )
        val state = SyncGlobalState(SyncPhase.LibrarySync, servers, currentServerIndex = 1, globalProgress = 50f)
        assertThat(state.currentServer?.serverId).isEqualTo("s2")
    }

    @Test
    fun `currentServer - invalid index returns null`() {
        val state = SyncGlobalState(SyncPhase.LibrarySync, emptyList(), currentServerIndex = 5, globalProgress = 0f)
        assertThat(state.currentServer).isNull()
    }

    @Test
    fun `currentServer - negative index returns null`() {
        val servers = listOf(SyncServerState("s1", "NAS"))
        val state = SyncGlobalState(SyncPhase.Discovering, servers, currentServerIndex = -1, globalProgress = 0f)
        assertThat(state.currentServer).isNull()
    }

    @Test
    fun `currentLibrary - returns first Running library`() {
        val servers = listOf(
            SyncServerState(
                "s1", "NAS",
                libraries = listOf(
                    SyncLibraryState("1", "Films", LibraryStatus.Success),
                    SyncLibraryState("2", "Shows", LibraryStatus.Running, 10, 50),
                    SyncLibraryState("3", "Music", LibraryStatus.Pending),
                ),
            ),
        )
        val state = SyncGlobalState(SyncPhase.LibrarySync, servers, currentServerIndex = 0, globalProgress = 30f)
        assertThat(state.currentLibrary?.key).isEqualTo("2")
        assertThat(state.currentLibrary?.itemsSynced).isEqualTo(10)
    }

    @Test
    fun `currentLibrary - no Running library returns null`() {
        val servers = listOf(
            SyncServerState(
                "s1", "NAS",
                libraries = listOf(
                    SyncLibraryState("1", "Films", LibraryStatus.Success),
                    SyncLibraryState("2", "Shows", LibraryStatus.Pending),
                ),
            ),
        )
        val state = SyncGlobalState(SyncPhase.LibrarySync, servers, currentServerIndex = 0, globalProgress = 30f)
        assertThat(state.currentLibrary).isNull()
    }

    // ── Serialization round-trip ──

    @Test
    fun `serialization - single server round-trip`() {
        val original = listOf(
            SyncServerState(
                serverId = "abc123",
                serverName = "My NAS",
                status = ServerStatus.Running,
                libraries = listOf(
                    SyncLibraryState("1", "Films", LibraryStatus.Success, 800, 800),
                    SyncLibraryState("2", "TV Shows", LibraryStatus.Running, 120, 400),
                ),
            ),
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<List<SyncServerState>>(encoded)

        assertThat(decoded).hasSize(1)
        assertThat(decoded[0].serverId).isEqualTo("abc123")
        assertThat(decoded[0].serverName).isEqualTo("My NAS")
        assertThat(decoded[0].status).isEqualTo(ServerStatus.Running)
        assertThat(decoded[0].libraries).hasSize(2)
        assertThat(decoded[0].libraries[0].status).isEqualTo(LibraryStatus.Success)
        assertThat(decoded[0].libraries[1].itemsSynced).isEqualTo(120)
    }

    @Test
    fun `serialization - 20 servers fits within 10KB`() {
        val servers = (1..20).map { i ->
            SyncServerState(
                serverId = "server-$i",
                serverName = "Media Server $i",
                status = if (i <= 5) ServerStatus.Success else ServerStatus.Pending,
                libraries = (1..5).map { j ->
                    SyncLibraryState(
                        key = "lib-$j",
                        name = "Library $j",
                        status = if (j <= 2) LibraryStatus.Success else LibraryStatus.Pending,
                        itemsSynced = if (j <= 2) 100 else 0,
                        itemsTotal = 100,
                    )
                },
            )
        }
        val encoded = json.encodeToString(servers)
        assertThat(encoded.toByteArray().size).isLessThan(10_240)
    }

    @Test
    fun `serialization - error message with special characters`() {
        val original = listOf(
            SyncServerState(
                serverId = "s1",
                serverName = "Serveur d'André",
                status = ServerStatus.Error,
                errorMessage = "Connexion échouée: \"timeout\" après 30s",
            ),
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<List<SyncServerState>>(encoded)

        assertThat(decoded[0].serverName).isEqualTo("Serveur d'André")
        assertThat(decoded[0].errorMessage).isEqualTo("Connexion échouée: \"timeout\" après 30s")
    }

    @Test
    fun `serialization - empty list round-trip`() {
        val encoded = json.encodeToString(emptyList<SyncServerState>())
        val decoded = json.decodeFromString<List<SyncServerState>>(encoded)
        assertThat(decoded).isEmpty()
    }

    @Test
    fun `serialization - ignores unknown keys`() {
        val jsonWithExtra = """[{
            "serverId": "s1",
            "serverName": "NAS",
            "status": "Running",
            "libraries": [],
            "unknownField": 42
        }]"""
        val decoded = json.decodeFromString<List<SyncServerState>>(jsonWithExtra)
        assertThat(decoded).hasSize(1)
        assertThat(decoded[0].serverId).isEqualTo("s1")
    }
}
