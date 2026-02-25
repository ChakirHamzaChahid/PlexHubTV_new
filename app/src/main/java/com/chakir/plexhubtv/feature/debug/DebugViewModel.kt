package com.chakir.plexhubtv.feature.debug

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import com.chakir.plexhubtv.core.database.PlexDatabase
import com.chakir.plexhubtv.domain.repository.MediaRepository
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import com.chakir.plexhubtv.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val application: Application,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository,
    private val database: PlexDatabase,
    private val imageLoader: ImageLoader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    init {
        loadDebugInfo()
    }

    fun onAction(action: DebugAction) {
        when (action) {
            is DebugAction.Back -> {
                // Navigation handled by UI
            }
            is DebugAction.Refresh -> loadDebugInfo()
            is DebugAction.ClearImageCache -> clearImageCache()
            is DebugAction.ClearMetadataCache -> clearMetadataCache()
            is DebugAction.ClearAllCache -> clearAllCache()
            is DebugAction.ExportLogs -> exportLogs()
            is DebugAction.ForceSync -> forceSync()
            is DebugAction.TestCrash -> testCrash()
        }
    }

    private fun testCrash() {
        if (BuildConfig.DEBUG) {
            FirebaseCrashlytics.getInstance().log("Test crash triggered from Debug screen")
            throw RuntimeException("Test crash from PlexHubTV Debug screen")
        }
    }

    private fun loadDebugInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val systemInfo = collectSystemInfo()
                val appInfo = collectAppInfo()
                val serverInfo = collectServerInfo()
                val cacheInfo = collectCacheInfo()
                val databaseInfo = collectDatabaseInfo()
                val networkInfo = collectNetworkInfo()
                val playbackInfo = collectPlaybackInfo()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        systemInfo = systemInfo,
                        appInfo = appInfo,
                        serverInfo = serverInfo,
                        cacheInfo = cacheInfo,
                        databaseInfo = databaseInfo,
                        networkInfo = networkInfo,
                        playbackInfo = playbackInfo
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load debug info")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun collectSystemInfo(): SystemInfo = withContext(Dispatchers.IO) {
        val activityManager = application.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return@withContext SystemInfo()
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalStorage = statFs.totalBytes / (1024 * 1024 * 1024) // GB
        val availableStorage = statFs.availableBytes / (1024 * 1024 * 1024) // GB

        SystemInfo(
            deviceModel = Build.MODEL,
            deviceManufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            cpuArchitecture = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown",
            totalMemoryMb = memoryInfo.totalMem / (1024 * 1024),
            availableMemoryMb = memoryInfo.availMem / (1024 * 1024),
            totalStorageGb = totalStorage,
            availableStorageGb = availableStorage
        )
    }

    private suspend fun collectAppInfo(): AppInfo = withContext(Dispatchers.IO) {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                application.packageManager.getPackageInfo(
                    application.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                application.packageManager.getPackageInfo(application.packageName, 0)
            }

            AppInfo(
                appVersion = packageInfo.versionName ?: "Unknown",
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                },
                buildType = if (android.os.Build.TYPE == "user") "Release" else "Debug",
                packageName = application.packageName,
                installTime = packageInfo.firstInstallTime,
                lastUpdateTime = packageInfo.lastUpdateTime
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to collect app info")
            AppInfo()
        }
    }

    private suspend fun collectServerInfo(): ServerInfo = withContext(Dispatchers.IO) {
        try {
            // This would need actual server connection logic
            // For now, return placeholder data
            ServerInfo(
                connectedServers = emptyList(),
                primaryServer = null,
                totalServers = 0
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to collect server info")
            ServerInfo()
        }
    }

    private suspend fun collectCacheInfo(): CacheInfo = withContext(Dispatchers.IO) {
        try {
            val cacheDir = application.cacheDir
            val imageCacheSize = getDirectorySize(File(cacheDir, "image_cache")) / (1024 * 1024)
            val metadataCacheSize = getDirectorySize(File(cacheDir, "okhttp")) / (1024 * 1024)
            val totalCacheSize = getDirectorySize(cacheDir) / (1024 * 1024)

            CacheInfo(
                imageCacheSizeMb = imageCacheSize,
                metadataCacheSizeMb = metadataCacheSize,
                totalCacheSizeMb = totalCacheSize
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to collect cache info")
            CacheInfo()
        }
    }

    private suspend fun collectDatabaseInfo(): DatabaseInfo = withContext(Dispatchers.IO) {
        try {
            val dbPath = application.getDatabasePath(database.openHelper.databaseName)
            val dbSize = if (dbPath.exists()) dbPath.length() / (1024 * 1024) else 0L

            // Note: These are approximations since exact count methods don't exist
            val mediaCount = 0 // No direct count method available
            val hubsCount = database.homeContentDao().getHubsList().size
            val librariesCount = 0 // No direct count method available

            DatabaseInfo(
                databaseSizeMb = dbSize,
                mediaItemsCount = mediaCount,
                hubsCount = hubsCount,
                librariesCount = librariesCount,
                lastSyncTime = System.currentTimeMillis(), // Placeholder
                databaseVersion = database.openHelper.readableDatabase.version
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to collect database info")
            DatabaseInfo()
        }
    }

    private suspend fun collectNetworkInfo(): NetworkInfo = withContext(Dispatchers.IO) {
        try {
            val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@withContext NetworkInfo()
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            val isConnected = capabilities != null
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val connectionType = when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                else -> "Unknown"
            }

            NetworkInfo(
                isConnected = isConnected,
                connectionType = connectionType,
                isWifi = isWifi,
                signalStrength = 0, // Requires additional permission
                downloadSpeedMbps = 0.0, // Would need speed test
                uploadSpeedMbps = 0.0
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to collect network info")
            NetworkInfo()
        }
    }

    private suspend fun collectPlaybackInfo(): PlaybackInfo = withContext(Dispatchers.IO) {
        try {
            val playerEngine = settingsRepository.playerEngine.first()

            PlaybackInfo(
                playerEngine = playerEngine,
                currentCodec = "N/A",
                currentResolution = "N/A",
                currentBitrateMbps = 0.0,
                bufferHealth = 0,
                droppedFrames = 0,
                playbackSessions = 0
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to collect playback info")
            PlaybackInfo()
        }
    }

    private fun getDirectorySize(directory: File): Long {
        if (!directory.exists()) return 0L
        var size = 0L
        directory.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                getDirectorySize(file)
            } else {
                file.length()
            }
        }
        return size
    }

    private fun clearImageCache() {
        viewModelScope.launch {
            try {
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
                Timber.i("Image cache cleared")
                loadDebugInfo() // Refresh info
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear image cache")
            }
        }
    }

    private fun clearMetadataCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = application.cacheDir
                val metadataCache = File(cacheDir, "okhttp")
                if (metadataCache.exists()) {
                    metadataCache.deleteRecursively()
                }
                Timber.i("Metadata cache cleared")
                withContext(Dispatchers.Main) {
                    loadDebugInfo()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear metadata cache")
            }
        }
    }

    private fun clearAllCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                application.cacheDir.deleteRecursively()
                Timber.i("All cache cleared")
                withContext(Dispatchers.Main) {
                    loadDebugInfo()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear all cache")
            }
        }
    }

    private fun exportLogs() {
        viewModelScope.launch {
            try {
                // Implement log export functionality
                Timber.i("Exporting logs...")
            } catch (e: Exception) {
                Timber.e(e, "Failed to export logs")
            }
        }
    }

    private fun forceSync() {
        viewModelScope.launch {
            try {
                Timber.i("Force sync triggered")
                // This would trigger a full resync
                loadDebugInfo()
            } catch (e: Exception) {
                Timber.e(e, "Failed to force sync")
            }
        }
    }
}
