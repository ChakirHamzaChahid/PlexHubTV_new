package com.chakir.plexhubtv.feature.player.url

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackUrlBuilderModule {
    @Binds
    @IntoSet
    abstract fun bindPlexUrlBuilder(impl: TranscodeUrlBuilder): PlaybackUrlBuilder

    @Binds
    @IntoSet
    abstract fun bindJellyfinUrlBuilder(impl: JellyfinUrlBuilder): PlaybackUrlBuilder
}
