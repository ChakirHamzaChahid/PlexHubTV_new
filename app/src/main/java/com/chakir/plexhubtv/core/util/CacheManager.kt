package com.chakir.plexhubtv.core.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        getFolderSize(context.cacheDir)
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        deleteDir(context.cacheDir)
    }

    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        if (file.isDirectory) {
            val children = file.listFiles() ?: return 0
            for (child in children) {
                size += getFolderSize(child)
            }
        } else {
            size = file.length()
        }
        return size
    }

    private fun deleteDir(dir: File): Boolean {
        if (dir.isDirectory) {
            val children = dir.listFiles() ?: return false
            for (child in children) {
                deleteDir(child)
            }
        }
        return dir.delete() || (dir.isDirectory && dir.listFiles()?.isEmpty() == true)
    }
}
