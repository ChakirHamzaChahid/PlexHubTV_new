package com.chakir.plexhubtv.di

import com.chakir.plexhubtv.BuildConfig
import com.chakir.plexhubtv.core.common.handler.IsDebugBuild
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing app-level dependencies that require access to BuildConfig.
 *
 * This module is in the app module, allowing access to BuildConfig for debug flags.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provides the debug build flag for dependency injection.
     *
     * Used by [GlobalCoroutineExceptionHandler] to determine whether to re-throw
     * exceptions (DEBUG) or handle them gracefully (RELEASE).
     */
    @Provides
    @Singleton
    @IsDebugBuild
    fun provideIsDebugBuild(): Boolean {
        return BuildConfig.DEBUG
    }
}
