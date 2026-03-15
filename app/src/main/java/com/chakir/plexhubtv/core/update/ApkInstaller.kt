package com.chakir.plexhubtv.core.update

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads an APK from a URL and launches the system package installer.
 * Uses OkHttp for download with progress tracking, and FileProvider
 * for secure APK sharing with the installer.
 */
@Singleton
class ApkInstaller @Inject constructor(
    private val application: Application,
    private val okHttpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    suspend fun downloadAndInstall(downloadUrl: String, versionName: String) {
        if (_state.value is InstallState.Downloading) return

        withContext(Dispatchers.IO) {
            try {
                _state.value = InstallState.Downloading(0f)

                val updatesDir = File(application.cacheDir, "updates").apply { mkdirs() }
                val apkFile = File(updatesDir, "PlexHubTV-$versionName.apk")

                // Download APK
                val request = Request.Builder().url(downloadUrl).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    _state.value = InstallState.Error("Download failed: HTTP ${response.code}")
                    return@withContext
                }

                val body = response.body ?: run {
                    _state.value = InstallState.Error("Download failed: empty response")
                    return@withContext
                }

                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                _state.value = InstallState.Downloading(
                                    downloadedBytes.toFloat() / totalBytes
                                )
                            }
                        }
                    }
                }

                Timber.i("[Update] APK downloaded: ${apkFile.absolutePath} (${downloadedBytes} bytes)")
                _state.value = InstallState.Downloaded(apkFile)

                // Launch installer
                installApk(apkFile)
            } catch (e: Exception) {
                Timber.e(e, "[Update] APK download failed")
                _state.value = InstallState.Error(e.message ?: "Download failed")
            }
        }
    }

    private fun installApk(apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                application,
                "${application.packageName}.fileprovider",
                apkFile,
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            application.startActivity(intent)
            Timber.i("[Update] Package installer launched")
        } catch (e: Exception) {
            Timber.e(e, "[Update] Failed to launch installer")
            _state.value = InstallState.Error("Failed to launch installer: ${e.message}")
        }
    }

    fun downloadAndInstallAsync(downloadUrl: String, versionName: String) {
        scope.launch { downloadAndInstall(downloadUrl, versionName) }
    }

    fun openInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
    }

    fun reset() {
        _state.value = InstallState.Idle
    }

    fun cleanupOldApks() {
        val updatesDir = File(application.cacheDir, "updates")
        if (updatesDir.exists()) {
            updatesDir.listFiles()?.forEach { it.delete() }
        }
    }
}

sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val progress: Float) : InstallState
    data class Downloaded(val apkFile: File) : InstallState
    data class Error(val message: String) : InstallState
}
