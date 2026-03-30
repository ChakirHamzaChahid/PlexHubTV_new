package com.chakir.plexhubtv.data.di

import com.chakir.plexhubtv.data.playback.JellyfinPlaybackReporter
import com.chakir.plexhubtv.data.playback.PlexPlaybackReporter
import com.chakir.plexhubtv.domain.service.PlaybackReporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackReporterModule {
    @Binds
    @IntoSet
    abstract fun bindPlexReporter(impl: PlexPlaybackReporter): PlaybackReporter

    @Binds
    @IntoSet
    abstract fun bindJellyfinReporter(impl: JellyfinPlaybackReporter): PlaybackReporter
}
