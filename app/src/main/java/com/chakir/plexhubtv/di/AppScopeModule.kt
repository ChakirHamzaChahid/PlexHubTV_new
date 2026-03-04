package com.chakir.plexhubtv.di

import com.chakir.plexhubtv.core.di.ApplicationScope
import com.chakir.plexhubtv.core.di.DefaultDispatcher
import com.chakir.plexhubtv.handler.GlobalCoroutineExceptionHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Provides the application-wide [CoroutineScope] with crash reporting.
 *
 * Separated from CoroutineModule (in core:common) because it depends on
 * [GlobalCoroutineExceptionHandler] which requires Firebase (app-level dependency).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        globalHandler: GlobalCoroutineExceptionHandler,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher + globalHandler)
}
