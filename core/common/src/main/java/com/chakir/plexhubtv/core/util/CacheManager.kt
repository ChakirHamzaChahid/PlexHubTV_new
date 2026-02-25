package com.chakir.plexhubtv.core.util

import android.content.Context
import com.chakir.plexhubtv.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestionnaire de cache stocké sur le système de fichiers.
 * Fournit des méthodes pour calculer la taille et vider le répertoire cache de l'application.
 */
@Singleton
class CacheManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend fun getCacheSize(): Long =
            withContext(ioDispatcher) {
                getFolderSize(context.cacheDir)
            }

        suspend fun clearCache() =
            withContext(ioDispatcher) {
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
