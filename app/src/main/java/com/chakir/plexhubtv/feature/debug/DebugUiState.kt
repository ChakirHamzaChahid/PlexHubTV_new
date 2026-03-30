package com.chakir.plexhubtv.feature.debug

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * État de l'UI pour l'écran de debug.
 * Regroupe toutes les informations de débogage système, réseau, base de données, etc.
 */
@Immutable
data class DebugUiState(
    val isLoading: Boolean = true,
    val systemInfo: SystemInfo = SystemInfo(),
    val appInfo: AppInfo = AppInfo(),
    val serverInfo: ServerInfo = ServerInfo(),
    val cacheInfo: CacheInfo = CacheInfo(),
    val databaseInfo: DatabaseInfo = DatabaseInfo(),
    val networkInfo: NetworkInfo = NetworkInfo(),
    val playbackInfo: PlaybackInfo = PlaybackInfo(),
    val deviceProfileInfo: DeviceProfileInfo = DeviceProfileInfo(),
)

/**
 * Informations système (Device, Android)
 */
@Immutable
data class SystemInfo(
    val deviceModel: String = "",
    val deviceManufacturer: String = "",
    val androidVersion: String = "",
    val apiLevel: Int = 0,
    val cpuArchitecture: String = "",
    val totalMemoryMb: Long = 0,
    val availableMemoryMb: Long = 0,
    val totalStorageGb: Long = 0,
    val availableStorageGb: Long = 0,
)

/**
 * Informations application
 */
@Immutable
data class AppInfo(
    val appVersion: String = "",
    val versionCode: Long = 0,
    val buildType: String = "",
    val packageName: String = "",
    val installTime: Long = 0,
    val lastUpdateTime: Long = 0,
)

/**
 * Informations serveurs Plex
 */
@Immutable
data class ServerInfo(
    val connectedServers: ImmutableList<ServerDetail> = persistentListOf(),
    val primaryServer: String? = null,
    val totalServers: Int = 0,
)

@Immutable
data class ServerDetail(
    val serverId: String,
    val name: String,
    val version: String,
    val isConnected: Boolean,
    val responseTimeMs: Long,
    val librariesCount: Int,
)

/**
 * Informations cache (Images, Métadonnées)
 */
@Immutable
data class CacheInfo(
    val imageCacheSizeMb: Long = 0,
    val imageCacheItemCount: Int = 0,
    val metadataCacheSizeMb: Long = 0,
    val videoCacheSizeMb: Long = 0,
    val totalCacheSizeMb: Long = 0,
)

/**
 * Informations base de données
 */
@Immutable
data class DatabaseInfo(
    val databaseSizeMb: Long = 0,
    val mediaItemsCount: Int = 0,
    val hubsCount: Int = 0,
    val librariesCount: Int = 0,
    val lastSyncTime: Long? = null,
    val databaseVersion: Int = 0,
)

/**
 * Informations réseau
 */
@Immutable
data class NetworkInfo(
    val isConnected: Boolean = false,
    val connectionType: String = "",
    val isWifi: Boolean = false,
    val signalStrength: Int = 0,
    val downloadSpeedMbps: Double = 0.0,
    val uploadSpeedMbps: Double = 0.0,
)

/**
 * Informations lecture
 */
@Immutable
data class PlaybackInfo(
    val playerEngine: String = "",
    val currentCodec: String = "",
    val currentResolution: String = "",
    val currentBitrateMbps: Double = 0.0,
    val bufferHealth: Int = 0,
    val droppedFrames: Int = 0,
    val playbackSessions: Int = 0,
)

@Immutable
data class DeviceProfileInfo(
    val videoCodecs: String = "",
    val audioCodecs: String = "",
    val maxResolution: String = "",
    val supportsHDR: Boolean = false,
    val maxBitDepth: Int = 0,
)

sealed interface DebugAction {
    data object Back : DebugAction
    data object Refresh : DebugAction
    data object ClearImageCache : DebugAction
    data object ClearMetadataCache : DebugAction
    data object ClearAllCache : DebugAction
    data object ExportLogs : DebugAction
    data object ForceSync : DebugAction
    data object TestCrash : DebugAction
}
