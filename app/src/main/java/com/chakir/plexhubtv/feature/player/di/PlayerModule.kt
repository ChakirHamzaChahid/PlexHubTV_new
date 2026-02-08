package com.chakir.plexhubtv.feature.player.di

import com.chakir.plexhubtv.feature.player.ExoPlayerFactory
import com.chakir.plexhubtv.feature.player.PlayerFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
abstract class PlayerModule {
    @Binds
    abstract fun bindPlayerFactory(factory: ExoPlayerFactory): PlayerFactory
}
