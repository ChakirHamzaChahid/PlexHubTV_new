package com.chakir.plexhubtv.data.di

import com.chakir.plexhubtv.data.source.BackendSourceHandler
import com.chakir.plexhubtv.data.source.PlexSourceHandler
import com.chakir.plexhubtv.data.source.XtreamSourceHandler
import com.chakir.plexhubtv.domain.source.MediaSourceHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class SourceHandlerModule {
    @Binds
    @IntoSet
    abstract fun bindPlexHandler(impl: PlexSourceHandler): MediaSourceHandler

    @Binds
    @IntoSet
    abstract fun bindXtreamHandler(impl: XtreamSourceHandler): MediaSourceHandler

    @Binds
    @IntoSet
    abstract fun bindBackendHandler(impl: BackendSourceHandler): MediaSourceHandler
}
